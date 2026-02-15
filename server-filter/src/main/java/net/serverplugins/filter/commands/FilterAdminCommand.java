package net.serverplugins.filter.commands;

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
import net.serverplugins.filter.filter.FilterResult;
import net.serverplugins.filter.filter.MessageFilterService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FilterAdminCommand implements CommandExecutor, TabCompleter {

    private final ServerFilter plugin;
    private final MessageFilterService filterService;
    private final FilterConfig config;

    public FilterAdminCommand(
            ServerFilter plugin, MessageFilterService filterService, FilterConfig config) {
        this.plugin = plugin;
        this.filterService = filterService;
        this.config = config;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (!sender.hasPermission("serverfilter.admin")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload" -> {
                plugin.reload();
                config.getMessenger().send(sender, "reload-success");
            }

            case "stats" -> {
                var wordListManager = filterService.getWordListManager();
                config.getMessenger().send(sender, "admin-stats-title");
                config.getMessenger()
                        .send(
                                sender,
                                "admin-stats-words",
                                Placeholder.of(
                                        "count",
                                        String.valueOf(wordListManager.getTotalWordCount())));
                config.getMessenger()
                        .send(
                                sender,
                                "admin-stats-patterns",
                                Placeholder.of(
                                        "count",
                                        String.valueOf(wordListManager.getTotalPatternCount())));
                config.getMessenger()
                        .send(
                                sender,
                                "admin-stats-whitelist",
                                Placeholder.of(
                                        "count",
                                        String.valueOf(
                                                wordListManager.getWhitelistedWords().size())));
            }

            case "test" -> {
                if (args.length < 2) {
                    config.getMessenger().send(sender, "admin-invalid-usage");
                    return true;
                }

                String testMessage = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                testFilter(sender, testMessage);
            }

            default -> sendHelp(sender);
        }

        return true;
    }

    private void testFilter(CommandSender sender, String message) {
        config.getMessenger()
                .send(sender, "admin-test-message", Placeholder.of("message", message));

        // Test against each level
        for (FilterLevel level : FilterLevel.values()) {
            FilterResult result = filterService.analyzeMessage(message, level);
            String filtered = filterService.filterMessage(message, level);

            String resultColor = result.hasMatches() ? ColorScheme.ERROR : ColorScheme.SUCCESS;
            config.getMessenger()
                    .send(
                            sender,
                            "admin-test-level",
                            Placeholder.of("level", level.name()),
                            Placeholder.of("result", resultColor + filtered));

            if (result.hasMatches()) {
                String matches =
                        result.getMatches().stream()
                                .map(m -> m.matchedText() + " (" + m.category().name() + ")")
                                .collect(Collectors.joining(", "));
                config.getMessenger()
                        .send(sender, "admin-test-matches", Placeholder.of("matches", matches));
            }
        }

        // Test slur detection specifically
        boolean containsSlurs = filterService.containsSlurs(message);
        String slurResult =
                containsSlurs
                        ? ColorScheme.ERROR + "YES (would be blocked)"
                        : ColorScheme.SUCCESS + "No";
        config.getMessenger()
                .send(sender, "admin-test-slurs", Placeholder.of("result", slurResult));
    }

    private void sendHelp(CommandSender sender) {
        String help = config.getMessenger().getPrefix() + ColorScheme.HIGHLIGHT + "Commands:";
        config.getMessenger().sendRaw(sender, help);

        config.getMessenger()
                .sendRaw(
                        sender,
                        ColorScheme.INFO + "  /filteradmin reload - Reload config and word lists");
        config.getMessenger()
                .sendRaw(
                        sender, ColorScheme.INFO + "  /filteradmin stats - Show filter statistics");
        config.getMessenger()
                .sendRaw(
                        sender,
                        ColorScheme.INFO + "  /filteradmin test <message> - Test a message");
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        if (!sender.hasPermission("serverfilter.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> completions = List.of("reload", "stats", "test");
            return completions.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
