package net.serverplugins.adminvelocity.redis;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.function.Consumer;
import org.slf4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

/** Redis pub/sub client for admin-velocity plugin. */
public class AdminRedisClient {

    public static final String CHANNEL_PUNISHMENT = "server:admin:punishment";
    public static final String CHANNEL_STAFFCHAT = "server:admin:staffchat";
    public static final String CHANNEL_KICK = "server:admin:kick";
    public static final String CHANNEL_MUTE_SYNC = "server:admin:muteSync";
    public static final String CHANNEL_VANISH_SYNC = "server:admin:vanishSync";
    public static final String CHANNEL_SERVER_CONTROL = "server:admin:servercontrol";

    private final ProxyServer server;
    private final Logger logger;
    private final Gson gson = new Gson();

    private JedisPool publisherPool;
    private JedisPool subscriberPool;
    private Thread subscriberThread;
    private AdminSubscriber subscriber;

    private Consumer<JsonObject> punishmentHandler;
    private Consumer<JsonObject> staffChatHandler;
    private Consumer<JsonObject> kickHandler;
    private Consumer<JsonObject> muteSyncHandler;
    private Consumer<JsonObject> vanishSyncHandler;

    public AdminRedisClient(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    /**
     * Connects to Redis and starts the subscriber thread.
     *
     * @param host Redis host
     * @param port Redis port
     * @param password Redis password (can be null)
     */
    public void connect(String host, int port, String password) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(5);
        poolConfig.setMaxIdle(2);
        poolConfig.setMinIdle(1);
        poolConfig.setTestOnBorrow(true);

        // Publisher pool
        if (password != null && !password.isEmpty()) {
            publisherPool = new JedisPool(poolConfig, host, port, 2000, password);
        } else {
            publisherPool = new JedisPool(poolConfig, host, port);
        }

        // Subscriber pool (separate from publisher)
        if (password != null && !password.isEmpty()) {
            subscriberPool = new JedisPool(poolConfig, host, port, 2000, password);
        } else {
            subscriberPool = new JedisPool(poolConfig, host, port);
        }

        // Test connection
        try (Jedis jedis = publisherPool.getResource()) {
            jedis.ping();
            logger.info("Successfully connected to Redis at {}:{}", host, port);
        } catch (Exception e) {
            logger.error("Failed to connect to Redis at {}:{}", host, port, e);
            return;
        }

        // Start subscriber thread
        subscribe();
    }

    /** Publishes a message to a channel. */
    public void publish(String channel, JsonObject message) {
        if (publisherPool == null || publisherPool.isClosed()) {
            logger.warn("Cannot publish to {} - Redis not connected", channel);
            return;
        }

        try (Jedis jedis = publisherPool.getResource()) {
            jedis.publish(channel, gson.toJson(message));
        } catch (Exception e) {
            logger.warn("Failed to publish to {}: {}", channel, e.getMessage());
        }
    }

    /** Starts the subscriber thread. */
    private void subscribe() {
        subscriber = new AdminSubscriber();
        subscriberThread =
                new Thread(
                        () -> {
                            try (Jedis jedis = subscriberPool.getResource()) {
                                jedis.subscribe(
                                        subscriber,
                                        CHANNEL_PUNISHMENT,
                                        CHANNEL_STAFFCHAT,
                                        CHANNEL_KICK,
                                        CHANNEL_MUTE_SYNC,
                                        CHANNEL_VANISH_SYNC);
                            } catch (Exception e) {
                                logger.error("Redis subscriber thread error: {}", e.getMessage());
                            }
                        },
                        "AdminRedis-Subscriber");
        subscriberThread.setDaemon(true);
        subscriberThread.start();
        logger.info("Started Redis subscriber thread");
    }

    /** Closes all Redis connections. */
    public void close() {
        if (subscriber != null && subscriber.isSubscribed()) {
            subscriber.unsubscribe();
        }

        if (subscriberThread != null && subscriberThread.isAlive()) {
            subscriberThread.interrupt();
        }

        if (publisherPool != null && !publisherPool.isClosed()) {
            publisherPool.close();
        }

        if (subscriberPool != null && !subscriberPool.isClosed()) {
            subscriberPool.close();
        }

        logger.info("Redis connections closed");
    }

