package net.serverplugins.core.commands;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.GeyserUtils;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.core.ServerCore;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Command that executes different commands based on whether the player is on Java or Bedrock.
 * Usage: /javabedrock <java_command> \\ <bedrock_command> Usage: /javabedrock player <playerName>
 * <java_command> \\ <bedrock_command>
 */
public class JavaBedrockCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "servercore.javabedrock";
    private final ServerCore plugin;

    public JavaBedrockCommand(ServerCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        if (args.length < 2) {
            TextUtil.send(
                    sender,
                    ColorScheme.ERROR
                            + "Usage: /javabedrock <java_command> \\\\ <bedrock_command>");
            TextUtil.send(
                    sender,
                    ColorScheme.ERROR
                            + "Usage: /javabedrock player <name> <java_command> \\\\ <bedrock_command>");
            return true;
        }

        Player target;
        int commandStartIndex;

        // Check if targeting specific player
        if (args[0].equalsIgnoreCase("player")) {
            if (args.length < 3) {
                TextUtil.send(
                        sender,
                        ColorScheme.ERROR
                                + "Usage: /javabedrock player <playerName> <java_command> \\\\ <bedrock_command>");
                return true;
            }

            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                plugin.getCoreConfig()
                        .getMessenger()
                        .sendError(sender, "Player not found: " + args[1]);
                return true;
            }
            commandStartIndex = 2;
        } else {
            if (!(sender instanceof Player)) {
                TextUtil.send(
                        sender,
                        ColorScheme.ERROR
                                + "This command can only be executed by a player, or use: /javabedrock player <name> ...");
                return true;
            }
            target = (Player) sender;
            commandStartIndex = 0;
        }

        // Build the full command string
        StringBuilder fullCommand = new StringBuilder();
        for (int i = commandStartIndex; i < args.length; i++) {
            if (i > commandStartIndex) fullCommand.append(" ");
            fullCommand.append(args[i]);
        }

        String commandString = fullCommand.toString().trim();

        // Split by separator (\\, ||, or similar)
        String[] commands = commandString.split("\\s*(\\\\\\\\|\\|\\|)\\s*");

        if (commands.length != 2 || commands[0].trim().isEmpty() || commands[1].trim().isEmpty()) {
            plugin.getCoreConfig()
                    .getMessenger()
                    .sendError(
                            sender,
                            "Invalid command format. Use: <java_command> \\\\ <bedrock_command>");
            TextUtil.send(
                    sender,
                    ColorScheme.INFO
                            + "Example: /javabedrock say Hello Java \\\\ say Hello Bedrock");
            return true;
        }

        String javaCommand = commands[0].trim();
        String bedrockCommand = commands[1].trim();

        // Execute the appropriate command
        try {
            if (GeyserUtils.isBedrockPlayer(target)) {
                target.performCommand(bedrockCommand);
            } else {
                target.performCommand(javaCommand);
            }
        } catch (Exception e) {
            plugin.getCoreConfig()
                    .getMessenger()
                    .sendError(sender, "Failed to execute command: " + e.getMessage());
            plugin.getLogger()
                    .warning(
                            "Error executing platform command for "
                                    + target.getName()
                                    + ": "
                                    + e.getMessage());
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("player");
            // Could add common commands here
        } else if (args.length == 2 && args[0].equalsIgnoreCase("player")) {
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        }

        // Filter based on current input
        String current = args[args.length - 1].toLowerCase();
        return completions.stream().filter(s -> s.toLowerCase().startsWith(current)).toList();
    }
}
