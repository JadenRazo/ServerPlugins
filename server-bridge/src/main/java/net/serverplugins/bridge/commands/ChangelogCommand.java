package net.serverplugins.bridge.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.bridge.ServerBridge;
import net.serverplugins.bridge.messaging.RedisClient;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class ChangelogCommand implements CommandExecutor, TabCompleter {

    private final ServerBridge plugin;
    private static final List<String> PLATFORMS =
            Arrays.asList("smp", "lobby", "website", "store", "discord", "network");
    private static final List<String> CATEGORIES =
            Arrays.asList("added", "changed", "fixed", "removed", "security");

    public ChangelogCommand(ServerBridge plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var messenger = plugin.getBridgeConfig().getMessenger();

        if (!sender.hasPermission("serverbridge.changelog")) {
            messenger.sendError(sender, "You don't have permission to use this command.");
            return true;
        }

        if (args.length < 4) {
            sendUsage(sender);
            return true;
        }

        String version = args[0];
        String platform = args[1].toLowerCase();
        String category = args[2].toLowerCase();

        // Validate platform
        if (!PLATFORMS.contains(platform)) {
            messenger.sendError(sender, "Invalid platform! Use: " + String.join(", ", PLATFORMS));
            return true;
        }

        // Validate category
        if (!CATEGORIES.contains(category)) {
            messenger.sendError(sender, "Invalid category! Use: " + String.join(", ", CATEGORIES));
            return true;
        }

        // Join remaining args as description
        String description = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

        if (description.isEmpty()) {
            messenger.sendError(sender, "Description cannot be empty!");
            return true;
        }

        RedisClient redis = plugin.getRedisClient();
        if (redis == null || !redis.isConnected()) {
            messenger.sendError(sender, "Redis is not connected. Cannot add changelog entry.");
            return true;
        }

        String playerName = sender instanceof Player ? sender.getName() : "Console";
        String requestId = UUID.randomUUID().toString();

        redis.publishChangelogAddAsync(
                requestId, version, platform, category, description, playerName);

        // Send confirmation using ColorScheme
        String message =
                ColorScheme.wrap("Changelog Entry Sent", ColorScheme.SUCCESS)
                        + "\n"
                        + ColorScheme.INFO
                        + "Version: "
                        + ColorScheme.wrap(version, ColorScheme.HIGHLIGHT)
                        + "\n"
                        + ColorScheme.INFO
                        + "Platform: "
                        + ColorScheme.wrap(getPlatformDisplay(platform), ColorScheme.COMMAND)
                        + "\n"
                        + ColorScheme.INFO
                        + "Category: "
                        + ColorScheme.wrap(
                                getCategoryDisplay(category), getCategoryColorTag(category))
                        + "\n"
                        + ColorScheme.INFO
                        + "Description: "
                        + ColorScheme.wrap(description, ColorScheme.HIGHLIGHT);

        messenger.sendRaw(sender, message);
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("serverbridge.changelog")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            // Version suggestions
            return Arrays.asList("v1.0.0", "v1.1.0", "v1.2.0", "v2.0.0");
        }

        if (args.length == 2) {
            // Platform suggestions
            return PLATFORMS.stream()
                    .filter(p -> p.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 3) {
            // Category suggestions
            return CATEGORIES.stream()
                    .filter(c -> c.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }

    private void sendUsage(CommandSender sender) {
        var messenger = plugin.getBridgeConfig().getMessenger();

        String usage =
                ColorScheme.wrap("Changelog Command Usage", ColorScheme.EMPHASIS)
                        + "\n"
                        + ColorScheme.WARNING
                        + "/changelog <version> <platform> <category> <description>\n\n"
                        + ColorScheme.INFO
                        + "Platforms: "
                        + ColorScheme.wrap(String.join(", ", PLATFORMS), ColorScheme.COMMAND)
                        + "\n"
                        + ColorScheme.INFO
                        + "Categories: "
                        + ColorScheme.wrap(String.join(", ", CATEGORIES), ColorScheme.SUCCESS)
                        + "\n\n"
                        + ColorScheme.INFO
                        + "Example: "
                        + ColorScheme.wrap(
                                "/changelog v1.2.0 smp added New parkour course",
                                ColorScheme.HIGHLIGHT);

        messenger.sendRaw(sender, usage);
    }

    private String getPlatformDisplay(String platform) {
        return switch (platform) {
            case "smp" -> "SMP Server";
            case "lobby" -> "Lobby";
            case "website" -> "Website";
            case "store" -> "Store";
            case "discord" -> "Discord Bot";
            case "network" -> "Network";
            default -> platform;
        };
    }

    private String getCategoryDisplay(String category) {
        return switch (category) {
            case "added" -> "Added";
            case "changed" -> "Changed";
            case "fixed" -> "Fixed";
            case "removed" -> "Removed";
            case "security" -> "Security";
            default -> category;
        };
    }

    private String getCategoryColorTag(String category) {
        return switch (category) {
            case "added" -> ColorScheme.SUCCESS;
            case "changed" -> ColorScheme.WARNING;
            case "fixed" -> ColorScheme.COMMAND;
            case "removed" -> ColorScheme.ERROR;
            case "security" -> ColorScheme.EMPHASIS;
            default -> ColorScheme.HIGHLIGHT;
        };
    }
}
