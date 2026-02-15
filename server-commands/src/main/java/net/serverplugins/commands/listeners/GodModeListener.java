package net.serverplugins.commands.listeners;

import net.serverplugins.commands.ServerCommands;
import net.serverplugins.commands.data.PlayerDataManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

public class GodModeListener implements Listener {

    private final ServerCommands plugin;

    public GodModeListener(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        PlayerDataManager.PlayerData data =
                plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data.isGodMode()) {
            event.setCancelled(true);
        }
    }
}
