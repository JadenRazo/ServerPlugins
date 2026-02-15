package net.serverplugins.claim.managers;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.Nation;
import net.serverplugins.claim.models.NationMember;
import net.serverplugins.claim.models.NationRelation;
import net.serverplugins.claim.repository.NationRepository;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class NationManager {

    private final ServerClaim plugin;
    private final NationRepository repository;
    private final Map<Integer, Nation> nationCache = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> claimToNationCache = new ConcurrentHashMap<>();

    // Flag to indicate cache preloading is complete
    private volatile boolean cacheLoaded = false;

    // Config values
    private boolean nationsEnabled;
    private double creationCost;
    private int maxMembers;
    private double maxTaxRate;
    private int inviteExpiryHours;
    private int taxCollectionIntervalHours;

    // Scheduled tasks
    private BukkitTask taxCollectionTask;
    private BukkitTask inviteCleanupTask;

    public NationManager(ServerClaim plugin, NationRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        loadConfig();
        preloadNations();
    }

    private void loadConfig() {
        nationsEnabled = plugin.getClaimConfig().isNationsEnabled();
        creationCost = plugin.getClaimConfig().getNationCreationCost();
        maxMembers = plugin.getClaimConfig().getNationMaxMembers();
        maxTaxRate = plugin.getClaimConfig().getNationMaxTaxRate();
        inviteExpiryHours = plugin.getClaimConfig().getNationInviteExpiryHours();
        taxCollectionIntervalHours = plugin.getClaimConfig().getNationTaxCollectionIntervalHours();
    }

    /** Start scheduled tasks for tax collection and invite cleanup. */
    public void start() {
        if (!nationsEnabled) {
            plugin.getLogger().info("Nations system is disabled in config");
            return;
        }

        // Schedule tax collection task (runs every tax collection interval)
        long ticksPerHour = 20L * 60 * 60;
        long taxIntervalTicks = ticksPerHour * taxCollectionIntervalHours;
        taxCollectionTask =
                plugin.getServer()
                        .getScheduler()
                        .runTaskTimerAsynchronously(
                                plugin, this::collectAllTaxes, taxIntervalTicks, taxIntervalTicks);

        // Schedule invite cleanup task (runs every hour)
        inviteCleanupTask =
                plugin.getServer()
                        .getScheduler()
                        .runTaskTimerAsynchronously(
                                plugin, this::cleanupExpiredInvites, ticksPerHour, ticksPerHour);

        plugin.getLogger()
                .info(
                        "Nation manager started - tax collection every "
                                + taxCollectionIntervalHours
                                + " hours");
    }

    /** Stop scheduled tasks. */
    public void stop() {
        if (taxCollectionTask != null) {
            taxCollectionTask.cancel();
            taxCollectionTask = null;
        }
        if (inviteCleanupTask != null) {
            inviteCleanupTask.cancel();
            inviteCleanupTask = null;
        }
    }

    /** Collect taxes from all nations. */
    private void collectAllTaxes() {
        plugin.getLogger().info("Collecting nation taxes...");

        for (Nation nation : getAllNations()) {
            double taxRate = nation.getTaxRate();
            if (taxRate <= 0) continue;

            List<NationMember> members = repository.getMembersByNation(nation.getId());
            double totalCollected = 0;

            for (NationMember member : members) {
                // Skip leader (doesn't pay taxes)
                if (member.isLeader()) continue;

                // Get the claim's bank
                Claim claim = plugin.getClaimManager().getClaimById(member.getClaimId());
                if (claim == null) continue;

                // Calculate tax amount based on claim bank balance
                double claimBalance = plugin.getBankManager().getBalance(claim.getId());
                double taxAmount = claimBalance * (taxRate / 100.0);

                if (taxAmount > 0 && claimBalance >= taxAmount) {
                    // Collect tax from claim bank
                    plugin.getBankManager()
                            .recordNationTax(claim.getId(), taxAmount, nation.getName());
                    totalCollected += taxAmount;
                }
            }

            // Add collected taxes to nation bank
            if (totalCollected > 0) {
                double currentBalance = repository.getNationBalance(nation.getId());
                repository.updateNationBalance(nation.getId(), currentBalance + totalCollected);
                repository.recordNationTransaction(
                        nation.getId(),
                        null,
                        null,
                        "TAX_COLLECTION",
                        totalCollected,
                        currentBalance + totalCollected,
                        "Tax collection from " + (members.size() - 1) + " member claims");

                plugin.getLogger()
                        .info(
                                "Nation '"
                                        + nation.getName()
                                        + "' collected $"
                                        + String.format("%.2f", totalCollected)
                                        + " in taxes");
            }
        }
    }

    /** Clean up expired invites. */
    private void cleanupExpiredInvites() {
        repository.deleteExpiredInvites();
        plugin.getLogger().info("Cleaned up expired nation invites");
    }

    private void preloadNations() {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            List<Nation> nations = repository.getAllNations();
                            for (Nation nation : nations) {
                                nationCache.put(nation.getId(), nation);
                            }

                            // Build claim -> nation mapping
                            for (Nation nation : nations) {
                                List<NationMember> members =
                                        repository.getMembersByNation(nation.getId());
                                for (NationMember member : members) {
                                    claimToNationCache.put(member.getClaimId(), nation.getId());
                                }
                            }

                            // Mark cache as loaded AFTER all data is in place
                            cacheLoaded = true;
                            plugin.getLogger()
                                    .info("Loaded " + nations.size() + " nations into cache");
                        });
    }

    public boolean isNationsEnabled() {
        return nationsEnabled;
    }

    public Nation getNation(int nationId) {
        Nation cached = nationCache.get(nationId);
        if (cached != null) {
            return cached;
        }

        // If cache is fully loaded and nation not in cache, use computeIfAbsent
        // If still loading, query directly to avoid partial cache state
        if (cacheLoaded) {
            return nationCache.computeIfAbsent(nationId, repository::getNation);
        }
        return repository.getNation(nationId);
    }

    public Nation getNationByName(String name) {
        return nationCache.values().stream()
                .filter(n -> n.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseGet(() -> repository.getNationByName(name));
    }

    public Nation getNationByTag(String tag) {
        return nationCache.values().stream()
                .filter(n -> n.getTag().equalsIgnoreCase(tag))
                .findFirst()
                .orElseGet(() -> repository.getNationByTag(tag));
    }

    public Nation getNationForClaim(int claimId) {
        Integer nationId = claimToNationCache.get(claimId);
        if (nationId != null) {
            return getNation(nationId);
        }
        return repository.getNationByClaim(claimId);
    }

    public List<Nation> getAllNations() {
        if (nationCache.isEmpty()) {
            return repository.getAllNations();
        }
        return new ArrayList<>(nationCache.values());
    }

    public void createNation(
            Player player,
            Claim claim,
            String name,
            String tag,
            Consumer<CreateNationResult> callback) {
        if (!nationsEnabled) {
            callback.accept(CreateNationResult.DISABLED);
            return;
        }

        // Validate name and tag
        if (name.length() < 3 || name.length() > 32) {
            callback.accept(CreateNationResult.INVALID_NAME);
            return;
        }

        if (tag.length() < 2 || tag.length() > 5) {
            callback.accept(CreateNationResult.INVALID_TAG);
            return;
        }

        // Check if claim is already in a nation
        if (getNationForClaim(claim.getId()) != null) {
            callback.accept(CreateNationResult.ALREADY_IN_NATION);
            return;
        }

        // Check if name/tag exists
        if (getNationByName(name) != null) {
            callback.accept(CreateNationResult.NAME_TAKEN);
            return;
        }

        if (getNationByTag(tag) != null) {
            callback.accept(CreateNationResult.TAG_TAKEN);
            return;
        }

        // Check economy
        if (plugin.getEconomy() == null || !plugin.getEconomy().has(player, creationCost)) {
            callback.accept(CreateNationResult.INSUFFICIENT_FUNDS);
            return;
        }

        // Withdraw cost
        if (!plugin.getEconomy().withdraw(player, creationCost)) {
            callback.accept(CreateNationResult.ECONOMY_ERROR);
            return;
        }

        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Nation nation = new Nation(name, tag, player.getUniqueId());
                            nation.setTotalChunks(claim.getChunks().size());
                            repository.saveNation(nation);

                            // Add founder as leader
                            NationMember member =
                                    new NationMember(
                                            nation.getId(),
                                            claim.getId(),
                                            NationMember.NationRole.LEADER);
                            repository.saveMember(member);

                            // Update caches
                            nationCache.put(nation.getId(), nation);
                            claimToNationCache.put(claim.getId(), nation.getId());

                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                // Notify BlueMap to update markers
                                                notifyBlueMapUpdate();

                                                callback.accept(CreateNationResult.SUCCESS);
                                            });
                        });
    }

    public void inviteClaim(
            Nation nation, Claim targetClaim, UUID invitedBy, Consumer<Boolean> callback) {
        if (getNationForClaim(targetClaim.getId()) != null) {
            callback.accept(false);
            return;
        }

        Instant expiresAt =
                inviteExpiryHours > 0
                        ? Instant.now().plus(Duration.ofHours(inviteExpiryHours))
                        : null;

        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            repository.createInvite(
                                    nation.getId(),
                                    targetClaim.getOwnerUuid(),
                                    invitedBy,
                                    expiresAt);
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                callback.accept(true);
                                            });
                        });
    }

    public void acceptInvite(int nationId, Claim claim, Consumer<Boolean> callback) {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            if (!repository.hasInvite(nationId, claim.getOwnerUuid())) {
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(plugin, () -> callback.accept(false));
                                return;
                            }

                            Nation nation = getNation(nationId);
                            if (nation == null) {
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(plugin, () -> callback.accept(false));
                                return;
                            }

                            // Add member
                            NationMember member =
                                    new NationMember(
                                            nationId,
                                            claim.getId(),
                                            NationMember.NationRole.MEMBER);
                            repository.saveMember(member);
                            repository.deleteInviteByNationAndPlayer(
                                    nationId, claim.getOwnerUuid());

                            // Update nation stats
                            nation.incrementMemberCount();
                            nation.addChunks(claim.getChunks().size());
                            repository.saveNation(nation);

                            // Update cache
                            claimToNationCache.put(claim.getId(), nationId);

                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                // Notify BlueMap to update markers
                                                notifyBlueMapUpdate();

                                                callback.accept(true);
                                            });
                        });
    }

    public void leaveNation(Claim claim, Consumer<Boolean> callback) {
        Nation nation = getNationForClaim(claim.getId());
        if (nation == null) {
            callback.accept(false);
            return;
        }

        // Leader cannot leave, must disband or transfer
        if (nation.isLeader(claim.getOwnerUuid())) {
            callback.accept(false);
            return;
        }

        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            repository.removeMember(claim.getId());

                            nation.decrementMemberCount();
                            nation.removeChunks(claim.getChunks().size());
                            repository.saveNation(nation);

                            claimToNationCache.remove(claim.getId());

                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                // Notify BlueMap to update markers
                                                notifyBlueMapUpdate();

                                                callback.accept(true);
                                            });
                        });
    }

    public void kickClaim(Nation nation, int claimId, Consumer<Boolean> callback) {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            NationMember member = repository.getMember(claimId);
                            if (member == null || member.getNationId() != nation.getId()) {
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(plugin, () -> callback.accept(false));
                                return;
                            }

                            // Cannot kick leader
                            if (member.isLeader()) {
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(plugin, () -> callback.accept(false));
                                return;
                            }

                            Claim claim = plugin.getClaimManager().getClaimById(claimId);
                            int chunkCount = claim != null ? claim.getChunks().size() : 0;

                            repository.removeMember(claimId);

                            nation.decrementMemberCount();
                            nation.removeChunks(chunkCount);
                            repository.saveNation(nation);

                            claimToNationCache.remove(claimId);

                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                // Notify BlueMap to update markers
                                                notifyBlueMapUpdate();

                                                callback.accept(true);
                                            });
                        });
    }

    public void promoteMember(Nation nation, int claimId, Consumer<Boolean> callback) {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            NationMember member = repository.getMember(claimId);
                            if (member == null || member.getNationId() != nation.getId()) {
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(plugin, () -> callback.accept(false));
                                return;
                            }

                            if (member.getRole() == NationMember.NationRole.MEMBER) {
                                repository.updateMemberRole(
                                        claimId, NationMember.NationRole.OFFICER);
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(plugin, () -> callback.accept(true));
                            } else {
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(plugin, () -> callback.accept(false));
                            }
                        });
    }

    public void demoteMember(Nation nation, int claimId, Consumer<Boolean> callback) {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            NationMember member = repository.getMember(claimId);
                            if (member == null || member.getNationId() != nation.getId()) {
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(plugin, () -> callback.accept(false));
                                return;
                            }

                            if (member.getRole() == NationMember.NationRole.OFFICER) {
                                repository.updateMemberRole(
                                        claimId, NationMember.NationRole.MEMBER);
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(plugin, () -> callback.accept(true));
                            } else {
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(plugin, () -> callback.accept(false));
                            }
                        });
    }

    public void disbandNation(Nation nation, Consumer<Boolean> callback) {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            // Clear all member caches
                            List<NationMember> members =
                                    repository.getMembersByNation(nation.getId());
                            for (NationMember member : members) {
                                claimToNationCache.remove(member.getClaimId());
                            }

                            repository.deleteNation(nation.getId());
                            nationCache.remove(nation.getId());

                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                // Notify BlueMap to update markers
                                                notifyBlueMapUpdate();

                                                callback.accept(true);
                                            });
                        });
    }

    public void setRelation(
            Nation nation,
            Nation target,
            NationRelation.RelationType type,
            Consumer<Boolean> callback) {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            repository.setMutualRelation(nation.getId(), target.getId(), type);
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                // Notify BlueMap to update markers (relation
                                                // changes affect colors)
                                                notifyBlueMapUpdate();

                                                callback.accept(true);
                                            });
                        });
    }

    public NationRelation.RelationType getRelation(int nationId, int targetNationId) {
        NationRelation relation = repository.getRelation(nationId, targetNationId);
        return relation != null ? relation.getRelationType() : NationRelation.RelationType.NEUTRAL;
    }

    public List<NationMember> getMembers(int nationId) {
        return repository.getMembersByNation(nationId);
    }

    public NationMember getMember(int claimId) {
        return repository.getMember(claimId);
    }

    public void depositToNationBank(
            Player player, Nation nation, double amount, Consumer<Boolean> callback) {
        if (plugin.getEconomy() == null || !plugin.getEconomy().has(player, amount)) {
            callback.accept(false);
            return;
        }

        if (!plugin.getEconomy().withdraw(player, amount)) {
            callback.accept(false);
            return;
        }

        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            double newBalance =
                                    repository.getNationBalance(nation.getId()) + amount;
                            repository.updateNationBalance(nation.getId(), newBalance);
                            repository.recordNationTransaction(
                                    nation.getId(),
                                    player.getUniqueId(),
                                    null,
                                    "DEPOSIT",
                                    amount,
                                    newBalance,
                                    "Deposit by " + player.getName());
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(plugin, () -> callback.accept(true));
                        });
    }

    public void withdrawFromNationBank(
            Player player, Nation nation, double amount, Consumer<Boolean> callback) {
        // Only leader can withdraw
        if (!nation.isLeader(player.getUniqueId())) {
            callback.accept(false);
            return;
        }

        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            double balance = repository.getNationBalance(nation.getId());
                            if (balance < amount) {
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(plugin, () -> callback.accept(false));
                                return;
                            }

                            double newBalance = balance - amount;
                            repository.updateNationBalance(nation.getId(), newBalance);
                            repository.recordNationTransaction(
                                    nation.getId(),
                                    player.getUniqueId(),
                                    null,
                                    "WITHDRAW",
                                    amount,
                                    newBalance,
                                    "Withdrawal by " + player.getName());

                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                plugin.getEconomy().deposit(player, amount);
                                                callback.accept(true);
                                            });
                        });
    }

    public double getNationBalance(int nationId) {
        return repository.getNationBalance(nationId);
    }

    public void broadcastToNation(Nation nation, String message) {
        List<NationMember> members = getMembers(nation.getId());
        for (NationMember member : members) {
            Claim claim = plugin.getClaimManager().getClaimById(member.getClaimId());
            if (claim != null) {
                OfflinePlayer owner = Bukkit.getOfflinePlayer(claim.getOwnerUuid());
                if (owner.isOnline() && owner.getPlayer() != null) {
                    TextUtil.send(owner.getPlayer(), nation.getColoredTag() + " " + message);
                }
            }
        }
    }

    public void invalidateCache(int nationId) {
        nationCache.remove(nationId);
    }

    /**
     * Notify BlueMap to update markers (if installed). Uses reflection to avoid compile-time
     * dependency.
     */
    private void notifyBlueMapUpdate() {
        try {
            Class<?> bluemapClass = Class.forName("net.serverplugins.bluemap.ServerBlueMap");
            java.lang.reflect.Method getInstanceMethod = bluemapClass.getMethod("getInstance");
            Object bluemapInstance = getInstanceMethod.invoke(null);
            if (bluemapInstance != null) {
                java.lang.reflect.Method markDirtyMethod = bluemapClass.getMethod("markDirty");
                markDirtyMethod.invoke(bluemapInstance);
            }
        } catch (ClassNotFoundException ignored) {
            // ServerBlueMap not installed
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to notify BlueMap: " + e.getMessage());
        }
    }

    public enum CreateNationResult {
        SUCCESS,
        DISABLED,
        INVALID_NAME,
        INVALID_TAG,
        NAME_TAKEN,
        TAG_TAKEN,
        ALREADY_IN_NATION,
        INSUFFICIENT_FUNDS,
        ECONOMY_ERROR
    }
}
