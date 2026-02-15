package net.serverplugins.adminvelocity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.slf4j.Logger;

/**
 * Configuration manager for ServerAdmin Velocity plugin.
 *
 * <p>Handles loading and accessing configuration values from config.properties with environment
 * variable substitution support.
 */
public class AdminVelocityConfig {

    private final Path dataDirectory;
    private final Logger logger;
    private final Properties properties;

    // Database configuration keys
    private static final String KEY_DB_HOST = "database.host";
    private static final String KEY_DB_PORT = "database.port";
    private static final String KEY_DB_DATABASE = "database.database";
    private static final String KEY_DB_USERNAME = "database.username";
    private static final String KEY_DB_PASSWORD = "database.password";

    // Redis configuration keys
    private static final String KEY_REDIS_ENABLED = "redis.enabled";
    private static final String KEY_REDIS_HOST = "redis.host";
    private static final String KEY_REDIS_PORT = "redis.port";
    private static final String KEY_REDIS_PASSWORD = "redis.password";

    // Alt detection configuration keys
    private static final String KEY_ALTS_ENABLED = "alts.enabled";
    private static final String KEY_ALTS_DENY_BANNED = "alts.deny-banned-alts";
    private static final String KEY_ALTS_NOTIFY_STAFF = "alts.notify-staff";
    private static final String KEY_ALTS_MAX_ACCOUNTS = "alts.max-accounts-per-ip";

    // Staff chat configuration keys
    private static final String KEY_STAFFCHAT_ENABLED = "staffchat.enabled";
    private static final String KEY_STAFFCHAT_FORMAT = "staffchat.format";

    // Punishment defaults
    private static final String KEY_DEFAULT_BAN_REASON = "punishment.default-ban-reason";
    private static final String KEY_DEFAULT_MUTE_REASON = "punishment.default-mute-reason";
    private static final String KEY_DEFAULT_KICK_REASON = "punishment.default-kick-reason";

    // Server control configuration keys
    private static final String KEY_SERVERCONTROL_ENABLED = "servercontrol.enabled";
    private static final String KEY_SERVERCONTROL_DEFAULT_DELAY = "servercontrol.default-delay";

    // Default values
    private static final String DEFAULT_DB_HOST = "127.0.0.1";
    private static final int DEFAULT_DB_PORT = 3306;
    private static final String DEFAULT_DB_DATABASE = "server";
    private static final String DEFAULT_DB_USERNAME = "root";
    private static final String DEFAULT_DB_PASSWORD = "";

    private static final boolean DEFAULT_REDIS_ENABLED = false;
    private static final String DEFAULT_REDIS_HOST = "127.0.0.1";
    private static final int DEFAULT_REDIS_PORT = 6379;
    private static final String DEFAULT_REDIS_PASSWORD = "";

    private static final boolean DEFAULT_ALTS_ENABLED = true;
    private static final boolean DEFAULT_ALTS_DENY_BANNED = true;
    private static final boolean DEFAULT_ALTS_NOTIFY_STAFF = true;
    private static final int DEFAULT_ALTS_MAX_ACCOUNTS = 5;

    private static final boolean DEFAULT_STAFFCHAT_ENABLED = true;
    private static final String DEFAULT_STAFFCHAT_FORMAT =
            "<dark_gray>[<gold>SC</gold>]</dark_gray> <gray>[{server}]</gray> <white>{player}</white><gray>: <white>{message}";

    private static final String DEFAULT_BAN_REASON = "Banned by an administrator.";
    private static final String DEFAULT_MUTE_REASON = "Muted by an administrator.";
    private static final String DEFAULT_KICK_REASON = "Kicked by an administrator.";

    private static final boolean DEFAULT_SERVERCONTROL_ENABLED = true;
    private static final int DEFAULT_SERVERCONTROL_DEFAULT_DELAY = 10;

