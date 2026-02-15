package net.serverplugins.filter.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.messages.Placeholder;
import net.serverplugins.filter.FilterConfig;
import net.serverplugins.filter.ServerFilter;
import net.serverplugins.filter.data.FilterLevel;
import net.serverplugins.filter.data.FilterPreferenceManager;
import net.serverplugins.filter.gui.FilterSettingsGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SwearFilterCommand implements CommandExecutor, TabCompleter {

    private final ServerFilter plugin;
    private final FilterPreferenceManager preferences;
    private final FilterConfig config;

    public SwearFilterCommand(
            ServerFilter plugin, FilterPreferenceManager preferences, FilterConfig config) {
        this.plugin = plugin;
        this.preferences = preferences;
        this.config = config;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("serverfilter.use")) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        if (args.length == 0) {
            // Open GUI
            new FilterSettingsGui(plugin, player, preferences).open();
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload" -> {
                if (!player.hasPermission("serverfilter.admin")) {
                    CommonMessages.NO_PERMISSION.send(player);
                    return true;
                }
                plugin.reload();
                config.getMessenger().send(player, "reload-success");
            }

            case "strict", "moderate", "relaxed", "minimal" -> {
                FilterLevel level = FilterLevel.valueOf(subCommand.toUpperCase());

                // Check permission for specific level
                String levelPerm = "serverfilter.level." + subCommand;
                if (!player.hasPermission(levelPerm)) {
                    config.getMessenger().send(player, "filter-level-no-permission");
                    return true;
                }

                preferences.setFilterLevel(player.getUniqueId(), player.getName(), level);
                config.getMessenger()
                        .send(
                                player,
                                "filter-level-set",
                                Placeholder.of("level", level.getDisplayName()));
            }

            case "status", "info" -> {
                FilterLevel current = preferences.getFilterLevel(player.getUniqueId());
                config.getMessenger()
                        .send(
                                player,
                                "filter-level-info",
                                Placeholder.of("level", current.getDisplayName()));

                // Show description
                String description =
                        ColorScheme.INFO
                                + "         "
                                + ColorScheme.SECONDARY
                                + current.getDescription();
                config.getMessenger().sendRaw(player, description);
            }

            default -> {
                config.getMessenger().send(player, "unknown-option");
            }
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions =
                    new ArrayList<>(
                            Arrays.stream(FilterLevel.values())
                                    .map(l -> l.name().toLowerCase())
                                    .collect(Collectors.toList()));
            completions.add("status");

            if (sender.hasPermission("serverfilter.admin")) {
                completions.add("reload");
            }

            return completions.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
