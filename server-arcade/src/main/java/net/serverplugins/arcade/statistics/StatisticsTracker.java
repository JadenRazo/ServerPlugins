package net.serverplugins.arcade.statistics;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.serverplugins.api.database.Database;
import net.serverplugins.arcade.ServerArcade;
import org.bukkit.Bukkit;

/**
 * Tracks and persists gambling statistics to database. Data is consumed by example.com website
 * and Discord bot.
 */
public class StatisticsTracker {

    private final ServerArcade plugin;
    private final Database database;

    // PERFORMANCE: Cache player stats to reduce database queries
    // Configurable TTL with write-through invalidation
    private final Cache<UUID, PlayerStats> statsCache;

    public StatisticsTracker(ServerArcade plugin) {
        this.plugin = plugin;
        this.database = plugin.getDatabase();

        // Read cache settings from config
        int statsTtlMinutes = plugin.getConfig().getInt("performance.cache.stats_ttl_minutes", 10);
        int maxEntries = plugin.getConfig().getInt("performance.cache.max_entries", 10000);

        // Initialize cache with configured expiration
        this.statsCache =
                Caffeine.newBuilder()
                        .expireAfterWrite(statsTtlMinutes, TimeUnit.MINUTES)
                        .maximumSize(maxEntries)
                        .build();

        plugin.getLogger()
                .info(
                        "Statistics cache initialized ("
                                + statsTtlMinutes
                                + "min TTL, "
                                + maxEntries
                                + " max entries)");
    }

