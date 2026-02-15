package net.serverplugins.parkour.data;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.serverplugins.api.database.Database;

public class ParkourDatabase {

    private final Database database;
    private final Logger logger;

    // Caches to prevent constant DB queries
    private final Map<UUID, Integer> highscoreCache = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> xpCache = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> levelCache = new ConcurrentHashMap<>();

    // Cached leaderboard for placeholders (updated when highscores change)
    private volatile List<LeaderboardEntry> leaderboardCache = new ArrayList<>();

    public ParkourDatabase(Database database) {
        this.database = database;
        this.logger = Logger.getLogger("ServerParkour");
    }

    public void initTables() {
        // Highscores table
        database.execute(
                """
            CREATE TABLE IF NOT EXISTS parkour_highscores (
                uuid VARCHAR(36) PRIMARY KEY,
                username VARCHAR(16),
                highscore INT DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);

        // Lobby XP table
        database.execute(
                """
            CREATE TABLE IF NOT EXISTS lobby_xp (
                uuid VARCHAR(36) PRIMARY KEY,
                username VARCHAR(16),
                xp INT DEFAULT 0,
                level INT DEFAULT 1,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);

        // Game history table (optional, for stats)
        database.execute(
                """
            CREATE TABLE IF NOT EXISTS parkour_games (
                id INT AUTO_INCREMENT PRIMARY KEY,
                uuid VARCHAR(36),
                score INT,
                duration_ms BIGINT,
                played_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);

        // Initial leaderboard load
        refreshLeaderboardCacheAsync();
    }

    /** Load player data into cache asynchronously. Call on player join. */
    public void loadPlayerCache(UUID uuid) {
        CompletableFuture.runAsync(
                () -> {
                    ResultSet rs = null;
                    ResultSet xpRs = null;
                    try {
                        // Load highscore
                        rs =
                                database.executeQuery(
                                        "SELECT highscore FROM parkour_highscores WHERE uuid = ?",
                                        uuid.toString());
                        if (rs != null && rs.next()) {
                            highscoreCache.put(uuid, rs.getInt("highscore"));
                        } else {
                            highscoreCache.put(uuid, 0);
                        }

                        // Load XP and level
                        xpRs =
                                database.executeQuery(
                                        "SELECT xp, level FROM lobby_xp WHERE uuid = ?",
                                        uuid.toString());
                        if (xpRs != null && xpRs.next()) {
                            xpCache.put(uuid, xpRs.getInt("xp"));
                            levelCache.put(uuid, xpRs.getInt("level"));
                        } else {
                            xpCache.put(uuid, 0);
                            levelCache.put(uuid, 1);
                        }
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Failed to load player cache for " + uuid, e);
                        highscoreCache.putIfAbsent(uuid, 0);
                        xpCache.putIfAbsent(uuid, 0);
                        levelCache.putIfAbsent(uuid, 1);
                    } finally {
                        closeQuietly(rs);
                        closeQuietly(xpRs);
                    }
                });
    }

    private void closeQuietly(ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (Exception ignored) {
            }
        }
    }

    /** Clear player from cache on quit. */
    public void unloadPlayerCache(UUID uuid) {
        highscoreCache.remove(uuid);
        xpCache.remove(uuid);
        levelCache.remove(uuid);
    }

    // Highscore methods - uses cache for reads
    public int getHighscore(UUID uuid) {
        return highscoreCache.getOrDefault(uuid, 0);
    }

    /**
     * Attempt to save a new highscore. Uses atomic update to only save if score is higher. Returns
     * a CompletableFuture that resolves to true if the score was a new highscore.
     */
    public CompletableFuture<Boolean> saveHighscoreIfHigher(
            UUID uuid, String username, int newScore) {
        return CompletableFuture.supplyAsync(
                () -> {
                    ResultSet rs = null;
                    ResultSet verifyRs = null;
                    try {
                        // Check if player exists
                        rs =
                                database.executeQuery(
                                        "SELECT highscore FROM parkour_highscores WHERE uuid = ?",
                                        uuid.toString());

                        boolean exists = rs != null && rs.next();
                        int currentDbScore = exists ? rs.getInt("highscore") : 0;
                        closeQuietly(rs);
                        rs = null;

                        // Only proceed if new score is actually higher
                        if (newScore <= currentDbScore) {
                            return false;
                        }

                        if (!exists) {
                            // Insert new record
                            database.execute(
                                    "INSERT INTO parkour_highscores (uuid, username, highscore, created_at, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                                    uuid.toString(),
                                    username,
                                    newScore);
                        } else {
                            // Atomic update - only update if current DB value is still less than
                            // new score
                            database.execute(
                                    "UPDATE parkour_highscores SET highscore = ?, username = ?, updated_at = CURRENT_TIMESTAMP WHERE uuid = ? AND highscore < ?",
                                    newScore,
                                    username,
                                    uuid.toString(),
                                    newScore);
                        }

                        // Verify the update was successful by reading back
                        verifyRs =
                                database.executeQuery(
                                        "SELECT highscore FROM parkour_highscores WHERE uuid = ?",
                                        uuid.toString());

                        if (verifyRs != null && verifyRs.next()) {
                            int savedScore = verifyRs.getInt("highscore");

                            // Update cache with actual DB value
                            highscoreCache.put(uuid, savedScore);

                            // Refresh leaderboard async
                            refreshLeaderboardCacheAsync();

                            return savedScore == newScore;
                        }

                        return false;
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Failed to save highscore for " + uuid, e);
                        return false;
                    } finally {
                        closeQuietly(rs);
                        closeQuietly(verifyRs);
                    }
                });
    }

    /** Refresh the leaderboard cache asynchronously. */
    public void refreshLeaderboardCacheAsync() {
        CompletableFuture.runAsync(
                () -> {
                    ResultSet rs = null;
                    try {
                        rs =
                                database.executeQuery(
                                        "SELECT username, highscore FROM parkour_highscores WHERE highscore > 0 ORDER BY highscore DESC LIMIT 10");
                        List<LeaderboardEntry> newCache = new ArrayList<>();
                        if (rs != null) {
                            while (rs.next()) {
                                newCache.add(
                                        new LeaderboardEntry(
                                                rs.getString("username"), rs.getInt("highscore")));
                            }
                        }
                        leaderboardCache = newCache;
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Failed to refresh leaderboard cache", e);
                    } finally {
                        closeQuietly(rs);
                    }
                });
    }

    /** Get cached leaderboard entry by rank (1-based). */
    public LeaderboardEntry getLeaderboardEntry(int rank) {
        List<LeaderboardEntry> cache = leaderboardCache;
        if (rank < 1 || rank > cache.size()) {
            return null;
        }
        return cache.get(rank - 1);
    }

    /** Get a player's rank from the cached leaderboard. Returns -1 if not on leaderboard. */
    public int getCachedRank(UUID uuid) {
        int highscore = highscoreCache.getOrDefault(uuid, 0);
        if (highscore <= 0) {
            return -1;
        }

        List<LeaderboardEntry> cache = leaderboardCache;
        // Count how many scores are higher than this player's
        int rank = 1;
        for (LeaderboardEntry entry : cache) {
            if (entry.score() > highscore) {
                rank++;
            }
        }
        return rank;
    }

    /** Get top scores asynchronously (fetches fresh from DB). */
    public CompletableFuture<List<LeaderboardEntry>> getTopScores(int limit) {
        return CompletableFuture.supplyAsync(
                () -> {
                    List<LeaderboardEntry> entries = new ArrayList<>();
                    ResultSet rs = null;
                    try {
                        rs =
                                database.executeQuery(
                                        "SELECT username, highscore FROM parkour_highscores WHERE highscore > 0 ORDER BY highscore DESC LIMIT ?",
                                        limit);
                        if (rs != null) {
                            while (rs.next()) {
                                entries.add(
                                        new LeaderboardEntry(
                                                rs.getString("username"), rs.getInt("highscore")));
                            }
                        }
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Failed to get top scores", e);
                    } finally {
                        closeQuietly(rs);
                    }
                    return entries;
                });
    }

    /** Get a player's rank on the leaderboard asynchronously. */
    public CompletableFuture<Integer> getPlayerRank(UUID uuid) {
        return CompletableFuture.supplyAsync(
                () -> {
                    ResultSet rs = null;
                    try {
                        rs =
                                database.executeQuery(
                                        "SELECT COUNT(*) + 1 as rank FROM parkour_highscores WHERE highscore > (SELECT COALESCE(highscore, 0) FROM parkour_highscores WHERE uuid = ?)",
                                        uuid.toString());
                        if (rs != null && rs.next()) {
                            return rs.getInt("rank");
                        }
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Failed to get player rank for " + uuid, e);
                    } finally {
                        closeQuietly(rs);
                    }
                    return -1;
                });
    }

    // XP methods - uses cache
    public int getXp(UUID uuid) {
        return xpCache.getOrDefault(uuid, 0);
    }

    public int getLevel(UUID uuid) {
        return levelCache.getOrDefault(uuid, 1);
    }

    public CompletableFuture<Void> addXp(UUID uuid, String username, int amount) {
        return CompletableFuture.runAsync(
                () -> {
                    ResultSet rs = null;
                    ResultSet verifyRs = null;
                    try {
                        // Get current XP from cache or DB
                        int currentXp = xpCache.getOrDefault(uuid, 0);
                        int newXp = currentXp + amount;
                        int newLevel = calculateLevel(newXp);

                        // Update DB first
                        rs =
                                database.executeQuery(
                                        "SELECT xp FROM lobby_xp WHERE uuid = ?", uuid.toString());

                        boolean exists = rs != null && rs.next();
                        closeQuietly(rs);
                        rs = null;

                        if (!exists) {
                            database.execute(
                                    "INSERT INTO lobby_xp (uuid, username, xp, level, created_at, updated_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                                    uuid.toString(),
                                    username,
                                    newXp,
                                    newLevel);
                        } else {
                            database.execute(
                                    "UPDATE lobby_xp SET xp = xp + ?, level = ?, username = ?, updated_at = CURRENT_TIMESTAMP WHERE uuid = ?",
                                    amount,
                                    newLevel,
                                    username,
                                    uuid.toString());
                        }

                        // Read back actual value and update cache
                        verifyRs =
                                database.executeQuery(
                                        "SELECT xp, level FROM lobby_xp WHERE uuid = ?",
                                        uuid.toString());
                        if (verifyRs != null && verifyRs.next()) {
                            xpCache.put(uuid, verifyRs.getInt("xp"));
                            levelCache.put(uuid, verifyRs.getInt("level"));
                        }
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Failed to add XP for " + uuid, e);
                    } finally {
                        closeQuietly(rs);
                        closeQuietly(verifyRs);
                    }
                });
    }

    public static int calculateLevel(int xp) {
        // Simple level formula: level = sqrt(xp / 100) + 1
        return (int) Math.floor(Math.sqrt(xp / 100.0)) + 1;
    }

    public static int getXpForLevel(int level) {
        // Inverse of above: xp = (level - 1)^2 * 100
        return (level - 1) * (level - 1) * 100;
    }

    /** Admin command to directly set a player's highscore. */
    public CompletableFuture<Boolean> setHighscore(UUID uuid, String username, int score) {
        return CompletableFuture.supplyAsync(
                () -> {
                    ResultSet rs = null;
                    try {
                        rs =
                                database.executeQuery(
                                        "SELECT uuid FROM parkour_highscores WHERE uuid = ?",
                                        uuid.toString());
                        boolean exists = rs != null && rs.next();
                        closeQuietly(rs);
                        rs = null;

                        if (!exists) {
                            database.execute(
                                    "INSERT INTO parkour_highscores (uuid, username, highscore, created_at, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                                    uuid.toString(),
                                    username,
                                    score);
                        } else {
                            database.execute(
                                    "UPDATE parkour_highscores SET highscore = ?, username = ?, updated_at = CURRENT_TIMESTAMP WHERE uuid = ?",
                                    score,
                                    username,
                                    uuid.toString());
                        }

                        highscoreCache.put(uuid, score);
                        refreshLeaderboardCacheAsync();
                        return true;
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Failed to set highscore for " + uuid, e);
                        return false;
                    } finally {
                        closeQuietly(rs);
                    }
                });
    }

    // Game history
    public CompletableFuture<Void> saveGameHistory(UUID uuid, int score, long durationMs) {
        return CompletableFuture.runAsync(
                () -> {
                    try {
                        database.execute(
                                "INSERT INTO parkour_games (uuid, score, duration_ms) VALUES (?, ?, ?)",
                                uuid.toString(),
                                score,
                                durationMs);
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Failed to save game history", e);
                    }
                });
    }

    public record LeaderboardEntry(String username, int score) {}
}
