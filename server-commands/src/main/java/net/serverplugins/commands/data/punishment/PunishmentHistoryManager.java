package net.serverplugins.commands.data.punishment;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import net.serverplugins.api.database.impl.MariaDBDatabase;
import net.serverplugins.commands.ServerCommands;
import org.bukkit.Bukkit;

/** Manages punishment history records in the database. */
public class PunishmentHistoryManager {

    private static final String CREATE_TABLE_SQL =
            """
            CREATE TABLE IF NOT EXISTS punishment_history (
                id INT AUTO_INCREMENT PRIMARY KEY,
                target_uuid VARCHAR(36) NOT NULL,
                target_name VARCHAR(16) NOT NULL,
                staff_uuid VARCHAR(36),
                staff_name VARCHAR(16) NOT NULL,
                punishment_type VARCHAR(20) NOT NULL,
                reason VARCHAR(255) NOT NULL DEFAULT 'No reason provided',
                duration_ms BIGINT,
                issued_at BIGINT NOT NULL,
                expires_at BIGINT,
                lifted_at BIGINT,
                lifted_by_uuid VARCHAR(36),
                lifted_by_name VARCHAR(16),
                is_active BOOLEAN DEFAULT TRUE,
                server_id VARCHAR(64) DEFAULT 'main',
                INDEX idx_target_uuid (target_uuid),
                INDEX idx_staff_uuid (staff_uuid),
                INDEX idx_punishment_type (punishment_type),
                INDEX idx_issued_at (issued_at),
                INDEX idx_is_active (is_active)
            )
            """;

    private final ServerCommands plugin;
    private MariaDBDatabase database;
    private boolean initialized = false;
    private String serverId;

    public PunishmentHistoryManager(ServerCommands plugin) {
        this.plugin = plugin;
    }

    /** Initialize the database connection and create tables */
    public void initialize() {
        // Get config values
        String host = plugin.getConfig().getString("database.host", "");
        int port = plugin.getConfig().getInt("database.port", 3306);
        String dbName = plugin.getConfig().getString("database.database", "");
        String username = plugin.getConfig().getString("database.username", "");
        String password = plugin.getConfig().getString("database.password", "");
        int poolSize = plugin.getConfig().getInt("database.pool-size", 10);
        long timeout = plugin.getConfig().getLong("database.connection-timeout", 30000);
        serverId = plugin.getConfig().getString("punishment-history.server-id", "main");

        // Check if database is configured
        if (host.isEmpty() || dbName.isEmpty()) {
            plugin.getLogger()
                    .warning(
                            "Database not configured for punishment history. Set database.host and database.database in config.yml");
            return;
        }

        try {
            database =
                    new MariaDBDatabase(host, port, dbName, username, password, poolSize, timeout);
            database.connect();

            // Create tables
            database.executeUpdate(CREATE_TABLE_SQL);

            initialized = true;
            plugin.getLogger().info("Punishment history database initialized successfully");

            // Schedule cleanup task for expired punishments
            scheduleExpiryCheck();

        } catch (SQLException e) {
            plugin.getLogger()
                    .log(Level.SEVERE, "Failed to initialize punishment history database", e);
        }
    }

    /** Check if the manager is initialized and ready */
    public boolean isInitialized() {
        return initialized && database != null && database.isConnected();
    }

    /** Schedule periodic check for expired punishments */
    private void scheduleExpiryCheck() {
        // Run every 5 minutes
        Bukkit.getScheduler()
                .runTaskTimerAsynchronously(plugin, this::updateExpiredPunishments, 6000L, 6000L);
    }

