package net.serverplugins.admin.alts;

import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class AltListener implements Listener {

    private final ServerAdmin plugin;

    public AltListener(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.getAltManager() == null || !plugin.getAltManager().isDatabaseAvailable()) {
            return;
        }

        Player player = event.getPlayer();

        // Record IP (already async in AltManager)
        plugin.getAltManager().recordPlayer(player);

        // Check for alts and notify staff (async)
        plugin.getAltManager()
                .getAlts(player.getUniqueId())
                .thenAccept(
                        alts -> {
                            if (alts.isEmpty()) {
                                return;
                            }

                            // Build notification message
                            StringBuilder altNames = new StringBuilder();
                            boolean hasBannedAlt = false;

                            for (int i = 0; i < alts.size(); i++) {
                                AltAccount alt = alts.get(i);
                                if (i > 0) altNames.append("<gray>, ");

                                if (alt.isBanned()) {
                                    altNames.append("<red><strikethrough>")
                                            .append(alt.getName())
                                            .append("</strikethrough></red>");
                                    hasBannedAlt = true;
                                } else if (alt.isOnline()) {
                                    altNames.append("<green>")
                                            .append(alt.getName())
                                            .append("</green>");
                                } else {
                                    altNames.append("<gray>")
                                            .append(alt.getName())
                                            .append("</gray>");
                                }
                            }

                            String prefix =
                                    hasBannedAlt
                                            ? "<red>[Alt Alert]</red>"
                                            : "<yellow>[Alt]</yellow>";
                            String message =
                                    prefix
                                            + " <white>"
                                            + player.getName()
                                            + "</white> <gray>has <white>"
                                            + alts.size()
                                            + "</white> alt(s): "
                                            + altNames;

                            // Notify staff on main thread
                            Bukkit.getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                for (Player staff : Bukkit.getOnlinePlayers()) {
                                                    if (staff.hasPermission("serveradmin.alts")) {
                                                        TextUtil.send(staff, message);
                                                    }
                                                }
                                                plugin.getLogger()
                                                        .info(
                                                                "[Alt] "
                                                                        + player.getName()
                                                                        + " has "
                                                                        + alts.size()
                                                                        + " alt(s)");
                                            });
                        })
                .exceptionally(
                        ex -> {
                            plugin.getLogger()
                                    .warning(
                                            "Failed to check alts for "
                                                    + player.getName()
                                                    + ": "
                                                    + ex.getMessage());
                            return null;
                        });
    }
}