    public AdminVelocityConfig(Path dataDirectory, Logger logger) {
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

        // Database settings (supports ${ENV_VAR} syntax)
        defaults.setProperty(KEY_DB_HOST, "${MARIADB_HOST}");
        defaults.setProperty(KEY_DB_PORT, "${MARIADB_PORT}");
        defaults.setProperty(KEY_DB_DATABASE, "${MARIADB_DATABASE}");
        defaults.setProperty(KEY_DB_USERNAME, "${MARIADB_USER}");
        defaults.setProperty(KEY_DB_PASSWORD, "${MARIADB_PASSWORD}");

        // Redis settings (supports ${ENV_VAR} syntax)
        defaults.setProperty(KEY_REDIS_ENABLED, String.valueOf(DEFAULT_REDIS_ENABLED));
        defaults.setProperty(KEY_REDIS_HOST, "${REDIS_HOST}");
        defaults.setProperty(KEY_REDIS_PORT, "${REDIS_PORT}");
        defaults.setProperty(KEY_REDIS_PASSWORD, "${REDIS_PASSWORD}");

        // Alt detection settings
        defaults.setProperty(KEY_ALTS_ENABLED, String.valueOf(DEFAULT_ALTS_ENABLED));
        defaults.setProperty(KEY_ALTS_DENY_BANNED, String.valueOf(DEFAULT_ALTS_DENY_BANNED));
        defaults.setProperty(KEY_ALTS_NOTIFY_STAFF, String.valueOf(DEFAULT_ALTS_NOTIFY_STAFF));
        defaults.setProperty(KEY_ALTS_MAX_ACCOUNTS, String.valueOf(DEFAULT_ALTS_MAX_ACCOUNTS));

        // Staff chat settings
        defaults.setProperty(KEY_STAFFCHAT_ENABLED, String.valueOf(DEFAULT_STAFFCHAT_ENABLED));
        defaults.setProperty(KEY_STAFFCHAT_FORMAT, DEFAULT_STAFFCHAT_FORMAT);

        // Punishment defaults
        defaults.setProperty(KEY_DEFAULT_BAN_REASON, DEFAULT_BAN_REASON);
        defaults.setProperty(KEY_DEFAULT_MUTE_REASON, DEFAULT_MUTE_REASON);
        defaults.setProperty(KEY_DEFAULT_KICK_REASON, DEFAULT_KICK_REASON);

        // Server control settings
        defaults.setProperty(
                KEY_SERVERCONTROL_ENABLED, String.valueOf(DEFAULT_SERVERCONTROL_ENABLED));
        defaults.setProperty(
                KEY_SERVERCONTROL_DEFAULT_DELAY,
                String.valueOf(DEFAULT_SERVERCONTROL_DEFAULT_DELAY));

        try (OutputStream out = Files.newOutputStream(configPath)) {
            defaults.store(
                    out,
                    """
                    ServerAdmin Velocity Configuration

                    Database Settings:
                    - database.host: MariaDB server hostname/IP
                    - database.port: MariaDB server port
                    - database.database: Database name
                    - database.username: Database username (supports ${ENV_VAR} syntax)
                    - database.password: Database password (supports ${ENV_VAR} syntax)

                    Redis Settings:
                    - redis.enabled: Enable/disable Redis for cross-server communication
                    - redis.host: Redis server hostname/IP
                    - redis.port: Redis server port
                    - redis.password: Redis password (supports ${ENV_VAR} syntax)

                    Alt Detection Settings:
                    - alts.enabled: Enable/disable alt account detection
                    - alts.deny-banned-alts: Auto-deny connections from IPs with banned accounts
                    - alts.notify-staff: Notify staff when alt accounts are detected
                    - alts.max-accounts-per-ip: Alert threshold for accounts per IP

                    Staff Chat Settings:
                    - staffchat.enabled: Enable/disable staff chat routing
                    - staffchat.format: Format for staff chat messages (MiniMessage)
                      Placeholders: {player}, {server}, {message}

                    Punishment Settings:
                    - punishment.default-ban-reason: Default reason for bans
                    - punishment.default-mute-reason: Default reason for mutes
                    - punishment.default-kick-reason: Default reason for kicks

                    Server Control Settings:
                    - servercontrol.enabled: Enable/disable server control commands
                    - servercontrol.default-delay: Default delay in seconds for shutdown/restart
                    """);
        }

        logger.info("Created default configuration file");
    }

    /**
     * Resolves environment variable placeholders in property values.
     *
     * @param value the property value potentially containing ${ENV_VAR} placeholders
     * @return the resolved value with environment variables substituted
     */
    private String resolveEnvVars(String value) {
        if (value == null || !value.contains("${")) {
            return value;
        }

        String result = value;
        int start;
        while ((start = result.indexOf("${")) != -1) {
            int end = result.indexOf("}", start);
            if (end == -1) break;

            String envVar = result.substring(start + 2, end);
            String envValue = System.getenv(envVar);
            if (envValue != null) {
                result = result.substring(0, start) + envValue + result.substring(end + 1);
            } else {
                logger.warn("Environment variable '{}' not found, using placeholder as-is", envVar);
                break;
            }
        }

        return result;
    }

    // ========== DATABASE CONFIGURATION ==========

    public String getDatabaseHost() {
        return resolveEnvVars(properties.getProperty(KEY_DB_HOST, DEFAULT_DB_HOST));
    }

    public int getDatabasePort() {
        try {
            String portValue =
                    resolveEnvVars(
                            properties.getProperty(KEY_DB_PORT, String.valueOf(DEFAULT_DB_PORT)));
            return Integer.parseInt(portValue);
        } catch (NumberFormatException e) {
            logger.warn("Invalid database port in config, using default: {}", DEFAULT_DB_PORT);
            return DEFAULT_DB_PORT;
        }
    }

    public String getDatabaseName() {
        return resolveEnvVars(properties.getProperty(KEY_DB_DATABASE, DEFAULT_DB_DATABASE));
    }

