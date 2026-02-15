package net.serverplugins.afk;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.serverplugins.api.messages.PluginMessenger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public class AfkConfig {

    private final ServerAFK plugin;
    private PluginMessenger messenger;

    public AfkConfig(ServerAFK plugin) {
        this.plugin = plugin;
        this.messenger =
                new PluginMessenger(
                        plugin.getConfig(),
                        "messages",
                        "<gradient:#9b59b6:#3498db>[AFK]</gradient> ");
    }

    private FileConfiguration config() {
        return plugin.getConfig();
    }

    public PluginMessenger getMessenger() {
        return messenger;
    }

    public String getDatabaseType() {
        return config().getString("database.type", "H2");
    }

    public String getDatabaseFile() {
        return config().getString("database.file", "afkzones");
    }

    // MariaDB settings
    public String getDatabaseHost() {
        return config().getString("database.host", "localhost");
    }

    public int getDatabasePort() {
        return config().getInt("database.port", 3306);
    }

    public String getDatabaseName() {
        return config().getString("database.name", "minecraft");
    }

    public String getDatabaseUsername() {
        return config().getString("database.username", "root");
    }

    public String getDatabasePassword() {
        return config().getString("database.password", "");
    }

    public int getDefaultTimeInterval() {
        return config().getInt("defaults.time-interval", 300);
    }

    public String getDefaultRewardType() {
        return config().getString("defaults.reward-type", "CURRENCY");
    }

    public double getDefaultCurrencyAmount() {
        return config().getDouble("defaults.currency-amount", 100);
    }

    public int getYAxisForgiveness() {
        return config().getInt("defaults.y-axis-forgiveness", 10);
    }

    public String getPrefix() {
        return messenger.getPrefix();
    }

    public String getMessage(String key) {
        return messenger.getMessage(key);
    }

    public String getRawMessage(String key) {
        return messenger.getRawMessage(key);
    }

    public void reload() {
        plugin.reloadConfig();
        this.messenger =
                new PluginMessenger(
                        plugin.getConfig(),
                        "messages",
                        "<gradient:#9b59b6:#3498db>[AFK]</gradient> ");
    }

    // Rank multiplier methods
    public double getRankMultiplier(String group) {
        if (group == null) return 1.0;
        return config().getDouble("rank-multipliers." + group.toLowerCase(), 1.0);
    }

    public Map<String, Double> getAllMultipliers() {
        ConfigurationSection section = config().getConfigurationSection("rank-multipliers");
        if (section == null) {
            return Map.of("default", 1.0);
        }

        Map<String, Double> multipliers = new HashMap<>();
        for (String key : section.getKeys(false)) {
            multipliers.put(key, section.getDouble(key, 1.0));
        }
        return multipliers;
    }

    // Hologram settings
    public int getHologramUpdateInterval() {
        return config().getInt("holograms.update-interval", 60);
    }

    public List<String> getHologramLines() {
        return config().getStringList("holograms.lines");
    }

    // Global AFK settings
    public int getAutoAfkTimeSeconds() {
        return config().getInt("global-afk.auto-afk-time-seconds", 300);
    }

    public boolean isAutoAfkEnabled() {
        return config().getBoolean("global-afk.enabled", true);
    }

    // Anti-Exploit settings
    public boolean isAntiExploitEnabled() {
        return config().getBoolean("anti-exploit.enabled", true);
    }

    public int getAntiExploitAnalysisInterval() {
        return config().getInt("anti-exploit.analysis-interval-seconds", 120);
    }

    public int getAntiExploitActivityWindow() {
        return config().getInt("anti-exploit.activity-window-seconds", 600);
    }

    public int getAntiExploitMaxRecords() {
        return config().getInt("anti-exploit.max-records-per-player", 100);
    }

    public double getAntiExploitSuspicionThreshold() {
        return config().getDouble("anti-exploit.suspicion-threshold", 0.7);
    }

    public int getAntiExploitVerificationLevel() {
        return config().getInt("anti-exploit.verification-trigger-level", 3);
    }

    // Verification settings
    public int getVerificationIntervalSeconds() {
        return config().getInt("verification.interval-seconds", 600);
    }

    public int getVerificationTimeoutSeconds() {
        return config().getInt("verification.timeout-seconds", 60);
    }

    public boolean isVerificationEnabled() {
        return config().getBoolean("verification.enabled", true);
    }

    // Statistics settings
    public boolean isStatsEnabled() {
        return config().getBoolean("statistics.enabled", true);
    }

    public int getStatsCacheMinutes() {
        return config().getInt("statistics.cache-minutes", 5);
    }

    public int getLeaderboardSize() {
        return config().getInt("statistics.leaderboard-size", 10);
    }
}