    /** Mark expired punishments as inactive */
    public void updateExpiredPunishments() {
        if (!isInitialized()) return;

        String sql =
                "UPDATE punishment_history SET is_active = FALSE WHERE is_active = TRUE AND expires_at IS NOT NULL AND expires_at < ?";
        try {
            int updated = database.executeUpdate(sql, System.currentTimeMillis());
            if (updated > 0) {
                plugin.getLogger().info("Marked " + updated + " expired punishments as inactive");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to update expired punishments", e);
        }
    }

    /** Log a new punishment (async) */
    public CompletableFuture<Integer> logPunishment(PunishmentRecord record) {
        if (!isInitialized()) {
            return CompletableFuture.completedFuture(-1);
        }

        return CompletableFuture.supplyAsync(
                () -> {
                    String sql =
                            """
                    INSERT INTO punishment_history
                    (target_uuid, target_name, staff_uuid, staff_name, punishment_type, reason, duration_ms, issued_at, expires_at, is_active, server_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;

                    try {
                        int id =
                                database.executeUpdateWithGeneratedKey(
                                        sql,
                                        record.getTargetUuid().toString(),
                                        record.getTargetName(),
                                        record.getStaffUuid() != null
                                                ? record.getStaffUuid().toString()
                                                : null,
                                        record.getStaffName(),
                                        record.getType().name(),
                                        record.getReason(),
                                        record.getDurationMs(),
                                        record.getIssuedAt(),
                                        record.getExpiresAt(),
                                        record.isActive(),
                                        serverId);
                        record.setId(id);

                        // Publish to Discord via Redis
                        String duration =
                                record.getDurationMs() != null
                                        ? formatDuration(record.getDurationMs())
                                        : null;
                        plugin.publishModerationLog(
                                record.getTargetName(),
                                record.getTargetUuid().toString(),
                                record.getStaffName(),
                                record.getStaffUuid() != null
                                        ? record.getStaffUuid().toString()
                                        : null,
                                record.getType().name(),
                                record.getReason(),
                                duration);

                        return id;
                    } catch (SQLException e) {
                        plugin.getLogger().log(Level.WARNING, "Failed to log punishment", e);
                        return -1;
                    }
                });
    }

    /** Lift a punishment by ID */
    public CompletableFuture<Boolean> liftPunishment(
            int punishmentId, UUID liftedByUuid, String liftedByName) {
        if (!isInitialized()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(
                () -> {
                    String sql =
                            "UPDATE punishment_history SET is_active = FALSE, lifted_at = ?, lifted_by_uuid = ?, lifted_by_name = ? WHERE id = ?";
                    try {
                        int updated =
                                database.executeUpdate(
                                        sql,
                                        System.currentTimeMillis(),
                                        liftedByUuid != null ? liftedByUuid.toString() : null,
                                        liftedByName,
                                        punishmentId);
                        return updated > 0;
                    } catch (SQLException e) {
                        plugin.getLogger().log(Level.WARNING, "Failed to lift punishment", e);
                        return false;
                    }
                });
    }

    /** Get all punishment history for a player */
    public CompletableFuture<List<PunishmentRecord>> getPlayerHistory(UUID playerUuid) {
        return getPlayerHistory(playerUuid, null, 100, 0);
    }

    /** Get punishment history for a player filtered by type */
    public CompletableFuture<List<PunishmentRecord>> getPlayerHistory(
            UUID playerUuid, PunishmentType type) {
        return getPlayerHistory(playerUuid, type, 100, 0);
    }

    /** Get punishment history for a player with pagination */
    public CompletableFuture<List<PunishmentRecord>> getPlayerHistory(
            UUID playerUuid, PunishmentType type, int limit, int offset) {
        if (!isInitialized()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        return CompletableFuture.supplyAsync(
                () -> {
                    String sql;
                    Object[] params;

                    if (type != null) {
                        sql =
                                "SELECT * FROM punishment_history WHERE target_uuid = ? AND punishment_type = ? ORDER BY issued_at DESC LIMIT ? OFFSET ?";
                        params = new Object[] {playerUuid.toString(), type.name(), limit, offset};
                    } else {
                        sql =
                                "SELECT * FROM punishment_history WHERE target_uuid = ? ORDER BY issued_at DESC LIMIT ? OFFSET ?";
                        params = new Object[] {playerUuid.toString(), limit, offset};
                    }

                    List<PunishmentRecord> records = new ArrayList<>();
                    try {
                        database.executeQueryWithConsumer(
                                sql,
                                rs -> {
                                    try {
                                        while (rs.next()) {
                                            records.add(mapResultSet(rs));
                                        }
                                    } catch (SQLException e) {
                                        plugin.getLogger()
                                                .log(
                                                        Level.WARNING,
                                                        "Failed to map punishment record",
                                                        e);
                                    }
                                },
                                params);
                    } catch (SQLException e) {
                        plugin.getLogger().log(Level.WARNING, "Failed to get player history", e);
                    }
                    return records;
                });
    }

    /** Get all punishments issued by a staff member */
    public CompletableFuture<List<PunishmentRecord>> getStaffHistory(UUID staffUuid) {
        return getStaffHistory(staffUuid, 100, 0);
    }

    /** Get punishments issued by a staff member with pagination */
    public CompletableFuture<List<PunishmentRecord>> getStaffHistory(
            UUID staffUuid, int limit, int offset) {
        if (!isInitialized()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        return CompletableFuture.supplyAsync(
                () -> {
                    String sql =
                            "SELECT * FROM punishment_history WHERE staff_uuid = ? ORDER BY issued_at DESC LIMIT ? OFFSET ?";
                    List<PunishmentRecord> records = new ArrayList<>();
                    try {
                        database.executeQueryWithConsumer(
                                sql,
                                rs -> {
                                    try {
                                        while (rs.next()) {
                                            records.add(mapResultSet(rs));
                                        }
                                    } catch (SQLException e) {
                                        plugin.getLogger()
                                                .log(
                                                        Level.WARNING,
                                                        "Failed to map punishment record",
                                                        e);
                                    }
                                },
                                staffUuid.toString(),
                                limit,
                                offset);
                    } catch (SQLException e) {
                        plugin.getLogger().log(Level.WARNING, "Failed to get staff history", e);
                    }
                    return records;
                });
    }

    /** Get active punishments for a player */
    public CompletableFuture<List<PunishmentRecord>> getActivePunishments(UUID playerUuid) {
        if (!isInitialized()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        return CompletableFuture.supplyAsync(
                () -> {
                    String sql =
                            "SELECT * FROM punishment_history WHERE target_uuid = ? AND is_active = TRUE ORDER BY issued_at DESC";
                    List<PunishmentRecord> records = new ArrayList<>();
                    try {
                        database.executeQueryWithConsumer(
                                sql,
                                rs -> {
                                    try {
                                        while (rs.next()) {
                                            records.add(mapResultSet(rs));
                                        }
                                    } catch (SQLException e) {
                                        plugin.getLogger()
                                                .log(
                                                        Level.WARNING,
                                                        "Failed to map punishment record",
                                                        e);
                                    }
                                },
                                playerUuid.toString());
                    } catch (SQLException e) {
                        plugin.getLogger()
                                .log(Level.WARNING, "Failed to get active punishments", e);
                    }
                    return records;
                });
    }

    /** Get recent punishments across all players */
    public CompletableFuture<List<PunishmentRecord>> getRecentPunishments(int limit) {
        if (!isInitialized()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        return CompletableFuture.supplyAsync(
                () -> {
                    String sql = "SELECT * FROM punishment_history ORDER BY issued_at DESC LIMIT ?";
                    List<PunishmentRecord> records = new ArrayList<>();
                    try {
                        database.executeQueryWithConsumer(
                                sql,
                                rs -> {
                                    try {
                                        while (rs.next()) {
                                            records.add(mapResultSet(rs));
                                        }
                                    } catch (SQLException e) {
                                        plugin.getLogger()
                                                .log(
                                                        Level.WARNING,
                                                        "Failed to map punishment record",
                                                        e);
                                    }
                                },
                                limit);
                    } catch (SQLException e) {
                        plugin.getLogger()
                                .log(Level.WARNING, "Failed to get recent punishments", e);
                    }
                    return records;
                });
    }

    /** Count punishments for a player by type */
    public CompletableFuture<Integer> countPunishments(UUID playerUuid, PunishmentType type) {
        if (!isInitialized()) {
            return CompletableFuture.completedFuture(0);
        }

        return CompletableFuture.supplyAsync(
                () -> {
                    String sql =
                            "SELECT COUNT(*) FROM punishment_history WHERE target_uuid = ? AND punishment_type = ?";
                    try {
                        ResultSet rs =
                                database.executeQuery(sql, playerUuid.toString(), type.name());
                        if (rs.next()) {
                            int count = rs.getInt(1);
                            rs.close();
                            return count;
                        }
                    } catch (SQLException e) {
                        plugin.getLogger().log(Level.WARNING, "Failed to count punishments", e);
                    }
                    return 0;
                });
    }

    /** Get punishment statistics for a player */
    public CompletableFuture<Map<PunishmentType, Integer>> getPlayerStats(UUID playerUuid) {
        if (!isInitialized()) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        return CompletableFuture.supplyAsync(
                () -> {
                    String sql =
                            "SELECT punishment_type, COUNT(*) as count FROM punishment_history WHERE target_uuid = ? GROUP BY punishment_type";
                    Map<PunishmentType, Integer> stats = new EnumMap<>(PunishmentType.class);
                    try {
                        database.executeQueryWithConsumer(
                                sql,
                                rs -> {
                                    try {
                                        while (rs.next()) {
                                            try {
                                                PunishmentType type =
                                                        PunishmentType.valueOf(
                                                                rs.getString("punishment_type"));
                                                stats.put(type, rs.getInt("count"));
                                            } catch (IllegalArgumentException ignored) {
                                                // Unknown type, skip
                                            }
                                        }
                                    } catch (SQLException e) {
                                        plugin.getLogger()
                                                .log(
                                                        Level.WARNING,
                                                        "Failed to map player stats",
                                                        e);
                                    }
                                },
                                playerUuid.toString());
                    } catch (SQLException e) {
                        plugin.getLogger().log(Level.WARNING, "Failed to get player stats", e);
                    }
                    return stats;
                });
    }

    /** Get total count for a player */
    public CompletableFuture<Integer> getTotalPunishmentCount(UUID playerUuid) {
        if (!isInitialized()) {
            return CompletableFuture.completedFuture(0);
        }

        return CompletableFuture.supplyAsync(
                () -> {
                    String sql = "SELECT COUNT(*) FROM punishment_history WHERE target_uuid = ?";
                    try {
                        ResultSet rs = database.executeQuery(sql, playerUuid.toString());
                        if (rs.next()) {
                            int count = rs.getInt(1);
                            rs.close();
                            return count;
                        }
                    } catch (SQLException e) {
                        plugin.getLogger()
                                .log(Level.WARNING, "Failed to count total punishments", e);
                    }
                    return 0;
                });
    }

    /** Map a ResultSet row to a PunishmentRecord */
    private PunishmentRecord mapResultSet(ResultSet rs) throws SQLException {
        PunishmentRecord.Builder builder =
                PunishmentRecord.builder()
                        .id(rs.getInt("id"))
                        .target(
                                UUID.fromString(rs.getString("target_uuid")),
                                rs.getString("target_name"))
                        .type(PunishmentType.valueOf(rs.getString("punishment_type")))
                        .reason(rs.getString("reason"))
                        .issuedAt(rs.getLong("issued_at"))
                        .active(rs.getBoolean("is_active"))
                        .serverId(rs.getString("server_id"));

        // Staff (may be null for console)
        String staffUuidStr = rs.getString("staff_uuid");
        if (staffUuidStr != null) {
            builder.staff(UUID.fromString(staffUuidStr), rs.getString("staff_name"));
        } else {
            builder.staff(null, rs.getString("staff_name"));
        }

        // Optional fields
        long durationMs = rs.getLong("duration_ms");
        if (!rs.wasNull()) {
            builder.duration(durationMs);
        }

        long expiresAt = rs.getLong("expires_at");
        if (!rs.wasNull()) {
            builder.expiresAt(expiresAt);
        }

        long liftedAt = rs.getLong("lifted_at");
        if (!rs.wasNull()) {
            builder.liftedAt(liftedAt);
            String liftedByUuidStr = rs.getString("lifted_by_uuid");
            if (liftedByUuidStr != null) {
                builder.liftedBy(UUID.fromString(liftedByUuidStr), rs.getString("lifted_by_name"));
            } else {
                builder.liftedBy(null, rs.getString("lifted_by_name"));
            }
        }

        return builder.build();
    }

    /** Format duration in milliseconds to a human-readable string */
    private String formatDuration(long durationMs) {
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + "d " + (hours % 24) + "h";
        } else if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m";
        } else if (minutes > 0) {
            return minutes + "m " + (seconds % 60) + "s";
        } else {
            return seconds + "s";
        }
    }

    /** Shutdown the database connection */
    public void shutdown() {
        if (database != null) {
            database.disconnect();
        }
        initialized = false;
    }
}
