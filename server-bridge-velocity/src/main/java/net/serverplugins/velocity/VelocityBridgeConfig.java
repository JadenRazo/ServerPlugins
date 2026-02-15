package net.serverplugins.velocity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.slf4j.Logger;

/**
 * Configuration manager for ServerBridge Velocity plugin.
 *
 * <p>Handles loading and accessing configuration values from config.properties.
 */
public class VelocityBridgeConfig {

    private final Path dataDirectory;
    private final Logger logger;
    private final Properties properties;

    // Configuration keys
    private static final String KEY_REDIS_ENABLED = "redis.enabled";
    private static final String KEY_REDIS_HOST = "redis.host";
    private static final String KEY_REDIS_PORT = "redis.port";
    private static final String KEY_REDIS_PASSWORD = "redis.password";

    private static final String KEY_NETWORK_NAME = "network.name";
    private static final String KEY_SHOW_CONNECTION_MESSAGES = "messages.show-connection-messages";
    private static final String KEY_JOIN_MESSAGE = "messages.join";
    private static final String KEY_QUIT_MESSAGE = "messages.quit";
    private static final String KEY_SWITCH_MESSAGE = "messages.switch";

    // Default values
    private static final boolean DEFAULT_REDIS_ENABLED = false;
    private static final String DEFAULT_REDIS_HOST = "127.0.0.1";
    private static final int DEFAULT_REDIS_PORT = 6379;
    private static final String DEFAULT_REDIS_PASSWORD = "";

    private static final String DEFAULT_NETWORK_NAME = "ServerPlugins";
    private static final boolean DEFAULT_SHOW_CONNECTION_MESSAGES = true;
    private static final String DEFAULT_JOIN_MESSAGE =
            "<green>Welcome to <gold>{network}</gold>, <white>{player}</white>! <gray>Connected to: <aqua>{server}</aqua></gray>";
    private static final String DEFAULT_QUIT_MESSAGE =
            "<yellow>Goodbye, <white>{player}</white>!</yellow>";
    private static final String DEFAULT_SWITCH_MESSAGE =
            "<gray>Switching from <aqua>{from}</aqua> to <aqua>{to}</aqua>...</gray>";

    public VelocityBridgeConfig(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
        this.properties = new Properties();
    }

    /**
     * Loads the configuration from disk, creating default config if it doesn't exist.
     *
     * @throws IOException if config cannot be loaded or created
     */
    public void load() throws IOException {
        if (!Files.exists(dataDirectory)) {
            Files.createDirectories(dataDirectory);
        }

        Path configPath = dataDirectory.resolve("config.properties");

        if (!Files.exists(configPath)) {
            createDefaultConfig(configPath);
        }

        try (InputStream in = Files.newInputStream(configPath)) {
            properties.load(in);
        }

        logger.info("Configuration loaded successfully");
    }

    /** Creates a default configuration file with comments. */
    private void createDefaultConfig(Path configPath) throws IOException {
        Properties defaults = new Properties();
        defaults.setProperty(KEY_REDIS_ENABLED, String.valueOf(DEFAULT_REDIS_ENABLED));
        defaults.setProperty(KEY_REDIS_HOST, DEFAULT_REDIS_HOST);
        defaults.setProperty(KEY_REDIS_PORT, String.valueOf(DEFAULT_REDIS_PORT));
        defaults.setProperty(KEY_REDIS_PASSWORD, DEFAULT_REDIS_PASSWORD);

        defaults.setProperty(KEY_NETWORK_NAME, DEFAULT_NETWORK_NAME);
        defaults.setProperty(
                KEY_SHOW_CONNECTION_MESSAGES, String.valueOf(DEFAULT_SHOW_CONNECTION_MESSAGES));
        defaults.setProperty(KEY_JOIN_MESSAGE, DEFAULT_JOIN_MESSAGE);
        defaults.setProperty(KEY_QUIT_MESSAGE, DEFAULT_QUIT_MESSAGE);
        defaults.setProperty(KEY_SWITCH_MESSAGE, DEFAULT_SWITCH_MESSAGE);

        try (OutputStream out = Files.newOutputStream(configPath)) {
            defaults.store(
                    out,
                    """
                    ServerBridge Velocity Configuration

                    Redis Settings:
                    - redis.enabled: Enable/disable Redis connection for network stats
                    - redis.host: Redis server hostname/IP
                    - redis.port: Redis server port
                    - redis.password: Redis authentication password (leave empty if none)

                    Network Settings:
                    - network.name: Display name of your network (used in messages)

                    Message Settings:
                    - messages.show-connection-messages: Enable/disable player connection messages
                    - messages.join: Message shown when player joins the network
                    - messages.quit: Message shown when player leaves the network
                    - messages.switch: Message shown when player switches servers

                    Message Placeholders:
                    - {player}: Player username
                    - {server}: Server name
                    - {from}: Source server (switch only)
                    - {to}: Destination server (switch only)
                    - {network}: Network name

                    Messages use MiniMessage format: https://docs.advntr.dev/minimessage/format.html
                    """);
        }

        logger.info("Created default configuration file");
    }

