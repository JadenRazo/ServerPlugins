package net.serverplugins.core.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.serverplugins.api.messages.PluginMessenger;
import net.serverplugins.core.ServerCore;
import net.serverplugins.core.features.CommandData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public class CoreConfig {

    private final ServerCore plugin;
    private final FileConfiguration config;
    private final PluginMessenger messenger;

    public CoreConfig(ServerCore plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.messenger =
                new PluginMessenger(
                        config, "messages", "<gradient:#ff6b6b:#4ecdc4>[Core]</gradient> ");
    }

    public boolean isFeatureEnabled(String featureName) {
        return config.getBoolean("features." + featureName + ".enabled", true);
    }

    public void setFeatureEnabled(String featureName, boolean enabled) {
        config.set("features." + featureName + ".enabled", enabled);
        plugin.saveConfig();
    }

    public long getAutoTotemCooldown() {
        return config.getLong("settings.auto-totem.cooldown", 100);
    }

    public int getDoubleDoorMaxDistance() {
        return config.getInt("settings.double-door.max-distance", 2);
    }

    public boolean shouldPlayPickupSound() {
        return config.getBoolean("settings.drop-to-inventory.play-sound", true);
    }

    public boolean allowEditingOtherSigns() {
        return config.getBoolean("settings.editable-signs.allow-others", false);
    }

    public boolean shouldKeepHatOnDeath() {
        return config.getBoolean("settings.hat.keep-on-death", false);
    }

    public List<String> getHatAllowedMaterials() {
        return config.getStringList("settings.hat.allowed-materials");
    }

    public List<Map<String, Integer>> getHatCustomModelDataRanges() {
        List<Map<String, Integer>> ranges = new ArrayList<>();

        ConfigurationSection rangesSection =
                config.getConfigurationSection("settings.hat.custom-model-data");
        if (rangesSection == null) {
            // Try as list instead
            List<?> rangesList = config.getList("settings.hat.custom-model-data");
            if (rangesList != null) {
                for (Object obj : rangesList) {
                    if (obj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> rangeMap = (Map<String, Object>) obj;
                        Map<String, Integer> range = new HashMap<>();

                        Object minObj = rangeMap.get("min");
                        Object maxObj = rangeMap.get("max");

                        if (minObj instanceof Integer && maxObj instanceof Integer) {
                            range.put("min", (Integer) minObj);
                            range.put("max", (Integer) maxObj);
                            ranges.add(range);
                        }
                    }
                }
            }
        }

        return ranges;
    }

    public boolean allowFormattingCodes() {
        return config.getBoolean("settings.anvil-colors.allow-formatting", true);
    }

    public Map<String, CommandData> getAutoCompleteCommands() {
        Map<String, CommandData> commands = new HashMap<>();

        ConfigurationSection autoCompleteSection =
                config.getConfigurationSection("settings.auto-complete.commands");
        if (autoCompleteSection == null) {
            return commands;
        }

        for (String cmdName : autoCompleteSection.getKeys(false)) {
            ConfigurationSection cmdSection = autoCompleteSection.getConfigurationSection(cmdName);
            if (cmdSection == null) continue;

            String description = cmdSection.getString("description", "");
            String usage = cmdSection.getString("usage", "/" + cmdName);
            List<String> aliases = cmdSection.getStringList("aliases");

            // Ensure aliases is never null
            if (aliases == null) {
                aliases = new ArrayList<>();
            }

            CommandData commandData = new CommandData(cmdName, description, usage, aliases);
            commands.put(cmdName, commandData);
        }

        return commands;
    }

    public boolean isGiveCommandEnabled() {
        return config.getBoolean("settings.give-command.enabled", true);
    }

    public boolean getGiveSendWithErrors() {
        return config.getBoolean("settings.give-command.send-with-errors", false);
    }

    public String getGiveMessage(String key) {
        String message = config.getString("settings.give-command.messages." + key);

        // Provide default messages if config value is missing
        if (message == null) {
            switch (key) {
                case "item-received":
                    return "<green>You received <item>!";
                case "item-given":
                    return "<green>Gave <item> to <player>!";
                case "item-dropped":
                    return "<yellow>Item was dropped on the ground.";
                default:
                    return "";
            }
        }

        return message;
    }

    public String getPluginListHoverText() {
        return config.getString(
                "settings.plugin-list.hover-text", "<gray>Click to view plugin details");
    }

    public String getShutdownMessage() {
        return config.getString(
                "settings.restart.shutdown-message",
                "<red><bold>SERVER RESTARTING</bold>\n<gray>We'll be back shortly!");
    }

    public boolean shouldDisableAIOnPlacedSpawners() {
        return config.getBoolean(
                "features.spawner-enhancement.disable-ai-on-placed-spawners", true);
    }

    public PluginMessenger getMessenger() {
        return messenger;
    }
}
