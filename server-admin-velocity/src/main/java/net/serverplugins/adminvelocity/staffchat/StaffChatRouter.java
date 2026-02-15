package net.serverplugins.adminvelocity.staffchat;

import com.google.gson.JsonObject;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.serverplugins.adminvelocity.messaging.VelocityPlaceholder;
import net.serverplugins.adminvelocity.messaging.VelocityTextUtil;
import net.serverplugins.adminvelocity.redis.AdminRedisClient;
import org.slf4j.Logger;

/** Routes staff chat messages across the network. */
public class StaffChatRouter {

    private static final String PERMISSION = "serveradmin.staffchat";

    private final ProxyServer server;
    private final Logger logger;
    private final AdminRedisClient redisClient;
    private final String format;

    public StaffChatRouter(
            ProxyServer server, Logger logger, AdminRedisClient redisClient, String format) {
        this.server = server;
        this.logger = logger;
        this.redisClient = redisClient;
        this.format = format;
    }

    /**
     * Sends a staff chat message from Velocity.
     *
     * @param sender Player sending the message
     * @param serverName Current server name (or "Velocity" if in limbo)
     * @param message Message content
     */
    public void sendMessage(Player sender, String serverName, String message) {
        // Format the message
        String formatted =
                VelocityPlaceholder.replaceAll(
                        format,
                        VelocityPlaceholder.of("server", serverName),
                        VelocityPlaceholder.of("player", sender.getUsername()),
                        VelocityPlaceholder.of("message", message));

        // Send to all staff on Velocity
        broadcastToProxyStaff(formatted);

        // Publish to Redis for Bukkit servers
        JsonObject json = new JsonObject();
        json.addProperty("type", "STAFF_CHAT");
        json.addProperty("sender", sender.getUsername());
        json.addProperty("server", serverName);
        json.addProperty("message", message);
        json.addProperty("timestamp", System.currentTimeMillis());
        redisClient.publish(AdminRedisClient.CHANNEL_STAFFCHAT, json);

        logger.info("[StaffChat] {}: {}", sender.getUsername(), message);
    }

    /**
     * Handles incoming staff chat message from Redis.
     *
     * @param json Message JSON
     */
    public void handleIncomingMessage(JsonObject json) {
        if (!json.has("sender") || !json.has("server") || !json.has("message")) {
            logger.warn("Received invalid staff chat message: {}", json);
            return;
        }

        String sender = json.get("sender").getAsString();
        String serverName = json.get("server").getAsString();
        String message = json.get("message").getAsString();

        // Don't re-broadcast messages that originated from Velocity
        if ("velocity".equalsIgnoreCase(serverName)) {
            return;
        }

        // Format the message
        String formatted =
                VelocityPlaceholder.replaceAll(
                        format,
                        VelocityPlaceholder.of("server", serverName),
                        VelocityPlaceholder.of("player", sender),
                        VelocityPlaceholder.of("message", message));

        // Broadcast to Velocity staff
        broadcastToProxyStaff(formatted);
    }

    /**
     * Broadcasts a message to all staff on the proxy.
     *
     * @param message Formatted message
     */
    private void broadcastToProxyStaff(String message) {
        server.getAllPlayers().stream()
                .filter(player -> player.hasPermission(PERMISSION))
                .forEach(player -> VelocityTextUtil.send(player, message));
    }
}
