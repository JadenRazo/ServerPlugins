package net.serverplugins.admin.listeners;

import net.serverplugins.admin.ServerAdmin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class AdminPlayerListener implements Listener {

    private final ServerAdmin plugin;

    public AdminPlayerListener(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Update vanish visibility for the joining player
        plugin.getVanishManager().handlePlayerJoin(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Handle vanish state
        plugin.getVanishManager().handlePlayerQuit(player);

        // Handle spectate - both as spectator and as target
        plugin.getSpectateManager().handleTargetQuit(player);
        plugin.getSpectateManager().handleSpectatorQuit(player);

        // Handle night vision cleanup
        plugin.getNightVisionManager().handleQuit(player);

        // Handle xray vision cleanup
        if (plugin.getXrayVisionManager() != null) {
            plugin.getXrayVisionManager().handleQuit(player);
        }

        // Handle disguise cleanup
        if (plugin.getDisguiseManager() != null) {
            plugin.getDisguiseManager().handleQuit(player);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        // Only handle when starting to sneak, not when releasing
        if (event.isSneaking()) {
            Player player = event.getPlayer();
            // Check if player is in POV mode and exit if so
            plugin.getSpectateManager().handleSneak(player);
        }
    }
}
