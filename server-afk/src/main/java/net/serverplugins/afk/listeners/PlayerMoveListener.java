package net.serverplugins.afk.listeners;

import java.util.Optional;
import net.serverplugins.afk.ServerAFK;
import net.serverplugins.afk.managers.PlayerTracker;
import net.serverplugins.afk.managers.ZoneManager;
import net.serverplugins.afk.models.AfkZone;
import net.serverplugins.afk.models.PlayerAfkSession;
import net.serverplugins.api.messages.Placeholder;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class PlayerMoveListener implements Listener {

    private final ServerAFK plugin;

    public PlayerMoveListener(ServerAFK plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only check if block position changed
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        handleMovement(event.getPlayer(), to);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to == null) return;

        handleMovement(event.getPlayer(), to);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getPlayerTracker().endSession(event.getPlayer());
    }

    private void handleMovement(Player player, Location to) {
        if (!player.hasPermission("serverafk.use")) {
            return;
        }

        ZoneManager zoneManager = plugin.getZoneManager();
        PlayerTracker tracker = plugin.getPlayerTracker();
        int yForgiveness = plugin.getAfkConfig().getYAxisForgiveness();

        Optional<AfkZone> zoneAtLocation = zoneManager.getZoneAt(to);
        Optional<PlayerAfkSession> currentSession = tracker.getSessionOptional(player);

        // Case 1: Player is in a session and still in the same zone
        if (currentSession.isPresent() && zoneAtLocation.isPresent()) {
            AfkZone currentZone = currentSession.get().getCurrentZone();
            AfkZone newZone = zoneAtLocation.get();

            if (currentZone.getId() == newZone.getId()) {
                PlayerAfkSession session = currentSession.get();
                int currentY = to.getBlockY();
                int initialY = session.getInitialY();

                // Handle sessions created before Y-axis tracking was added
                if (initialY == 0) {
                    session.setInitialY(currentY);
                    return;
                }

                // Check Y-axis forgiveness
                int yDiff = Math.abs(currentY - initialY);

                if (yDiff > yForgiveness) {
                    // Player moved too far vertically, end session
                    tracker.endSession(player);
                    plugin.getAfkConfig().getMessenger().send(player, "zone-leave");
                    return;
                }

                // Still in same zone and within Y forgiveness, do nothing
                return;
            }

            // Moved to different zone - end old session and start new one
            tracker.endSession(player);
            plugin.getAfkConfig().getMessenger().send(player, "zone-leave");

            tracker.startSession(player, newZone, to.getBlockY());
            plugin.getAfkConfig()
                    .getMessenger()
                    .send(player, "zone-enter", Placeholder.of("zone", newZone.getName()));
            return;
        }

        // Case 2: Player is in a session but left all zones
        if (currentSession.isPresent() && zoneAtLocation.isEmpty()) {
            tracker.endSession(player);
            plugin.getAfkConfig().getMessenger().send(player, "zone-leave");
            return;
        }

        // Case 3: Player has no session but entered a zone
        if (currentSession.isEmpty() && zoneAtLocation.isPresent()) {
            AfkZone zone = zoneAtLocation.get();
            tracker.startSession(player, zone, to.getBlockY());
            plugin.getAfkConfig()
                    .getMessenger()
                    .send(player, "zone-enter", Placeholder.of("zone", zone.getName()));
        }

        // Case 4: No session and not in any zone - do nothing
    }
}
