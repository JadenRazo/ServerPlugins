package net.serverplugins.bridge;

import net.serverplugins.api.messages.PluginMessenger;
import org.bukkit.configuration.file.FileConfiguration;

public class BridgeConfig {

    private final ServerBridge plugin;
    private FileConfiguration config;
    private PluginMessenger messenger;

    public BridgeConfig(ServerBridge plugin) {
        this.plugin = plugin;
        reload();
    }

    public final void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        this.messenger =
                new PluginMessenger(
                        config, "messages", "<gradient:#00CED1:#20B2AA>[Bridge]</gradient> ");
    }

    public PluginMessenger getMessenger() {
        return messenger;
    }

    public String getServerName() {
        return config.getString("server-name", "unknown");
    }

    public boolean isRedisEnabled() {
        return config.getBoolean("redis.enabled", true);
    }

    public String getRedisHost() {
        return config.getString("redis.host", "127.0.0.1");
    }

    public int getRedisPort() {
        return config.getInt("redis.port", 6379);
    }

    public String getRedisPassword() {
        String password = config.getString("redis.password", "");
        if (password.startsWith("${") && password.endsWith("}")) {
            String envKey = password.substring(2, password.length() - 1);
            String envValue = System.getenv(envKey);
            return envValue != null ? envValue : "";
        }
        return password;
    }

    public String getDatabaseHost() {
        return config.getString("database.host");
    }

    public int getDatabasePort() {
        return config.getInt("database.port", 3306);
    }

    public String getDatabaseName() {
        return config.getString("database.database");
    }

    public String getDatabaseUsername() {
        return config.getString("database.username");
    }

    public String getDatabasePassword() {
        String password = config.getString("database.password", "");
        if (password.startsWith("${") && password.endsWith("}")) {
            String envKey = password.substring(2, password.length() - 1);
            String envValue = System.getenv(envKey);
            return envValue != null ? envValue : "";
        }
        return password;
    }

    public boolean isChatBridgeEnabled() {
        return config.getBoolean("chat-bridge.enabled", true);
    }

    public String getChatBridgeFormat() {
        return config.getString(
                "chat-bridge.format", "<dark_aqua>[Discord]</dark_aqua> %player%: %message%");
    }

    public boolean isCrossServerChatEnabled() {
        return config.getBoolean("cross-server-chat.enabled", true);
    }

    public String getCrossServerChatFormat() {
        return config.getString(
                "cross-server-chat.format",
                "<dark_gray>[<gray>%server%</gray>]</dark_gray> %prefix%%player%%suffix%<gray>: <white>%message%");
    }

    public boolean isRoleSyncEnabled() {
        return config.getBoolean("role-sync.enabled", true);
    }

    public int getRoleSyncInterval() {
        return config.getInt("role-sync.check-interval", 60);
    }

    public String getMessage(String key) {
        return config.getString("messages." + key, "");
    }
}
