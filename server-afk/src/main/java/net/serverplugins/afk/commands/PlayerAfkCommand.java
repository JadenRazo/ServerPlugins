package net.serverplugins.afk.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.serverplugins.afk.ServerAFK;
import net.serverplugins.afk.models.AfkZone;
import net.serverplugins.afk.models.PlayerAfkSession;
import net.serverplugins.afk.models.PlayerStats;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.messages.CommonMessages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Handles player AFK commands: /afk - Toggle AFK status /afk stats - View your statistics /afk
 * stats <player> - View another player's statistics /afk top - View leaderboard /afk zones - List
 * all zones
 */
public class PlayerAfkCommand implements CommandExecutor, TabCompleter {

    private final ServerAFK plugin;

    public PlayerAfkCommand(ServerAFK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        Player player = (Player) sender;

        // No arguments - toggle AFK
        if (args.length == 0) {
            toggleAfk(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "stats":
                if (args.length == 1) {
                    showStats(player, player);
                } else if (args.length == 2) {
                    if (!player.hasPermission("serverafk.stats.others")) {
                        plugin.getAfkConfig().getMessenger().send(player, "no-permission");
                        return true;
                    }
                    Player target = Bukkit.getPlayer(args[1]);
                    if (target == null) {
                        CommonMessages.PLAYER_NOT_FOUND.send(player);
                        return true;
                    }
                    showStats(player, target);
                } else {
                    plugin.getAfkConfig()
                            .getMessenger()
                            .sendError(player, "Usage: /afk stats [player]");
                }
                return true;

            case "top":
            case "leaderboard":
                showLeaderboard(player);
                return true;

            case "zones":
                showZones(player);
                return true;

            case "session":
                showSession(player);
                return true;

            default:
                plugin.getAfkConfig()
                        .getMessenger()
                        .sendError(
                                player, "Unknown subcommand. Use: /afk [stats|top|zones|session]");
                return true;
        }
    }

    private void toggleAfk(Player player) {
        UUID playerId = player.getUniqueId();

        if (plugin.getGlobalAfkManager() == null) {
            plugin.getAfkConfig().getMessenger().sendError(player, "AFK system is not available.");
            return;
        }

        boolean isAfk = plugin.getGlobalAfkManager().toggleAfk(playerId);

        // Message is sent by GlobalAfkManager
    }

    private void showStats(Player viewer, Player target) {
        if (plugin.getStatsManager() == null) {
            plugin.getAfkConfig()
                    .getMessenger()
                    .sendError(viewer, "Statistics system is not available.");
            return;
        }

        PlayerStats stats = plugin.getStatsManager().getStats(target.getUniqueId());

        if (stats == null) {
            plugin.getAfkConfig()
                    .getMessenger()
                    .sendError(viewer, "No statistics found for " + target.getName());
            return;
        }

        // Build stats display using ColorScheme
        var messenger = plugin.getAfkConfig().getMessenger();
        messenger.sendRaw(
                viewer,
                "<gradient:#9b59b6:#3498db>=== AFK Statistics for " + target.getName() + " ===");
        messenger.sendRaw(viewer, ColorScheme.INFO + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        messenger.sendRaw(
                viewer,
                ColorScheme.WARNING
                        + "Total AFK Time: "
                        + ColorScheme.HIGHLIGHT
                        + stats.getFormattedTotalTime());
        messenger.sendRaw(
                viewer,
                ColorScheme.WARNING
                        + "Longest Session: "
                        + ColorScheme.HIGHLIGHT
                        + stats.getFormattedLongestSession());
        messenger.sendRaw(
                viewer,
                ColorScheme.WARNING
                        + "Sessions Completed: "
                        + ColorScheme.HIGHLIGHT
                        + stats.getSessionsCompleted());
        messenger.sendRaw(
                viewer,
                ColorScheme.WARNING
                        + "Total Rewards: "
                        + ColorScheme.HIGHLIGHT
                        + stats.getTotalRewardsReceived());
        messenger.sendRaw(
                viewer,
                ColorScheme.WARNING
                        + "Currency Earned: "
                        + ColorScheme.HIGHLIGHT
                        + String.format("%.2f", stats.getTotalCurrencyEarned()));
        messenger.sendRaw(
                viewer,
                ColorScheme.WARNING
                        + "XP Earned: "
                        + ColorScheme.HIGHLIGHT
                        + stats.getTotalXpEarned());
        messenger.sendRaw(
                viewer,
                ColorScheme.WARNING
                        + "Current Streak: "
                        + ColorScheme.HIGHLIGHT
                        + stats.getCurrentStreakDays()
                        + " days");
        messenger.sendRaw(
                viewer,
                ColorScheme.WARNING
                        + "Best Streak: "
                        + ColorScheme.HIGHLIGHT
                        + stats.getBestStreakDays()
                        + " days");

        // Show favorite zone
        if (stats.getFavoriteZoneId() != null) {
            AfkZone favoriteZone =
                    plugin.getZoneManager().getZoneById(stats.getFavoriteZoneId()).orElse(null);
            if (favoriteZone != null) {
                messenger.sendRaw(
                        viewer,
                        ColorScheme.WARNING
                                + "Favorite Zone: "
                                + ColorScheme.HIGHLIGHT
                                + favoriteZone.getName());
            }
        }

        messenger.sendRaw(viewer, ColorScheme.INFO + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private void showLeaderboard(Player player) {
        if (plugin.getStatsManager() == null) {
            plugin.getAfkConfig()
                    .getMessenger()
                    .sendError(player, "Statistics system is not available.");
            return;
        }

        List<PlayerStats> topPlayers =
                plugin.getStatsManager()
                        .getTopPlayersByAfkTime(plugin.getAfkConfig().getLeaderboardSize());

        if (topPlayers.isEmpty()) {
            plugin.getAfkConfig().getMessenger().sendError(player, "No statistics available yet.");
            return;
        }

        var messenger = plugin.getAfkConfig().getMessenger();
        messenger.sendRaw(player, "<gradient:#9b59b6:#3498db>=== AFK Leaderboard ===");
        messenger.sendRaw(player, ColorScheme.INFO + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        int rank = 1;
        for (PlayerStats stats : topPlayers) {
            Player p = Bukkit.getPlayer(stats.getPlayerUuid());
            String playerName =
                    p != null ? p.getName() : stats.getPlayerUuid().toString().substring(0, 8);

            messenger.sendRaw(
                    player,
                    String.format(
                            ColorScheme.WARNING
                                    + "%d. "
                                    + ColorScheme.HIGHLIGHT
                                    + "%s "
                                    + ColorScheme.INFO
                                    + "- "
                                    + ColorScheme.WARNING
                                    + "%s",
                            rank,
                            playerName,
                            stats.getFormattedTotalTime()));
            rank++;
        }

        messenger.sendRaw(player, ColorScheme.INFO + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private void showZones(Player player) {
        List<AfkZone> zones = plugin.getZoneManager().getAllZones();

        if (zones.isEmpty()) {
            plugin.getAfkConfig().getMessenger().sendError(player, "No AFK zones configured.");
            return;
        }

        var messenger = plugin.getAfkConfig().getMessenger();
        messenger.sendRaw(player, "<gradient:#9b59b6:#3498db>=== AFK Zones ===");
        messenger.sendRaw(player, ColorScheme.INFO + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        for (AfkZone zone : zones) {
            if (!zone.isEnabled()) {
                continue; // Don't show disabled zones to players
            }

            int playerCount = plugin.getPlayerTracker().getPlayersInZone(zone.getId()).size();

            messenger.sendRaw(
                    player,
                    String.format(
                            ColorScheme.WARNING
                                    + "%s "
                                    + ColorScheme.INFO
                                    + "- "
                                    + ColorScheme.HIGHLIGHT
                                    + "%ds interval "
                                    + ColorScheme.INFO
                                    + "- "
                                    + ColorScheme.SUCCESS
                                    + "%d players",
                            zone.getName(),
                            zone.getTimeIntervalSeconds(),
                            playerCount));

            // Show first reward as preview
            if (!zone.getRewards().isEmpty()) {
                messenger.sendRaw(
                        player,
                        "  "
                                + ColorScheme.INFO
                                + ColorScheme.ARROW
                                + " "
                                + ColorScheme.HIGHLIGHT
                                + zone.getRewards().get(0).getDisplayName());
            }
        }

        messenger.sendRaw(player, ColorScheme.INFO + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private void showSession(Player player) {
        UUID playerId = player.getUniqueId();
        PlayerAfkSession session = plugin.getPlayerTracker().getSession(playerId);

        if (session == null) {
            plugin.getAfkConfig()
                    .getMessenger()
                    .sendError(player, "You are not in an AFK session.");
            return;
        }

        AfkZone zone = session.getCurrentZone();
        if (zone == null) {
            plugin.getAfkConfig().getMessenger().sendError(player, "Invalid session state.");
            return;
        }

        var messenger = plugin.getAfkConfig().getMessenger();
        messenger.sendRaw(player, "<gradient:#9b59b6:#3498db>=== Current AFK Session ===");
        messenger.sendRaw(player, ColorScheme.INFO + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        messenger.sendRaw(
                player, ColorScheme.WARNING + "Zone: " + ColorScheme.HIGHLIGHT + zone.getName());
        messenger.sendRaw(
                player,
                ColorScheme.WARNING
                        + "Time in zone: "
                        + ColorScheme.HIGHLIGHT
                        + formatSeconds(session.getTimeInZoneSeconds()));
        messenger.sendRaw(
                player,
                ColorScheme.WARNING
                        + "Next reward in: "
                        + ColorScheme.HIGHLIGHT
                        + formatSeconds(
                                zone.getTimeIntervalSeconds()
                                        - session.getTimeSinceLastRewardSeconds()));
        messenger.sendRaw(
                player,
                ColorScheme.WARNING
                        + "Rewards this session: "
                        + ColorScheme.HIGHLIGHT
                        + session.getRewardsEarnedThisSession());
        messenger.sendRaw(
                player,
                ColorScheme.WARNING
                        + "Currency earned: "
                        + ColorScheme.HIGHLIGHT
                        + String.format("%.2f", session.getCurrencyEarnedThisSession()));
        messenger.sendRaw(
                player,
                ColorScheme.WARNING
                        + "XP earned: "
                        + ColorScheme.HIGHLIGHT
                        + session.getXpEarnedThisSession());

        if (session.isManuallyAfk()) {
            messenger.sendRaw(player, ColorScheme.SUCCESS + "Status: Manually AFK");
        }

        if (session.needsVerification()) {
            messenger.sendRaw(
                    player,
                    ColorScheme.ERROR + ColorScheme.WARNING_ICON + " Verification Required!");
        }

        messenger.sendRaw(player, ColorScheme.INFO + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private String formatSeconds(long seconds) {
        if (seconds < 0) seconds = 0;

        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, secs);
        } else {
            return String.format("%ds", secs);
        }
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("stats");
            completions.add("top");
            completions.add("zones");
            completions.add("session");

            // Filter by what they've typed
            String input = args[0].toLowerCase();
            completions.removeIf(s -> !s.toLowerCase().startsWith(input));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("stats")) {
            // Tab complete player names for /afk stats <player>
            if (sender.hasPermission("serverafk.stats.others")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    completions.add(p.getName());
                }
            }
        }

        return completions;
    }
}
