package net.serverplugins.admin.punishment;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import net.serverplugins.api.database.Database;

public class PunishmentRepository {

    private final Database database;
    private final Logger logger;

    public PunishmentRepository(Database database, Logger logger) {
        this.database = database;
        this.logger = logger;
        initTables();
    }

    private void initTables() {
        try {
            // Create punishments table
            database.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS server_punishments ("
                            + "id INT AUTO_INCREMENT PRIMARY KEY, "
                            + "target_uuid VARCHAR(36) NOT NULL, "
                            + "target_name VARCHAR(16), "
                            + "staff_uuid VARCHAR(36), "
                            + "staff_name VARCHAR(16), "
                            + "type VARCHAR(20) NOT NULL, "
                            + "category VARCHAR(30), "
                            + "reason_id VARCHAR(50), "
                            + "offense_number INT, "
                            + "reason VARCHAR(255), "
                            + "duration_ms BIGINT, "
                            + "issued_at BIGINT NOT NULL, "
                            + "expires_at BIGINT, "
                            + "active BOOLEAN DEFAULT TRUE, "
                            + "pardoned_by_uuid VARCHAR(36), "
                            + "pardoned_by_name VARCHAR(16), "
                            + "pardoned_at BIGINT, "
                            + "pardon_reason VARCHAR(255))");

            // Create category offenses table
            database.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS server_player_offenses ("
                            + "id INT AUTO_INCREMENT PRIMARY KEY, "
                            + "player_uuid VARCHAR(36) NOT NULL, "
                            + "category VARCHAR(30) NOT NULL, "
                            + "offense_count INT DEFAULT 0, "
                            + "last_offense_at BIGINT, "
                            + "CONSTRAINT unique_player_category UNIQUE (player_uuid, category))");

            // Create reason offenses table
            database.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS server_player_reason_offenses ("
                            + "id INT AUTO_INCREMENT PRIMARY KEY, "
                            + "player_uuid VARCHAR(36) NOT NULL, "
                            + "reason_id VARCHAR(50) NOT NULL, "
                            + "offense_count INT DEFAULT 0, "
                            + "last_offense_at BIGINT, "
                            + "CONSTRAINT unique_player_reason UNIQUE (player_uuid, reason_id))");

            // Create reset log table
            database.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS server_reset_log ("
                            + "id INT AUTO_INCREMENT PRIMARY KEY, "
                            + "target_uuid VARCHAR(36) NOT NULL, "
                            + "target_name VARCHAR(16), "
                            + "staff_uuid VARCHAR(36), "
                            + "staff_name VARCHAR(16), "
                            + "reset_type VARCHAR(30) NOT NULL, "
                            + "details TEXT, "
                            + "reset_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            logger.info("Punishment tables initialized.");
        } catch (SQLException e) {
            logger.warning("Failed to initialize punishment tables: " + e.getMessage());
        }
    }

    public int savePunishment(Punishment punishment) {
        try {
            database.executeUpdate(
                    "INSERT INTO server_punishments (target_uuid, target_name, staff_uuid, staff_name, "
                            + "type, category, reason_id, offense_number, reason, duration_ms, issued_at, expires_at, active) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    punishment.getTargetUuid().toString(),
                    punishment.getTargetName(),
                    punishment.getStaffUuid() != null ? punishment.getStaffUuid().toString() : null,
                    punishment.getStaffName(),
                    punishment.getType().name(),
                    punishment.getCategory(),
                    punishment.getReasonId(),
                    punishment.getOffenseNumber(),
                    punishment.getReason(),
                    punishment.getDurationMs(),
                    punishment.getIssuedAt(),
                    punishment.getExpiresAt(),
                    punishment.isActive());

            // H2 uses IDENTITY(), MySQL uses LAST_INSERT_ID()
            ResultSet rs = database.executeQuery("SELECT IDENTITY() as id");
            if (rs.next()) {
                int id = rs.getInt("id");
                rs.close();
                return id;
            }
            rs.close();
        } catch (SQLException e) {
            logger.warning("Failed to save punishment: " + e.getMessage());
        }
        return -1;
    }

    public Punishment getPunishment(int id) {
        try {
            ResultSet rs =
                    database.executeQuery("SELECT * FROM server_punishments WHERE id = ?", id);
            if (rs.next()) {
                Punishment p = mapResultSet(rs);
                rs.close();
                return p;
            }
            rs.close();
        } catch (SQLException e) {
            logger.warning("Failed to get punishment: " + e.getMessage());
        }
        return null;
    }

    public List<Punishment> getActivePunishments(UUID targetUuid) {
        List<Punishment> punishments = new ArrayList<>();
        try {
            ResultSet rs =
                    database.executeQuery(
                            "SELECT * FROM server_punishments WHERE target_uuid = ? AND active = true "
                                    + "AND (expires_at IS NULL OR expires_at > ?) ORDER BY issued_at DESC",
                            targetUuid.toString(),
                            System.currentTimeMillis());
            while (rs.next()) {
                punishments.add(mapResultSet(rs));
            }
            rs.close();
        } catch (SQLException e) {
            logger.warning("Failed to get active punishments: " + e.getMessage());
        }
        return punishments;
    }

    public Punishment getActivePunishment(UUID targetUuid, PunishmentType type) {
        try {
            ResultSet rs =
                    database.executeQuery(
                            "SELECT * FROM server_punishments WHERE target_uuid = ? AND type = ? AND active = true "
                                    + "AND (expires_at IS NULL OR expires_at > ?) ORDER BY issued_at DESC LIMIT 1",
                            targetUuid.toString(),
                            type.name(),
                            System.currentTimeMillis());
            if (rs.next()) {
                Punishment p = mapResultSet(rs);
                rs.close();
                return p;
            }
            rs.close();
        } catch (SQLException e) {
            logger.warning("Failed to get active punishment: " + e.getMessage());
        }
        return null;
    }

    public List<Punishment> getPunishmentHistory(UUID targetUuid, int limit) {
        List<Punishment> punishments = new ArrayList<>();
        try {
            ResultSet rs =
                    database.executeQuery(
                            "SELECT * FROM server_punishments WHERE target_uuid = ? ORDER BY issued_at DESC LIMIT ?",
                            targetUuid.toString(),
                            limit);
            while (rs.next()) {
                punishments.add(mapResultSet(rs));
            }
            rs.close();
        } catch (SQLException e) {
            logger.warning("Failed to get punishment history: " + e.getMessage());
        }
        return punishments;
    }

    public int getPunishmentCount(UUID targetUuid) {
        try {
            ResultSet rs =
                    database.executeQuery(
                            "SELECT COUNT(*) as count FROM server_punishments WHERE target_uuid = ?",
                            targetUuid.toString());
            if (rs.next()) {
                int count = rs.getInt("count");
                rs.close();
                return count;
            }
            rs.close();
        } catch (SQLException e) {
            logger.warning("Failed to get punishment count: " + e.getMessage());
        }
        return 0;
    }

    public void pardonPunishment(int id, UUID pardonerUuid, String pardonerName, String reason) {
        try {
            database.executeUpdate(
                    "UPDATE server_punishments SET active = false, pardoned_by_uuid = ?, "
                            + "pardoned_by_name = ?, pardoned_at = ?, pardon_reason = ? WHERE id = ?",
                    pardonerUuid != null ? pardonerUuid.toString() : null,
                    pardonerName,
                    System.currentTimeMillis(),
                    reason,
                    id);
        } catch (SQLException e) {
            logger.warning("Failed to pardon punishment: " + e.getMessage());
        }
    }

    public void expireOldPunishments() {
        try {
            database.executeUpdate(
                    "UPDATE server_punishments SET active = false WHERE active = true "
                            + "AND expires_at IS NOT NULL AND expires_at <= ?",
                    System.currentTimeMillis());
        } catch (SQLException e) {
            logger.warning("Failed to expire old punishments: " + e.getMessage());
        }
    }

    public int getOffenseCount(UUID playerUuid, String category) {
        try {
            ResultSet rs =
                    database.executeQuery(
                            "SELECT offense_count FROM server_player_offenses WHERE player_uuid = ? AND category = ?",
                            playerUuid.toString(),
                            category);
            if (rs.next()) {
                int count = rs.getInt("offense_count");
                rs.close();
                return count;
            }
            rs.close();
        } catch (SQLException e) {
            logger.warning("Failed to get offense count: " + e.getMessage());
        }
        return 0;
    }

    public void incrementOffenseCount(UUID playerUuid, String category) {
        try {
            // First try to update existing record
            int updated =
                    database.executeUpdate(
                            "UPDATE server_player_offenses SET offense_count = offense_count + 1, last_offense_at = ? "
                                    + "WHERE player_uuid = ? AND category = ?",
                            System.currentTimeMillis(),
                            playerUuid.toString(),
                            category);
            // If no record exists, insert new one
            if (updated == 0) {
                database.executeUpdate(
                        "INSERT INTO server_player_offenses (player_uuid, category, offense_count, last_offense_at) "
                                + "VALUES (?, ?, 1, ?)",
                        playerUuid.toString(),
                        category,
                        System.currentTimeMillis());
            }
        } catch (SQLException e) {
            logger.warning("Failed to increment offense count: " + e.getMessage());
        }
    }

    public void resetOffenseCount(UUID playerUuid, String category) {
        try {
            database.executeUpdate(
                    "DELETE FROM server_player_offenses WHERE player_uuid = ? AND category = ?",
                    playerUuid.toString(),
                    category);
        } catch (SQLException e) {
            logger.warning("Failed to reset offense count: " + e.getMessage());
        }
    }

    public void resetAllOffenses(UUID playerUuid) {
        try {
            database.executeUpdate(
                    "DELETE FROM server_player_offenses WHERE player_uuid = ?",
                    playerUuid.toString());
        } catch (SQLException e) {
            logger.warning("Failed to reset all offenses: " + e.getMessage());
        }
    }

    // Reason-based offense tracking methods

    public int getReasonOffenseCount(UUID playerUuid, String reasonId) {
        try {
            ResultSet rs =
                    database.executeQuery(
                            "SELECT offense_count FROM server_player_reason_offenses WHERE player_uuid = ? AND reason_id = ?",
                            playerUuid.toString(),
                            reasonId);
            if (rs.next()) {
                int count = rs.getInt("offense_count");
                rs.close();
                return count;
            }
            rs.close();
        } catch (SQLException e) {
            logger.warning("Failed to get reason offense count: " + e.getMessage());
        }
        return 0;
    }

    public void incrementReasonOffenseCount(UUID playerUuid, String reasonId) {
        try {
            // First try to update existing record
            int updated =
                    database.executeUpdate(
                            "UPDATE server_player_reason_offenses SET offense_count = offense_count + 1, last_offense_at = ? "
                                    + "WHERE player_uuid = ? AND reason_id = ?",
                            System.currentTimeMillis(),
                            playerUuid.toString(),
                            reasonId);
            // If no record exists, insert new one
            if (updated == 0) {
                database.executeUpdate(
                        "INSERT INTO server_player_reason_offenses (player_uuid, reason_id, offense_count, last_offense_at) "
                                + "VALUES (?, ?, 1, ?)",
                        playerUuid.toString(),
                        reasonId,
                        System.currentTimeMillis());
            }
        } catch (SQLException e) {
            logger.warning("Failed to increment reason offense count: " + e.getMessage());
        }
    }

    public void resetReasonOffenseCount(UUID playerUuid, String reasonId) {
        try {
            database.executeUpdate(
                    "DELETE FROM server_player_reason_offenses WHERE player_uuid = ? AND reason_id = ?",
                    playerUuid.toString(),
                    reasonId);
        } catch (SQLException e) {
            logger.warning("Failed to reset reason offense count: " + e.getMessage());
        }
    }

    public void resetAllReasonOffenses(UUID playerUuid) {
        try {
            database.executeUpdate(
                    "DELETE FROM server_player_reason_offenses WHERE player_uuid = ?",
                    playerUuid.toString());
        } catch (SQLException e) {
            logger.warning("Failed to reset all reason offenses: " + e.getMessage());
        }
    }

    public void logReset(
            UUID targetUuid,
            String targetName,
            UUID staffUuid,
            String staffName,
            String resetType,
            String details) {
        try {
            database.executeUpdate(
                    "INSERT INTO server_reset_log (target_uuid, target_name, staff_uuid, staff_name, reset_type, details) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    targetUuid.toString(),
                    targetName,
                    staffUuid != null ? staffUuid.toString() : null,
                    staffName,
                    resetType,
                    details);
        } catch (SQLException e) {
            logger.warning("Failed to log reset: " + e.getMessage());
        }
    }

    private Punishment mapResultSet(ResultSet rs) throws SQLException {
        Punishment p = new Punishment();
        p.setId(rs.getInt("id"));
        p.setTargetUuid(UUID.fromString(rs.getString("target_uuid")));
        p.setTargetName(rs.getString("target_name"));

        String staffUuidStr = rs.getString("staff_uuid");
        p.setStaffUuid(staffUuidStr != null ? UUID.fromString(staffUuidStr) : null);
        p.setStaffName(rs.getString("staff_name"));

        p.setType(PunishmentType.fromString(rs.getString("type")));
        p.setCategory(rs.getString("category"));
        p.setReasonId(rs.getString("reason_id"));

        int offenseNum = rs.getInt("offense_number");
        p.setOffenseNumber(rs.wasNull() ? null : offenseNum);

        p.setReason(rs.getString("reason"));

        long durationMs = rs.getLong("duration_ms");
        p.setDurationMs(rs.wasNull() ? null : durationMs);

        p.setIssuedAt(rs.getLong("issued_at"));

        long expiresAt = rs.getLong("expires_at");
        p.setExpiresAt(rs.wasNull() ? null : expiresAt);

        p.setActive(rs.getBoolean("active"));

        String pardonedByUuidStr = rs.getString("pardoned_by_uuid");
        p.setPardonedByUuid(pardonedByUuidStr != null ? UUID.fromString(pardonedByUuidStr) : null);
        p.setPardonedByName(rs.getString("pardoned_by_name"));

        long pardonedAt = rs.getLong("pardoned_at");
        p.setPardonedAt(rs.wasNull() ? null : pardonedAt);

        p.setPardonReason(rs.getString("pardon_reason"));

        return p;
    }
}
