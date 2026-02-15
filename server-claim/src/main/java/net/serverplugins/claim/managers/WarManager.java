package net.serverplugins.claim.managers;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimedChunk;
import net.serverplugins.claim.models.Nation;
import net.serverplugins.claim.models.NationMember;
import net.serverplugins.claim.models.NationRelation;
import net.serverplugins.claim.models.War;
import net.serverplugins.claim.models.WarCapture;
import net.serverplugins.claim.models.WarShield;
import net.serverplugins.claim.models.WarTribute;
import net.serverplugins.claim.repository.WarRepository;
import org.bukkit.scheduler.BukkitTask;

public class WarManager {

    private final ServerClaim plugin;
    private final WarRepository repository;
    private final Map<Integer, War> activeWarsCache = new ConcurrentHashMap<>();
    private final Set<String> warZoneChunks = ConcurrentHashMap.newKeySet();
    private BukkitTask warProcessingTask;

    // Config values
    private boolean warsEnabled;
    private int declarationNoticeHours;
    private int captureTimeMinutes;
    private int warShieldDays;
    private double maxTributeAmount;

    public WarManager(ServerClaim plugin, WarRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        loadConfig();
        preloadActiveWars();
    }

    private void loadConfig() {
        warsEnabled = plugin.getClaimConfig().isWarsEnabled();
        declarationNoticeHours = plugin.getClaimConfig().getWarDeclarationNoticeHours();
        captureTimeMinutes = plugin.getClaimConfig().getWarCaptureTimeMinutes();
        warShieldDays = plugin.getClaimConfig().getWarShieldDays();
        maxTributeAmount = plugin.getClaimConfig().getWarMaxTributeAmount();
    }

