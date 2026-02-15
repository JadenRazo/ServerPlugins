package net.serverplugins.events.commands;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.messages.MessageBuilder;
import net.serverplugins.api.messages.PluginMessenger;
import net.serverplugins.events.ServerEvents;
import net.serverplugins.events.data.EventStats;
import net.serverplugins.events.events.EventManager;
import net.serverplugins.events.events.ServerEvent;
import net.serverplugins.events.gui.EventsGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** Admin command for managing events. */
public class EventsCommand implements CommandExecutor, TabCompleter {

    private final ServerEvents plugin;
    private final PluginMessenger messenger;

    public EventsCommand(ServerEvents plugin) {
        this.plugin = plugin;
        this.messenger = plugin.getEventsConfig().getMessenger();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("serverevents.admin")) {
            messenger.sendError(sender, "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "trigger" -> handleTrigger(sender, args);
            case "random" -> handleRandom(sender);
            case "stop" -> handleStop(sender);
            case "reload" -> handleReload(sender);
            case "status" -> handleStatus(sender);
            case "gui", "menu" -> handleGui(sender);
            case "leaderboard", "top" -> handleLeaderboard(sender);
            case "history" -> handleHistory(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleTrigger(CommandSender sender, String[] args) {
        EventManager manager = plugin.getEventManager();

        if (manager.hasActiveEvent()) {
            messenger.sendError(sender, "An event is already in progress!");
            MessageBuilder.create()
                    .prefix(messenger.getPrefix())
                    .info("Use ")
                    .command("/event stop")
                    .info(" to end it first.")
                    .send(sender);
            return;
        }

        if (args.length < 2) {
            messenger.sendError(sender, "Usage: /event trigger <type> [challenge]");
            messenger.sendInfo(
                    sender, "Types: pinata, spelling, crafting, math, drop_party, dragon");
            return;
        }

        String typeName = args[1].toLowerCase();
        ServerEvent.EventType type = ServerEvent.EventType.fromString(typeName);

        if (type == null) {
            messenger.sendError(sender, "Unknown event type: " + typeName);
            return;
        }

        // Special handling for crafting with specific challenge
        if (type == ServerEvent.EventType.CRAFTING && args.length >= 3) {
            String challengeKey = args[2].toLowerCase();
            if (manager.triggerCraftingEvent(challengeKey)) {
                MessageBuilder.create()
                        .prefix(messenger.getPrefix())
                        .checkmark()
                        .success("Triggered crafting challenge: ")
                        .highlight(challengeKey)
                        .send(sender);
            } else {
                messenger.sendError(sender, "Failed to trigger challenge: " + challengeKey);
            }
            return;
        }

        if (manager.triggerEvent(type)) {
            MessageBuilder.create()
                    .prefix(messenger.getPrefix())
                    .checkmark()
                    .success("Triggered event: ")
                    .highlight(type.getDisplayName())
                    .send(sender);
        } else {
            messenger.sendError(sender, "Failed to trigger event.");
        }
    }

    private void handleRandom(CommandSender sender) {
        EventManager manager = plugin.getEventManager();

        if (manager.hasActiveEvent()) {
            messenger.sendError(sender, "An event is already in progress!");
            return;
        }

        if (manager.triggerRandomEvent()) {
            ServerEvent event = manager.getActiveEvent();
            MessageBuilder.create()
                    .prefix(messenger.getPrefix())
                    .checkmark()
                    .success("Triggered random event: ")
                    .highlight(event != null ? event.getDisplayName() : "Unknown")
                    .send(sender);
        } else {
            messenger.sendError(sender, "Failed to trigger random event.");
        }
    }

    private void handleStop(CommandSender sender) {
        EventManager manager = plugin.getEventManager();

        if (!manager.hasActiveEvent()) {
            messenger.sendError(sender, "No active event to stop.");
            return;
        }

        String eventName = manager.getActiveEvent().getDisplayName();
        if (manager.stopCurrentEvent()) {
            MessageBuilder.create()
                    .prefix(messenger.getPrefix())
                    .checkmark()
                    .success("Stopped event: ")
                    .highlight(eventName)
                    .send(sender);
        } else {
            messenger.sendError(sender, "Failed to stop event.");
        }
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfiguration();
        messenger.sendSuccess(sender, "Configuration reloaded.");
    }

    private void handleStatus(CommandSender sender) {
        EventManager manager = plugin.getEventManager();

        MessageBuilder.create()
                .prefix(messenger.getPrefix())
                .emphasis("--- Event Status ---")
                .send(sender);

        if (manager.hasActiveEvent()) {
            ServerEvent event = manager.getActiveEvent();
            MessageBuilder.create()
                    .prefix(messenger.getPrefix())
                    .warning("Active Event: ")
                    .highlight(event.getDisplayName())
                    .send(sender);
        } else {
            MessageBuilder.create()
                    .prefix(messenger.getPrefix())
                    .warning("Active Event: ")
                    .info("None")
                    .send(sender);
        }

        int nextEvent = manager.getTimeUntilNextEvent();
        if (nextEvent >= 0) {
            int minutes = nextEvent / 60;
            int seconds = nextEvent % 60;
            MessageBuilder.create()
                    .prefix(messenger.getPrefix())
                    .warning("Next Event In: ")
                    .highlight(minutes + "m " + seconds + "s")
                    .send(sender);
        } else {
            MessageBuilder.create()
                    .prefix(messenger.getPrefix())
                    .warning("Scheduler: ")
                    .info("Disabled")
                    .send(sender);
        }

        MessageBuilder.create()
                .prefix(messenger.getPrefix())
                .warning("Economy: ")
                .append(
                        plugin.hasEconomy()
                                ? ColorScheme.SUCCESS + "Connected"
                                : ColorScheme.ERROR + "Not connected")
                .send(sender);
    }

    private void handleGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messenger.sendError(sender, "This command can only be used by players.");
            return;
        }

        new EventsGui(plugin, player).open();
    }

    private void handleLeaderboard(CommandSender sender) {
        EventStats stats = plugin.getEventStats();
        List<EventStats.PlayerStats> topPlayers = stats.getTopWinners(10);

        MessageBuilder.create()
                .prefix(messenger.getPrefix())
                .emphasis("--- Event Leaderboard ---")
                .send(sender);

        if (topPlayers.isEmpty()) {
            messenger.sendInfo(sender, "No event winners yet!");
            return;
        }

        int rank = 1;
        for (EventStats.PlayerStats player : topPlayers) {
            String medal =
                    switch (rank) {
                        case 1 -> ColorScheme.EMPHASIS + "★ ";
                        case 2 -> ColorScheme.HIGHLIGHT + "★ ";
                        case 3 -> ColorScheme.ERROR + "★ ";
                        default -> ColorScheme.INFO + rank + ". ";
                    };
            MessageBuilder.create()
                    .prefix(messenger.getPrefix())
                    .append(medal)
                    .highlight(player.name())
                    .info(" - ")
                    .emphasis(player.wins() + " wins")
                    .info(" | ")
                    .success("$" + player.coinsEarned())
                    .send(sender);
            rank++;
        }
    }

    private void handleHistory(CommandSender sender) {
        EventStats stats = plugin.getEventStats();
        List<EventStats.EventRecord> recentEvents = stats.getRecentEvents(10);

        MessageBuilder.create()
                .prefix(messenger.getPrefix())
                .emphasis("--- Recent Events ---")
                .send(sender);

        if (recentEvents.isEmpty()) {
            messenger.sendInfo(sender, "No events recorded yet!");
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm");
        for (EventStats.EventRecord record : recentEvents) {
            String eventName =
                    record.eventType() != null ? record.eventType().getDisplayName() : "Unknown";
            String winner = record.winnerName() != null ? record.winnerName() : "No winner";
            String time = sdf.format(new Date(record.timestamp() * 1000));

            MessageBuilder.create()
                    .prefix(messenger.getPrefix())
                    .info(time + " ")
                    .warning(eventName)
                    .info(" - Winner: ")
                    .highlight(winner)
                    .send(sender);
        }
    }

    private void sendHelp(CommandSender sender) {
        MessageBuilder.create()
                .prefix(messenger.getPrefix())
                .emphasis("--- Events Commands ---")
                .send(sender);

        MessageBuilder.create()
                .prefix(messenger.getPrefix())
                .command("/event trigger <type>")
                .info(" - Trigger specific event")
                .send(sender);

        MessageBuilder.create()
                .prefix(messenger.getPrefix())
                .command("/event random")
                .info(" - Trigger random event")
                .send(sender);

        MessageBuilder.create()
                .prefix(messenger.getPrefix())
                .command("/event stop")
                .info(" - Stop current event")
                .send(sender);

        MessageBuilder.create()
                .prefix(messenger.getPrefix())
                .command("/event status")
                .info(" - View event status")
                .send(sender);

        MessageBuilder.create()
                .prefix(messenger.getPrefix())
                .command("/event leaderboard")
                .info(" - View top winners")
                .send(sender);

        MessageBuilder.create()
                .prefix(messenger.getPrefix())
                .command("/event history")
                .info(" - View recent events")
                .send(sender);

        MessageBuilder.create()
                .prefix(messenger.getPrefix())
                .command("/event reload")
                .info(" - Reload config")
                .send(sender);

        MessageBuilder.create()
                .prefix(messenger.getPrefix())
                .command("/event gui")
                .info(" - Open admin menu")
                .send(sender);

        messenger.sendInfo(
                sender, "Event types: pinata, spelling, crafting, math, drop_party, dragon");
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("serverevents.admin")) {
            return List.of();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(
                    Arrays.asList(
                            "trigger",
                            "random",
                            "stop",
                            "status",
                            "reload",
                            "gui",
                            "leaderboard",
                            "history"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("trigger")) {
            completions.addAll(
                    Arrays.stream(ServerEvent.EventType.values())
                            .map(t -> t.getConfigKey())
                            .collect(Collectors.toList()));
        } else if (args.length == 3
                && args[0].equalsIgnoreCase("trigger")
                && args[1].equalsIgnoreCase("crafting")) {
            completions.addAll(plugin.getEventsConfig().getCraftingChallengeKeys());
        }

        String prefix = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix))
                .collect(Collectors.toList());
    }
}
