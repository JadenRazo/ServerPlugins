package net.serverplugins.api.broadcast;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Per-plugin configuration wrapper for broadcast messages. Loads and caches broadcast templates
 * from config.yml.
 */
public class BroadcastConfig {
    private final Plugin plugin;
    private final Logger logger;
    private final Map<String, BroadcastMessage> messages;

    public BroadcastConfig(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.messages = new HashMap<>();
        load();
    }

    /** Load broadcasts from the plugin's config. */
    public final void load() {
        messages.clear();

        FileConfiguration config = plugin.getConfig();
        if (!config.contains("broadcasts")) {
            logger.info("No broadcasts section found in config.yml");
            return;
        }

        BroadcastParser parser = new BroadcastParser(logger);
        Map<String, BroadcastMessage> parsed =
                parser.parseAll(config.getConfigurationSection("broadcasts"));

        // Set plugin reference on all messages (for boss bar scheduling)
        for (BroadcastMessage message : parsed.values()) {
            message.setPlugin(plugin);
        }

        messages.putAll(parsed);
        logger.info("Loaded " + messages.size() + " broadcast messages");
    }

    /** Reload broadcasts from config. */
    public void reload() {
        plugin.reloadConfig();
        load();
    }

    /**
     * Get a broadcast message by key.
     *
     * @param key The message key
     * @return The BroadcastMessage, or null if not found
     */
    public BroadcastMessage getMessage(String key) {
        return messages.get(key);
    }

    /**
     * Check if a message exists.
     *
     * @param key The message key
     * @return true if the message exists
     */
    public boolean hasMessage(String key) {
        return messages.containsKey(key);
    }

    /**
     * Get all loaded messages (immutable).
     *
     * @return Map of all messages
     */
    public Map<String, BroadcastMessage> getAllMessages() {
        return Collections.unmodifiableMap(messages);
    }

    /**
     * Register a programmatic broadcast (not from config).
     *
     * @param message The message to register
     */
    public void registerMessage(BroadcastMessage message) {
        messages.put(message.getKey(), message);
    }

    /** Get the plugin this config belongs to. */
    public Plugin getPlugin() {
        return plugin;
    }
}
