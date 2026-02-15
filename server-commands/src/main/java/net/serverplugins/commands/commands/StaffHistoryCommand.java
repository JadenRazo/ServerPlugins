package net.serverplugins.commands.commands;

import java.util.*;
import java.util.stream.Collectors;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.ServerCommands;
import net.serverplugins.commands.data.punishment.PunishmentHistoryManager;
import net.serverplugins.commands.data.punishment.PunishmentRecord;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class StaffHistoryCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;
    private static final int PAGE_SIZE = 10;

    public StaffHistoryCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("servercommands.staffhistory")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        if (args.length < 1) {
            TextUtil.sendError(sender, "Usage: /staffhistory <staff> [page]");
            return true;
        }

        PunishmentHistoryManager historyManager = plugin.getPunishmentHistoryManager();
        if (!historyManager.isInitialized()) {
            TextUtil.sendError(sender, "Punishment history database is not available.");
            return true;
        }

        String staffName = args[0];
        OfflinePlayer staff = Bukkit.getOfflinePlayer(staffName);

        if (!staff.hasPlayedBefore() && !staff.isOnline()) {
            TextUtil.sendError(sender, "Staff member not found!");
            return true;
        }

        // Parse page number
        int page = 1;
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
                if (page < 1) page = 1;
            } catch (NumberFormatException ignored) {
            }
        }

        final int finalPage = page;

        TextUtil.send(
                sender,
                "<gray>Loading staff action history for <white>" + staff.getName() + "<gray>...");

        historyManager
                .getStaffHistory(staff.getUniqueId(), PAGE_SIZE, (page - 1) * PAGE_SIZE)
                .thenAccept(
                        records -> {
                            Bukkit.getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                if (records.isEmpty()) {
                                                    TextUtil.send(
                                                            sender,
                                                            "<gray>No punishment records found for staff member <white>"
                                                                    + staff.getName()
                                                                    + "<gray>.");
                                                    return;
                                                }

                                                // Header
                                                TextUtil.send(sender, "");
                                                TextUtil.send(
                                                        sender,
                                                        "<gold><bold>═══ Staff Actions: "
                                                                + staff.getName()
                                                                + " ═══");
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
                                                            "  <gray>Target: <white>"
                                                                    + record.getTargetName()
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
                                                                                .getFormattedDuration());
                                                    }
                                                    TextUtil.send(sender, "");
                                                }

                                                // Footer with pagination hint
                                                if (records.size() == PAGE_SIZE) {
                                                    TextUtil.send(
                                                            sender,
                                                            "<gray>Use <white>/staffhistory "
                                                                    + staff.getName()
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
        if (!sender.hasPermission("servercommands.staffhistory")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .filter(
                            p ->
                                    p.hasPermission("servercommands.ban")
                                            || p.hasPermission("servercommands.kick")
                                            || p.hasPermission("servercommands.mute")
                                            || p.hasPermission("servercommands.warn"))
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            return List.of("1", "2", "3");
        }

        return Collections.emptyList();
    }
}
