package net.serverplugins.commands.dynamic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.serverplugins.api.effects.CustomSound;
import net.serverplugins.api.handlers.PlaceholderHandler;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.dynamic.placeholders.PlaceholderProcessor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CustomCommand implements CommandExecutor {

    private final String name;
    private final String description;
    private final CommandType type;
    private final List<String> text;
    private final String targetCommand;
    private final List<String> runCmd;
    private final String permission;
    private final int cooldown;
    private final String cooldownMessage;
    private final String noPermissionMessage;
    private final DelayedCommandQueue commandQueue;
    private final CustomSound executeSound;
    private DynamicCommandManager commandManager;

    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public CustomCommand(
            String name,
            String description,
            CommandType type,
            List<String> text,
            String targetCommand,
            List<String> runCmd,
            String permission,
            int cooldown,
            String cooldownMessage,
            String noPermissionMessage,
            DelayedCommandQueue commandQueue,
            CustomSound executeSound) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.text = text;
        this.targetCommand = targetCommand;
        this.runCmd = runCmd;
        this.permission = permission;
        this.cooldown = cooldown;
        this.cooldownMessage = cooldownMessage;
        this.noPermissionMessage = noPermissionMessage;
        this.commandQueue = commandQueue;
        this.executeSound = executeSound != null ? executeSound : CustomSound.NONE;
    }

    public void setCommandManager(DynamicCommandManager manager) {
        this.commandManager = manager;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {

        // Check permission
        if (permission != null && !permission.isEmpty() && !sender.hasPermission(permission)) {
            if (noPermissionMessage != null && !noPermissionMessage.isEmpty()) {
                TextUtil.send(sender, noPermissionMessage);
            }
            return true;
        }

        // Check cooldown (only for players)
        if (sender instanceof Player player && cooldown > 0) {
            UUID playerId = player.getUniqueId();
            long now = System.currentTimeMillis();

            if (cooldowns.containsKey(playerId)) {
                long lastUse = cooldowns.get(playerId);
                long remaining = (cooldown * 1000L) - (now - lastUse);

                if (remaining > 0) {
                    String message =
                            cooldownMessage != null
                                    ? cooldownMessage
                                    : "<red>Please wait " + (remaining / 1000) + " seconds.";
                    message = message.replace("%time%", String.valueOf(remaining / 1000));
                    TextUtil.send(player, message);
                    return true;
                }
            }

            cooldowns.put(playerId, now);
        }

        // Play execute sound if sender is a player
        if (sender instanceof Player player && executeSound != null) {
            executeSound.playSound(player);
        }

        // Execute based on type
        switch (type) {
            case RAW_TEXT -> executeRawText(sender);
            case COMMAND -> executeCommand(sender, args);
            case CONSOLE_COMMAND -> executeConsoleCommand(sender, args);
            case RUN_COMMAND -> executeRunCommand(sender);
            case RUN_CONSOLE, SYSTEM -> executeRunConsole(sender);
            case ACTION_BAR_TEXT -> executeActionBarText(sender);
            case GUI_MENU -> executeGuiMenu(sender);
        }

        return true;
    }

    private void executeRawText(CommandSender sender) {
        if (text == null || text.isEmpty()) return;

        Player player = sender instanceof Player ? (Player) sender : null;

        for (String line : text) {
            String parsed = player != null ? PlaceholderHandler.parse(player, line) : line;
            TextUtil.send(sender, parsed);
        }
    }

    private void executeCommand(CommandSender sender, String[] args) {
        if (targetCommand == null || targetCommand.isEmpty()) return;

        String cmd = targetCommand;

        // Replace {args} placeholder
        if (args.length > 0) {
            cmd = cmd.replace("{args}", String.join(" ", args));
        } else {
            cmd = cmd.replace("{args}", "");
        }

        // Replace individual arg placeholders
        for (int i = 0; i < args.length; i++) {
            cmd = cmd.replace("{arg" + i + "}", args[i]);
        }

        // Apply player placeholders
        if (sender instanceof Player player) {
            cmd = PlaceholderHandler.parse(player, cmd);
        }

        // Dispatch command
        Bukkit.dispatchCommand(sender, cmd.trim());
    }

    private void executeConsoleCommand(CommandSender sender, String[] args) {
        if (targetCommand == null || targetCommand.isEmpty()) return;

        String cmd = targetCommand;

        // Replace sender name
        cmd = cmd.replace("{player}", sender.getName());

        // Replace {args} placeholder
        if (args.length > 0) {
            cmd = cmd.replace("{args}", String.join(" ", args));
        } else {
            cmd = cmd.replace("{args}", "");
        }

        // Replace individual arg placeholders
        for (int i = 0; i < args.length; i++) {
            cmd = cmd.replace("{arg" + i + "}", args[i]);
        }

        // Apply player placeholders
        if (sender instanceof Player player) {
            cmd = PlaceholderHandler.parse(player, cmd);
        }

        // Dispatch as console
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.trim());
    }

    private void executeRunCommand(CommandSender sender) {
        // Only players can execute run commands (needed for command queue)
        if (!(sender instanceof Player player)) {
            TextUtil.send(sender, "<red>This command can only be executed by players.");
            return;
        }

        // Prefer runCmd if available, fallback to targetCommand for backward compatibility
        List<String> commandsToRun = runCmd;
        if (commandsToRun == null || commandsToRun.isEmpty()) {
            if (targetCommand != null && !targetCommand.isEmpty()) {
                commandsToRun = List.of(targetCommand);
            } else {
                return;
            }
        }

        // Parse commands with PlaceholderProcessor
        List<PlaceholderProcessor.ParsedCommand> parsedCommands =
                PlaceholderProcessor.parseCommands(commandsToRun, player);

        if (parsedCommands.isEmpty()) {
            return;
        }

        // Execute commands sequentially with command queue
        if (commandQueue != null) {
            commandQueue.executeSequentially(player, parsedCommands);
        }
    }

    private void executeRunConsole(CommandSender sender) {
        // Prefer runCmd if available, fallback to targetCommand for backward compatibility
        List<String> commandsToRun = runCmd;
        if (commandsToRun == null || commandsToRun.isEmpty()) {
            if (targetCommand != null && !targetCommand.isEmpty()) {
                commandsToRun = List.of(targetCommand);
            } else {
                return;
            }
        }

        // Execute commands as console
        Player player = sender instanceof Player ? (Player) sender : null;
        for (String cmd : commandsToRun) {
            // Apply placeholders if player
            String processed = player != null ? PlaceholderHandler.parse(player, cmd) : cmd;

            // Replace {player} placeholder with sender name
            processed = processed.replace("{player}", sender.getName());
            processed = processed.replace("%player_name%", sender.getName());

            // Strip leading slash if present (dispatchCommand doesn't expect it)
            processed = processed.trim();
            if (processed.startsWith("/")) {
                processed = processed.substring(1);
            }

            // Execute as console
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processed);
        }
    }

    private void executeActionBarText(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            TextUtil.send(sender, "<red>This command can only be executed by players.");
            return;
        }

        if (text == null || text.isEmpty()) return;

        // Join all text lines with a space for action bar
        String message = String.join(" ", text);
        String parsed = PlaceholderHandler.parse(player, message);
        TextUtil.sendActionBar(player, parsed);
    }

    private void executeGuiMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            TextUtil.send(sender, "<red>Only players can open GUI menus.");
            return;
        }

        // Get GUI ID from targetCommand field
        String guiId = targetCommand;

        if (guiId == null || guiId.isEmpty()) {
            TextUtil.send(player, "<red>No GUI configured for this command.");
            return;
        }

        // Use DynamicCommandManager to open GUI
        if (commandManager != null) {
            if (!commandManager.openGui(guiId, player)) {
                TextUtil.send(player, "<red>GUI '" + guiId + "' not found or failed to load.");
            }
        } else {
            TextUtil.send(player, "<red>GUI system not available.");
        }
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public CommandType getType() {
        return type;
    }

    public List<String> getText() {
        return text;
    }

    public String getPermission() {
        return permission;
    }

    public enum CommandType {
        RAW_TEXT,
        COMMAND,
        CONSOLE_COMMAND,
        RUN_COMMAND,
        RUN_CONSOLE,
        ACTION_BAR_TEXT,
        GUI_MENU,
        SYSTEM // Alias for RUN_CONSOLE - runs commands as console
    }
}