    // ========== REDIS CONFIGURATION ==========

    /**
     * Gets whether Redis is enabled.
     *
     * @return true if Redis should be used
     */
    public boolean isRedisEnabled() {
        return Boolean.parseBoolean(
                properties.getProperty(KEY_REDIS_ENABLED, String.valueOf(DEFAULT_REDIS_ENABLED)));
    }

    /**
     * Gets the Redis server host.
     *
     * @return Redis hostname or IP address
     */
    public String getRedisHost() {
        return properties.getProperty(KEY_REDIS_HOST, DEFAULT_REDIS_HOST);
    }

    /**
     * Gets the Redis server port.
     *
     * @return Redis port number
     */
    public int getRedisPort() {
        try {
            return Integer.parseInt(
                    properties.getProperty(KEY_REDIS_PORT, String.valueOf(DEFAULT_REDIS_PORT)));
        } catch (NumberFormatException e) {
            logger.warn("Invalid Redis port in config, using default: {}", DEFAULT_REDIS_PORT);
            return DEFAULT_REDIS_PORT;
        }
    }

    /**
     * Gets the Redis authentication password.
     *
     * @return Redis password, or empty string if no password
     */
    public String getRedisPassword() {
        return properties.getProperty(KEY_REDIS_PASSWORD, DEFAULT_REDIS_PASSWORD);
    }

    /**
     * Checks if Redis password is configured.
     *
     * @return true if a password is set
     */
    public boolean hasRedisPassword() {
        String password = getRedisPassword();
        return password != null && !password.isEmpty();
    }

    // ========== NETWORK CONFIGURATION ==========

    /**
     * Gets the network display name.
     *
     * @return Network name
     */
    public String getNetworkName() {
        return properties.getProperty(KEY_NETWORK_NAME, DEFAULT_NETWORK_NAME);
    }

    // ========== MESSAGE CONFIGURATION ==========

    /**
     * Checks if connection messages should be shown to players.
     *
     * @return true if connection messages are enabled
     */
    public boolean isShowConnectionMessages() {
        return Boolean.parseBoolean(
                properties.getProperty(
                        KEY_SHOW_CONNECTION_MESSAGES,
                        String.valueOf(DEFAULT_SHOW_CONNECTION_MESSAGES)));
    }

    /**
     * Gets the join message template.
     *
     * @return Join message in MiniMessage format
     */
    public String getJoinMessage() {
        return properties.getProperty(KEY_JOIN_MESSAGE, DEFAULT_JOIN_MESSAGE);
    }

    /**
     * Gets the quit message template.
     *
     * @return Quit message in MiniMessage format
     */
    public String getQuitMessage() {
        return properties.getProperty(KEY_QUIT_MESSAGE, DEFAULT_QUIT_MESSAGE);
    }

    /**
     * Gets the server switch message template.
     *
     * @return Switch message in MiniMessage format
     */
    public String getSwitchMessage() {
        return properties.getProperty(KEY_SWITCH_MESSAGE, DEFAULT_SWITCH_MESSAGE);
    }
}
