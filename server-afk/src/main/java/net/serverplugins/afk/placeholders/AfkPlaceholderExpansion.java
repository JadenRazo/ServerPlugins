package net.serverplugins.afk.placeholders;

import java.util.List;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.serverplugins.afk.ServerAFK;
import net.serverplugins.afk.managers.StatsManager;
import net.serverplugins.afk.models.AfkZone;
import net.serverplugins.afk.models.PlayerAfkSession;
import net.serverplugins.afk.models.PlayerStats;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion for ServerAFK. Provides placeholders for AFK status, statistics, and
 * leaderboards.
 */
public class AfkPlaceholderExpansion extends PlaceholderExpansion {

    private final ServerAFK plugin;

    public AfkPlaceholderExpansion(ServerAFK plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "serverafk";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // This expansion should persist through reloads
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        // Handle leaderboard placeholders first (they don't require a player)
        if (params.startsWith("rank_")) {
            return handleLeaderboardPlaceholder(params);
        }

        // All other placeholders require a player
        if (player == null) {
            return "";
        }

        // Current session placeholders
        switch (params.toLowerCase()) {
            case "is_afk":
                return String.valueOf(isPlayerAfk(player));

            case "afk_time":
                return formatAfkTime(player);

            case "current_zone":
                return getCurrentZoneName(player);
        }

        // Statistics placeholders (require database access)
        StatsManager statsManager = plugin.getStatsManager();
        if (statsManager == null) {
            return "";
        }

        PlayerStats stats = statsManager.getStats(player.getUniqueId());
        if (stats == null) {
            return "0";
        }

        switch (params.toLowerCase()) {
            case "total_time":
                return formatTotalTime(stats.getTotalAfkTimeSeconds());

            case "total_currency":
                return String.valueOf((int) stats.getTotalCurrencyEarned());

            case "total_rewards":
                return String.valueOf(stats.getTotalRewardsReceived());

            case "sessions":
                return String.valueOf(stats.getSessionsCompleted());

            case "longest_session":
                return formatTotalTime(stats.getLongestSessionSeconds());

            case "current_streak":
                return String.valueOf(stats.getCurrentStreakDays());

            case "favorite_zone":
                return getFavoriteZoneName(stats);

            case "total_xp":
                return String.valueOf(stats.getTotalXpEarned());

            default:
                return null;
        }
    }

    /**
     * Handles leaderboard placeholders. Format: rank_<number>_<type>_<metric> Example: rank_1_name,
     * rank_2_afk_time, rank_3_currency
     */
    private String handleLeaderboardPlaceholder(String params) {
        String[] parts = params.split("_");
        if (parts.length < 3) {
            return "";
        }

        try {
            int rank = Integer.parseInt(parts[1]);
            String metric = parts[2].toLowerCase();

            // rank_1_name format
            if (parts.length == 3 && metric.equals("name")) {
                return getLeaderboardName(rank, "afk_time");
            }

            // rank_1_value format
            if (parts.length == 3 && metric.equals("value")) {
                return getLeaderboardValue(rank, "afk_time");
            }

            // rank_1_<metric>_name or rank_1_<metric>_value format
            if (parts.length >= 4) {
                String type = parts[3].toLowerCase();

                if (type.equals("name")) {
                    return getLeaderboardName(rank, metric);
                } else if (type.equals("value")) {
                    return getLeaderboardValue(rank, metric);
                }
            }

            // Legacy: rank_1_afk_time, rank_2_currency, etc.
            return getLeaderboardValue(rank, metric);

        } catch (NumberFormatException e) {
            return "";
        }
    }

    /** Gets leaderboard player name by rank and metric. */
    private String getLeaderboardName(int rank, String metric) {
        if (rank < 1 || plugin.getStatsManager() == null) {
            return "";
        }

        List<PlayerStats> leaderboard = getLeaderboardForMetric(metric);
        if (leaderboard == null || rank > leaderboard.size()) {
            return "";
        }

        PlayerStats stats = leaderboard.get(rank - 1);
        OfflinePlayer player = Bukkit.getOfflinePlayer(stats.getPlayerUuid());
        return player.getName() != null ? player.getName() : "Unknown";
    }

