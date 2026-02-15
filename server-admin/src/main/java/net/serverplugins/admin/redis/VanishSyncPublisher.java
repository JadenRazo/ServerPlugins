package net.serverplugins.admin.redis;

import com.google.gson.JsonObject;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Publishes vanish state changes to Redis for network-wide visibility sync.
 *
 * <p>Publishes to channel: server:admin:vanishSync
 *
 * <p>Message format:
 *
 * <pre>{@code
 * {
 *   "type": "VANISH_UPDATE",
 *   "playerUuid": "uuid-string",
 *   "playerName": "StaffName",
 *   "vanished": true,
 *   "mode": "FULL",
 *   "server": "smp",
 *   "timestamp": 1707350400000
 * }
 * }</pre>
 */
public class VanishSyncPublisher {

    private static final String CHANNEL = "server:admin:vanishSync";

    private final BiConsumer<String, JsonObject> publishFunction;
    private final String serverName;

    /**
     * Creates a new vanish sync publisher.
     *
     * @param publishFunction Function to publish JSON messages to a Redis channel (channel, json)
     * @param serverName This server's name (e.g., "smp", "lobby")
     */
    public VanishSyncPublisher(BiConsumer<String, JsonObject> publishFunction, String serverName) {
        this.publishFunction = publishFunction;
        this.serverName = serverName;
    }

    /**
     * Publishes a vanish state update to Redis.
     *
     * @param playerUuid Player's UUID
     * @param playerName Player's name
     * @param vanished Whether the player is now vanished
     * @param mode Vanish mode (e.g., "FULL", "STAFF", "OFF")
     */
    public void publishVanishUpdate(
            UUID playerUuid, String playerName, boolean vanished, String mode) {
        if (publishFunction == null) {
            return; // Redis not available, silently skip
        }

        JsonObject message = new JsonObject();
        message.addProperty("type", "VANISH_UPDATE");
        message.addProperty("playerUuid", playerUuid.toString());
        message.addProperty("playerName", playerName);
        message.addProperty("vanished", vanished);
        message.addProperty("mode", mode);
        message.addProperty("server", serverName);
        message.addProperty("timestamp", System.currentTimeMillis());

        publishFunction.accept(CHANNEL, message);
    }
}
