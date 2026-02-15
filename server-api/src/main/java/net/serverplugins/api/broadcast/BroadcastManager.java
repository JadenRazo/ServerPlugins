package net.serverplugins.api.broadcast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

/**
 * Central manager for the broadcast messaging system. Plugins register with this manager and can
 * then send broadcasts via static API.
 */
public class BroadcastManager {
    private static final Map<String, BroadcastConfig> PLUGIN_CONFIGS = new HashMap<>();
    private static final String REDIS_CHANNEL = "server:broadcast:global";
    private static final Gson GSON = new Gson();
    private static JedisPool jedisPool;
    private static boolean redisEnabled = false;

    /**
     * Register a plugin's broadcast configuration.
     *
     * @param plugin The plugin to register
     */
    public static void registerPlugin(Plugin plugin) {
        String pluginKey = plugin.getName().toLowerCase();
        BroadcastConfig config = new BroadcastConfig(plugin);
        PLUGIN_CONFIGS.put(pluginKey, config);

        plugin.getLogger()
                .info(
                        "Registered with BroadcastManager ("
                                + config.getAllMessages().size()
                                + " broadcasts)");
    }

    /**
     * Unregister a plugin.
     *
     * @param plugin The plugin to unregister
     */
    public static void unregisterPlugin(Plugin plugin) {
        String pluginKey = plugin.getName().toLowerCase();
        PLUGIN_CONFIGS.remove(pluginKey);
    }

    /**
     * Broadcast a message from config.
     *
     * @param messageKey The message key (e.g., "pinata-spawn")
     * @param placeholders Custom placeholders
     */
    public static void broadcast(String messageKey, Placeholder... placeholders) {
        // Search all registered plugins for this message key
        for (BroadcastConfig config : PLUGIN_CONFIGS.values()) {
            BroadcastMessage message = config.getMessage(messageKey);
            if (message != null) {
                message.send(placeholders);
                return;
            }
        }

        // Message not found
        Logger.getLogger("BroadcastManager").warning("Broadcast message not found: " + messageKey);
    }

    /**
     * Broadcast a message from a specific plugin.
     *
     * @param pluginName The plugin name (e.g., "ServerEvents")
     * @param messageKey The message key
     * @param placeholders Custom placeholders
     */
    public static void broadcast(
            String pluginName, String messageKey, Placeholder... placeholders) {
        String pluginKey = pluginName.toLowerCase();
        BroadcastConfig config = PLUGIN_CONFIGS.get(pluginKey);

        if (config == null) {
            Logger.getLogger("BroadcastManager").warning("Plugin not registered: " + pluginName);
            return;
        }

        BroadcastMessage message = config.getMessage(messageKey);
        if (message == null) {
            Logger.getLogger("BroadcastManager")
                    .warning("Message not found in " + pluginName + ": " + messageKey);
            return;
        }

        message.send(placeholders);
    }

    /**
     * Create a builder for programmatic broadcasts.
     *
     * @param messageKey Unique key for this message
     * @return A new BroadcastBuilder
     */
    public static BroadcastBuilder builder(String messageKey) {
        return new BroadcastBuilder(messageKey);
    }

