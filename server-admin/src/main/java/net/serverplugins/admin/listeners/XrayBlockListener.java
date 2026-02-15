package net.serverplugins.admin.listeners;

import net.serverplugins.admin.ServerAdmin;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class XrayBlockListener implements Listener {

    private final ServerAdmin plugin;

    public XrayBlockListener(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // Ignore creative/spectator mode
        if (player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        // Ignore staff with xray permission (they might be testing)
        if (player.hasPermission("serveradmin.xray")) {
            return;
        }

        // Track ore mining for xray detection
        if (plugin.getXrayManager() != null) {
            plugin.getXrayManager().recordBlockBreak(player, event.getBlock());
        }

        // Track spawner breaks
        if (plugin.getSpawnerManager() != null) {
            Material type = event.getBlock().getType();
            if (type == Material.SPAWNER || type == Material.TRIAL_SPAWNER) {
                plugin.getSpawnerManager().recordSpawnerBreak(player, event.getBlock());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Material type = event.getBlock().getType();

        // Mark player-placed spawners so we don't alert on them later
        if (type == Material.SPAWNER || type == Material.TRIAL_SPAWNER) {
            if (plugin.getSpawnerManager() != null) {
                plugin.getSpawnerManager().markAsPlayerPlaced(event.getBlock());
            }
        }
    }
}
