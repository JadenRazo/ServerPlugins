package net.serverplugins.admin.redis;

import com.google.gson.JsonObject;
import java.util.Set;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Handles incoming server control signals from Velocity via Redis.
 *
 * <p>Listens on channel: server:admin:servercontrol
 *
 * <p>Message format:
 *
 * <pre>{@code
 * {
 *   "type": "SHUTDOWN" or "RESTART",
 *   "server": "smp" or "lobby" or "all",
 *   "delay": 10,
 *   "reason": "Maintenance",
 *   "staffName": "Admin"
 * }
 * }</pre>
 */
public class ServerControlHandler {

    private static final Set<Integer> COUNTDOWN_INTERVALS = Set.of(60, 30, 15, 10, 5, 4, 3, 2, 1);

    private final ServerAdmin plugin;
    private final String serverName;
    private BukkitTask shutdownTask;

    public ServerControlHandler(ServerAdmin plugin, String serverName) {
        this.plugin = plugin;
        this.serverName = serverName;
    }

    /**
     * Handles a server control message from Redis.
     *
     * @param message JSON message containing server control details
     */
    public void handleMessage(JsonObject message) {
        try {
            String type = message.has("type") ? message.get("type").getAsString() : null;
            String targetServer =
                    message.has("server") ? message.get("server").getAsString() : null;
            int delay = message.has("delay") ? message.get("delay").getAsInt() : 10;
            String reason =
                    message.has("reason")
                            ? message.get("reason").getAsString()
                            : "Server maintenance";
            String staffName =
                    message.has("staffName") ? message.get("staffName").getAsString() : "Console";

            // Validate required fields
            if (type == null || targetServer == null) {
                plugin.getLogger()
                        .warning("Received invalid server control message: missing fields");
                return;
            }

            // Check if this message applies to this server
            if (!targetServer.equalsIgnoreCase("all")
                    && !targetServer.equalsIgnoreCase(serverName)) {
                plugin.getLogger()
                        .fine(
                                "Ignoring server control message for "
                                        + targetServer
                                        + " (this is "
                                        + serverName
                                        + ")");
                return;
            }

            boolean isRestart = type.equalsIgnoreCase("RESTART");
            String action = isRestart ? "restart" : "shutdown";

            plugin.getLogger()
                    .info(
                            "Received "
                                    + action
                                    + " signal from Velocity (staff: "
                                    + staffName
                                    + ", delay: "
                                    + delay
                                    + "s)");

            // Announce to all players
            broadcastMessage("<red><bold>SERVER " + action.toUpperCase());
            broadcastMessage(
                    "<yellow>The server will "
                            + action
                            + " in <red>"
                            + delay
                            + "</red> second"
                            + (delay != 1 ? "s" : "")
                            + ".");
            if (!reason.equals("Server maintenance")) {
                broadcastMessage("<gray>Reason: <white>" + reason);
            }

            // Schedule countdown and shutdown/restart
            scheduleServerControl(delay, reason, isRestart);

        } catch (Exception e) {
            plugin.getLogger().severe("Error handling server control message: " + e.getMessage());
        }
    }

    /**
     * Schedules countdown announcements and the actual server shutdown/restart.
     *
     * @param delaySeconds Delay in seconds before shutdown
     * @param reason Shutdown reason
     * @param isRestart Whether this is a restart (true) or shutdown (false)
     */
    private void scheduleServerControl(int delaySeconds, String reason, boolean isRestart) {
        String action = isRestart ? "restart" : "shutdown";

        // Schedule countdown announcements
        for (int interval : COUNTDOWN_INTERVALS) {
            if (interval < delaySeconds) {
                Bukkit.getScheduler()
                        .runTaskLater(
                                plugin,
                                () -> {
                                    broadcastMessage(
                                            "<yellow>Server "
                                                    + action
                                                    + " in <red>"
                                                    + interval
                                                    + "</red> second"
                                                    + (interval != 1 ? "s" : "")
                                                    + "...");
                                },
                                (delaySeconds - interval) * 20L);
            }
        }

        // Schedule the actual shutdown/restart
        shutdownTask =
                Bukkit.getScheduler()
                        .runTaskLater(
                                plugin,
                                () -> {
                                    broadcastMessage(
                                            "<red><bold>Server "
                                                    + (isRestart ? "restarting" : "shutting down")
                                                    + " now!");

                                    // Kick all players with a nice message
                                    net.kyori.adventure.text.Component kickMessage =
                                            isRestart
                                                    ? net.kyori.adventure.text.minimessage
                                                            .MiniMessage.miniMessage()
                                                            .deserialize(
                                                                    "<gold>Server is restarting\n<gray>Please reconnect in a moment!")
                                                    : net.kyori.adventure.text.minimessage
                                                            .MiniMessage.miniMessage()
                                                            .deserialize(
                                                                    "<red>Server is shutting down\n<gray>"
                                                                            + reason);

                                    for (Player player : Bukkit.getOnlinePlayers()) {
                                        player.kick(kickMessage);
                                    }

                                    // Small delay to ensure kicks are processed
                                    Bukkit.getScheduler()
                                            .runTaskLater(
                                                    plugin,
                                                    () -> {
                                                        if (isRestart) {
                                                            try {
                                                                Bukkit.spigot().restart();
                                                            } catch (Exception e) {
                                                                plugin.getLogger()
                                                                        .warning(
                                                                                "Failed to use Spigot restart, falling back to shutdown");
                                                                Bukkit.shutdown();
                                                            }
                                                        } else {
                                                            Bukkit.shutdown();
                                                        }
                                                    },
                                                    20L); // 1 second delay
                                },
                                delaySeconds * 20L);
    }

    /**
     * Broadcasts a message to all online players.
     *
     * @param message MiniMessage-formatted text
     */
    private void broadcastMessage(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            TextUtil.send(player, message);
        }
        // Also log to console (strip formatting)
        plugin.getLogger().info(message.replaceAll("<[^>]+>", ""));
    }

    /**
     * Gets the server name from server.properties or plugin config.
     *
     * @return Server name (e.g., "smp", "lobby")
     */
    public String getServerName() {
        return serverName;
    }

    /** Cancels any pending shutdown/restart task. */
    public void cancel() {
        if (shutdownTask != null && !shutdownTask.isCancelled()) {
            shutdownTask.cancel();
            broadcastMessage("<green>Server shutdown/restart has been cancelled.");
        }
    }
}
