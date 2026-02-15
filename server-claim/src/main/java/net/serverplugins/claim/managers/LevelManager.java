package net.serverplugins.claim.managers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimBenefits;
import net.serverplugins.claim.models.ClaimLevel;
import net.serverplugins.claim.models.XpSource;
import net.serverplugins.claim.repository.ClaimBankRepository;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class LevelManager {

    private final ServerClaim plugin;
    private final ClaimBankRepository repository;
    private final Map<Integer, ClaimLevel> levelCache = new ConcurrentHashMap<>();
    private final Map<Integer, ClaimBenefits> benefitsCache = new ConcurrentHashMap<>();

    // Config values
    private int xpPerMinutePlaytime;
    private int xpPer100Blocks;
    private int xpPerMemberAdded;
    private int xpPerUpkeepPaid;
    private int xpPerChunkClaimed;

    // Tracking for batched block XP
    private final Map<Integer, Integer> pendingBlocksPlaced = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> pendingBlocksBroken = new ConcurrentHashMap<>();

    // Scheduled task reference for cleanup on disable
    private BukkitTask blockXpBatchTask;

    public LevelManager(ServerClaim plugin, ClaimBankRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        loadConfig();
        startBlockXpBatchTask();
    }

    private void loadConfig() {
        xpPerMinutePlaytime = plugin.getClaimConfig().getXpPerMinutePlaytime();
        xpPer100Blocks = plugin.getClaimConfig().getXpPer100Blocks();
        xpPerMemberAdded = plugin.getClaimConfig().getXpPerMemberAdded();
        xpPerUpkeepPaid = plugin.getClaimConfig().getXpPerUpkeepPaid();
        xpPerChunkClaimed = plugin.getClaimConfig().getXpPerChunkClaimed();
    }

    private void startBlockXpBatchTask() {
        // Process batched block XP every minute
        blockXpBatchTask =
                plugin.getServer()
                        .getScheduler()
                        .runTaskTimerAsynchronously(
                                plugin,
                                () -> {
                                    processBlockXpBatch(
                                            pendingBlocksPlaced, XpSource.BLOCKS_PLACED);
                                    processBlockXpBatch(
                                            pendingBlocksBroken, XpSource.BLOCKS_BROKEN);
                                },
                                20L * 60,
                                20L * 60);
    }

    /** Stop the batch XP task and flush remaining batched XP. Call this on plugin disable. */
    public void stop() {
        if (blockXpBatchTask != null) {
            blockXpBatchTask.cancel();
            blockXpBatchTask = null;
        }
        // Flush remaining batched XP before shutdown
        processBlockXpBatch(pendingBlocksPlaced, XpSource.BLOCKS_PLACED);
        processBlockXpBatch(pendingBlocksBroken, XpSource.BLOCKS_BROKEN);
    }

    private void processBlockXpBatch(Map<Integer, Integer> pending, XpSource source) {
        for (Map.Entry<Integer, Integer> entry : pending.entrySet()) {
            int claimId = entry.getKey();
            int blocks = entry.getValue();

            if (blocks >= 100) {
                int xpToGrant = (blocks / 100) * xpPer100Blocks;
                grantXpInternal(claimId, null, xpToGrant, source);
                pending.put(claimId, blocks % 100);
            }
        }
    }

    public ClaimLevel getLevel(int claimId) {
        return levelCache.computeIfAbsent(claimId, repository::getOrCreateLevel);
    }

    public ClaimBenefits getBenefits(int claimId) {
        return benefitsCache.computeIfAbsent(
                claimId,
                id -> {
                    ClaimBenefits benefits = repository.getBenefits(id);
                    if (benefits == null) {
                        ClaimLevel level = getLevel(id);
                        benefits = ClaimBenefits.forLevel(id, level.getLevel());
                        repository.saveBenefits(benefits);
                    }
                    return benefits;
                });
    }

    public void grantXp(int claimId, UUID playerUuid, XpSource source) {
        int amount = getXpAmount(source);
        if (amount > 0) {
            plugin.getServer()
                    .getScheduler()
                    .runTaskAsynchronously(
                            plugin,
                            () -> {
                                grantXpInternal(claimId, playerUuid, amount, source);
                            });
        }
    }

    public void grantXp(int claimId, UUID playerUuid, int amount, XpSource source) {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            grantXpInternal(claimId, playerUuid, amount, source);
                        });
    }

    private void grantXpInternal(int claimId, UUID playerUuid, int amount, XpSource source) {
        ClaimLevel level = getLevel(claimId);
        int oldLevel = level.getLevel();

        boolean leveledUp = level.addXp(amount);
        repository.saveLevel(level);
        repository.recordXpGain(claimId, playerUuid, amount, source);

        if (leveledUp) {
            int newLevel = level.getLevel();
            ClaimBenefits benefits = ClaimBenefits.forLevel(claimId, newLevel);
            benefitsCache.put(claimId, benefits);
            repository.saveBenefits(benefits);

            // Notify owner of level up
            Claim claim = plugin.getClaimManager().getClaimById(claimId);
            if (claim != null) {
                notifyLevelUp(claim, oldLevel, newLevel);
            }
        }
    }

    private void notifyLevelUp(Claim claim, int oldLevel, int newLevel) {
        plugin.getServer()
                .getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            OfflinePlayer owner = Bukkit.getOfflinePlayer(claim.getOwnerUuid());
                            if (owner.isOnline() && owner.getPlayer() != null) {
                                Player player = owner.getPlayer();
                                TextUtil.send(
                                        player,
                                        plugin.getClaimConfig().getMessage("prefix")
                                                + "<gold>Your claim '<yellow>"
                                                + claim.getName()
                                                + "</yellow>' leveled up! "
                                                + "<gray>"
                                                + oldLevel
                                                + "</gray> -> <green>"
                                                + newLevel
                                                + "</green>");

                                ClaimBenefits benefits = getBenefits(claim.getId());
                                TextUtil.send(
                                        player,
                                        "<gray>New benefits: "
                                                + "<white>"
                                                + benefits.getMaxMemberSlots()
                                                + "</white> member slots, "
                                                + "<white>"
                                                + benefits.getMaxWarpSlots()
                                                + "</white> warp slots, "
                                                + "<white>"
                                                + String.format(
                                                        "%.0f", benefits.getUpkeepDiscountPercent())
                                                + "%</white> upkeep discount");
                            }
                        });
    }

    public void trackBlockPlaced(int claimId) {
        pendingBlocksPlaced.merge(claimId, 1, Integer::sum);
    }

    public void trackBlockBroken(int claimId) {
        pendingBlocksBroken.merge(claimId, 1, Integer::sum);
    }

    public void trackPlaytime(int claimId, UUID playerUuid, int minutes) {
        int xp = minutes * xpPerMinutePlaytime;
        if (xp > 0) {
            grantXp(claimId, playerUuid, xp, XpSource.PLAYTIME);
        }
    }

    private int getXpAmount(XpSource source) {
        return switch (source) {
            case PLAYTIME -> xpPerMinutePlaytime;
            case BLOCKS_PLACED, BLOCKS_BROKEN -> xpPer100Blocks;
            case MEMBER_ADDED -> xpPerMemberAdded;
            case UPKEEP_PAID -> xpPerUpkeepPaid;
            case CHUNK_CLAIMED -> xpPerChunkClaimed;
            default -> source.getDefaultAmount();
        };
    }

    public void invalidateCache(int claimId) {
        levelCache.remove(claimId);
        benefitsCache.remove(claimId);
    }

    public int getMaxMemberSlots(int claimId) {
        return getBenefits(claimId).getMaxMemberSlots();
    }

    public int getMaxWarpSlots(int claimId) {
        return getBenefits(claimId).getMaxWarpSlots();
    }

    public double getUpkeepDiscount(int claimId) {
        return getBenefits(claimId).getUpkeepDiscountPercent();
    }

    public int getWelcomeMessageLength(int claimId) {
        return getBenefits(claimId).getWelcomeMessageLength();
    }
}