    /** Record a crash game result. */
    public void recordCrashGame(
            UUID playerId, String playerName, int bet, double multiplier, int payout, boolean won) {
        if (database == null) return;

        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            try {
                                // Insert into history
                                database.executeUpdate(
                                        "INSERT INTO server_arcade_history (player_uuid, game_type, bet_amount, payout, multiplier, won, timestamp) "
                                                + "VALUES (?, 'crash', ?, ?, ?, ?, ?)",
                                        playerId.toString(),
                                        bet,
                                        payout,
                                        multiplier,
                                        won,
                                        System.currentTimeMillis());

                                // Update player stats
                                ensurePlayerStats(playerId, playerName);

                                if (won) {
                                    int profit = payout - bet;

                                    database.executeUpdate(
                                            "UPDATE server_arcade_stats SET "
                                                    + "crash_total_bets = crash_total_bets + 1, "
                                                    + "crash_total_wins = crash_total_wins + 1, "
                                                    + "crash_biggest_win = GREATEST(crash_biggest_win, ?), "
                                                    + "crash_highest_mult = GREATEST(crash_highest_mult, ?), "
                                                    + "total_wagered = total_wagered + ?, "
                                                    + "total_won = total_won + ?, "
                                                    + "net_profit = net_profit + ?, "
                                                    + "current_streak = IF(current_streak >= 0, current_streak + 1, 1), "
                                                    + "best_win_streak = GREATEST(best_win_streak, IF(current_streak >= 0, current_streak + 1, 1)), "
                                                    + "last_updated = ? "
                                                    + "WHERE player_uuid = ?",
                                            payout,
                                            multiplier,
                                            bet,
                                            payout,
                                            profit,
                                            System.currentTimeMillis(),
                                            playerId.toString());
                                } else {
                                    database.executeUpdate(
                                            "UPDATE server_arcade_stats SET "
                                                    + "crash_total_bets = crash_total_bets + 1, "
                                                    + "total_wagered = total_wagered + ?, "
                                                    + "total_lost = total_lost + ?, "
                                                    + "net_profit = net_profit - ?, "
                                                    + "current_streak = IF(current_streak <= 0, current_streak - 1, -1), "
                                                    + "worst_loss_streak = LEAST(worst_loss_streak, IF(current_streak <= 0, current_streak - 1, -1)), "
                                                    + "last_updated = ? "
                                                    + "WHERE player_uuid = ?",
                                            bet,
                                            bet,
                                            bet,
                                            System.currentTimeMillis(),
                                            playerId.toString());
                                }

                                // PERFORMANCE: Invalidate cache on write
                                statsCache.invalidate(playerId);

                            } catch (Exception e) {
                                plugin.getLogger()
                                        .warning("Failed to record crash game: " + e.getMessage());
                            }
                        });
    }

    /** Record a lottery/jackpot win. */
    public void recordLotteryWin(
            UUID playerId, String playerName, int bet, int payout, String lotteryType) {
        if (database == null) return;

        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            try {
                                // Insert into history
                                database.executeUpdate(
                                        "INSERT INTO server_arcade_history (player_uuid, game_type, bet_amount, payout, multiplier, won, timestamp) "
                                                + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                                        playerId.toString(),
                                        lotteryType,
                                        bet,
                                        payout,
                                        (double) payout / bet,
                                        true,
                                        System.currentTimeMillis());

                                // Update player stats
                                ensurePlayerStats(playerId, playerName);

                                int profit = payout - bet;

                                database.executeUpdate(
                                        "UPDATE server_arcade_stats SET "
                                                + "lottery_total_bets = lottery_total_bets + 1, "
                                                + "lottery_total_wins = lottery_total_wins + 1, "
                                                + "lottery_biggest_win = GREATEST(lottery_biggest_win, ?), "
                                                + "total_wagered = total_wagered + ?, "
                                                + "total_won = total_won + ?, "
                                                + "net_profit = net_profit + ?, "
                                                + "current_streak = IF(current_streak >= 0, current_streak + 1, 1), "
                                                + "best_win_streak = GREATEST(best_win_streak, IF(current_streak >= 0, current_streak + 1, 1)), "
                                                + "last_updated = ? "
                                                + "WHERE player_uuid = ?",
                                        payout,
                                        bet,
                                        payout,
                                        profit,
                                        System.currentTimeMillis(),
                                        playerId.toString());

                                // PERFORMANCE: Invalidate cache on write
                                statsCache.invalidate(playerId);

                            } catch (Exception e) {
                                plugin.getLogger()
                                        .warning("Failed to record lottery win: " + e.getMessage());
                            }
                        });
    }

    /** Record a lottery bet (when player doesn't win). */
    public void recordLotteryLoss(UUID playerId, String playerName, int bet, String lotteryType) {
        if (database == null) return;

        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            try {
                                // Insert into history
                                database.executeUpdate(
                                        "INSERT INTO server_arcade_history (player_uuid, game_type, bet_amount, payout, multiplier, won, timestamp) "
                                                + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                                        playerId.toString(),
                                        lotteryType,
                                        bet,
                                        0,
                                        0.0,
                                        false,
                                        System.currentTimeMillis());

                                // Update player stats
                                ensurePlayerStats(playerId, playerName);

                                database.executeUpdate(
                                        "UPDATE server_arcade_stats SET "
                                                + "lottery_total_bets = lottery_total_bets + 1, "
                                                + "total_wagered = total_wagered + ?, "
                                                + "total_lost = total_lost + ?, "
                                                + "net_profit = net_profit - ?, "
                                                + "current_streak = IF(current_streak <= 0, current_streak - 1, -1), "
                                                + "worst_loss_streak = LEAST(worst_loss_streak, IF(current_streak <= 0, current_streak - 1, -1)), "
                                                + "last_updated = ? "
                                                + "WHERE player_uuid = ?",
                                        bet,
                                        bet,
                                        bet,
                                        System.currentTimeMillis(),
                                        playerId.toString());

                                // PERFORMANCE: Invalidate cache on write
                                statsCache.invalidate(playerId);

                            } catch (Exception e) {
                                plugin.getLogger()
                                        .warning(
                                                "Failed to record lottery loss: " + e.getMessage());
                            }
                        });
    }

    /** Record a dice game result. */
    public void recordDiceGame(
            UUID playerId, String playerName, int bet, double multiplier, int payout, boolean won) {
        if (database == null) return;

        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            try {
                                // Insert into history
                                database.executeUpdate(
                                        "INSERT INTO server_arcade_history (player_uuid, game_type, bet_amount, payout, multiplier, won, timestamp) "
                                                + "VALUES (?, 'dice', ?, ?, ?, ?, ?)",
                                        playerId.toString(),
                                        bet,
                                        payout,
                                        multiplier,
                                        won,
                                        System.currentTimeMillis());

                                // Update player stats
                                ensurePlayerStats(playerId, playerName);

                                if (won) {
                                    int profit = payout - bet;

                                    database.executeUpdate(
                                            "UPDATE server_arcade_stats SET "
                                                    + "dice_total_bets = dice_total_bets + 1, "
                                                    + "dice_total_wins = dice_total_wins + 1, "
                                                    + "dice_biggest_win = GREATEST(dice_biggest_win, ?), "
                                                    + "total_wagered = total_wagered + ?, "
                                                    + "total_won = total_won + ?, "
                                                    + "net_profit = net_profit + ?, "
                                                    + "current_streak = IF(current_streak >= 0, current_streak + 1, 1), "
                                                    + "best_win_streak = GREATEST(best_win_streak, IF(current_streak >= 0, current_streak + 1, 1)), "
                                                    + "last_updated = ? "
                                                    + "WHERE player_uuid = ?",
                                            payout,
                                            bet,
                                            payout,
                                            profit,
                                            System.currentTimeMillis(),
                                            playerId.toString());
                                } else {
                                    database.executeUpdate(
                                            "UPDATE server_arcade_stats SET "
                                                    + "dice_total_bets = dice_total_bets + 1, "
                                                    + "total_wagered = total_wagered + ?, "
                                                    + "total_lost = total_lost + ?, "
                                                    + "net_profit = net_profit - ?, "
                                                    + "current_streak = IF(current_streak <= 0, current_streak - 1, -1), "
                                                    + "worst_loss_streak = LEAST(worst_loss_streak, IF(current_streak <= 0, current_streak - 1, -1)), "
                                                    + "last_updated = ? "
                                                    + "WHERE player_uuid = ?",
                                            bet,
                                            bet,
                                            bet,
                                            System.currentTimeMillis(),
                                            playerId.toString());
                                }

                                // PERFORMANCE: Invalidate cache on write
                                statsCache.invalidate(playerId);

                            } catch (Exception e) {
                                plugin.getLogger()
                                        .warning("Failed to record dice game: " + e.getMessage());
                            }
                        });
    }

    /**
     * Ensure player has a stats entry. Uses INSERT ... ON DUPLICATE KEY UPDATE to avoid
     * SELECT+INSERT race and connection leaks.
     */
    private void ensurePlayerStats(UUID playerId, String playerName) {
        try {
            database.executeUpdate(
                    "INSERT INTO server_arcade_stats (player_uuid, player_name, last_updated) VALUES (?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE player_name = VALUES(player_name)",
                    playerId.toString(),
                    playerName,
                    System.currentTimeMillis());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to ensure player stats: " + e.getMessage());
        }
    }

    /**
     * Get player statistics (for API/Discord). PERFORMANCE: Uses cache to avoid repeated database
     * queries.
     */
    public PlayerStats getPlayerStats(UUID playerId) {
        if (database == null) return null;

        // Check cache first
        PlayerStats cached = statsCache.getIfPresent(playerId);
        if (cached != null) {
            return cached;
        }

        // Not in cache, fetch from database
        try (ResultSet rs =
                database.executeQuery(
                        "SELECT * FROM server_arcade_stats WHERE player_uuid = ?",
                        playerId.toString())) {

            if (rs.next()) {
                PlayerStats stats = new PlayerStats(rs);
                // Cache the result
                statsCache.put(playerId, stats);
                return stats;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get player stats: " + e.getMessage());
        }

        return null;
    }

    /** Player statistics data object. */
    public static class PlayerStats {
        public String playerName;
        public int crashTotalBets;
        public int crashTotalWins;
        public int crashBiggestWin;
        public double crashHighestMult;
        public int lotteryTotalBets;
        public int lotteryTotalWins;
        public int lotteryBiggestWin;
        public int diceTotalBets;
        public int diceTotalWins;
        public int diceBiggestWin;
        public long totalWagered;
        public long totalWon;
        public long totalLost;
        public long netProfit;
        public int currentStreak;
        public int bestWinStreak;
        public int worstLossStreak;

        public PlayerStats(ResultSet rs) throws Exception {
            this.playerName = rs.getString("player_name");
            this.crashTotalBets = rs.getInt("crash_total_bets");
            this.crashTotalWins = rs.getInt("crash_total_wins");
            this.crashBiggestWin = rs.getInt("crash_biggest_win");
            this.crashHighestMult = rs.getDouble("crash_highest_mult");
            this.lotteryTotalBets = rs.getInt("lottery_total_bets");
            this.lotteryTotalWins = rs.getInt("lottery_total_wins");
            this.lotteryBiggestWin = rs.getInt("lottery_biggest_win");
            this.diceTotalBets = rs.getInt("dice_total_bets");
            this.diceTotalWins = rs.getInt("dice_total_wins");
            this.diceBiggestWin = rs.getInt("dice_biggest_win");
            this.totalWagered = rs.getLong("total_wagered");
            this.totalWon = rs.getLong("total_won");
            this.totalLost = rs.getLong("total_lost");
            this.netProfit = rs.getLong("net_profit");
            this.currentStreak = rs.getInt("current_streak");
            this.bestWinStreak = rs.getInt("best_win_streak");
            this.worstLossStreak = rs.getInt("worst_loss_streak");
        }

        public double getWinRate() {
            int totalGames = crashTotalBets + lotteryTotalBets + diceTotalBets;
            int totalWins = crashTotalWins + lotteryTotalWins + diceTotalWins;
            return totalGames > 0 ? (double) totalWins / totalGames * 100 : 0;
        }

        public double getCrashWinRate() {
            return crashTotalBets > 0 ? (double) crashTotalWins / crashTotalBets * 100 : 0;
        }

        public double getDiceWinRate() {
            return diceTotalBets > 0 ? (double) diceTotalWins / diceTotalBets * 100 : 0;
        }
    }
}
