package net.serverplugins.admin.staffchat;

import net.serverplugins.admin.ServerAdmin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class StaffChatListener implements Listener {

    private final ServerAdmin plugin;

    public StaffChatListener(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (plugin.getStaffChatManager() == null) {
            return;
        }

        Player player = event.getPlayer();

        // Check if player has staff chat toggled
        if (plugin.getStaffChatManager().isToggled(player)) {
            event.setCancelled(true);

            // Send to staff chat (run sync)
            String message = event.getMessage();
            plugin.getServer()
                    .getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                plugin.getStaffChatManager().sendMessage(player, message);
                            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.getStaffChatManager() != null) {
            plugin.getStaffChatManager().handlePlayerQuit(event.getPlayer().getUniqueId());
        }
    }
}
