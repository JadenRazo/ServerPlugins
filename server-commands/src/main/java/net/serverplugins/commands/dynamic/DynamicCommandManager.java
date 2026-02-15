package net.serverplugins.commands.dynamic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;
import net.serverplugins.api.effects.CustomSound;
import net.serverplugins.commands.ServerCommands;
import net.serverplugins.commands.dynamic.gui.ConfigurableGui;
import net.serverplugins.commands.dynamic.gui.GuiConfigParser;
import net.serverplugins.commands.dynamic.gui.GuiRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class DynamicCommandManager {

    private final ServerCommands plugin;
    private final Map<String, CustomCommand> dynamicCommands = new HashMap<>();
    private final Map<String, ConfigurationSection> guiConfigs = new HashMap<>();
    private CommandMap commandMap;
    private DelayedCommandQueue commandQueue;

    public DynamicCommandManager(ServerCommands plugin) {
        this.plugin = plugin;
        this.commandQueue = new DelayedCommandQueue(plugin);
        initCommandMap();
    }

    private void initCommandMap() {
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get command map: " + e.getMessage());
        }
    }

    public void loadCommands() {
        ConfigurationSection commandsSection =
                plugin.getConfig().getConfigurationSection("dynamic-commands");
        if (commandsSection == null) {
            plugin.getLogger().info("No dynamic commands configured");
            return;
        }

        int loaded = 0;
        for (String key : commandsSection.getKeys(false)) {
            ConfigurationSection cmdSection = commandsSection.getConfigurationSection(key);
            if (cmdSection == null) continue;

            try {
                CustomCommand cmd = parseCommand(key, cmdSection);
                if (cmd != null) {
                    cmd.setCommandManager(this); // Set reference for GUI access
                    registerCommand(key, cmd);
                    dynamicCommands.put(key, cmd);
                    loaded++;
                }
            } catch (Exception e) {
                plugin.getLogger()
                        .warning("Failed to load dynamic command '" + key + "': " + e.getMessage());
            }
        }

        plugin.getLogger().info("Loaded " + loaded + " dynamic commands");

        // Load GUI definitions
        loadGuiMenus();
    }

    private void loadGuiMenus() {
        ConfigurationSection guiSection = plugin.getConfig().getConfigurationSection("gui-menus");
        if (guiSection == null) {
            plugin.getLogger().info("No GUI menus configured");
            return;
        }

        // Clear existing GUI configs
        guiConfigs.clear();
        GuiRegistry registry = GuiRegistry.getInstance();
        registry.clear();

        int loaded = 0;
        for (String guiId : guiSection.getKeys(false)) {
            ConfigurationSection guiConfig = guiSection.getConfigurationSection(guiId);
            if (guiConfig != null) {
                // Store locally AND in registry for backwards compatibility
                guiConfigs.put(guiId, guiConfig);
                registry.registerGui(guiId, guiConfig);
                loaded++;
            }
        }

        plugin.getLogger()
                .info(
                        "Loaded "
                                + loaded
                                + " GUI menus: "
                                + guiConfigs.keySet()
                                + " (DCM@"
                                + System.identityHashCode(this)
                                + ")");
    }

    private CustomCommand parseCommand(String name, ConfigurationSection section) {
        String description = section.getString("description", "Dynamic command");
        String typeStr = section.getString("type", "RAW_TEXT").toUpperCase();
        CustomCommand.CommandType type;

        try {
            type = CustomCommand.CommandType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            plugin.getLogger()
                    .warning("Unknown command type '" + typeStr + "' for command " + name);
            type = CustomCommand.CommandType.RAW_TEXT;
        }

        List<String> text = section.getStringList("text");
        String command = section.getString("command", "");
        List<String> runcmd = section.getStringList("runcmd");
        String permission = section.getString("permission", "");
        int cooldown = section.getInt("cooldown", 0);
        String cooldownMessage =
                section.getString("cooldown-message", "<red>Please wait %time% seconds.");
        String noPermMessage =
                section.getString("no-permission-message", "<red>You don't have permission.");

        // Parse sound configuration
        CustomSound executeSound = parseSound(section);

        return new CustomCommand(
                name,
                description,
                type,
                text,
                command,
                runcmd,
                permission,
                cooldown,
                cooldownMessage,
                noPermMessage,
                commandQueue,
                executeSound);
    }

    private CustomSound parseSound(ConfigurationSection section) {
        // Check for sound configuration section
        if (section.contains("sound")) {
            Object soundObj = section.get("sound");

            // Handle simple string format: "SOUND_NAME volume pitch"
            if (soundObj instanceof String soundString) {
                return CustomSound.parse(soundString);
            }

            // Handle configuration section format with type, volume, pitch
            if (soundObj instanceof ConfigurationSection soundSection) {
                String soundType = soundSection.getString("type");
                if (soundType == null
                        || soundType.isEmpty()
                        || soundType.equalsIgnoreCase("none")) {
                    return CustomSound.NONE;
                }

                try {
                    Sound sound = Sound.valueOf(soundType.toUpperCase());
                    float volume = (float) soundSection.getDouble("volume", 1.0);
                    float pitch = (float) soundSection.getDouble("pitch", 1.0);
                    return new CustomSound(sound, volume, pitch);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger()
                            .warning(
                                    "Unknown sound type '"
                                            + soundType
                                            + "' in command configuration");
                    return CustomSound.NONE;
                }
            }
        }

        return CustomSound.NONE;
    }

    private void registerCommand(String name, CustomCommand customCommand) {
        if (commandMap == null) {
            plugin.getLogger().warning("Cannot register dynamic command - no command map");
            return;
        }

        try {
            // Create a new PluginCommand using reflection
            Constructor<PluginCommand> constructor =
                    PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            constructor.setAccessible(true);
            PluginCommand command = constructor.newInstance(name, plugin);

            command.setDescription(customCommand.getDescription());
            command.setExecutor(customCommand);

            if (customCommand.getPermission() != null && !customCommand.getPermission().isEmpty()) {
                command.setPermission(customCommand.getPermission());
            }

            // Get aliases from config
            List<String> aliases =
                    plugin.getConfig().getStringList("dynamic-commands." + name + ".aliases");
            if (!aliases.isEmpty()) {
                command.setAliases(aliases);
            }

            // Register the command
            commandMap.register(plugin.getName().toLowerCase(), command);

            plugin.getLogger().info("Registered dynamic command: /" + name);
        } catch (Exception e) {
            plugin.getLogger()
                    .warning("Failed to register command '" + name + "': " + e.getMessage());
        }
    }

    public void unloadCommands() {
        // Cancel all active command queues
        if (commandQueue != null) {
            commandQueue.cancelAll();
        }

        // NOTE: We intentionally do NOT clear guiConfigs here because:
        // 1. loadGuiMenus() already clears them at the start
        // 2. Clearing here breaks /reload since the plugin objects remain in memory
        // The guiConfigs will be properly cleared and reloaded in loadCommands()
        GuiRegistry.getInstance().clear();

        // Note: Unregistering commands dynamically is complex and not fully supported
        // For now, we just clear our tracking map
        dynamicCommands.clear();
    }

    public void reload() {
        unloadCommands();
        loadCommands();
    }

    public Map<String, CustomCommand> getDynamicCommands() {
        return Collections.unmodifiableMap(dynamicCommands);
    }

    public CustomCommand getCommand(String name) {
        return dynamicCommands.get(name);
    }

    public DelayedCommandQueue getCommandQueue() {
        return commandQueue;
    }

    /** Check if a GUI menu is registered. */
    public boolean hasGui(String guiId) {
        return guiConfigs.containsKey(guiId);
    }

    /** Get all registered GUI IDs. */
    public Set<String> getGuiIds() {
        return Collections.unmodifiableSet(guiConfigs.keySet());
    }

    /**
     * Create and open a GUI for a player.
     *
     * @return true if GUI was opened successfully
     */
    public boolean openGui(String guiId, Player player) {
        ConfigurationSection config = guiConfigs.get(guiId);
        if (config == null) {
            plugin.getLogger()
                    .warning(
                            "GUI not found: "
                                    + guiId
                                    + " (available: "
                                    + guiConfigs.keySet()
                                    + ")");
            return false;
        }

        try {
            ConfigurableGui gui = GuiConfigParser.parseGui(config, player);
            if (gui != null) {
                gui.open(player);
                return true;
            } else {
                plugin.getLogger().warning("Failed to parse GUI: " + guiId);
                return false;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error opening GUI " + guiId + ": " + e.getMessage());
            return false;
        }
    }
}