    /** Gets leaderboard value by rank and metric. */
    private String getLeaderboardValue(int rank, String metric) {
        if (rank < 1 || plugin.getStatsManager() == null) {
            return "0";
        }

        List<PlayerStats> leaderboard = getLeaderboardForMetric(metric);
        if (leaderboard == null || rank > leaderboard.size()) {
            return "0";
        }

        PlayerStats stats = leaderboard.get(rank - 1);

        switch (metric.toLowerCase()) {
            case "afk_time":
            case "time":
                return formatTotalTime(stats.getTotalAfkTimeSeconds());

            case "currency":
            case "coins":
                return String.valueOf((int) stats.getTotalCurrencyEarned());

            case "sessions":
                return String.valueOf(stats.getSessionsCompleted());

            case "rewards":
                return String.valueOf(stats.getTotalRewardsReceived());

            case "streak":
                return String.valueOf(stats.getCurrentStreakDays());

            case "xp":
                return String.valueOf(stats.getTotalXpEarned());

            default:
                return "0";
        }
    }

    /** Gets the appropriate leaderboard for a metric. */
    private List<PlayerStats> getLeaderboardForMetric(String metric) {
        StatsManager statsManager = plugin.getStatsManager();
        int limit = plugin.getAfkConfig().getLeaderboardSize();

        switch (metric.toLowerCase()) {
            case "afk_time":
            case "time":
                return statsManager.getTopPlayersByAfkTime(limit);

            case "currency":
            case "coins":
                return statsManager.getTopPlayersByCurrency(limit);

            case "sessions":
                return statsManager.getTopPlayersBySessions(limit);

            case "streak":
                return statsManager.getTopPlayersByStreak(limit);

            default:
                return List.of();
        }
    }

    /** Checks if a player is currently AFK. */
    private boolean isPlayerAfk(OfflinePlayer player) {
        if (!player.isOnline()) {
            return false;
        }

        PlayerAfkSession session = plugin.getPlayerTracker().getSession(player.getUniqueId());
        return session != null && session.getCurrentZone() != null;
    }

    /** Formats current AFK session time. */
    private String formatAfkTime(OfflinePlayer player) {
        if (!player.isOnline()) {
            return "0s";
        }

        PlayerAfkSession session = plugin.getPlayerTracker().getSession(player.getUniqueId());
        if (session == null || session.getCurrentZone() == null) {
            return "0s";
        }

        long seconds = session.getTimeInZoneSeconds();
        return formatTime(seconds);
    }

    /** Gets current zone name for a player. */
    private String getCurrentZoneName(OfflinePlayer player) {
        if (!player.isOnline()) {
            return "None";
        }

        PlayerAfkSession session = plugin.getPlayerTracker().getSession(player.getUniqueId());
        if (session == null) {
            return "None";
        }

        AfkZone zone = session.getCurrentZone();
        return zone != null ? zone.getName() : "None";
    }

    /** Gets favorite zone name from stats. */
    private String getFavoriteZoneName(PlayerStats stats) {
        Integer zoneId = stats.getFavoriteZoneId();
        if (zoneId == null) {
            return "None";
        }

        return plugin.getZoneManager().getZoneById(zoneId).map(AfkZone::getName).orElse("None");
    }

    /** Formats total time (can be large values). */
    private String formatTotalTime(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }

        long minutes = seconds / 60;
        if (minutes < 60) {
            long secs = seconds % 60;
            return minutes + "m" + (secs > 0 ? " " + secs + "s" : "");
        }

        long hours = minutes / 60;
        long mins = minutes % 60;

        if (hours < 24) {
            return hours + "h" + (mins > 0 ? " " + mins + "m" : "");
        }

        long days = hours / 24;
        long hrs = hours % 24;
        return days + "d" + (hrs > 0 ? " " + hrs + "h" : "");
    }

    /** Formats short time durations (for current session). */
    private String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }

        long minutes = seconds / 60;
        long secs = seconds % 60;

        if (minutes < 60) {
            return minutes + "m" + (secs > 0 ? " " + secs + "s" : "");
        }

        long hours = minutes / 60;
        long mins = minutes % 60;
        return hours + "h" + (mins > 0 ? " " + mins + "m" : "");
    }
}