    private void preloadActiveWars() {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            List<War> wars = repository.getActiveWars();
                            for (War war : wars) {
                                activeWarsCache.put(war.getId(), war);
                                updateWarZoneChunks(war);
                            }
                            plugin.getLogger().info("Loaded " + wars.size() + " active wars");
                        });
    }

    public void start() {
        if (!warsEnabled) {
            plugin.getLogger().info("War system is disabled in config");
            return;
        }

        // Process war state changes every minute
        warProcessingTask =
                plugin.getServer()
                        .getScheduler()
                        .runTaskTimerAsynchronously(plugin, this::processWars, 20L * 60, 20L * 60);

        plugin.getLogger().info("War manager started");
    }

    public void stop() {
        if (warProcessingTask != null) {
            warProcessingTask.cancel();
            warProcessingTask = null;
        }
    }

    private void processWars() {
        for (War war : new ArrayList<>(activeWarsCache.values())) {
            if (war.isDeclared()) {
                // Validate both nations still exist before activating
                Nation attacker =
                        war.getAttackerNationId() != null
                                ? plugin.getNationManager().getNation(war.getAttackerNationId())
                                : null;
                Nation defender =
                        war.getDefenderNationId() != null
                                ? plugin.getNationManager().getNation(war.getDefenderNationId())
                                : null;

                if (attacker == null || defender == null) {
                    plugin.getLogger()
                            .warning(
                                    "Cancelling war "
                                            + war.getId()
                                            + " - one or both nations no longer exist (Attacker: "
                                            + (attacker != null ? attacker.getName() : "null")
                                            + ", Defender: "
                                            + (defender != null ? defender.getName() : "null")
                                            + ")");
                    endWar(war, War.WarOutcome.TRUCE, success -> {});
                    continue;
                }

                // Check if notice period is over
                Duration sinceDeclared = Duration.between(war.getDeclaredAt(), Instant.now());
                if (sinceDeclared.toHours() >= declarationNoticeHours) {
                    war.activate();
                    repository.saveWar(war);
                    repository.recordEvent(
                            war.getId(),
                            "WAR_ACTIVE",
                            null,
                            "War is now active! Combat has begun.");
                    updateWarZoneChunks(war);

                    // Notify both sides
                    notifyWarParticipants(
                            war, "The war is now ACTIVE! PvP is enabled in contested territories.");
                }
            }

            // Decay capture progress for inactive captures
            List<WarCapture> captures = repository.getActiveCapturesForWar(war.getId());
            for (WarCapture capture : captures) {
                Duration sinceUpdate =
                        Duration.between(capture.getLastProgressUpdate(), Instant.now());
                if (sinceUpdate.toMinutes() >= 1) {
                    capture.decay(5); // Decay 5% per minute of inactivity
                    repository.saveCapture(capture);
                }
            }
        }

        // Clean up expired shields
        repository.deleteExpiredShields();
    }

    public boolean isWarsEnabled() {
        return warsEnabled;
    }

    public void declareWar(
            Nation attacker, Nation defender, String reason, Consumer<DeclareWarResult> callback) {
        if (!warsEnabled) {
            callback.accept(DeclareWarResult.DISABLED);
            return;
        }

        // Check if already at war
        War existingWar = getWarBetween(attacker.getId(), defender.getId());
        if (existingWar != null && !existingWar.isEnded()) {
            callback.accept(DeclareWarResult.ALREADY_AT_WAR);
            return;
        }

        // Check war shields
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            if (repository.hasActiveShield(defender.getId())) {
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () ->
                                                        callback.accept(
                                                                DeclareWarResult.TARGET_SHIELDED));
                                return;
                            }

                            // Create war
                            War war =
                                    new War(attacker.getId(), null, defender.getId(), null, reason);
                            repository.saveWar(war);
                            activeWarsCache.put(war.getId(), war);

                            // Set relations to AT_WAR
                            plugin.getNationManager()
                                    .setRelation(
                                            attacker,
                                            defender,
                                            NationRelation.RelationType.AT_WAR,
                                            success -> {});

                            repository.recordEvent(
                                    war.getId(),
                                    "WAR_DECLARED",
                                    null,
                                    attacker.getName()
                                            + " has declared war on "
                                            + defender.getName()
                                            + (reason != null ? ": " + reason : ""));

                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                notifyWarParticipants(
                                                        war,
                                                        attacker.getName()
                                                                + " has declared war on "
                                                                + defender.getName()
                                                                + "! Combat will begin in "
                                                                + declarationNoticeHours
                                                                + " hours.");
                                                callback.accept(DeclareWarResult.SUCCESS);
                                            });
                        });
    }

    public War getWar(int warId) {
        return activeWarsCache.computeIfAbsent(warId, repository::getWar);
    }

    public War getWarBetween(int nationId1, int nationId2) {
        for (War war : activeWarsCache.values()) {
            if (!war.isEnded() && war.involvesNation(nationId1) && war.involvesNation(nationId2)) {
                return war;
            }
        }
        return repository.getActiveWarBetween(nationId1, nationId2);
    }

    public List<War> getActiveWarsForNation(int nationId) {
        List<War> wars = new ArrayList<>();
        for (War war : activeWarsCache.values()) {
            if (!war.isEnded() && war.involvesNation(nationId)) {
                wars.add(war);
            }
        }
        return wars;
    }

    public boolean isInWarZone(String world, int chunkX, int chunkZ) {
        return warZoneChunks.contains(world + ":" + chunkX + ":" + chunkZ);
    }

    public boolean isWarPvpEnabled(Claim claim) {
        if (!warsEnabled) return false;

        Nation nation = plugin.getNationManager().getNationForClaim(claim.getId());
        if (nation == null) return false;

        List<War> wars = getActiveWarsForNation(nation.getId());
        for (War war : wars) {
            if (war.isActive()) {
                // Check if this claim's chunks are war zones
                for (ClaimedChunk chunk : claim.getChunks()) {
                    if (isInWarZone(chunk.getWorld(), chunk.getChunkX(), chunk.getChunkZ())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void updateWarZoneChunks(War war) {
        if (war.getAttackerNationId() != null) {
            addNationChunksToWarZone(war.getAttackerNationId());
        }
        if (war.getDefenderNationId() != null) {
            addNationChunksToWarZone(war.getDefenderNationId());
        }
    }

    private void addNationChunksToWarZone(int nationId) {
        List<NationMember> members = plugin.getNationManager().getMembers(nationId);
        for (NationMember member : members) {
            Claim claim = plugin.getClaimManager().getClaimById(member.getClaimId());
            if (claim != null) {
                for (ClaimedChunk chunk : claim.getChunks()) {
                    warZoneChunks.add(
                            chunk.getWorld() + ":" + chunk.getChunkX() + ":" + chunk.getChunkZ());
                }
            }
        }
    }

    public void progressCapture(War war, String world, int chunkX, int chunkZ, int nationId) {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            WarCapture capture =
                                    repository.getCapture(war.getId(), world, chunkX, chunkZ);
                            if (capture == null) {
                                capture = new WarCapture(war.getId(), world, chunkX, chunkZ);
                            }

                            capture.addProgress(WarCapture.PROGRESS_PER_TICK, nationId);
                            repository.saveCapture(capture);

                            if (capture.isCaptured()) {
                                // Chunk has been captured
                                repository.recordEvent(
                                        war.getId(),
                                        "CHUNK_CAPTURED",
                                        null,
                                        "Chunk at "
                                                + chunkX
                                                + ", "
                                                + chunkZ
                                                + " in "
                                                + world
                                                + " has been captured!");
                            }
                        });
    }

    public void contestCapture(War war, String world, int chunkX, int chunkZ) {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            WarCapture capture =
                                    repository.getCapture(war.getId(), world, chunkX, chunkZ);
                            if (capture != null && capture.isBeingCaptured()) {
                                capture.contest();
                                repository.saveCapture(capture);
                            }
                        });
    }

    public void offerTribute(
            War war,
            WarTribute.OfferingSide side,
            WarTribute.TributeType type,
            double amount,
            String message,
            Consumer<Boolean> callback) {
        if (amount > maxTributeAmount) {
            callback.accept(false);
            return;
        }

        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            WarTribute tribute =
                                    new WarTribute(war.getId(), side, type, amount, message);
                            repository.saveTribute(tribute);
                            repository.recordEvent(
                                    war.getId(),
                                    "TRIBUTE_OFFERED",
                                    null,
                                    side.name()
                                            + " has offered "
                                            + type.getDisplayName()
                                            + (amount > 0
                                                    ? " ($" + String.format("%.2f", amount) + ")"
                                                    : ""));

                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(plugin, () -> callback.accept(true));
                        });
    }

    public void acceptTribute(WarTribute tribute, Consumer<Boolean> callback) {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            tribute.accept();
                            repository.saveTribute(tribute);

                            War war = getWar(tribute.getWarId());
                            if (war != null) {
                                // Determine outcome based on tribute type
                                War.WarOutcome outcome =
                                        switch (tribute.getTributeType()) {
                                            case SURRENDER ->
                                                    tribute.getOfferingSide()
                                                                    == WarTribute.OfferingSide
                                                                            .ATTACKER
                                                            ? War.WarOutcome.DEFENDER_WIN
                                                            : War.WarOutcome.ATTACKER_WIN;
                                            default -> War.WarOutcome.TRUCE;
                                        };

                                endWar(war, outcome, success -> {});
                                repository.recordEvent(
                                        war.getId(),
                                        "TRIBUTE_ACCEPTED",
                                        null,
                                        "Tribute accepted - war ended as "
                                                + outcome.getDisplayName());
                            }

                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(plugin, () -> callback.accept(true));
                        });
    }

    public void rejectTribute(WarTribute tribute, Consumer<Boolean> callback) {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            tribute.reject();
                            repository.saveTribute(tribute);
                            repository.recordEvent(
                                    tribute.getWarId(),
                                    "TRIBUTE_REJECTED",
                                    null,
                                    "Tribute has been rejected");
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(plugin, () -> callback.accept(true));
                        });
    }

    public void endWar(War war, War.WarOutcome outcome, Consumer<Boolean> callback) {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            war.end(outcome);
                            repository.saveWar(war);
                            activeWarsCache.remove(war.getId());

                            // Clear war zone chunks
                            clearWarZoneChunks(war);

                            // Apply war shield to loser
                            Integer loserNationId =
                                    switch (outcome) {
                                        case ATTACKER_WIN, SURRENDER -> war.getDefenderNationId();
                                        case DEFENDER_WIN -> war.getAttackerNationId();
                                        default -> null;
                                    };

                            if (loserNationId != null && warShieldDays > 0) {
                                WarShield shield =
                                        WarShield.createForNation(
                                                loserNationId,
                                                warShieldDays,
                                                "Post-war immunity after "
                                                        + outcome.getDisplayName());
                                repository.createShield(shield);
                            }

                            // Update nation relations to TRUCE
                            if (war.getAttackerNationId() != null
                                    && war.getDefenderNationId() != null) {
                                Nation attacker =
                                        plugin.getNationManager()
                                                .getNation(war.getAttackerNationId());
                                Nation defender =
                                        plugin.getNationManager()
                                                .getNation(war.getDefenderNationId());
                                if (attacker != null && defender != null) {
                                    plugin.getNationManager()
                                            .setRelation(
                                                    attacker,
                                                    defender,
                                                    NationRelation.RelationType.TRUCE,
                                                    success -> {});
                                }
                            }

                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                notifyWarParticipants(
                                                        war,
                                                        "The war has ended! Outcome: "
                                                                + outcome.getDisplayName());
                                                callback.accept(true);
                                            });
                        });
    }

    private void clearWarZoneChunks(War war) {
        // Remove war zone markers - this is simplified, in production you'd
        // track which chunks belong to which war
        if (war.getAttackerNationId() != null) {
            removeNationChunksFromWarZone(war.getAttackerNationId());
        }
        if (war.getDefenderNationId() != null) {
            removeNationChunksFromWarZone(war.getDefenderNationId());
        }
    }

    private void removeNationChunksFromWarZone(int nationId) {
        // Only remove if nation is not in any other active wars
        List<War> otherWars = getActiveWarsForNation(nationId);
        if (otherWars.isEmpty()) {
            List<NationMember> members = plugin.getNationManager().getMembers(nationId);
            for (NationMember member : members) {
                Claim claim = plugin.getClaimManager().getClaimById(member.getClaimId());
                if (claim != null) {
                    for (ClaimedChunk chunk : claim.getChunks()) {
                        warZoneChunks.remove(
                                chunk.getWorld()
                                        + ":"
                                        + chunk.getChunkX()
                                        + ":"
                                        + chunk.getChunkZ());
                    }
                }
            }
        }
    }

    private void notifyWarParticipants(War war, String message) {
        if (war.getAttackerNationId() != null) {
            Nation attacker = plugin.getNationManager().getNation(war.getAttackerNationId());
            if (attacker != null) {
                plugin.getNationManager()
                        .broadcastToNation(attacker, "<red>[WAR] </red>" + message);
            }
        }
        if (war.getDefenderNationId() != null) {
            Nation defender = plugin.getNationManager().getNation(war.getDefenderNationId());
            if (defender != null) {
                plugin.getNationManager()
                        .broadcastToNation(defender, "<red>[WAR] </red>" + message);
            }
        }
    }

    public boolean hasActiveShield(int nationId) {
        return repository.hasActiveShield(nationId);
    }

    public WarShield getActiveShield(int nationId) {
        return repository.getActiveShieldForNation(nationId);
    }

    public List<WarTribute> getPendingTributes(int warId) {
        return repository.getPendingTributes(warId);
    }

    public enum DeclareWarResult {
        SUCCESS,
        DISABLED,
        ALREADY_AT_WAR,
        TARGET_SHIELDED,
        INVALID_TARGET
    }
}
