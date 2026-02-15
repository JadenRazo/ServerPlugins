package net.serverplugins.commands.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.serverplugins.api.broadcast.BroadcastBuilder;
import net.serverplugins.api.broadcast.BroadcastManager;
import net.serverplugins.api.broadcast.BroadcastType;
import net.serverplugins.api.broadcast.Placeholder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.ServerCommands;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/**
 * Enhanced broadcast command supporting the new BroadcastManager system.
 *
 * <p>Usage: - /announce <message> - Simple chat broadcast - /announce key <key> - Send
 * pre-configured broadcast - /announce chat <message> - Chat broadcast - /announce actionbar
 * <message> - Action bar broadcast - /announce title <title> - Title broadcast - /announce title
 * <title> <subtitle> - Title with subtitle
 */
public class BroadcastCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;

    public BroadcastCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Support both new and legacy permissions for backward compatibility
        if (!sender.hasPermission("servercommands.announce")
                && !sender.hasPermission("servercommands.broadcast")) {
            TextUtil.send(sender, "<red>You don't have permission to use this command!");
            return true;
        }

        if (args.length < 1) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        try {
            switch (subCommand) {
                case "key" -> handleKeyBroadcast(sender, args);
                case "chat" -> handleChatBroadcast(sender, args);
                case "actionbar", "action", "ab" -> handleActionBarBroadcast(sender, args);
                case "title" -> handleTitleBroadcast(sender, args);
                case "help", "?" -> sendUsage(sender);
                default -> handleSimpleBroadcast(sender, args);
            }
        } catch (Exception e) {
            TextUtil.send(sender, "<red>Error sending broadcast: " + e.getMessage());
            plugin.getLogger().warning("Broadcast error: " + e.getMessage());
        }

        return true;
    }

    /** Handle simple broadcast: /broadcast <message> */
    private void handleSimpleBroadcast(CommandSender sender, String[] args) {
        String message = String.join(" ", args);
        String prefix =
                plugin.getConfig()
                        .getString(
                                "broadcast.prefix", "<red><bold>[Broadcast]</bold></red> <white>");
        String suffix = plugin.getConfig().getString("broadcast.suffix", "");
        String formatted = prefix + message + suffix;

        BroadcastBuilder builder =
                BroadcastManager.builder("admin-broadcast")
                        .message(formatted)
                        .type(BroadcastType.CHAT)
                        .plugin(plugin);

        // Add sound effect if configured
        if (plugin.getConfig().getBoolean("broadcast.sound.enabled", false)) {
            try {
                Sound sound =
                        Sound.valueOf(
                                plugin.getConfig()
                                        .getString(
                                                "broadcast.sound.type", "ENTITY_PLAYER_LEVELUP"));
                float volume = (float) plugin.getConfig().getDouble("broadcast.sound.volume", 1.0);
                float pitch = (float) plugin.getConfig().getDouble("broadcast.sound.pitch", 1.0);
                builder.sound(sound, volume, pitch);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid sound in config: " + e.getMessage());
            }
        }

        builder.send();

        TextUtil.send(sender, "<green>Broadcast sent to all players!");
    }

    /** Handle key-based broadcast: /broadcast key <key> [placeholders...] */
    private void handleKeyBroadcast(CommandSender sender, String[] args) {
        if (args.length < 2) {
            TextUtil.send(sender, "<red>Usage: /announce key <key> [placeholder:value...]");
            TextUtil.send(sender, "<gray>Example: /broadcast key pinata-spawn");
            return;
        }

        String key = args[1];

        // Parse placeholders from remaining args (format: key:value)
        List<Placeholder> placeholders = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            String[] parts = args[i].split(":", 2);
            if (parts.length == 2) {
                placeholders.add(Placeholder.of(parts[0], parts[1]));
            }
        }

        try {
            BroadcastManager.broadcast(key, placeholders.toArray(new Placeholder[0]));
            TextUtil.send(sender, "<green>Broadcast '" + key + "' sent!");
        } catch (Exception e) {
            TextUtil.send(sender, "<red>Failed to send broadcast '" + key + "'");
            TextUtil.send(sender, "<gray>Make sure the broadcast is defined in a plugin's config");
        }
    }

    /** Handle chat broadcast: /broadcast chat <message> */
    private void handleChatBroadcast(CommandSender sender, String[] args) {
        if (args.length < 2) {
            TextUtil.send(sender, "<red>Usage: /announce chat <message>");
            return;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        BroadcastManager.builder("custom-chat-broadcast")
                .message(message)
                .type(BroadcastType.CHAT)
                .plugin(plugin)
                .send();

        TextUtil.send(sender, "<green>Chat broadcast sent!");
    }

    /** Handle action bar broadcast: /broadcast actionbar <message> */
    private void handleActionBarBroadcast(CommandSender sender, String[] args) {
        if (args.length < 2) {
            TextUtil.send(sender, "<red>Usage: /announce actionbar <message>");
            return;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        BroadcastManager.builder("custom-actionbar-broadcast")
                .message(message)
                .type(BroadcastType.ACTION_BAR)
                .plugin(plugin)
                .send();

        TextUtil.send(sender, "<green>Action bar broadcast sent!");
    }

    /** Handle title broadcast: /broadcast title <title> [subtitle] */
    private void handleTitleBroadcast(CommandSender sender, String[] args) {
        if (args.length < 2) {
            TextUtil.send(sender, "<red>Usage: /announce title <title> [subtitle]");
            return;
        }

        // Find subtitle separator (using | or --)
        int separatorIndex = -1;
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("|") || args[i].equals("--")) {
                separatorIndex = i;
                break;
            }
        }

        String title;
        String subtitle = null;

        if (separatorIndex > 0) {
            title = String.join(" ", Arrays.copyOfRange(args, 1, separatorIndex));
            if (separatorIndex + 1 < args.length) {
                subtitle =
                        String.join(" ", Arrays.copyOfRange(args, separatorIndex + 1, args.length));
            }
        } else {
            title = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        }

        BroadcastBuilder builder =
                BroadcastManager.builder("custom-title-broadcast")
                        .message(title)
                        .type(BroadcastType.TITLE)
                        .plugin(plugin);

        if (subtitle != null) {
            builder.subtitle(subtitle);
        }

        builder.send();

        TextUtil.send(sender, "<green>Title broadcast sent!");
    }

    /** Send usage information */
    private void sendUsage(CommandSender sender) {
        TextUtil.send(sender, "<gold><bold>Broadcast Command Usage:");
        TextUtil.send(sender, "<yellow>/announce <message> <gray>- Simple chat broadcast");
        TextUtil.send(
                sender,
                "<yellow>/announce key <key> [placeholder:value...] <gray>- Send configured broadcast");
        TextUtil.send(sender, "<yellow>/announce chat <message> <gray>- Chat broadcast");
        TextUtil.send(sender, "<yellow>/announce actionbar <message> <gray>- Action bar broadcast");
        TextUtil.send(
                sender, "<yellow>/announce title <title> | <subtitle> <gray>- Title broadcast");
        TextUtil.send(sender, "");
        TextUtil.send(sender, "<gray>Examples:");
        TextUtil.send(sender, "<white>/announce Hello everyone!");
        TextUtil.send(sender, "<white>/announce key server-restart seconds:300");
        TextUtil.send(
                sender, "<white>/announce key event-announcement message:Pinata event starting!");
        TextUtil.send(sender, "<white>/announce actionbar <yellow>Server restarting soon!");
        TextUtil.send(sender, "<white>/announce title <gold>ALERT | <yellow>Important message");
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        // Support both new and legacy permissions for backward compatibility
        if (!sender.hasPermission("servercommands.announce")
                && !sender.hasPermission("servercommands.broadcast")) {
            return new ArrayList<>();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // First argument: subcommands
            completions.addAll(
                    Arrays.asList("key", "chat", "actionbar", "action", "ab", "title", "help"));

            // Filter based on input
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("key")) {
            // For "key" subcommand, suggest some common broadcast keys
            // Note: We can't easily get all registered keys without more API methods
            completions.addAll(
                    Arrays.asList(
                            "event-starting",
                            "event-ended",
                            "pinata-spawn",
                            "dragon-start",
                            "admin-alert"));

            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return completions;
    }
}
