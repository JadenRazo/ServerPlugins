package net.serverplugins.claim.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import net.serverplugins.api.database.Database;
import org.bukkit.entity.Player;

/**
 * Repository for claim audit logs - tracks all important actions for security and accountability.
 */
public class AuditLogRepository {

    private final Database database;
    private static final Logger LOGGER = Logger.getLogger("ServerClaimAuditLog");

    /** Activity types for enhanced logging. */
    public enum ActivityType {
        CLAIM_ACCESS,
        CHUNK_PURCHASE,
        CHUNK_UNCLAIM,
        MEMBER_ADDED,
        GROUP_CREATED,
        GROUP_MODIFIED,
        BANK_DEPOSIT,
        BANK_WITHDRAW,
        WARP_TELEPORT
    }

    public AuditLogRepository(Database database) {
        this.database = database;
    }

    /**
     * Log a claim action to the audit log.
     *
     * @param claimId Claim ID (can be null for player-level actions)
     * @param playerUuid Player UUID performing the action
     * @param actionType Type of action (PERMISSION_CHANGE, TRANSFER_OWNERSHIP, etc.)
     * @param details Detailed description of the action
     * @param ipAddress Player's IP address (optional, for security auditing)
     */
    public void logAction(
            Integer claimId, UUID playerUuid, String actionType, String details, String ipAddress) {
        try {
            database.execute(
                    "INSERT INTO server_claim_audit_log (claim_id, player_uuid, action_type, details, ip_address) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    claimId,
                    playerUuid != null ? playerUuid.toString() : null,
                    actionType,
                    details,
                    ipAddress);

            // Also log to server console for immediate visibility
            String logMessage =
                    String.format(
                            "[AUDIT] %s - Claim: %s - Player: %s - %s",
                            actionType,
                            claimId != null ? claimId : "N/A",
                            playerUuid != null ? playerUuid : "SYSTEM",
                            details);
            LOGGER.info(logMessage);

        } catch (Exception e) {
            LOGGER.severe("Failed to write audit log: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Convenience method to log action from a Player object. */
    public void logAction(Integer claimId, Player player, String actionType, String details) {
        String ipAddress = null;
        if (player != null && player.getAddress() != null) {
            ipAddress = player.getAddress().getAddress().getHostAddress();
        }
        logAction(
                claimId,
                player != null ? player.getUniqueId() : null,
                actionType,
                details,
                ipAddress);
    }

    /** Log a permission change. */
    public void logPermissionChange(int claimId, Player player, String details) {
        logAction(claimId, player, "PERMISSION_CHANGE", details);
    }

    /** Log an admin command execution. */
    public void logAdminCommand(Integer claimId, Player admin, String command, String details) {
        logAction(claimId, admin, "ADMIN_COMMAND", command + " - " + details);
    }

    /** Log a claim transfer. */
    public void logTransfer(int claimId, Player oldOwner, UUID newOwnerUuid, String newOwnerName) {
        logAction(
                claimId,
                oldOwner,
                "TRANSFER_OWNERSHIP",
                "Transferred to " + newOwnerName + " (" + newOwnerUuid + ")");
    }

    /** Log a claim deletion. */
    public void logDeletion(int claimId, Player player, String reason) {
        logAction(claimId, player, "DELETE_CLAIM", reason);
    }

    /** Log member addition. */
    public void logMemberAdd(
            int claimId, Player adder, UUID memberUuid, String memberName, String group) {
        logAction(
                claimId,
                adder,
                "ADD_MEMBER",
                "Added " + memberName + " (" + memberUuid + ") to group " + group);
    }

    /** Log member removal. */
    public void logMemberRemove(int claimId, Player remover, UUID memberUuid, String memberName) {
        logAction(
                claimId,
                remover,
                "REMOVE_MEMBER",
                "Removed " + memberName + " (" + memberUuid + ")");
    }

    /** Log bank transaction. */
    public void logBankTransaction(
            int claimId,
            Player player,
            String transactionType,
            double amount,
            double balanceAfter) {
        logAction(
                claimId,
                player,
                "BANK_" + transactionType,
                String.format("Amount: $%.2f, Balance After: $%.2f", amount, balanceAfter));
    }

    /** Log settings change. */
    public void logSettingsChange(
            int claimId, Player player, String setting, String oldValue, String newValue) {
        logAction(claimId, player, "SETTINGS_CHANGE", setting + ": " + oldValue + " → " + newValue);
    }

    /** Log chunk operations. */
    public void logChunkAdd(int claimId, Player player, String world, int chunkX, int chunkZ) {
        logAction(
                claimId,
                player,
                "CHUNK_ADD",
                String.format("Added chunk at %s (%d, %d)", world, chunkX, chunkZ));
    }

    public void logChunkRemove(int claimId, Player player, String world, int chunkX, int chunkZ) {
        logAction(
                claimId,
                player,
                "CHUNK_REMOVE",
                String.format("Removed chunk at %s (%d, %d)", world, chunkX, chunkZ));
    }

    /**
     * Get recent audit logs for a claim.
     *
     * @param claimId Claim ID
     * @param limit Maximum number of logs to return
     * @return List of audit log entries
     */
    public List<AuditLogEntry> getLogsForClaim(int claimId, int limit) {
        return database.query(
                "SELECT * FROM server_claim_audit_log WHERE claim_id = ? ORDER BY timestamp DESC LIMIT ?",
                rs -> {
                    List<AuditLogEntry> logs = new ArrayList<>();
                    while (rs.next()) {
                        UUID playerUuid = null;
                        String uuidStr = rs.getString("player_uuid");
                        if (uuidStr != null) {
                            playerUuid = UUID.fromString(uuidStr);
                        }

                        logs.add(
                                new AuditLogEntry(
                                        rs.getInt("id"),
                                        rs.getInt("claim_id"),
                                        playerUuid,
                                        rs.getString("action_type"),
                                        rs.getString("details"),
                                        rs.getString("ip_address"),
                                        rs.getTimestamp("timestamp").toInstant()));
                    }
                    return logs;
                },
                claimId,
                limit);
    }

    /**
     * Get recent audit logs for a player.
     *
     * @param playerUuid Player UUID
     * @param limit Maximum number of logs to return
     * @return List of audit log entries
     */
    public List<AuditLogEntry> getLogsForPlayer(UUID playerUuid, int limit) {
        return database.query(
                "SELECT * FROM server_claim_audit_log WHERE player_uuid = ? ORDER BY timestamp DESC LIMIT ?",
                rs -> {
                    List<AuditLogEntry> logs = new ArrayList<>();
                    while (rs.next()) {
                        Integer claimId = rs.getInt("claim_id");
                        if (rs.wasNull()) {
                            claimId = null;
                        }

                        logs.add(
                                new AuditLogEntry(
                                        rs.getInt("id"),
                                        claimId,
                                        playerUuid,
                                        rs.getString("action_type"),
                                        rs.getString("details"),
                                        rs.getString("ip_address"),
                                        rs.getTimestamp("timestamp").toInstant()));
                    }
                    return logs;
                },
                playerUuid.toString(),
                limit);
    }

    /**
     * Get all audit logs within a time range (for admin review).
     *
     * @param startTime Start time
     * @param endTime End time
     * @param limit Maximum number of logs
     * @return List of audit log entries
     */
    public List<AuditLogEntry> getLogsByTimeRange(Instant startTime, Instant endTime, int limit) {
        return database.query(
                "SELECT * FROM server_claim_audit_log WHERE timestamp BETWEEN ? AND ? "
                        + "ORDER BY timestamp DESC LIMIT ?",
                rs -> {
                    List<AuditLogEntry> logs = new ArrayList<>();
                    while (rs.next()) {
                        UUID playerUuid = null;
                        String uuidStr = rs.getString("player_uuid");
                        if (uuidStr != null) {
                            playerUuid = UUID.fromString(uuidStr);
                        }

                        Integer claimId = rs.getInt("claim_id");
                        if (rs.wasNull()) {
                            claimId = null;
                        }

                        logs.add(
                                new AuditLogEntry(
                                        rs.getInt("id"),
                                        claimId,
                                        playerUuid,
                                        rs.getString("action_type"),
                                        rs.getString("details"),
                                        rs.getString("ip_address"),
                                        rs.getTimestamp("timestamp").toInstant()));
                    }
                    return logs;
                },
                Timestamp.from(startTime),
                Timestamp.from(endTime),
                limit);
    }

    /** Log an activity with activity_type field. */
    public void logActivity(
            Integer claimId,
            UUID playerUuid,
            ActivityType activityType,
            String details,
            Double amount) {
        try {
            database.execute(
                    "INSERT INTO server_claim_audit_log (claim_id, player_uuid, action_type, activity_type, details, amount) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    claimId,
                    playerUuid != null ? playerUuid.toString() : null,
                    null, // action_type is legacy, use NULL for new activity_type system
                    activityType.name(),
                    details,
                    amount);

            LOGGER.fine(
                    String.format(
                            "[ACTIVITY] %s - Claim: %s - Player: %s - %s",
                            activityType,
                            claimId != null ? claimId : "N/A",
                            playerUuid != null ? playerUuid : "SYSTEM",
                            details));

        } catch (Exception e) {
            LOGGER.severe("Failed to log activity: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Log an activity from a Player object. */
    public void logActivity(
            Integer claimId,
            Player player,
            ActivityType activityType,
            String details,
            Double amount) {
        logActivity(
                claimId,
                player != null ? player.getUniqueId() : null,
                activityType,
                details,
                amount);
    }

    /**
     * Get audit logs with filtering and pagination.
     *
     * @param claimId Claim ID to filter by (null for all)
     * @param activityType Activity type to filter by (null for all)
     * @param limit Maximum number of logs
     * @param offset Offset for pagination
     * @return List of audit log entries
     */
    public List<AuditLogEntry> getLogsFiltered(
            Integer claimId, ActivityType activityType, int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT * FROM server_claim_audit_log WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (claimId != null) {
            sql.append(" AND claim_id = ?");
            params.add(claimId);
        }

        if (activityType != null) {
            sql.append(" AND activity_type = ?");
            params.add(activityType.name());
        }

        sql.append(" ORDER BY timestamp DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return database.query(
                sql.toString(),
                rs -> {
                    List<AuditLogEntry> logs = new ArrayList<>();
                    while (rs.next()) {
                        UUID playerUuid = null;
                        String uuidStr = rs.getString("player_uuid");
                        if (uuidStr != null) {
                            playerUuid = UUID.fromString(uuidStr);
                        }

                        Integer logClaimId = rs.getInt("claim_id");
                        if (rs.wasNull()) {
                            logClaimId = null;
                        }

                        String actType = rs.getString("activity_type");
                        ActivityType activity = null;
                        if (actType != null) {
                            try {
                                activity = ActivityType.valueOf(actType);
                            } catch (IllegalArgumentException e) {
                                // Ignore invalid activity types
                            }
                        }

                        Double amount = rs.getDouble("amount");
                        if (rs.wasNull()) {
                            amount = null;
                        }

                        String oldValue = rs.getString("old_value");
                        String newValue = rs.getString("new_value");

                        logs.add(
                                new AuditLogEntry(
                                        rs.getInt("id"),
                                        logClaimId,
                                        playerUuid,
                                        rs.getString("action_type"),
                                        activity,
                                        rs.getString("details"),
                                        rs.getString("ip_address"),
                                        rs.getTimestamp("timestamp").toInstant(),
                                        amount,
                                        oldValue,
                                        newValue));
                    }
                    return logs;
                },
                params.toArray());
    }

    /** Get total count of logs for pagination. */
    public int getLogCount(Integer claimId, ActivityType activityType) {
        StringBuilder sql =
                new StringBuilder("SELECT COUNT(*) as count FROM server_claim_audit_log WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (claimId != null) {
            sql.append(" AND claim_id = ?");
            params.add(claimId);
        }

        if (activityType != null) {
            sql.append(" AND activity_type = ?");
            params.add(activityType.name());
        }

        Integer count =
                database.query(
                        sql.toString(), rs -> rs.next() ? rs.getInt("count") : 0, params.toArray());

        return count != null ? count : 0;
    }

    /** Data class for audit log entries (enhanced with activity_type). */
    public record AuditLogEntry(
            int id,
            Integer claimId,
            UUID playerUuid,
            String actionType,
            ActivityType activityType,
            String details,
            String ipAddress,
            Instant timestamp,
            Double amount,
            String oldValue,
            String newValue) {
        // Convenience constructor for backward compatibility
        public AuditLogEntry(
                int id,
                Integer claimId,
                UUID playerUuid,
                String actionType,
                String details,
                String ipAddress,
                Instant timestamp) {
            this(
                    id,
                    claimId,
                    playerUuid,
                    actionType,
                    null,
                    details,
                    ipAddress,
                    timestamp,
                    null,
                    null,
                    null);
        }
    }
}
