package net.serverplugins.keys.repository;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.serverplugins.api.database.Database;
import net.serverplugins.keys.models.KeyStats;
import net.serverplugins.keys.models.KeyTransaction;
import net.serverplugins.keys.models.KeyType;
import net.serverplugins.keys.models.UnclaimedKey;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Repository for key statistics and history with async database operations. All read operations
 * return CompletableFutures to avoid blocking the main thread. Write operations are fire-and-forget
 * async to maximize throughput.
 */
public class KeysRepository {

    private final Database database;
    private final Logger logger;
    private final Plugin plugin;
    private final Executor asyncExecutor;

    public KeysRepository(Database database, Logger logger, Plugin plugin) {
        this.database = database;
        this.logger = logger;
        this.plugin = plugin;
        // Use a cached thread pool for async operations
        this.asyncExecutor =
                runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
    }

    public void createTables(InputStream schemaStream) {
        if (schemaStream == null) {
            logger.warning("schema.sql not found, creating tables manually");
            createTablesManually();
            return;
        }

        try {
            String sql = new String(schemaStream.readAllBytes());
            String[] statements = sql.split(";");
            for (String statement : statements) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                    database.execute(trimmed);
                }
            }
            logger.info("Keys database tables created successfully");
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to read schema.sql", e);
            createTablesManually();
        }
    }

    private void createTablesManually() {
        database.execute(
                """
            CREATE TABLE IF NOT EXISTS server_key_stats (
                id INT AUTO_INCREMENT PRIMARY KEY,
                uuid VARCHAR(36) NOT NULL,
                username VARCHAR(16) NOT NULL,
                key_type VARCHAR(16) NOT NULL,
                key_name VARCHAR(32) NOT NULL,
                total_received INT DEFAULT 0,
                total_used INT DEFAULT 0,
                last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                UNIQUE KEY unique_player_key (uuid, key_type, key_name)
            )
        """);

        database.execute(
                """
            CREATE TABLE IF NOT EXISTS server_key_history (
                id INT AUTO_INCREMENT PRIMARY KEY,
                uuid VARCHAR(36) NOT NULL,
                username VARCHAR(16) NOT NULL,
                key_type VARCHAR(16) NOT NULL,
                key_name VARCHAR(32) NOT NULL,
                amount INT NOT NULL,
                action VARCHAR(16) NOT NULL,
                source VARCHAR(64) NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);

        try {
            database.execute(
                    "CREATE INDEX IF NOT EXISTS idx_key_stats_uuid ON server_key_stats (uuid)");
            database.execute(
                    "CREATE INDEX IF NOT EXISTS idx_key_history_uuid ON server_key_history (uuid)");
            database.execute(
                    "CREATE INDEX IF NOT EXISTS idx_key_history_created ON server_key_history (created_at DESC)");
        } catch (Exception ignored) {
            // Some databases don't support IF NOT EXISTS for indexes
        }
    }

    // ==================== Async Write Operations (Fire-and-forget) ====================

    /**
     * Asynchronously add received keys to player stats. This is fire-and-forget to avoid blocking
     * the main thread.
     */
    public void addReceivedAsync(
            UUID uuid, String username, KeyType keyType, String keyName, int amount) {
        CompletableFuture.runAsync(
                () -> {
                    try {
                        database.executeUpdate(
                                """
                    INSERT INTO server_key_stats (uuid, username, key_type, key_name, total_received, total_used)
                    VALUES (?, ?, ?, ?, ?, 0)
                    ON DUPLICATE KEY UPDATE
                        username = VALUES(username),
                        total_received = total_received + VALUES(total_received),
                        last_updated = CURRENT_TIMESTAMP
                    """,
                                uuid.toString(),
                                username,
                                keyType.name(),
                                keyName,
                                amount);
                    } catch (SQLException e) {
                        logger.log(Level.SEVERE, "Failed to update key stats", e);
                    }
                },
                asyncExecutor);
    }

    /** Asynchronously add used keys to player stats. */
    public void addUsedAsync(
            UUID uuid, String username, KeyType keyType, String keyName, int amount) {
        CompletableFuture.runAsync(
                () -> {
                    try {
                        database.executeUpdate(
                                """
                    INSERT INTO server_key_stats (uuid, username, key_type, key_name, total_received, total_used)
                    VALUES (?, ?, ?, ?, 0, ?)
                    ON DUPLICATE KEY UPDATE
                        username = VALUES(username),
                        total_used = total_used + VALUES(total_used),
                        last_updated = CURRENT_TIMESTAMP
                    """,
                                uuid.toString(),
                                username,
                                keyType.name(),
                                keyName,
                                amount);
                    } catch (SQLException e) {
                        logger.log(Level.SEVERE, "Failed to update key stats", e);
                    }
                },
                asyncExecutor);
    }

    /** Asynchronously record a key transaction. */
    public void recordTransactionAsync(
            UUID uuid,
            String username,
            KeyType keyType,
            String keyName,
            int amount,
            String action,
            String source) {
        CompletableFuture.runAsync(
                () -> {
                    try {
                        database.executeUpdate(
                                """
                    INSERT INTO server_key_history (uuid, username, key_type, key_name, amount, action, source)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                                uuid.toString(),
                                username,
                                keyType.name(),
                                keyName,
                                amount,
                                action,
                                source);
                    } catch (SQLException e) {
                        logger.log(Level.SEVERE, "Failed to record key transaction", e);
                    }
                },
                asyncExecutor);
    }

    // ==================== Async Read Operations ====================

    /** Asynchronously get player stats. */
    public CompletableFuture<List<KeyStats>> getPlayerStatsAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(
                () ->
                        database.query(
                                "SELECT * FROM server_key_stats WHERE uuid = ? ORDER BY key_type, key_name",
                                rs -> {
                                    List<KeyStats> stats = new ArrayList<>();
                                    while (rs.next()) {
                                        stats.add(
                                                new KeyStats(
                                                        UUID.fromString(rs.getString("uuid")),
                                                        rs.getString("username"),
                                                        KeyType.valueOf(rs.getString("key_type")),
                                                        rs.getString("key_name"),
                                                        rs.getInt("total_received"),
                                                        rs.getInt("total_used"),
                                                        rs.getTimestamp("last_updated")));
                                    }
                                    return stats;
                                },
                                uuid.toString()),
                asyncExecutor);
    }

    /** Asynchronously get total received count. */
    public CompletableFuture<Integer> getTotalReceivedAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(
                () ->
                        database.query(
                                "SELECT COALESCE(SUM(total_received), 0) as total FROM server_key_stats WHERE uuid = ?",
                                rs -> rs.next() ? rs.getInt("total") : 0,
                                uuid.toString()),
                asyncExecutor);
    }

    /** Asynchronously get total received by type. */
    public CompletableFuture<Integer> getTotalReceivedByTypeAsync(UUID uuid, KeyType keyType) {
        return CompletableFuture.supplyAsync(
                () ->
                        database.query(
                                "SELECT COALESCE(SUM(total_received), 0) as total FROM server_key_stats WHERE uuid = ? AND key_type = ?",
                                rs -> rs.next() ? rs.getInt("total") : 0,
                                uuid.toString(),
                                keyType.name()),
                asyncExecutor);
    }

    /** Asynchronously get received count for specific key. */
    public CompletableFuture<Integer> getReceivedForKeyAsync(
            UUID uuid, KeyType keyType, String keyName) {
        return CompletableFuture.supplyAsync(
                () ->
                        database.query(
                                "SELECT total_received FROM server_key_stats WHERE uuid = ? AND key_type = ? AND key_name = ?",
                                rs -> rs.next() ? rs.getInt("total_received") : 0,
                                uuid.toString(),
                                keyType.name(),
                                keyName),
                asyncExecutor);
    }

    /** Asynchronously get player history. */
    public CompletableFuture<List<KeyTransaction>> getPlayerHistoryAsync(UUID uuid, int limit) {
        return CompletableFuture.supplyAsync(
                () ->
                        database.query(
                                "SELECT * FROM server_key_history WHERE uuid = ? ORDER BY created_at DESC LIMIT ?",
                                rs -> {
                                    List<KeyTransaction> history = new ArrayList<>();
                                    while (rs.next()) {
                                        history.add(mapTransaction(rs));
                                    }
                                    return history;
                                },
                                uuid.toString(),
                                limit),
                asyncExecutor);
    }

    /** Asynchronously get recent history. */
    public CompletableFuture<List<KeyTransaction>> getRecentHistoryAsync(int limit) {
        return CompletableFuture.supplyAsync(
                () ->
                        database.query(
                                "SELECT * FROM server_key_history ORDER BY created_at DESC LIMIT ?",
                                rs -> {
                                    List<KeyTransaction> history = new ArrayList<>();
                                    while (rs.next()) {
                                        history.add(mapTransaction(rs));
                                    }
                                    return history;
                                },
                                limit),
                asyncExecutor);
    }

    // ==================== Synchronous reads for cache warming ====================

    /** Synchronously get player stats (for cache warming on async thread). */
    public List<KeyStats> getPlayerStatsSync(UUID uuid) {
        return database.query(
                "SELECT * FROM server_key_stats WHERE uuid = ? ORDER BY key_type, key_name",
                rs -> {
                    List<KeyStats> stats = new ArrayList<>();
                    while (rs.next()) {
                        stats.add(
                                new KeyStats(
                                        UUID.fromString(rs.getString("uuid")),
                                        rs.getString("username"),
                                        KeyType.valueOf(rs.getString("key_type")),
                                        rs.getString("key_name"),
                                        rs.getInt("total_received"),
                                        rs.getInt("total_used"),
                                        rs.getTimestamp("last_updated")));
                    }
                    return stats;
                },
                uuid.toString());
    }

    // ==================== Unclaimed Key Operations ====================

    /** Store an unclaimed key record asynchronously (fire-and-forget). */
    public void storeUnclaimedAsync(
            UUID uuid,
            String username,
            KeyType keyType,
            String keyName,
            int amount,
            String source) {
        CompletableFuture.runAsync(
                () -> {
                    try {
                        database.executeUpdate(
                                """
                    INSERT INTO server_key_unclaimed (uuid, username, key_type, key_name, amount, source)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                                uuid.toString(),
                                username,
                                keyType.name(),
                                keyName,
                                amount,
                                source);
                    } catch (SQLException e) {
                        logger.log(Level.SEVERE, "Failed to store unclaimed key", e);
                    }
                },
                asyncExecutor);
    }

    /** Get all unclaimed keys for a player. */
    public CompletableFuture<List<UnclaimedKey>> getUnclaimedAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(
                () ->
                        database.query(
                                "SELECT * FROM server_key_unclaimed WHERE uuid = ? AND claimed = FALSE ORDER BY created_at ASC",
                                rs -> {
                                    List<UnclaimedKey> keys = new ArrayList<>();
                                    while (rs.next()) {
                                        keys.add(mapUnclaimedKey(rs));
                                    }
                                    return keys;
                                },
                                uuid.toString()),
                asyncExecutor);
    }

    /** Mark a specific unclaimed key record as claimed. */
    public void markClaimedAsync(int id) {
        CompletableFuture.runAsync(
                () -> {
                    try {
                        database.executeUpdate(
                                "UPDATE server_key_unclaimed SET claimed = TRUE, claimed_at = CURRENT_TIMESTAMP WHERE id = ?",
                                id);
                    } catch (SQLException e) {
                        logger.log(Level.SEVERE, "Failed to mark key as claimed", e);
                    }
                },
                asyncExecutor);
    }

    /** Get the total count of unclaimed keys for a player. */
    public CompletableFuture<Integer> getUnclaimedCountAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(
                () ->
                        database.query(
                                "SELECT COALESCE(SUM(amount), 0) as total FROM server_key_unclaimed WHERE uuid = ? AND claimed = FALSE",
                                rs -> rs.next() ? rs.getInt("total") : 0,
                                uuid.toString()),
                asyncExecutor);
    }

    private UnclaimedKey mapUnclaimedKey(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new UnclaimedKey(
                rs.getInt("id"),
                UUID.fromString(rs.getString("uuid")),
                rs.getString("username"),
                KeyType.valueOf(rs.getString("key_type")),
                rs.getString("key_name"),
                rs.getInt("amount"),
                rs.getString("source"),
                rs.getTimestamp("created_at"));
    }

    private KeyTransaction mapTransaction(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new KeyTransaction(
                rs.getInt("id"),
                UUID.fromString(rs.getString("uuid")),
                rs.getString("username"),
                KeyType.valueOf(rs.getString("key_type")),
                rs.getString("key_name"),
                rs.getInt("amount"),
                rs.getString("action"),
                rs.getString("source"),
                rs.getTimestamp("created_at"));
    }
}
