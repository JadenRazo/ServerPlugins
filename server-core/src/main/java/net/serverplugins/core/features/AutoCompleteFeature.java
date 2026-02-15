package net.serverplugins.core.features;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.serverplugins.core.ServerCore;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;

public class AutoCompleteFeature extends Feature {

    private CommandMap commandMap;
    private final Map<String, PluginCommand> registeredCommands = new HashMap<>();

    public AutoCompleteFeature(ServerCore plugin) {
        super(plugin);
        initCommandMap();
    }

    @Override
    public String getName() {
        return "Auto-Complete";
    }

    @Override
    public String getDescription() {
        return "Registers commands from config for tab-completion";
    }

    @Override
    protected void onEnable() {
        Map<String, CommandData> commands = plugin.getCoreConfig().getAutoCompleteCommands();

        if (commands.isEmpty()) {
            plugin.getLogger().info("No auto-complete commands configured");
            return;
        }

        int registered = 0;
        for (Map.Entry<String, CommandData> entry : commands.entrySet()) {
            String cmdName = entry.getKey();
            CommandData cmdData = entry.getValue();

            if (registerCommand(cmdName, cmdData)) {
                registered++;
            }
        }

        plugin.getLogger().info("Registered " + registered + " auto-complete commands");
    }

    @Override
    protected void onDisable() {
        // Clear our tracking map
        // Note: Unregistering commands dynamically is complex and not fully supported
        registeredCommands.clear();
        plugin.getLogger().info("Auto-complete commands unloaded");
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

    private boolean registerCommand(String name, CommandData cmdData) {
        if (commandMap == null) {
            plugin.getLogger().warning("Cannot register auto-complete command - no command map");
            return false;
        }

        // Check if command already exists
        if (Bukkit.getPluginCommand(name) != null) {
            plugin.getLogger()
                    .info(
                            "Command '"
                                    + name
                                    + "' already exists, skipping auto-complete registration");
            return false;
        }

        try {
            // Create a new PluginCommand using reflection
            Constructor<PluginCommand> constructor =
                    PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            constructor.setAccessible(true);
            PluginCommand command = constructor.newInstance(name, plugin);

            command.setDescription(cmdData.getDescription());
            command.setUsage(cmdData.getUsage());

            // Set a dummy executor that does nothing (just for tab-completion)
            command.setExecutor(new DummyCommandExecutor());

            // Set aliases if provided
            List<String> aliases = cmdData.getAliases();
            if (aliases != null && !aliases.isEmpty()) {
                command.setAliases(aliases);
            }

            // Register the command
            commandMap.register(plugin.getName().toLowerCase(), command);

            // Track the registered command
            registeredCommands.put(name, command);

            plugin.getLogger().info("Registered auto-complete command: /" + name);
            return true;
        } catch (Exception e) {
            plugin.getLogger()
                    .warning(
                            "Failed to register auto-complete command '"
                                    + name
                                    + "': "
                                    + e.getMessage());
            return false;
        }
    }

    /**
     * Dummy command executor that does nothing. These commands are registered purely for
     * tab-completion purposes.
     */
    private static class DummyCommandExecutor implements org.bukkit.command.CommandExecutor {
        @Override
        public boolean onCommand(
                CommandSender sender, Command command, String label, String[] args) {
            // Do nothing - this is just for tab-completion
            return true;
        }
    }
}
