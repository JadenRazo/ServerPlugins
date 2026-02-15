package net.serverplugins.admin.redis;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.util.UUID;
import net.serverplugins.admin.punishment.Punishment;
import net.serverplugins.admin.staffchat.StaffChatManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

/**
 * Handles Redis communication for the admin plugin by borrowing ServerBridge's RedisClient. This
 * class publishes punishment events, staff chat messages, and mute sync events to Redis channels
 * for cross-server coordination with server-admin-velocity.
 *
 * <p>Uses soft-dependency pattern: if ServerBridge is not loaded, Redis features are gracefully
 * disabled.
 */
public class AdminRedisHandler {

    // Redis channel names matching the plan
    public static final String CHANNEL_ADMIN_PUNISHMENT = "server:admin:punishment";
    public static final String CHANNEL_ADMIN_STAFFCHAT = "server:admin:staffchat";
    public static final String CHANNEL_ADMIN_MUTE_SYNC = "server:admin:muteSync";
    public static final String CHANNEL_ADMIN_KICK = "server:admin:kick";
    public static final String CHANNEL_ADMIN_SERVERCONTROL = "server:admin:servercontrol";

    private final Plugin adminPlugin;
    private final Gson gson;
    private Object redisClient; // ServerBridge's RedisClient instance
    private JedisPool jedisPool; // For subscribing to admin channels
    private Method publishAsyncMethod;
    private Thread subscriberThread;
    private volatile boolean running = false;
    private boolean available = false;
    private StaffChatManager staffChatManager;
    private Object
            serverControlHandler; // ServerControlHandler instance (avoid circular dependency)

    public AdminRedisHandler(Plugin adminPlugin) {
        this.adminPlugin = adminPlugin;
        this.gson = new Gson();
    }

    /**
     * Initialize by obtaining RedisClient from ServerBridge via soft-dependency.
     *
     * @param bridgePlugin The ServerBridge plugin instance
     * @param staffChatManager Staff chat manager for handling incoming messages
     * @return true if Redis is available, false otherwise
     */
    public boolean init(Plugin bridgePlugin, StaffChatManager staffChatManager) {
        if (bridgePlugin == null) {
            adminPlugin.getLogger().info("ServerBridge not found - Redis features disabled");
            return false;
        }

        this.staffChatManager = staffChatManager;

        try {
            // Get RedisClient via ServerBridge.getRedisClient()
            Method getRedisClientMethod = bridgePlugin.getClass().getMethod("getRedisClient");
            this.redisClient = getRedisClientMethod.invoke(bridgePlugin);

            if (this.redisClient == null) {
                adminPlugin
                        .getLogger()
                        .warning("ServerBridge RedisClient is null - Redis features disabled");
                return false;
            }

            // Get the publishAsync method: publishAsync(String channel, Object message)
            this.publishAsyncMethod =
                    redisClient.getClass().getMethod("publishAsync", String.class, Object.class);

            // Get JedisPool for subscribing to admin channels
            Method getJedisPoolMethod = redisClient.getClass().getMethod("getJedisPool");
            this.jedisPool = (JedisPool) getJedisPoolMethod.invoke(redisClient);

            if (this.jedisPool == null) {
                adminPlugin
                        .getLogger()
                        .warning("ServerBridge JedisPool is null - subscriber disabled");
                return false;
            }

            // Check if Redis is actually connected
            Method isConnectedMethod = redisClient.getClass().getMethod("isConnected");
            Boolean connected = (Boolean) isConnectedMethod.invoke(redisClient);

            if (!connected) {
                adminPlugin
                        .getLogger()
                        .warning("ServerBridge Redis is not connected - features disabled");
                return false;
            }

            this.available = true;
            this.running = true;
            startSubscriber();
            adminPlugin.getLogger().info("Admin Redis integration enabled via ServerBridge");
            return true;

        } catch (Exception e) {
            adminPlugin
                    .getLogger()
                    .warning(
                            "Failed to initialize Redis integration: "
                                    + e.getClass().getSimpleName()
                                    + " - "
                                    + e.getMessage());
            this.available = false;
            return false;
        }
    }