    public String getDatabaseUsername() {
        return resolveEnvVars(properties.getProperty(KEY_DB_USERNAME, DEFAULT_DB_USERNAME));
    }

    public String getDatabasePassword() {
        return resolveEnvVars(properties.getProperty(KEY_DB_PASSWORD, DEFAULT_DB_PASSWORD));
    }

    // ========== REDIS CONFIGURATION ==========

    public boolean isRedisEnabled() {
        return Boolean.parseBoolean(
                properties.getProperty(KEY_REDIS_ENABLED, String.valueOf(DEFAULT_REDIS_ENABLED)));
    }

    public String getRedisHost() {
        return resolveEnvVars(properties.getProperty(KEY_REDIS_HOST, DEFAULT_REDIS_HOST));
    }

    public int getRedisPort() {
        try {
            return Integer.parseInt(
                    properties.getProperty(KEY_REDIS_PORT, String.valueOf(DEFAULT_REDIS_PORT)));
        } catch (NumberFormatException e) {
            logger.warn("Invalid Redis port in config, using default: {}", DEFAULT_REDIS_PORT);
            return DEFAULT_REDIS_PORT;
        }
    }

    public String getRedisPassword() {
        return resolveEnvVars(properties.getProperty(KEY_REDIS_PASSWORD, DEFAULT_REDIS_PASSWORD));
    }

    public boolean hasRedisPassword() {
        String password = getRedisPassword();
        return password != null && !password.isEmpty();
    }

    // ========== ALT DETECTION CONFIGURATION ==========

    public boolean isAltsEnabled() {
        return Boolean.parseBoolean(
                properties.getProperty(KEY_ALTS_ENABLED, String.valueOf(DEFAULT_ALTS_ENABLED)));
    }

    public boolean isDenyBannedAlts() {
        return Boolean.parseBoolean(
                properties.getProperty(
                        KEY_ALTS_DENY_BANNED, String.valueOf(DEFAULT_ALTS_DENY_BANNED)));
    }

    public boolean isNotifyStaffOfAlts() {
        return Boolean.parseBoolean(
                properties.getProperty(
                        KEY_ALTS_NOTIFY_STAFF, String.valueOf(DEFAULT_ALTS_NOTIFY_STAFF)));
    }

    public int getMaxAccountsPerIp() {
        try {
            return Integer.parseInt(
                    properties.getProperty(
                            KEY_ALTS_MAX_ACCOUNTS, String.valueOf(DEFAULT_ALTS_MAX_ACCOUNTS)));
        } catch (NumberFormatException e) {
            logger.warn(
                    "Invalid max accounts per IP in config, using default: {}",
                    DEFAULT_ALTS_MAX_ACCOUNTS);
            return DEFAULT_ALTS_MAX_ACCOUNTS;
        }
    }

    // ========== STAFF CHAT CONFIGURATION ==========

    public boolean isStaffChatEnabled() {
        return Boolean.parseBoolean(
                properties.getProperty(
                        KEY_STAFFCHAT_ENABLED, String.valueOf(DEFAULT_STAFFCHAT_ENABLED)));
    }

    public String getStaffChatFormat() {
        return properties.getProperty(KEY_STAFFCHAT_FORMAT, DEFAULT_STAFFCHAT_FORMAT);
    }

    // ========== PUNISHMENT CONFIGURATION ==========

    public String getDefaultBanReason() {
        return properties.getProperty(KEY_DEFAULT_BAN_REASON, DEFAULT_BAN_REASON);
    }

    public String getDefaultMuteReason() {
        return properties.getProperty(KEY_DEFAULT_MUTE_REASON, DEFAULT_MUTE_REASON);
    }

    public String getDefaultKickReason() {
        return properties.getProperty(KEY_DEFAULT_KICK_REASON, DEFAULT_KICK_REASON);
    }

    // ========== SERVER CONTROL CONFIGURATION ==========

    public boolean isServerControlEnabled() {
        return Boolean.parseBoolean(
                properties.getProperty(
                        KEY_SERVERCONTROL_ENABLED, String.valueOf(DEFAULT_SERVERCONTROL_ENABLED)));
    }

    public int getDefaultShutdownDelay() {
        try {
            return Integer.parseInt(
                    properties.getProperty(
                            KEY_SERVERCONTROL_DEFAULT_DELAY,
                            String.valueOf(DEFAULT_SERVERCONTROL_DEFAULT_DELAY)));
        } catch (NumberFormatException e) {
            logger.warn(
                    "Invalid server control delay in config, using default: {}",
                    DEFAULT_SERVERCONTROL_DEFAULT_DELAY);
            return DEFAULT_SERVERCONTROL_DEFAULT_DELAY;
        }
    }

    public int getServerControlDefaultDelay() {
        return getDefaultShutdownDelay();
    }
}
