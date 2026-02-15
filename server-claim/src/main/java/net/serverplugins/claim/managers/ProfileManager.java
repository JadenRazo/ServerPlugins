package net.serverplugins.claim.managers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimProfile;
import net.serverplugins.claim.models.ClaimSettings;
import net.serverplugins.claim.models.ProfileColor;
import net.serverplugins.claim.repository.ClaimRepository;
import org.bukkit.entity.Player;

public class ProfileManager {

    private final Map<UUID, ClaimProfile> pendingRenameInputs = new ConcurrentHashMap<>();

    private final ServerClaim plugin;
    private final ClaimRepository repository;

    public ProfileManager(ServerClaim plugin, ClaimRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public int getMaxProfiles(Player player) {
        int maxCheck = plugin.getClaimConfig().getMaxProfileCheck();

        plugin.getLogger()
                .info(
                        "[DEBUG] Checking max profiles for "
                                + player.getName()
                                + " (maxCheck="
                                + maxCheck
                                + ")");

        // Check permissions from highest to lowest
        for (int i = maxCheck; i >= 1; i--) {
            boolean hasPerm = player.hasPermission("serverclaim.profiles." + i);
            plugin.getLogger()
                    .info(
                            "[DEBUG] "
                                    + player.getName()
                                    + " hasPermission(serverclaim.profiles."
                                    + i
                                    + ") = "
                                    + hasPerm);
            if (hasPerm) {
                plugin.getLogger()
                        .info("[DEBUG] Returning maxProfiles=" + i + " for " + player.getName());
                return i;
            }
        }

        int defaultMax = plugin.getClaimConfig().getDefaultMaxProfiles();
        plugin.getLogger()
                .info(
                        "[DEBUG] No permission found, returning default="
                                + defaultMax
                                + " for "
                                + player.getName());
        return defaultMax;
    }

    public boolean canCreateProfile(Player player, Claim claim) {
        int max = getMaxProfiles(player);
        return claim.getProfiles().size() < max;
    }

    public ClaimProfile createProfile(Claim claim, String name, ProfileColor color) {
        int slotIndex = claim.getProfiles().size();
        ClaimProfile profile = new ClaimProfile(claim.getId(), name, color, slotIndex);

        profile.setSettings(
                new ClaimSettings(
                        plugin.getClaimConfig().getDefaultPvp(),
                        plugin.getClaimConfig().getDefaultFireSpread(),
                        plugin.getClaimConfig().getDefaultExplosions(),
                        plugin.getClaimConfig().getDefaultHostileSpawns(),
                        plugin.getClaimConfig().getDefaultMobGriefing(),
                        plugin.getClaimConfig().getDefaultPassiveSpawns(),
                        false, // crop trampling disabled by default (crops protected)
                        true // leaf decay enabled by default
                        ));

        repository.saveProfile(profile);
        claim.addProfile(profile);

        return profile;
    }

    public void setActiveProfile(Claim claim, ClaimProfile profile) {
        for (ClaimProfile p : claim.getProfiles()) {
            if (p.isActive()) {
                p.setActive(false);
                repository.saveProfile(p);
            }
        }

        profile.setActive(true);
        repository.saveProfile(profile);
    }

    public void updateProfileSettings(ClaimProfile profile) {
        repository.saveProfileSettings(profile);
    }

    public void updateProfile(ClaimProfile profile) {
        repository.saveProfile(profile);
    }

    public void trustPlayer(Claim claim, Player player) {
        repository.trustPlayer(claim.getId(), player.getUniqueId());
        claim.trustPlayer(player.getUniqueId());
    }

    public void untrustPlayer(Claim claim, Player player) {
        repository.untrustPlayer(claim.getId(), player.getUniqueId());
        claim.untrustPlayer(player.getUniqueId());
    }

    public void awaitRenameInput(Player player, ClaimProfile profile) {
        pendingRenameInputs.put(player.getUniqueId(), profile);
    }

    public ClaimProfile getPendingRenameInput(UUID uuid) {
        return pendingRenameInputs.remove(uuid);
    }

    public boolean hasPendingRenameInput(UUID uuid) {
        return pendingRenameInputs.containsKey(uuid);
    }

    /** Clears all pending inputs for a player. Call on player quit to prevent memory leaks. */
    public void clearPendingInputs(UUID uuid) {
        pendingRenameInputs.remove(uuid);
    }

    public void renameProfile(ClaimProfile profile, String newName) {
        profile.setName(newName);
        repository.saveProfile(profile);
    }

    public void deleteProfile(Claim claim, ClaimProfile profile) {
        boolean wasActive = profile.isActive();

        // Remove from database
        repository.deleteProfile(profile);

        // Remove from claim
        claim.removeProfile(profile);

        // If it was active, activate another profile if available
        if (wasActive && !claim.getProfiles().isEmpty()) {
            ClaimProfile newActive = claim.getProfiles().get(0);
            setActiveProfile(claim, newActive);
        }
    }

    // Async variants for GUI-triggered operations

    public void trustPlayerAsync(Claim claim, UUID playerUuid, Runnable callback) {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            repository.trustPlayer(claim.getId(), playerUuid);

                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                claim.trustPlayer(playerUuid);
                                                if (callback != null) {
                                                    callback.run();
                                                }
                                            });
                        });
    }

    public void untrustPlayerAsync(Claim claim, UUID playerUuid, Runnable callback) {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            repository.untrustPlayer(claim.getId(), playerUuid);

                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                claim.untrustPlayer(playerUuid);
                                                if (callback != null) {
                                                    callback.run();
                                                }
                                            });
                        });
    }

    public void renameProfileAsync(ClaimProfile profile, String newName, Runnable callback) {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            ClaimProfile updatedProfile = profile;
                            updatedProfile.setName(newName);
                            repository.saveProfile(updatedProfile);

                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                if (callback != null) {
                                                    callback.run();
                                                }
                                            });
                        });
    }

    public void deleteProfileAsync(Claim claim, ClaimProfile profile, Runnable callback) {
        boolean wasActive = profile.isActive();

        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            repository.deleteProfile(profile);

                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                claim.removeProfile(profile);

                                                if (wasActive && !claim.getProfiles().isEmpty()) {
                                                    ClaimProfile newActive =
                                                            claim.getProfiles().get(0);
                                                    setActiveProfile(claim, newActive);
                                                }

                                                if (callback != null) {
                                                    callback.run();
                                                }
                                            });
                        });
    }

    public void updateProfileSettingsAsync(ClaimProfile profile, Runnable callback) {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            repository.saveProfileSettings(profile);

                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                if (callback != null) {
                                                    callback.run();
                                                }
                                            });
                        });
    }

    public void setActiveProfileAsync(Claim claim, ClaimProfile profile, Runnable callback) {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            // Deactivate all other profiles
                            for (ClaimProfile p : claim.getProfiles()) {
                                if (p.isActive()) {
                                    p.setActive(false);
                                    repository.saveProfile(p);
                                }
                            }

                            // Activate the target profile
                            profile.setActive(true);
                            repository.saveProfile(profile);

                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                if (callback != null) {
                                                    callback.run();
                                                }
                                            });
                        });
    }
}
