package net.serverplugins.filter;

import java.util.List;
import net.serverplugins.api.messages.PluginMessenger;
import net.serverplugins.filter.data.FilterLevel;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Configuration wrapper for ServerFilter. Provides type-safe access to config values and
 * centralized messaging.
 */
public class FilterConfig {

    private final ServerFilter plugin;
    private final FileConfiguration config;
    private PluginMessenger messenger;

    // Filter-themed prefix with shield icon
    private static final String DEFAULT_PREFIX = "<gradient:#ff6b6b:#feca57>🛡 Filter</gradient> ";

    public FilterConfig(ServerFilter plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        reload();
    }

    /** Reloads the configuration and messaging system. */
    public final void reload() {
        plugin.reloadConfig();
        this.messenger = new PluginMessenger(plugin.getConfig(), "messages", DEFAULT_PREFIX);
    }

    /** Gets the PluginMessenger instance for sending messages. */
    public PluginMessenger getMessenger() {
        return messenger;
    }

    // ========== General Settings ==========

    public FilterLevel getDefaultFilterLevel() {
        String levelStr = config.getString("default-filter-level", "STRICT");
        try {
            return FilterLevel.valueOf(levelStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger()
                    .warning("Invalid default-filter-level: " + levelStr + ", using STRICT");
            return FilterLevel.STRICT;
        }
    }

    public char getCensorCharacter() {
        String censor = config.getString("censor-character", "*");
        return censor.isEmpty() ? '*' : censor.charAt(0);
    }

    public List<String> getWhitelist() {
        return config.getStringList("whitelist");
    }

    // ========== Chat Format ==========

    public boolean isChatFormatEnabled() {
        return config.getBoolean("chat-format.enabled", true);
    }

    public String getChatFormat() {
        return config.getString(
                "chat-format.format",
                "%vault_prefix%{displayname}%vault_suffix%<gray>: <white>{message}");
    }

    // ========== Protection Settings ==========

    public boolean isAntiSpamEnabled() {
        return config.getBoolean("protection.anti-spam.enabled", true);
    }

    public long getMessageDelayMs() {
        return config.getLong("protection.anti-spam.message-delay-ms", 1000L);
    }

    public int getDuplicateMessageCount() {
        return config.getInt("protection.anti-spam.duplicate-message-count", 3);
    }

    public int getDuplicateWindowSeconds() {
        return config.getInt("protection.anti-spam.duplicate-window-seconds", 30);
    }

    public boolean isAntiCapsEnabled() {
        return config.getBoolean("protection.anti-caps.enabled", true);
    }

    public int getMinMessageLength() {
        return config.getInt("protection.anti-caps.min-message-length", 5);
    }

    public int getMaxCapsPercentage() {
        return config.getInt("protection.anti-caps.max-caps-percentage", 70);
    }

    public boolean isAntiAdvertisingEnabled() {
        return config.getBoolean("protection.anti-advertising.enabled", true);
    }

    public boolean shouldBlockUrls() {
        return config.getBoolean("protection.anti-advertising.block-urls", true);
    }

    public boolean shouldBlockIps() {
        return config.getBoolean("protection.anti-advertising.block-ips", true);
    }

    public List<String> getWhitelistedDomains() {
        return config.getStringList("protection.anti-advertising.whitelisted-domains");
    }

    // ========== Violation Settings ==========

    public boolean isLogToFileEnabled() {
        return config.getBoolean("violations.log-to-file", true);
    }

    public String getStaffNotificationPermission() {
        return config.getString("violations.staff-notification-permission", "serverfilter.notify");
    }
}
