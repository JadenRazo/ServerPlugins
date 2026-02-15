package net.serverplugins.claim.listeners;

import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Listener for notification events (e.g., showing unread count on join). */
public class NotificationListener implements Listener {

    private final ServerClaim plugin;

    public NotificationListener(ServerClaim plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Get unread count asynchronously
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            int unreadCount =
                                    plugin.getNotificationManager()
                                            .getUnreadCount(player.getUniqueId());

                            if (unreadCount > 0) {
                                // Notify player on main thread
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    TextUtil.send(
                                                            player,
                                                            "<gray>[<yellow>!<gray>] <yellow>You have <gold>"
                                                                    + unreadCount
                                                                    + " <yellow>unread notification(s)!");
                                                    TextUtil.send(
                                                            player,
                                                            "<gray>Type <yellow>/claim notifications <gray>to view them");
                                                });
                            }
                        });
    }
}
