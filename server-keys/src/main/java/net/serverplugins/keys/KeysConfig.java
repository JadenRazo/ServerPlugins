package net.serverplugins.keys;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.serverplugins.api.messages.PluginMessenger;
import net.serverplugins.keys.models.KeyType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public class KeysConfig {

    private final FileConfiguration config;
    private PluginMessenger messenger;

    public KeysConfig(FileConfiguration config) {
        this.config = config;
        this.messenger =
                new PluginMessenger(
                        config, "messages", "<gradient:#FFD700:#FFA500>[Keys]</gradient> ");
    }

    // ==================== Key Definitions ====================

    public Set<String> getCrateKeys() {
        ConfigurationSection section = config.getConfigurationSection("keys.crate");
        if (section == null) return Collections.emptySet();
        return section.getKeys(false);
    }

    public Set<String> getDungeonKeys() {
        ConfigurationSection section = config.getConfigurationSection("keys.dungeon");
        if (section == null) return Collections.emptySet();
        return section.getKeys(false);
    }

    public Set<String> getKeys(KeyType type) {
        return type == KeyType.CRATE ? getCrateKeys() : getDungeonKeys();
    }

    public Set<String> getAllKeys() {
        Set<String> all = new HashSet<>();
        all.addAll(getCrateKeys());
        all.addAll(getDungeonKeys());
        return all;
    }

    public boolean isValidKey(KeyType type, String keyName) {
        return getKeys(type).contains(keyName.toLowerCase());
    }

    public String getKeyDisplay(KeyType type, String keyName) {
        String path = "keys." + type.name().toLowerCase() + "." + keyName + ".display";
        return config.getString(path, capitalize(keyName));
    }

    public String getKeyDescription(KeyType type, String keyName) {
        String path = "keys." + type.name().toLowerCase() + "." + keyName + ".description";
        return config.getString(path, "");
    }

    public int getDungeonKeyModelData(String keyName) {
        String path = "keys.dungeon." + keyName + ".custom-model-data";
        return config.getInt(path, 145);
    }

    // ==================== Schedules ====================

    public ConfigurationSection getSchedulesSection() {
        return config.getConfigurationSection("schedules");
    }

    public boolean isScheduleEnabled(String scheduleId) {
        return config.getBoolean("schedules." + scheduleId + ".enabled", false);
    }

    public String getScheduleType(String scheduleId) {
        return config.getString("schedules." + scheduleId + ".type", "crate");
    }

    public String getScheduleKey(String scheduleId) {
        return config.getString("schedules." + scheduleId + ".key", "daily");
    }

    public int getScheduleAmount(String scheduleId) {
        return config.getInt("schedules." + scheduleId + ".amount", 1);
    }

    public java.util.List<String> getScheduleTimes(String scheduleId) {
        return config.getStringList("schedules." + scheduleId + ".times");
    }

    public java.util.List<String> getScheduleDays(String scheduleId) {
        return config.getStringList("schedules." + scheduleId + ".days");
    }

    public String getScheduleBroadcast(String scheduleId) {
        return config.getString("schedules." + scheduleId + ".broadcast", "");
    }

    // ==================== Messages ====================

    public PluginMessenger getMessenger() {
        return messenger;
    }

    public void reload(FileConfiguration newConfig) {
        this.messenger =
                new PluginMessenger(
                        newConfig, "messages", "<gradient:#FFD700:#FFA500>[Keys]</gradient> ");
    }

    // ==================== Utilities ====================

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}