    /** Start a subscriber thread to listen for admin-specific Redis channels. */
    private void startSubscriber() {
        subscriberThread =
                new Thread(
                        () -> {
                            while (running) {
                                try (Jedis jedis = jedisPool.getResource()) {
                                    adminPlugin
                                            .getLogger()
                                            .info("Admin Redis subscriber started...");
                                    jedis.subscribe(
                                            new JedisPubSub() {
                                                @Override
                                                public void onMessage(
                                                        String channel, String message) {
                                                    handleMessage(channel, message);
                                                }
                                            },
                                            CHANNEL_ADMIN_STAFFCHAT,
                                            CHANNEL_ADMIN_SERVERCONTROL);
                                } catch (Exception e) {
                                    if (running) {
                                        adminPlugin
                                                .getLogger()
                                                .warning(
                                                        "Admin Redis subscriber error: "
                                                                + e.getMessage());
                                        try {
                                            Thread.sleep(5000);
                                        } catch (InterruptedException ie) {
                                            Thread.currentThread().interrupt();
                                            break;
                                        }
                                    }
                                }
                            }
                        },
                        "ServerAdmin-Redis-Subscriber");
        subscriberThread.setDaemon(true);
        subscriberThread.start();
    }

    /**
     * Handle incoming Redis messages on admin channels.
     *
     * @param channel The Redis channel
     * @param message The message JSON
     */
    private void handleMessage(String channel, String message) {
        try {
            JsonObject json = gson.fromJson(message, JsonObject.class);

            switch (channel) {
                case CHANNEL_ADMIN_STAFFCHAT -> handleStaffChatMessage(json);
                case CHANNEL_ADMIN_SERVERCONTROL -> handleServerControlMessage(json);
            }

        } catch (Exception e) {
            adminPlugin
                    .getLogger()
                    .warning("Error handling admin Redis message: " + e.getMessage());
        }
    }

    /**
     * Handle incoming staff chat message from another server.
     *
     * @param json The message JSON
     */
    private void handleStaffChatMessage(JsonObject json) {
        String sender = json.has("sender") ? json.get("sender").getAsString() : "Unknown";
        String server = json.has("server") ? json.get("server").getAsString() : "Unknown";
        String chatMessage = json.has("message") ? json.get("message").getAsString() : "";

        // Schedule on main thread
        Bukkit.getScheduler()
                .runTask(
                        adminPlugin,
                        () -> {
                            if (staffChatManager != null) {
                                staffChatManager.handleCrossServerMessage(
                                        sender, server, chatMessage);
                            }
                        });
    }

    /**
     * Handle incoming server control message from Velocity.
     *
     * @param json The message JSON
     */
    private void handleServerControlMessage(JsonObject json) {
        // Schedule on main thread
        Bukkit.getScheduler()
                .runTask(
                        adminPlugin,
                        () -> {
                            if (serverControlHandler != null) {
                                try {
                                    // Use reflection to avoid circular dependency
                                    Method handleMethod =
                                            serverControlHandler
                                                    .getClass()
                                                    .getMethod("handleMessage", JsonObject.class);
                                    handleMethod.invoke(serverControlHandler, json);
                                } catch (Exception e) {
                                    adminPlugin
                                            .getLogger()
                                            .warning(
                                                    "Failed to handle server control message: "
                                                            + e.getMessage());
                                }
                            }
                        });
    }

    /** Shutdown the subscriber thread. */
    public void shutdown() {
        running = false;
        if (subscriberThread != null) {
            subscriberThread.interrupt();
        }
    }

    /**
     * Check if Redis integration is available and connected.
     *
     * @return true if Redis can be used, false otherwise
     */
    public boolean isAvailable() {
        return available && redisClient != null;
    }

