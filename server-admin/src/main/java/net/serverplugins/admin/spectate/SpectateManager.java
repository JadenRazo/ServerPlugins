package net.serverplugins.admin.spectate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.admin.vanish.VanishMode;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class SpectateManager {

    private final ServerAdmin plugin;
    private final Map<UUID, SpectateSession> activeSessions;

    public SpectateManager(ServerAdmin plugin) {
        this.plugin = plugin;
        this.activeSessions = new ConcurrentHashMap<>();
    }

    public boolean startSpectating(
            Player spectator, Player target, SpectateSession.SpectateType type) {
        if (spectator.equals(target)) {
            plugin.getAdminConfig()
                    .getMessenger()
                    .sendError(spectator, "You cannot spectate yourself!");
            return false;
        }

        if (isSpectating(spectator)) {
            stopSpectating(spectator);
        }

        // Store previous state
        boolean wasVanished = plugin.getVanishManager().isVanished(spectator);
        SpectateSession session =
                new SpectateSession(
                        spectator.getUniqueId(),
                        target.getUniqueId(),
                        spectator.getLocation().clone(),
                        spectator.getGameMode(),
                        spectator.isFlying(),
                        spectator.getAllowFlight(),
                        wasVanished,
                        type);

        activeSessions.put(spectator.getUniqueId(), session);

        // Auto-vanish if configured
        if (plugin.getAdminConfig().autoVanish() && !wasVanished) {
            plugin.getVanishManager().vanish(spectator, VanishMode.FULL);
        }

        // Set spectator mode
        spectator.setGameMode(GameMode.SPECTATOR);

        // Handle based on type
        switch (type) {
            case SPECTATE:
                spectator.teleport(target.getLocation());
                // Note: setSpectatorTarget() only works if target is also in spectator mode
                // For admin spectating, we just teleport and let them follow manually
                break;
            case POV:
                // Start the tick-based POV following task for camera lock
                startPovFollowTask(spectator, target, session);
                break;
            case FREECAM:
                // Just put in spectator mode at current location
                break;
        }

        // Send message
        String message =
                plugin.getAdminConfig().getSpectateStartMsg().replace("%player%", target.getName());
        TextUtil.send(spectator, plugin.getAdminConfig().getPrefix() + message);

        return true;
    }

    public void stopSpectating(Player spectator) {
        SpectateSession session = activeSessions.remove(spectator.getUniqueId());
        if (session == null) {
            return;
        }

        // Cancel POV following task if active
        cancelPovTask(session);

        // Clear spectator target
        spectator.setSpectatorTarget(null);

        // Restore previous game mode
        spectator.setGameMode(session.getPreviousGameMode());

        // Restore location if configured
        if (plugin.getAdminConfig().restoreLocation() && session.getPreviousLocation() != null) {
            spectator.teleport(session.getPreviousLocation());
        }

        // Restore flight state
        spectator.setAllowFlight(session.wasAllowFlight());
        spectator.setFlying(session.wasFlying());

        // Handle vanish state
        if (!session.wasVanished() && plugin.getVanishManager().isVanished(spectator)) {
            plugin.getVanishManager().unvanish(spectator);
        }

        // Send message
        TextUtil.send(
                spectator,
                plugin.getAdminConfig().getPrefix() + plugin.getAdminConfig().getSpectateEndMsg());
    }

    /**
     * Starts a tick-based task that continuously syncs the spectator's position and view direction
     * with the target player, creating a camera lock effect. Optimized to run every 3 ticks (150ms)
     * with delta checking.
     */
    private void startPovFollowTask(Player spectator, Player target, SpectateSession session) {
        // Initial teleport to target's exact position and view
        teleportToTargetView(spectator, target);
        updateSessionLocation(session, target);

        // Show initial hint
        sendPovActionBar(spectator, target.getName());

        // Counter for action bar updates (every 40 ticks = 2 seconds)
        final int[] tickCounter = {0};

        // Create a repeating task that syncs position and rotation every 3 ticks
        BukkitTask task =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                plugin,
                                () -> {
                                    // Check if spectator is still online and in this session
                                    if (!spectator.isOnline()) {
                                        cancelPovTask(session);
                                        return;
                                    }

                                    // Check if target is still online
                                    Player currentTarget = Bukkit.getPlayer(session.getTargetId());
                                    if (currentTarget == null || !currentTarget.isOnline()) {
                                        return; // Target offline - handled by handleTargetQuit
                                    }

                                    // Handle cross-world following (always sync on world change)
                                    if (!spectator.getWorld().equals(currentTarget.getWorld())) {
                                        spectator.teleport(currentTarget.getLocation());
                                        updateSessionLocation(session, currentTarget);
                                    } else {
                                        // Only sync if target has moved or rotated enough
                                        if (needsPositionSync(session, currentTarget)) {
                                            teleportToTargetView(spectator, currentTarget);
                                            updateSessionLocation(session, currentTarget);
                                        }
                                    }

                                    // Update action bar hint every 40 ticks (2 seconds) to keep it
                                    // visible
                                    tickCounter[0] += 3;
                                    if (tickCounter[0] >= 40) {
                                        tickCounter[0] = 0;
                                        sendPovActionBar(spectator, currentTarget.getName());
                                    }
                                },
                                0L,
                                3L); // Every 3 ticks (150ms) for optimized following

        // Store task ID in session for later cancellation
        session.setPovTaskId(task.getTaskId());
    }

    /** Sends the POV action bar hint to the spectator. */
    private void sendPovActionBar(Player spectator, String targetName) {
        String message =
                "<gold>POV: <white>"
                        + targetName
                        + "<gray> | <yellow>Sneak<gray> or <yellow>/pov<gray> to exit";
        TextUtil.sendActionBar(spectator, message);
    }

    /** Called when a player sneaks while in POV mode to exit. */
    public void handleSneak(Player player) {
        SpectateSession session = activeSessions.get(player.getUniqueId());
        if (session != null && session.getType() == SpectateSession.SpectateType.POV) {
            stopSpectating(player);
        }
    }

    /**
     * Checks if the target has moved enough to warrant a position sync. Returns true if the target
     * moved more than 0.1 blocks OR rotated more than 2 degrees.
     */
    private boolean needsPositionSync(SpectateSession session, Player target) {
        Location lastLoc = session.getLastSyncedLocation();

        // First sync always needed
        if (lastLoc == null) {
            return true;
        }

        Location currentLoc = target.getLocation();

        // Check position delta (0.1 blocks threshold)
        double distanceSquared = lastLoc.distanceSquared(currentLoc);
        if (distanceSquared > 0.01) { // 0.1 * 0.1 = 0.01
            return true;
        }

        // Check rotation delta (2 degrees threshold)
        float yawDelta = Math.abs(currentLoc.getYaw() - session.getLastSyncedYaw());
        float pitchDelta = Math.abs(currentLoc.getPitch() - session.getLastSyncedPitch());

        // Handle yaw wrapping (359 -> 1 = 2 degrees, not 358)
        if (yawDelta > 180) {
            yawDelta = 360 - yawDelta;
        }

        return yawDelta > 2.0f || pitchDelta > 2.0f;
    }

    /** Updates the cached location data in the session after a sync. */
    private void updateSessionLocation(SpectateSession session, Player target) {
        Location loc = target.getLocation();
        session.setLastSyncedLocation(loc.clone());
        session.setLastSyncedYaw(loc.getYaw());
        session.setLastSyncedPitch(loc.getPitch());
    }

    /** Teleports the spectator to the target's eye location with matching view direction. */
    private void teleportToTargetView(Player spectator, Player target) {
        Location loc = target.getLocation().clone();
        // Adjust to eye location for true first-person view
        loc.setY(target.getEyeLocation().getY());
        // Copy exact yaw and pitch for view direction
        loc.setYaw(target.getLocation().getYaw());
        loc.setPitch(target.getLocation().getPitch());
        spectator.teleport(loc);
    }

    /** Cancels the POV following task if one is active for this session. */
    private void cancelPovTask(SpectateSession session) {
        if (session != null && session.hasPovTask()) {
            Bukkit.getScheduler().cancelTask(session.getPovTaskId());
            session.setPovTaskId(-1);
        }
    }

    public boolean isSpectating(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    public SpectateSession getSession(Player player) {
        return activeSessions.get(player.getUniqueId());
    }

    public Player getTarget(Player spectator) {
        SpectateSession session = activeSessions.get(spectator.getUniqueId());
        if (session == null) return null;
        return Bukkit.getPlayer(session.getTargetId());
    }

    public void handleTargetQuit(Player target) {
        UUID targetId = target.getUniqueId();
        for (Map.Entry<UUID, SpectateSession> entry : activeSessions.entrySet()) {
            if (entry.getValue().getTargetId().equals(targetId)) {
                // Always cancel POV task when target quits
                cancelPovTask(entry.getValue());

                if (plugin.getAdminConfig().exitOnTargetQuit()) {
                    Player spectator = Bukkit.getPlayer(entry.getKey());
                    if (spectator != null) {
                        plugin.getAdminConfig()
                                .getMessenger()
                                .sendWarning(
                                        spectator, "Target disconnected, exiting spectate mode.");
                        stopSpectating(spectator);
                    }
                }
            }
        }
    }

    public void handleSpectatorQuit(Player spectator) {
        SpectateSession session = activeSessions.remove(spectator.getUniqueId());
        if (session != null) {
            cancelPovTask(session);
        }
    }

    public void shutdown() {
        for (Map.Entry<UUID, SpectateSession> entry : activeSessions.entrySet()) {
            // Cancel POV tasks first
            cancelPovTask(entry.getValue());

            Player spectator = Bukkit.getPlayer(entry.getKey());
            if (spectator != null) {
                stopSpectating(spectator);
            }
        }
        activeSessions.clear();
    }
}
