package net.serverplugins.stats;

import java.util.List;
import net.serverplugins.api.messages.PluginMessenger;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Configuration wrapper for ServerStats plugin. Provides centralized configuration management and
 * messaging system.
 */
public class StatsConfig {

    private final ServerStats plugin;
    private PluginMessenger messenger;

    // HTTP Configuration
    private int httpPort;
    private List<String> allowedOrigins;
    private int cacheTtl;

    public StatsConfig(ServerStats plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();

        // HTTP settings
        httpPort = config.getInt("http.port", 20090);
        allowedOrigins = config.getStringList("http.allowed-origins");
        cacheTtl = config.getInt("cache-ttl", 30);

        // Initialize PluginMessenger with stats-themed prefix
        messenger =
                new PluginMessenger(
                        config, "messages", "<gradient:#4facfe:#00f2fe>[Stats]</gradient> ");
    }

    /**
     * Gets the HTTP server port.
     *
     * @return The port number
     */
    public int getHttpPort() {
        return httpPort;
    }

    /**
     * Gets the allowed CORS origins for HTTP API.
     *
     * @return The list of allowed origins
     */
    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    /**
     * Gets the cache TTL in seconds for stats endpoint.
     *
     * @return The cache TTL
     */
    public int getCacheTtl() {
        return cacheTtl;
    }

    /**
     * Gets the PluginMessenger instance for sending messages.
     *
     * @return The PluginMessenger instance
     */
    public PluginMessenger getMessenger() {
        return messenger;
    }
}
