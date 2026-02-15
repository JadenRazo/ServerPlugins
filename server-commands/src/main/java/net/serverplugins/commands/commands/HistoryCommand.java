package net.serverplugins.commands.commands;

import java.util.*;
import java.util.stream.Collectors;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.ServerCommands;
import net.serverplugins.commands.data.punishment.PunishmentHistoryManager;
import net.serverplugins.commands.data.punishment.PunishmentRecord;
import net.serverplugins.commands.data.punishment.PunishmentType;
import net.serverplugins.commands.gui.PunishmentHistoryGui;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class HistoryCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;
    private static final int PAGE_SIZE = 10;

    public HistoryCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("servercommands.history")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        if (args.length < 1) {
            TextUtil.sendError(sender, "Usage: /history <player> [type|gui] [page]");
            TextUtil.send(sender, "<gray>Types: ban, tempban, kick, mute, warn, unban, unmute");
            return true;
        }

        PunishmentHistoryManager historyManager = plugin.getPunishmentHistoryManager();
        if (!historyManager.isInitialized()) {
            TextUtil.sendError(sender, "Punishment history database is not available.");
            return true;
        }

        String targetName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            TextUtil.sendError(sender, "Player not found!");
            return true;
        }

        // Parse optional arguments
        PunishmentType filterType = null;
        int page = 1;
        boolean useGui = false;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i].toLowerCase();

            // Check for GUI
            if (arg.equals("gui")) {
                useGui = true;
                continue;
            }

            // Check for page number
            try {
                page = Integer.parseInt(arg);
                if (page < 1) page = 1;
                continue;
            } catch (NumberFormatException ignored) {
            }

            // Check for punishment type
            try {
                filterType = PunishmentType.valueOf(arg.toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        // Open GUI if requested
        if (useGui) {
            if (!(sender instanceof Player player)) {
                TextUtil.sendError(sender, "GUI can only be used by players!");
                return true;
            }
            if (!sender.hasPermission("servercommands.history.gui")) {
                TextUtil.sendError(sender, "You don't have permission to use the history GUI!");
                return true;
            }
            new PunishmentHistoryGui(plugin, player, target, filterType).open();
            return true;
        }

        // Show text-based history
        final PunishmentType finalFilterType = filterType;
        final int finalPage = page;

        TextUtil.send(
                sender,
                "<gray>Loading punishment history for <white>" + target.getName() + "<gray>...");

        historyManager
                .getPlayerHistory(
                        target.getUniqueId(), filterType, PAGE_SIZE, (page - 1) * PAGE_SIZE)
                .thenAccept(
                        records -> {
                            Bukkit.getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                if (records.isEmpty()) {
                                                    if (finalFilterType != null) {
                                                        TextUtil.send(
                                                                sender,
                                                                "<gray>No "
                                                                        + finalFilterType
                                                                                .getDisplayName()
                                                                                .toLowerCase()
                                                                        + " records found for <white>"
                                                                        + target.getName()
                                                                        + "<gray>.");
                                                    } else {
                                                        TextUtil.send(
                                                                sender,
                                                                "<gray>No punishment history found for <white>"
                                                                        + target.getName()
                                                                        + "<gray>.");
                                                    }
                                                    return;
                                                }

                                                // Header
                                                TextUtil.send(sender, "");
                                                TextUtil.send(
                                                        sender,
                                                        "<gold><bold>═══ Punishment History: "
                                                                + target.getName()
                                                                + " ═══");
                                                if (finalFilterType != null) {
                                                    TextUtil.send(
                                                            sender,
                                                            "<gray>Filtering by: <white>"
                                                                    + finalFilterType
                                                                            .getDisplayName());
                                                }
                                                TextUtil.send(
                                                        sender, "<gray>Page: <white>" + finalPage);
                                                TextUtil.send(sender, "");

                                                // Records
                                                for (PunishmentRecord record : records) {
                                                    String status =
                                                            record.isActive()
                                                                    ? "<green>[ACTIVE]"
                                                                    : "<gray>[INACTIVE]";
                                                    String typeColor =
                                                            "<"
                                                                    + record.getType()
                                                                            .getColorCode()
                                                                    + ">";

                                                    TextUtil.send(
                                                            sender,
                                                            "<dark_gray>#"
                                                                    + record.getId()
                                                                    + " "
                                                                    + status
                                                                    + " "
                                                                    + typeColor
                                                                    + record.getType()
                                                                            .getDisplayName());
                                                    TextUtil.send(
                                                            sender,
                                                            "  <gray>Staff: <white>"
                                                                    + record.getStaffName()
                                                                    + " <dark_gray>| <gray>Date: <white>"
                                                                    + record
                                                                            .getFormattedIssuedDate());
                                                    TextUtil.send(
                                                            sender,
                                                            "  <gray>Reason: <white>"
                                                                    + record.getReason());

                                                    if (record.getDurationMs() != null) {
                                                        TextUtil.send(
                                                                sender,
                                                                "  <gray>Duration: <white>"
                                                                        + record
                                                                                .getFormattedDuration()
                                                                        + (record.isActive()
                                                                                ? " <dark_gray>(<white>"
                                                                                        + record
                                                                                                .getFormattedRemainingTime()
                                                                                        + " <gray>remaining<dark_gray>)"
                                                                                : ""));
                                                    }

                                                    if (record.getLiftedAt() != null) {
                                                        TextUtil.send(
                                                                sender,
                                                                "  <gray>Lifted by: <white>"
                                                                        + record.getLiftedByName());
                                                    }
                                                    TextUtil.send(sender, "");
                                                }

                                                // Footer with pagination hint
                                                if (records.size() == PAGE_SIZE) {
                                                    TextUtil.send(
                                                            sender,
                                                            "<gray>Use <white>/history "
                                                                    + target.getName()
                                                                    + (finalFilterType != null
                                                                            ? " "
                                                                                    + finalFilterType
                                                                                            .name()
                                                                                            .toLowerCase()
                                                                            : "")
                                                                    + " "
                                                                    + (finalPage + 1)
                                                                    + " <gray>for more.");
                                                }
                                            });
                        });

        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("servercommands.history")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            List<String> suggestions = new ArrayList<>();
            String input = args[1].toLowerCase();

            // Add type suggestions
            for (PunishmentType type : PunishmentType.values()) {
                if (type.name().toLowerCase().startsWith(input)) {
                    suggestions.add(type.name().toLowerCase());
                }
            }

            // Add GUI option
            if ("gui".startsWith(input)) {
                suggestions.add("gui");
            }

            // Add page numbers
            suggestions.add("1");
            suggestions.add("2");

            return suggestions;
        }

        if (args.length == 3) {
            // Page number or GUI
            List<String> suggestions = new ArrayList<>();
            if ("gui".startsWith(args[2].toLowerCase())) {
                suggestions.add("gui");
            }
            suggestions.add("1");
            suggestions.add("2");
            suggestions.add("3");
            return suggestions;
        }

        return Collections.emptyList();
    }
}
