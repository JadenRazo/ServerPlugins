package net.serverplugins.arcade.commands;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.protection.ExclusionManager;
import net.serverplugins.arcade.statistics.StatisticsTracker;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Main gambling command handler. Commands: - /gamble exclude <duration> - Self-exclude from
 * gambling - /gamble exclude status - Check exclusion status - /gamble stats [player] - View
 * gambling statistics
 */
public class GambleCommand implements CommandExecutor, TabCompleter {

    private final ServerArcade plugin;
    private final ExclusionManager exclusionManager;
    private final StatisticsTracker statisticsTracker;
    private final DecimalFormat moneyFormat = new DecimalFormat("#,##0");
    private final DecimalFormat percentFormat = new DecimalFormat("#0.0");

    // Pending confirmations (player UUID -> expiry time)
    private final Map<UUID, PendingExclusion> pendingConfirmations = new HashMap<>();

    public GambleCommand(ServerArcade plugin) {
        this.plugin = plugin;
        this.exclusionManager = plugin.getExclusionManager();
        this.statisticsTracker = plugin.getStatisticsTracker();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "exclude" -> handleExclude(player, args);
            case "stats" -> handleStats(player, args);
            default -> sendHelpMessage(player);
        }

        return true;
    }

    /** Handle /gamble exclude commands. */
    private void handleExclude(Player player, String[] args) {
        if (exclusionManager == null) {
            TextUtil.sendError(
                    player, "Exclusion system is currently unavailable (database not connected).");
            return;
        }

        if (args.length == 1) {
            TextUtil.sendError(player, "Usage: /gamble exclude <duration|status>");
            TextUtil.send(
                    player,
                    ColorScheme.INFO
                            + "Examples: /gamble exclude 1d, /gamble exclude 7d, /gamble exclude permanent");
            TextUtil.send(player, ColorScheme.INFO + "Or: /gamble exclude status");
            return;
        }

        String subCommand = args[1].toLowerCase();

        if (subCommand.equals("status")) {
            handleExclusionStatus(player);
            return;
        }

        if (subCommand.equals("confirm")) {
            handleExclusionConfirm(player);
            return;
        }

        // Parse duration
        String duration = args[1];
        long durationMillis;
        boolean permanent = false;

        try {
            durationMillis = ExclusionManager.parseDuration(duration);
            permanent = durationMillis == Long.MAX_VALUE;
        } catch (IllegalArgumentException e) {
            TextUtil.sendError(player, e.getMessage());
            return;
        }

        // Store pending confirmation
        PendingExclusion pending = new PendingExclusion(durationMillis, permanent);
        pendingConfirmations.put(player.getUniqueId(), pending);

        // Send confirmation prompt
        TextUtil.send(player, "");
        TextUtil.send(
                player,
                "<yellow>"
                        + ColorScheme.WARNING_ICON
                        + " <red><bold>WARNING</bold> <yellow>"
                        + ColorScheme.WARNING_ICON);
        TextUtil.send(player, "");
        TextUtil.send(
                player, ColorScheme.INFO + "You are about to self-exclude from gambling for:");
        TextUtil.send(
                player,
                permanent
                        ? "<red><bold>PERMANENTLY</bold>"
                        : ColorScheme.EMPHASIS + formatDuration(durationMillis));
        TextUtil.send(player, "");
        TextUtil.send(player, "<red><bold>This action CANNOT be undone!</bold>");
        TextUtil.send(player, ColorScheme.INFO + "You will NOT be able to:");
        TextUtil.send(
                player,
                ColorScheme.INFO
                        + "  "
                        + ColorScheme.BULLET
                        + " Place bets on crash, lottery, or dice games");
        TextUtil.send(
                player, ColorScheme.INFO + "  " + ColorScheme.BULLET + " Access gambling machines");
        TextUtil.send(
                player, ColorScheme.INFO + "  " + ColorScheme.BULLET + " Use gambling commands");
        TextUtil.send(player, "");
        TextUtil.send(
                player, ColorScheme.INFO + "Only server administrators can remove exclusions.");
        TextUtil.send(player, "");
        TextUtil.send(
                player,
                ColorScheme.INFO
                        + "Type "
                        + ColorScheme.COMMAND
                        + "/gamble exclude confirm "
                        + ColorScheme.INFO
                        + "to proceed.");
        TextUtil.send(player, ColorScheme.INFO + "This confirmation expires in 30 seconds.");
        TextUtil.send(player, "");

        // Schedule expiry
        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            pendingConfirmations.remove(player.getUniqueId());
                        },
                        20L * 30); // 30 seconds
    }

    /** Handle /gamble exclude confirm. */
    private void handleExclusionConfirm(Player player) {
        PendingExclusion pending = pendingConfirmations.remove(player.getUniqueId());

        if (pending == null) {
            TextUtil.sendError(
                    player,
                    "No pending exclusion found. Please run /gamble exclude <duration> first.");
            return;
        }

        // Apply exclusion
        exclusionManager.excludePlayer(
                player.getUniqueId(),
                player.getName(),
                pending.duration,
                pending.permanent,
                "Self-excluded");

        TextUtil.send(player, "");
        TextUtil.sendSuccess(player, "You have been excluded from gambling.");
        if (pending.permanent) {
            TextUtil.send(player, ColorScheme.INFO + "Exclusion: <red><bold>PERMANENT</bold>");
        } else {
            TextUtil.send(
                    player,
                    ColorScheme.INFO
                            + "Exclusion Duration: "
                            + ColorScheme.EMPHASIS
                            + formatDuration(pending.duration));
        }
        TextUtil.send(
                player,
                ColorScheme.INFO
                        + "If you change your mind, please contact a server administrator.");
        TextUtil.send(player, "");

        plugin.getLogger()
                .info(
                        String.format(
                                "Player %s self-excluded from gambling%s",
                                player.getName(),
                                pending.permanent
                                        ? " permanently"
                                        : " for " + formatDuration(pending.duration)));
    }

    /** Handle /gamble exclude status. */
    private void handleExclusionStatus(Player player) {
        ExclusionManager.ExclusionRecord record =
                exclusionManager.getExclusion(player.getUniqueId());

        if (record == null) {
            TextUtil.sendSuccess(player, "You are NOT excluded from gambling.");
            return;
        }

        TextUtil.send(player, "");
        TextUtil.send(
                player,
                "<red>" + ColorScheme.WARNING_ICON + " You are currently excluded from gambling.");
        TextUtil.send(
                player,
                ColorScheme.INFO
                        + "Excluded Since: "
                        + ColorScheme.HIGHLIGHT
                        + formatTimestamp(record.getExcludedAt()));
        TextUtil.send(
                player,
                ColorScheme.INFO
                        + "Time Remaining: "
                        + ColorScheme.EMPHASIS
                        + record.getFormattedRemaining());
        TextUtil.send(
                player, ColorScheme.INFO + "Reason: " + ColorScheme.HIGHLIGHT + record.getReason());
        TextUtil.send(
                player,
                ColorScheme.INFO + "To remove this exclusion, contact a server administrator.");
        TextUtil.send(player, "");
    }

    /** Handle /gamble stats [player]. */
    private void handleStats(Player player, String[] args) {
        if (statisticsTracker == null) {
            TextUtil.sendError(
                    player, "Statistics system is currently unavailable (database not connected).");
            return;
        }

        String targetName = args.length > 1 ? args[1] : player.getName();
        Player target = Bukkit.getPlayer(targetName);
        UUID targetId = target != null ? target.getUniqueId() : null;

        if (targetId == null) {
            TextUtil.sendError(player, "Player not found: " + targetName);
            return;
        }

        StatisticsTracker.PlayerStats stats = statisticsTracker.getPlayerStats(targetId);

        if (stats == null) {
            TextUtil.sendError(player, "No gambling statistics found for " + targetName);
            return;
        }

        // Build stats display
        TextUtil.send(player, "<dark_gray>╔══════════════════════════════════╗");
        TextUtil.send(
                player,
                "<dark_gray>║  <gold><bold>🎰 "
                        + stats.playerName
                        + "'s Gambling Stats</bold>  <dark_gray>║");
        TextUtil.send(player, "<dark_gray>╠══════════════════════════════════╣");
        TextUtil.send(
                player,
                "<dark_gray>║ <green><bold>Net Profit:</bold> <white>$"
                        + moneyFormat.format(stats.netProfit)
                        + "             <dark_gray>║");
        TextUtil.send(
                player,
                "<dark_gray>║ <gray>Total Wagered: <white>$"
                        + moneyFormat.format(stats.totalWagered)
                        + "          <dark_gray>║");
        TextUtil.send(
                player,
                "<dark_gray>║ <gray>Win Rate: <white>"
                        + percentFormat.format(stats.getWinRate())
                        + "%                    <dark_gray>║");
        TextUtil.send(player, "<dark_gray>║                                  ║");

        // Crash stats
        if (stats.crashTotalBets > 0) {
            TextUtil.send(
                    player,
                    "<dark_gray>║ <gold><bold>🎲 Crash</bold>                        <dark_gray>║");
            TextUtil.send(
                    player,
                    "<dark_gray>║   <gray>Biggest Win: <white>$"
                            + moneyFormat.format(stats.crashBiggestWin)
                            + " <gray>("
                            + String.format("%.2f", stats.crashHighestMult)
                            + "x)   <dark_gray>║");
            TextUtil.send(
                    player,
                    "<dark_gray>║   <gray>Highest Mult: <white>"
                            + String.format("%.2f", stats.crashHighestMult)
                            + "x               <dark_gray>║");
            TextUtil.send(
                    player,
                    "<dark_gray>║   <gray>Total Bets: <white>"
                            + stats.crashTotalBets
                            + "              <dark_gray>║");
            TextUtil.send(player, "<dark_gray>║                                  ║");
        }

        // Lottery stats
        if (stats.lotteryTotalBets > 0) {
            TextUtil.send(
                    player,
                    "<dark_gray>║ <yellow><bold>🎰 Lottery</bold>                      <dark_gray>║");
            TextUtil.send(
                    player,
                    "<dark_gray>║   <gray>Jackpots Won: <white>"
                            + stats.lotteryTotalWins
                            + "            <dark_gray>║");
            TextUtil.send(
                    player,
                    "<dark_gray>║   <gray>Biggest Win: <white>$"
                            + moneyFormat.format(stats.lotteryBiggestWin)
                            + "        <dark_gray>║");
            TextUtil.send(player, "<dark_gray>║                                  ║");
        }

        // Dice stats
        if (stats.diceTotalBets > 0) {
            TextUtil.send(
                    player,
                    "<dark_gray>║ <aqua><bold>🎲 Dice</bold>                         <dark_gray>║");
            TextUtil.send(
                    player,
                    "<dark_gray>║   <gray>Biggest Win: <white>$"
                            + moneyFormat.format(stats.diceBiggestWin)
                            + "         <dark_gray>║");
            TextUtil.send(
                    player,
                    "<dark_gray>║   <gray>Win Rate: <white>"
                            + percentFormat.format(stats.getDiceWinRate())
                            + "%              <dark_gray>║");
            TextUtil.send(player, "<dark_gray>║                                  ║");
        }

        // Streaks
        String streakEmoji =
                stats.currentStreak > 0
                        ? "<green>🔥"
                        : stats.currentStreak < 0 ? "<red>❄" : "<gray>➖";
        String streakText =
                stats.currentStreak > 0
                        ? "<green>+" + stats.currentStreak + " wins"
                        : stats.currentStreak < 0
                                ? "<red>" + Math.abs(stats.currentStreak) + " losses"
                                : "<gray>0";

        TextUtil.send(
                player,
                "<dark_gray>║ <light_purple><bold>📊 Streaks</bold>                      <dark_gray>║");
        TextUtil.send(
                player,
                "<dark_gray>║   "
                        + streakEmoji
                        + " <gray>Current: "
                        + streakText
                        + "          <dark_gray>║");
        TextUtil.send(
                player,
                "<dark_gray>║   <green>"
                        + ColorScheme.CHECKMARK
                        + " <gray>Best Win Streak: <white>"
                        + stats.bestWinStreak
                        + "      <dark_gray>║");
        TextUtil.send(
                player,
                "<dark_gray>║   <red>"
                        + ColorScheme.CROSS
                        + " <gray>Worst Loss Streak: <white>"
                        + Math.abs(stats.worstLossStreak)
                        + "    <dark_gray>║");
        TextUtil.send(player, "<dark_gray>╚══════════════════════════════════╝");
    }

    /** Send help message. */
    private void sendHelpMessage(Player player) {
        TextUtil.send(player, "");
        TextUtil.send(player, "<gold><bold>=== Gambling Commands ===</bold>");
        TextUtil.send(
                player, "<yellow>/gamble exclude <duration> <gray>- Self-exclude from gambling");
        TextUtil.send(player, "<yellow>/gamble exclude status <gray>- Check your exclusion status");
        TextUtil.send(player, "<yellow>/gamble stats [player] <gray>- View gambling statistics");
        TextUtil.send(player, "");
        TextUtil.send(player, ColorScheme.INFO + "Example durations: 1d, 7d, 30d, permanent");
        TextUtil.send(player, "");
    }

    /** Format duration in human-readable format. */
    private String formatDuration(long millis) {
        long days = millis / (1000 * 60 * 60 * 24);
        if (days > 0) {
            return days + " day" + (days > 1 ? "s" : "");
        }

        long hours = millis / (1000 * 60 * 60);
        if (hours > 0) {
            return hours + " hour" + (hours > 1 ? "s" : "");
        }

        long minutes = millis / (1000 * 60);
        return minutes + " minute" + (minutes > 1 ? "s" : "");
    }

    /** Format timestamp. */
    private String formatTimestamp(long timestamp) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm");
        return sdf.format(new java.util.Date(timestamp));
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("exclude");
            completions.add("stats");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("exclude")) {
            completions.add("1d");
            completions.add("7d");
            completions.add("30d");
            completions.add("permanent");
            completions.add("status");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("stats")) {
            // Add online player names
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        }

        return completions;
    }

    /** Pending exclusion confirmation data. */
    private static class PendingExclusion {
        final long duration;
        final boolean permanent;

        PendingExclusion(long duration, boolean permanent) {
            this.duration = duration;
            this.permanent = permanent;
        }
    }
}
