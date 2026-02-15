package net.serverplugins.core.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.core.ServerCore;
import net.serverplugins.core.data.PlayerDataManager;
import net.serverplugins.core.features.Feature;
import net.serverplugins.core.features.PerPlayerFeature;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class ToggleCommand implements CommandExecutor, TabCompleter {

    private final ServerCore plugin;
    private final Map<String, Feature> features;

    public ToggleCommand(ServerCore plugin, Map<String, Feature> features) {
        this.plugin = plugin;
        this.features = features;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "toggle" -> {
                return handleToggle(sender, args);
            }
            case "list" -> {
                return handleList(sender);
            }
            case "reload" -> {
                return handleReload(sender);
            }
            default -> {
                sendHelp(sender);
                return true;
            }
        }
    }

    private boolean handleToggle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            TextUtil.send(sender, "<red>Usage: /servercore toggle <feature> [global]");
            return true;
        }

        String featureName = args[1].toLowerCase();
        Feature feature = features.get(featureName);

        if (feature == null) {
            TextUtil.send(sender, "<red>Unknown feature: " + featureName);
            return true;
        }

        // Check if global toggle (requires admin permission)
        boolean globalToggle = args.length >= 3 && args[2].equalsIgnoreCase("global");

        if (globalToggle) {
            // Global toggle - requires admin permission
            if (!sender.hasPermission("servercore.admin")) {
                TextUtil.send(
                        sender, "<red>You don't have permission to globally toggle features!");
                return true;
            }

            if (feature.isEnabled()) {
                feature.disable();
                plugin.getCoreConfig().setFeatureEnabled(featureName, false);
                TextUtil.send(
                        sender, "<green>Globally disabled feature: <white>" + feature.getName());
            } else {
                feature.enable();
                plugin.getCoreConfig().setFeatureEnabled(featureName, true);
                TextUtil.send(
                        sender, "<green>Globally enabled feature: <white>" + feature.getName());
            }
        } else {
            // Per-player toggle
            if (!(sender instanceof Player player)) {
                TextUtil.send(
                        sender,
                        "<red>Only players can use per-player toggles. Use 'global' for server-wide toggle.");
                return true;
            }

            if (!(feature instanceof PerPlayerFeature perPlayerFeature)) {
                TextUtil.send(sender, "<red>This feature does not support per-player toggles.");
                TextUtil.send(
                        sender,
                        "<gray>Use <white>/servercore toggle "
                                + featureName
                                + " global <gray>to toggle globally.");
                return true;
            }

            PlayerDataManager dataManager = plugin.getPlayerDataManager();
            boolean newState = dataManager.toggleFeature(player, featureName);

            if (newState) {
                TextUtil.send(
                        player, "<green>Enabled <white>" + feature.getName() + " <green>for you!");
            } else {
                TextUtil.send(
                        player, "<red>Disabled <white>" + feature.getName() + " <red>for you!");
            }
        }

        return true;
    }

    private boolean handleList(CommandSender sender) {
        TextUtil.send(
                sender,
                "<gradient:#FFD700:#FFA500><bold>=== ServerCore Features ===</bold></gradient>");
        TextUtil.send(sender, "");

        boolean isPlayer = sender instanceof Player;
        Player player = isPlayer ? (Player) sender : null;
        PlayerDataManager dataManager = plugin.getPlayerDataManager();

        features.forEach(
                (name, feature) -> {
                    boolean globalEnabled = feature.isEnabled();
                    String globalStatus = globalEnabled ? "<green>ON" : "<red>OFF";

                    TextUtil.send(
                            sender, "<yellow>" + name + " <gray>- " + feature.getDescription());

                    // Show per-player status if applicable
                    if (isPlayer && feature instanceof PerPlayerFeature perPlayerFeature) {
                        boolean playerEnabled = perPlayerFeature.isEnabledForPlayer(player);
                        String playerStatus = playerEnabled ? "<green>ON" : "<red>OFF";
                        TextUtil.send(
                                sender,
                                "  <gray>Global: "
                                        + globalStatus
                                        + " <dark_gray>| <gray>You: "
                                        + playerStatus);
                    } else {
                        TextUtil.send(sender, "  <gray>Global: " + globalStatus);
                    }
                });

        TextUtil.send(sender, "");
        if (isPlayer) {
            TextUtil.send(
                    sender,
                    "<gray>Use <white>/servercore toggle <feature> <gray>to customize your settings.");
        }
        if (sender.hasPermission("servercore.admin")) {
            TextUtil.send(
                    sender,
                    "<gray>Use <white>/servercore toggle <feature> global <gray>to change server defaults.");
        }

        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("servercore.admin")) {
            TextUtil.send(sender, "<red>You don't have permission to reload the configuration!");
            return true;
        }

        plugin.reloadConfiguration();
        TextUtil.send(sender, "<green>ServerCore configuration reloaded!");
        return true;
    }

    private void sendHelp(CommandSender sender) {
        TextUtil.send(
                sender,
                "<gradient:#FFD700:#FFA500><bold>=== ServerCore Commands ===</bold></gradient>");
        TextUtil.send(sender, "");
        TextUtil.send(sender, "<yellow>/servercore list <gray>- List all features");
        TextUtil.send(
                sender,
                "<yellow>/servercore toggle <feature> <gray>- Toggle a feature for yourself");

        if (sender.hasPermission("servercore.admin")) {
            TextUtil.send(
                    sender,
                    "<yellow>/servercore toggle <feature> global <gray>- Toggle a feature globally");
            TextUtil.send(sender, "<yellow>/servercore reload <gray>- Reload configuration");
        }
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("toggle");
            completions.add("list");
            if (sender.hasPermission("servercore.admin")) {
                completions.add("reload");
            }
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("toggle")) {
            return features.keySet().stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 3
                && args[0].equalsIgnoreCase("toggle")
                && sender.hasPermission("servercore.admin")) {
            completions.add("global");
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return completions;
    }
}