    // ========== HANDLER REGISTRATION ==========

    public void onPunishment(Consumer<JsonObject> handler) {
        this.punishmentHandler = handler;
    }

    public void onStaffChat(Consumer<JsonObject> handler) {
        this.staffChatHandler = handler;
    }

    public void onKick(Consumer<JsonObject> handler) {
        this.kickHandler = handler;
    }

    public void onMuteSync(Consumer<JsonObject> handler) {
        this.muteSyncHandler = handler;
    }

    public void onVanishSync(Consumer<JsonObject> handler) {
        this.vanishSyncHandler = handler;
    }

    // ========== PUBLISHING METHODS ==========

    /**
     * Publishes a server control message to coordinate shutdown/restart.
     *
     * @param action Action type ("SHUTDOWN" or "RESTART")
     * @param server Target server name ("smp", "lobby", or "all")
     * @param delay Delay in seconds before action
     * @param reason Human-readable reason for the action
     * @param staffName Name of the staff member issuing the command
     */
    public void publishServerControl(
            String action, String server, int delay, String reason, String staffName) {
        JsonObject message = new JsonObject();
        message.addProperty("type", action);
        message.addProperty("server", server);
        message.addProperty("delay", delay);
        message.addProperty("reason", reason);
        message.addProperty("staffName", staffName);
        message.addProperty("timestamp", System.currentTimeMillis());

        publish(CHANNEL_SERVER_CONTROL, message);
        logger.info(
                "Published {} signal for {} (delay: {}s, staff: {})",
                action,
                server,
                delay,
                staffName);
    }

    // ========== SUBSCRIBER CLASS ==========

    private class AdminSubscriber extends JedisPubSub {
        @Override
        public void onMessage(String channel, String message) {
            try {
                JsonObject json = gson.fromJson(message, JsonObject.class);

                // Dispatch to appropriate handler based on channel
                switch (channel) {
                    case CHANNEL_PUNISHMENT:
                        if (punishmentHandler != null) {
                            server.getScheduler()
                                    .buildTask(
                                            AdminRedisClient.this,
                                            () -> punishmentHandler.accept(json))
                                    .schedule();
                        }
                        break;

                    case CHANNEL_STAFFCHAT:
                        if (staffChatHandler != null) {
                            server.getScheduler()
                                    .buildTask(
                                            AdminRedisClient.this,
                                            () -> staffChatHandler.accept(json))
                                    .schedule();
                        }
                        break;

                    case CHANNEL_KICK:
                        if (kickHandler != null) {
                            server.getScheduler()
                                    .buildTask(
                                            AdminRedisClient.this, () -> kickHandler.accept(json))
                                    .schedule();
                        }
                        break;

                    case CHANNEL_MUTE_SYNC:
                        if (muteSyncHandler != null) {
                            server.getScheduler()
                                    .buildTask(
                                            AdminRedisClient.this,
                                            () -> muteSyncHandler.accept(json))
                                    .schedule();
                        }
                        break;

                    case CHANNEL_VANISH_SYNC:
                        if (vanishSyncHandler != null) {
                            server.getScheduler()
                                    .buildTask(
                                            AdminRedisClient.this,
                                            () -> vanishSyncHandler.accept(json))
                                    .schedule();
                        }
                        break;

                    default:
                        logger.warn("Received message on unknown channel: {}", channel);
                }
            } catch (Exception e) {
                logger.error("Error handling Redis message on {}: {}", channel, e.getMessage());
            }
        }

        @Override
        public void onSubscribe(String channel, int subscribedChannels) {
            logger.info("Subscribed to Redis channel: {}", channel);
        }

        @Override
        public void onUnsubscribe(String channel, int subscribedChannels) {
            logger.info("Unsubscribed from Redis channel: {}", channel);
        }
    }
}
