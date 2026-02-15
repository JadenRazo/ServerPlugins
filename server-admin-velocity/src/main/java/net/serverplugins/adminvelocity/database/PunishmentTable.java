package net.serverplugins.adminvelocity.database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serverplugins.adminvelocity.punishment.VelocityPunishment;
import net.serverplugins.adminvelocity.punishment.VelocityPunishmentType;
import org.slf4j.Logger;

/**
 * Database access layer for punishment records.
 *
 * <p>Provides async methods for querying and modifying punishment data in the server_punishments
 * table.
 */
public class PunishmentTable {

    private final AdminDatabase database;
    private final Logger logger;

    public PunishmentTable(AdminDatabase database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    /**
     * Gets the active ban for a player.
     *
     * @param playerUuid the player's UUID
     * @return a CompletableFuture containing the active ban, or null if none exists
     */
    public CompletableFuture<VelocityPunishment> getActiveBan(UUID playerUuid) {
        return database.executeAsync(
                conn -> {
                    String sql =
                            "SELECT * FROM server_punishments WHERE target_uuid = ? AND type = ? "
                                    + "AND active = 1 AND (expires_at IS NULL OR expires_at > ?) "
                                    + "ORDER BY issued_at DESC LIMIT 1";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, playerUuid.toString());
                        stmt.setString(2, VelocityPunishmentType.BAN.name());
                        stmt.setLong(3, System.currentTimeMillis());

                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                return mapResultSet(rs);
                            }
                        }
                    }
                    return null;
                });
    }

    /**
     * Gets the active mute for a player.
     *
     * @param playerUuid the player's UUID
     * @return a CompletableFuture containing the active mute, or null if none exists
     */
    public CompletableFuture<VelocityPunishment> getActiveMute(UUID playerUuid) {
        return database.executeAsync(
                conn -> {
                    String sql =
                            "SELECT * FROM server_punishments WHERE target_uuid = ? AND type = ? "
                                    + "AND active = 1 AND (expires_at IS NULL OR expires_at > ?) "
                                    + "ORDER BY issued_at DESC LIMIT 1";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, playerUuid.toString());
                        stmt.setString(2, VelocityPunishmentType.MUTE.name());
                        stmt.setLong(3, System.currentTimeMillis());

                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                return mapResultSet(rs);
                            }
                        }
                    }
                    return null;
                });
    }

    /**
     * Gets all active punishments for a player.
     *
     * @param playerUuid the player's UUID
     * @return a CompletableFuture containing the list of active punishments
     */
    public CompletableFuture<List<VelocityPunishment>> getActivePunishments(UUID playerUuid) {
        return database.executeAsync(
                conn -> {
                    List<VelocityPunishment> punishments = new ArrayList<>();
                    String sql =
                            "SELECT * FROM server_punishments WHERE target_uuid = ? AND active = 1 "
                                    + "AND (expires_at IS NULL OR expires_at > ?) ORDER BY issued_at DESC";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, playerUuid.toString());
                        stmt.setLong(2, System.currentTimeMillis());

                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                punishments.add(mapResultSet(rs));
                            }
                        }
                    }
                    return punishments;
                });
    }

    /**
     * Gets punishment history for a player.
     *
     * @param playerUuid the player's UUID
     * @param limit maximum number of records to return
     * @return a CompletableFuture containing the list of punishments
     */
    public CompletableFuture<List<VelocityPunishment>> getPunishmentHistory(
            UUID playerUuid, int limit) {
        return database.executeAsync(
                conn -> {
                    List<VelocityPunishment> punishments = new ArrayList<>();
                    String sql =
                            "SELECT * FROM server_punishments WHERE target_uuid = ? "
                                    + "ORDER BY issued_at DESC LIMIT ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, playerUuid.toString());
                        stmt.setInt(2, limit);

                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                punishments.add(mapResultSet(rs));
                            }
                        }
                    }
                    return punishments;
                });
    }

    /**
     * Creates a new punishment record.
     *
     * @param punishment the punishment to create
     * @return a CompletableFuture containing the generated punishment ID
     */
    public CompletableFuture<Integer> createPunishment(VelocityPunishment punishment) {
        return database.executeAsync(
                conn -> {
                    String sql =
                            "INSERT INTO server_punishments "
                                    + "(target_uuid, target_name, staff_name, type, reason, "
                                    + "issued_at, expires_at, active, source_server) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement stmt =
                            conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                        stmt.setString(1, punishment.getTargetUuid().toString());
                        stmt.setString(2, punishment.getTargetName());
                        stmt.setString(3, punishment.getStaffName());
                        stmt.setString(4, punishment.getType().name());
                        stmt.setString(5, punishment.getReason());
                        stmt.setLong(6, punishment.getCreatedAt());

                        if (punishment.getExpiresAt() != null) {
                            stmt.setLong(7, punishment.getExpiresAt());
                        } else {
                            stmt.setNull(7, java.sql.Types.BIGINT);
                        }

                        stmt.setBoolean(8, punishment.isActive());
                        stmt.setString(9, punishment.getSourceServer());

                        stmt.executeUpdate();

                        try (ResultSet rs = stmt.getGeneratedKeys()) {
                            if (rs.next()) {
                                return rs.getInt(1);
                            }
                        }
                    }
                    return -1;
                });
    }

    /**
     * Pardons (deactivates) a punishment.
     *
     * @param targetUuid the target player's UUID
     * @param type the punishment type to pardon
     * @param staffName the name of the staff member pardoning
     * @param reason the pardon reason
     * @return a CompletableFuture with true if a punishment was pardoned, false otherwise
     */
    public CompletableFuture<Boolean> pardonPunishment(
            UUID targetUuid, String type, String staffName, String reason) {
        return database.executeAsync(
                conn -> {
                    String sql =
                            "UPDATE server_punishments SET active = 0, pardoned_by_name = ?, "
                                    + "pardon_reason = ?, pardoned_at = ? "
                                    + "WHERE target_uuid = ? AND type = ? AND active = 1";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, staffName);
                        stmt.setString(2, reason);
                        stmt.setLong(3, System.currentTimeMillis());
                        stmt.setString(4, targetUuid.toString());
                        stmt.setString(5, type);

                        int updated = stmt.executeUpdate();
                        if (updated > 0) {
                            logger.info(
                                    "Pardoned {} punishment for {} by {}",
                                    type,
                                    targetUuid,
                                    staffName);
                            return true;
                        }
                        return false;
                    }
                });
    }

    /**
     * Maps a ResultSet row to a VelocityPunishment object.
     *
     * @param rs the ResultSet positioned at a valid row
     * @return the mapped VelocityPunishment
     * @throws SQLException if mapping fails
     */
    private VelocityPunishment mapResultSet(ResultSet rs) throws SQLException {
        VelocityPunishment punishment = new VelocityPunishment();
        punishment.setId(rs.getInt("id"));
        punishment.setTargetUuid(UUID.fromString(rs.getString("target_uuid")));
        punishment.setTargetName(rs.getString("target_name"));
        punishment.setStaffName(rs.getString("staff_name"));
        punishment.setType(VelocityPunishmentType.fromString(rs.getString("type")));
        punishment.setReason(rs.getString("reason"));
        punishment.setCreatedAt(rs.getLong("issued_at"));

        long expiresAt = rs.getLong("expires_at");
        punishment.setExpiresAt(rs.wasNull() ? null : expiresAt);

        punishment.setActive(rs.getBoolean("active"));
        punishment.setPermanent(punishment.getExpiresAt() == null);
        punishment.setPardonedBy(rs.getString("pardoned_by_name"));
        punishment.setPardonReason(rs.getString("pardon_reason"));
        punishment.setSourceServer(rs.getString("source_server"));

        return punishment;
    }
}