    /**
     * Publish a punishment event to Redis. Used when a punishment is created or pardoned on this
     * server.
     *
     * @param punishment The punishment object
     * @param action Either "PUNISHMENT_CREATED" or "PUNISHMENT_PARDONED"
     */
    public void publishPunishment(Punishment punishment, String action) {
        if (!isAvailable()) return;

        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        adminPlugin,
                        () -> {
                            try {
                                JsonObject json = new JsonObject();
                                json.addProperty("type", action);
                                json.addProperty("id", punishment.getId());
                                json.addProperty(
                                        "targetUuid", punishment.getTargetUuid().toString());
                                json.addProperty("targetName", punishment.getTargetName());
                                json.addProperty(
                                        "staffName",
                                        punishment.getStaffName() != null
                                                ? punishment.getStaffName()
                                                : "Console");
                                json.addProperty("punishmentType", punishment.getType().name());
                                json.addProperty("reason", punishment.getReason());
                                json.addProperty(
                                        "durationMs",
                                        punishment.getDurationMs() != null
                                                ? punishment.getDurationMs()
                                                : 0);
                                json.addProperty(
                                        "expiresAt",
                                        punishment.getExpiresAt() != null
                                                ? punishment.getExpiresAt()
                                                : 0);
                                json.addProperty("permanent", punishment.isPermanent());
                                json.addProperty("server", getServerName());
                                json.addProperty("timestamp", System.currentTimeMillis());

                                publishAsyncMethod.invoke(
                                        redisClient, CHANNEL_ADMIN_PUNISHMENT, json);

                            } catch (Exception e) {
                                adminPlugin
                                        .getLogger()
                                        .warning(
                                                "Failed to publish punishment to Redis: "
                                                        + e.getMessage());
                            }
                        });
    }

    /**
     * Publish a staff chat message to Redis for cross-server routing.
     *
     * @param sender The staff member's name
     * @param message The chat message
     */
    public void publishStaffChat(String sender, String message) {
        if (!isAvailable()) return;

        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        adminPlugin,
                        () -> {
                            try {
                                JsonObject json = new JsonObject();
                                json.addProperty("type", "STAFF_CHAT");
                                json.addProperty("sender", sender);
                                json.addProperty("server", getServerName());
                                json.addProperty("message", message);
                                json.addProperty("timestamp", System.currentTimeMillis());

                                publishAsyncMethod.invoke(
                                        redisClient, CHANNEL_ADMIN_STAFFCHAT, json);

                            } catch (Exception e) {
                                adminPlugin
                                        .getLogger()
                                        .warning(
                                                "Failed to publish staff chat to Redis: "
                                                        + e.getMessage());
                            }
                        });
    }

    /**
     * Publish a mute sync event to Redis. This tells all servers (including Velocity) to update
     * their mute cache for a player.
     *
     * @param targetUuid The player's UUID
     * @param active true if muted, false if unmuted
     * @param expiresAt Timestamp when mute expires (null if permanent)
     * @param reason Mute reason
     */
    public void publishMuteSync(UUID targetUuid, boolean active, Long expiresAt, String reason) {
        if (!isAvailable()) return;

        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        adminPlugin,
                        () -> {
                            try {
                                JsonObject json = new JsonObject();
                                json.addProperty("type", "MUTE_SYNC");
                                json.addProperty("targetUuid", targetUuid.toString());
                                json.addProperty("active", active);
                                json.addProperty("expiresAt", expiresAt != null ? expiresAt : 0);
                                json.addProperty("reason", reason != null ? reason : "");
                                json.addProperty("timestamp", System.currentTimeMillis());

                                publishAsyncMethod.invoke(
                                        redisClient, CHANNEL_ADMIN_MUTE_SYNC, json);

                            } catch (Exception e) {
                                adminPlugin
                                        .getLogger()
                                        .warning(
                                                "Failed to publish mute sync to Redis: "
                                                        + e.getMessage());
                            }
                        });
    }

    /**
     * Publish a kick request to Redis. This tells Velocity to disconnect the player from the
     * network immediately.
     *
     * @param targetUuid The player's UUID
     * @param targetName The player's name
     * @param staffName The staff member issuing the kick
     * @param reason Kick reason
     */
    public void publishKick(UUID targetUuid, String targetName, String staffName, String reason) {
        if (!isAvailable()) return;

        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        adminPlugin,
                        () -> {
                            try {
                                JsonObject json = new JsonObject();
                                json.addProperty("type", "KICK_REQUEST");
                                json.addProperty("targetUuid", targetUuid.toString());
                                json.addProperty("targetName", targetName);
                                json.addProperty(
                                        "staffName", staffName != null ? staffName : "Console");
                                json.addProperty("reason", reason != null ? reason : "Kicked");
                                json.addProperty("timestamp", System.currentTimeMillis());

                                publishAsyncMethod.invoke(redisClient, CHANNEL_ADMIN_KICK, json);

                            } catch (Exception e) {
                                adminPlugin
                                        .getLogger()
                                        .warning(
                                                "Failed to publish kick to Redis: "
                                                        + e.getMessage());
                            }
                        });
    }

    /**
     * Generic method to publish a JsonObject to any channel. Used by VanishSyncPublisher.
     *
     * @param channel Redis channel name
     * @param json JSON message to publish
     */
    public void publish(String channel, JsonObject json) {
        if (!isAvailable()) return;

        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        adminPlugin,
                        () -> {
                            try {
                                publishAsyncMethod.invoke(redisClient, channel, json);
                            } catch (Exception e) {
                                adminPlugin
                                        .getLogger()
                                        .warning(
                                                "Failed to publish to Redis channel "
                                                        + channel
                                                        + ": "
                                                        + e.getMessage());
                            }
                        });
    }

    /**
     * Set the server control handler to receive server control messages from Velocity.
     *
     * @param handler ServerControlHandler instance
     */
    public void setServerControlHandler(Object handler) {
        this.serverControlHandler = handler;
    }

    /**
     * Get the server name from the config. Defaults to "smp" if not configured.
     *
     * @return Server name
     */
    private String getServerName() {
        String serverName = adminPlugin.getConfig().getString("server-name", "smp");
        return serverName.toLowerCase();
    }
}