    /**
     * Broadcast a message to ALL servers via Redis.
     *
     * @param messageKey The message key
     * @param placeholders Custom placeholders
     */
    public static void broadcastGlobal(String messageKey, Placeholder... placeholders) {
        if (!redisEnabled) {
            // Fallback to local broadcast
            broadcast(messageKey, placeholders);
            return;
        }

        // Find the message
        BroadcastMessage message = null;
        for (BroadcastConfig config : PLUGIN_CONFIGS.values()) {
            message = config.getMessage(messageKey);
            if (message != null) break;
        }

        if (message == null) {
            Logger.getLogger("BroadcastManager")
                    .warning("Broadcast message not found for global: " + messageKey);
            return;
        }

        // Serialize and publish via Redis
        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, Object> data = message.toMap();

            // Add placeholders to the payload
            if (placeholders.length > 0) {
                Map<String, String> placeholderMap = new HashMap<>();
                for (Placeholder p : placeholders) {
                    placeholderMap.put(p.key(), p.value());
                }
                data.put("placeholders", placeholderMap);
            }

            String json = GSON.toJson(data);
            jedis.publish(REDIS_CHANNEL, json);
        } catch (Exception e) {
            Logger.getLogger("BroadcastManager")
                    .warning("Failed to publish global broadcast: " + e.getMessage());
            // Fallback to local broadcast
            message.send(placeholders);
        }
    }

    /** Reload all plugin broadcast configs. */
    public static void reloadAll() {
        for (BroadcastConfig config : PLUGIN_CONFIGS.values()) {
            config.reload();
        }
    }

    /**
     * Reload a specific plugin's broadcast config.
     *
     * @param plugin The plugin to reload
     */
    public static void reload(Plugin plugin) {
        String pluginKey = plugin.getName().toLowerCase();
        BroadcastConfig config = PLUGIN_CONFIGS.get(pluginKey);
        if (config != null) {
            config.reload();
        }
    }

    /**
     * Enable Redis cross-server broadcasting.
     *
     * @param pool The JedisPool to use
     */
    public static void enableRedis(JedisPool pool) {
        jedisPool = pool;
        redisEnabled = true;

        // Start listening for broadcasts from other servers
        startRedisListener();

        Logger.getLogger("BroadcastManager").info("Redis cross-server broadcasting enabled");
    }

    /** Start Redis subscriber thread. */
    private static void startRedisListener() {
        new Thread(
                        () -> {
                            try (Jedis jedis = jedisPool.getResource()) {
                                jedis.subscribe(
                                        new JedisPubSub() {
                                            @Override
                                            public void onMessage(String channel, String message) {
                                                if (!channel.equals(REDIS_CHANNEL)) return;

                                                // Parse and broadcast the message
                                                try {
                                                    Type type =
                                                            new TypeToken<
                                                                    Map<
                                                                            String,
                                                                            Object>>() {}.getType();
                                                    Map<String, Object> data =
                                                            GSON.fromJson(message, type);

                                                    BroadcastMessage broadcastMsg =
                                                            BroadcastMessage.fromMap(data);

                                                    // Extract placeholders if present
                                                    Placeholder[] placeholders = new Placeholder[0];
                                                    if (data.containsKey("placeholders")) {
                                                        @SuppressWarnings("unchecked")
                                                        Map<String, String> placeholderMap =
                                                                (Map<String, String>)
                                                                        data.get("placeholders");
                                                        placeholders =
                                                                placeholderMap.entrySet().stream()
                                                                        .map(
                                                                                e ->
                                                                                        Placeholder
                                                                                                .of(
                                                                                                        e
                                                                                                                .getKey(),
                                                                                                        e
                                                                                                                .getValue()))
                                                                        .toArray(
                                                                                Placeholder[]::new);
                                                    }

                                                    // Schedule broadcast on main thread
                                                    final Placeholder[] finalPlaceholders =
                                                            placeholders;
                                                    Bukkit.getScheduler()
                                                            .runTask(
                                                                    Bukkit.getPluginManager()
                                                                            .getPlugin("ServerAPI"),
                                                                    () ->
                                                                            broadcastMsg.send(
                                                                                    finalPlaceholders));
                                                } catch (Exception e) {
                                                    Logger.getLogger("BroadcastManager")
                                                            .warning(
                                                                    "Failed to process Redis broadcast: "
                                                                            + e.getMessage());
                                                }
                                            }
                                        },
                                        REDIS_CHANNEL);
                            } catch (Exception e) {
                                Logger.getLogger("BroadcastManager")
                                        .severe("Redis listener died: " + e.getMessage());
                            }
                        },
                        "BroadcastManager-Redis")
                .start();
    }

    /**
     * Get the config for a specific plugin.
     *
     * @param plugin The plugin
     * @return The BroadcastConfig, or null if not registered
     */
    public static BroadcastConfig getConfig(Plugin plugin) {
        return PLUGIN_CONFIGS.get(plugin.getName().toLowerCase());
    }

    /** Check if Redis is enabled. */
    public static boolean isRedisEnabled() {
        return redisEnabled;
    }

    /** Get all registered plugin configs (for debugging). */
    public static Map<String, BroadcastConfig> getAllConfigs() {
        return new HashMap<>(PLUGIN_CONFIGS);
    }
}
