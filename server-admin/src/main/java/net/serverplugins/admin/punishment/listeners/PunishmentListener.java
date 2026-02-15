package net.serverplugins.admin.punishment.listeners;

import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.admin.punishment.Punishment;
import net.serverplugins.admin.punishment.PunishmentManager;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PunishmentListener implements Listener {

    private final ServerAdmin plugin;

    public PunishmentListener(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PunishmentManager manager = plugin.getPunishmentManager();

        if (manager != null) {
            manager.handlePlayerJoin(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PunishmentManager manager = plugin.getPunishmentManager();

        if (manager != null) {
            manager.handlePlayerQuit(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PunishmentManager manager = plugin.getPunishmentManager();

        if (manager == null) return;

        // Handle custom reason input for punishment
        if (manager.hasPendingCustomReason(player.getUniqueId())) {
            event.setCancelled(true);
            String message = event.getMessage();
            org.bukkit.Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                manager.handleCustomReasonInput(player, message);
                            });
            return;
        }

        if (manager.isMuted(player.getUniqueId())) {
            event.setCancelled(true);

            Punishment mute = manager.getActiveMute(player.getUniqueId());
            if (mute != null) {
                String msg;
                if (mute.isPermanent()) {
                    msg = "<red>You are permanently muted.";
                } else {
                    msg = "<red>You are muted for " + mute.getFormattedRemainingTime() + ".";
                }
                if (mute.getReason() != null) {
                    msg += " <gray>Reason: <white>" + mute.getReason();
                }
                TextUtil.send(player, msg);
            } else {
                TextUtil.sendError(player, "You are muted.");
            }
        }
    }
}
