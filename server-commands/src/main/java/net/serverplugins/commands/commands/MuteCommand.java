package net.serverplugins.commands.commands;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.ServerCommands;
import net.serverplugins.commands.data.punishment.PunishmentRecord;
import net.serverplugins.commands.data.punishment.PunishmentType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class MuteCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;
    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)([smhdw])$");

    public MuteCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("servercommands.mute")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        if (args.length < 1) {
            TextUtil.sendError(
                    sender, "Usage: " + ColorScheme.COMMAND + "/mute <player> [duration] [reason]");
            TextUtil.sendInfo(sender, "Duration examples: 30s, 5m, 1h, 7d, 1w");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            CommonMessages.PLAYER_NOT_FOUND.send(sender);
            return true;
        }

        if (target.isOnline() && target.getPlayer().hasPermission("servercommands.mute.exempt")) {
            TextUtil.sendError(sender, "You cannot mute this player!");
            return true;
        }

        long duration = -1;
        String reason = "No reason provided";
        int reasonStartIndex = 1;

        if (args.length > 1) {
            Long parsedDuration = parseDuration(args[1]);
            if (parsedDuration != null) {
                duration = parsedDuration;
                reasonStartIndex = 2;
            }
        }

        if (args.length > reasonStartIndex) {
            reason = String.join(" ", Arrays.copyOfRange(args, reasonStartIndex, args.length));
        }

        String muterName = sender instanceof Player ? sender.getName() : "Console";
        plugin.getMuteManager().mute(target.getUniqueId(), duration, reason, muterName);

        // Log to punishment history
        PunishmentRecord record =
                PunishmentRecord.builder()
                        .target(target.getUniqueId(), target.getName())
                        .staff(sender)
                        .type(PunishmentType.MUTE)
                        .reason(reason)
                        .duration(duration == -1 ? null : duration)
                        .build();
        plugin.getPunishmentHistoryManager().logPunishment(record);

        String durationStr = duration == -1 ? "permanently" : "for " + formatDuration(duration);
        TextUtil.sendSuccess(sender, target.getName() + " has been muted " + durationStr + "!");

        if (target.isOnline()) {
            TextUtil.sendError(
                    target.getPlayer(),
                    "You have been muted "
                            + durationStr
                            + "! Reason: "
                            + ColorScheme.HIGHLIGHT
                            + reason);
        }

        TextUtil.broadcastRaw(
                ColorScheme.ERROR
                        + target.getName()
                        + " "
                        + ColorScheme.INFO
                        + "has been muted "
                        + durationStr
                        + " by "
                        + ColorScheme.ERROR
                        + muterName
                        + ColorScheme.INFO
                        + ".");

        return true;
    }

    private Long parseDuration(String input) {
        Matcher matcher = DURATION_PATTERN.matcher(input.toLowerCase());
        if (!matcher.matches()) return null;

        long amount = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);

        return switch (unit) {
            case "s" -> amount * 1000;
            case "m" -> amount * 60 * 1000;
            case "h" -> amount * 60 * 60 * 1000;
            case "d" -> amount * 24 * 60 * 60 * 1000;
            case "w" -> amount * 7 * 24 * 60 * 60 * 1000;
            default -> null;
        };
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + " day" + (days > 1 ? "s" : "");
        if (hours > 0) return hours + " hour" + (hours > 1 ? "s" : "");
        if (minutes > 0) return minutes + " minute" + (minutes > 1 ? "s" : "");
        return seconds + " second" + (seconds > 1 ? "s" : "");
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2) {
            return Arrays.asList("30s", "5m", "1h", "1d", "7d", "30d");
        }
        return Collections.emptyList();
    }
}
