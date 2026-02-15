package net.serverplugins.claim.repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.serverplugins.api.database.Database;
import net.serverplugins.claim.models.NotificationPriority;
import net.serverplugins.claim.models.NotificationType;
import net.serverplugins.claim.models.PlayerNotification;

/** Repository for player notifications. */
public class NotificationRepository {

    private final Database database;
    private static final Logger LOGGER = Logger.getLogger("ServerNotificationRepo");

    public NotificationRepository(Database database) {
        this.database = database;
    }

    /** Save a new notification. */
    public void saveNotification(PlayerNotification notification) {
        String sql =
                "INSERT INTO server_player_notifications "
                        + "(player_uuid, type, title, message, priority, related_claim_id, related_nation_id, "
                        + "action_button, expires_at, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, notification.getPlayerUuid().toString());
            stmt.setString(2, notification.getType().name());
            stmt.setString(3, notification.getTitle());
            stmt.setString(4, notification.getMessage());
            stmt.setString(5, notification.getPriority().name());

            if (notification.getRelatedClaimId() != null) {
                stmt.setInt(6, notification.getRelatedClaimId());
            } else {
                stmt.setNull(6, Types.INTEGER);
            }

            if (notification.getRelatedNationId() != null) {
                stmt.setInt(7, notification.getRelatedNationId());
            } else {
                stmt.setNull(7, Types.INTEGER);
            }

            stmt.setString(8, notification.getActionButton());

            if (notification.getExpiresAt() != null) {
                stmt.setTimestamp(9, Timestamp.from(notification.getExpiresAt()));
            } else {
                stmt.setNull(9, Types.TIMESTAMP);
            }

            stmt.setTimestamp(10, Timestamp.from(notification.getCreatedAt()));

            stmt.executeUpdate();

            // Get generated ID
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    notification.setId(rs.getLong(1));
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to save notification", e);
        }
    }

    /** Get all unread notifications for a player. */
    public List<PlayerNotification> getUnreadNotifications(UUID playerUuid) {
        String sql =
                "SELECT * FROM server_player_notifications "
                        + "WHERE player_uuid = ? AND read_at IS NULL "
                        + "AND (expires_at IS NULL OR expires_at > NOW()) "
                        + "ORDER BY priority DESC, created_at DESC";

        List<PlayerNotification> notifications = new ArrayList<>();

        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playerUuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    notifications.add(mapResultSet(rs));
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to get unread notifications for " + playerUuid, e);
        }

        return notifications;
    }

    /** Get all notifications for a player (paginated). */
    public List<PlayerNotification> getNotifications(UUID playerUuid, int offset, int limit) {
        String sql =
                "SELECT * FROM server_player_notifications "
                        + "WHERE player_uuid = ? "
                        + "AND (expires_at IS NULL OR expires_at > NOW()) "
                        + "ORDER BY read_at IS NULL DESC, priority DESC, created_at DESC "
                        + "LIMIT ? OFFSET ?";

        List<PlayerNotification> notifications = new ArrayList<>();

        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playerUuid.toString());
            stmt.setInt(2, limit);
            stmt.setInt(3, offset);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    notifications.add(mapResultSet(rs));
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to get notifications for " + playerUuid, e);
        }

        return notifications;
    }

    /** Get notifications by type for a player. */
    public List<PlayerNotification> getNotificationsByType(
            UUID playerUuid, NotificationType type, int limit) {
        String sql =
                "SELECT * FROM server_player_notifications "
                        + "WHERE player_uuid = ? AND type = ? "
                        + "AND (expires_at IS NULL OR expires_at > NOW()) "
                        + "ORDER BY read_at IS NULL DESC, created_at DESC "
                        + "LIMIT ?";

        List<PlayerNotification> notifications = new ArrayList<>();

        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, type.name());
            stmt.setInt(3, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    notifications.add(mapResultSet(rs));
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to get notifications by type for " + playerUuid, e);
        }

        return notifications;
    }

    /** Get count of unread notifications for a player. */
    public int getUnreadCount(UUID playerUuid) {
        String sql =
                "SELECT COUNT(*) FROM server_player_notifications "
                        + "WHERE player_uuid = ? AND read_at IS NULL "
                        + "AND (expires_at IS NULL OR expires_at > NOW())";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playerUuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to get unread count for " + playerUuid, e);
        }

        return 0;
    }

    /** Mark a notification as read. */
    public void markAsRead(long notificationId) {
        String sql = "UPDATE server_player_notifications SET read_at = NOW() WHERE id = ?";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, notificationId);
            stmt.executeUpdate();

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to mark notification as read: " + notificationId, e);
        }
    }

    /** Mark all notifications as read for a player. */
    public void markAllAsRead(UUID playerUuid) {
        String sql =
                "UPDATE server_player_notifications SET read_at = NOW() "
                        + "WHERE player_uuid = ? AND read_at IS NULL";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playerUuid.toString());
            stmt.executeUpdate();

        } catch (SQLException e) {
            LOGGER.log(
                    Level.SEVERE, "Failed to mark all notifications as read for " + playerUuid, e);
        }
    }

    /** Delete a notification. */
    public void deleteNotification(long notificationId) {
        String sql = "DELETE FROM server_player_notifications WHERE id = ?";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, notificationId);
            stmt.executeUpdate();

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to delete notification: " + notificationId, e);
        }
    }

    /** Delete all read notifications for a player. */
    public void deleteReadNotifications(UUID playerUuid) {
        String sql =
                "DELETE FROM server_player_notifications "
                        + "WHERE player_uuid = ? AND read_at IS NOT NULL";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playerUuid.toString());
            stmt.executeUpdate();

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to delete read notifications for " + playerUuid, e);
        }
    }

    /** Delete expired notifications (cleanup task). */
    public int deleteExpiredNotifications() {
        String sql =
                "DELETE FROM server_player_notifications "
                        + "WHERE expires_at IS NOT NULL AND expires_at < NOW()";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            return stmt.executeUpdate();

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to delete expired notifications", e);
            return 0;
        }
    }

    /** Delete old read notifications (older than 30 days). */
    public int deleteOldReadNotifications() {
        String sql =
                "DELETE FROM server_player_notifications "
                        + "WHERE read_at IS NOT NULL AND read_at < DATE_SUB(NOW(), INTERVAL 30 DAY)";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            return stmt.executeUpdate();

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to delete old read notifications", e);
            return 0;
        }
    }

    /** Map ResultSet to PlayerNotification object. */
    private PlayerNotification mapResultSet(ResultSet rs) throws SQLException {
        PlayerNotification notification = new PlayerNotification();

        notification.setId(rs.getLong("id"));
        notification.setPlayerUuid(UUID.fromString(rs.getString("player_uuid")));

        String typeStr = rs.getString("type");
        try {
            notification.setType(NotificationType.valueOf(typeStr));
        } catch (IllegalArgumentException e) {
            LOGGER.warning("Unknown notification type: " + typeStr);
            notification.setType(NotificationType.PERMISSION_CHANGED);
        }

        notification.setTitle(rs.getString("title"));
        notification.setMessage(rs.getString("message"));
        notification.setPriority(NotificationPriority.fromString(rs.getString("priority")));

        int claimId = rs.getInt("related_claim_id");
        if (!rs.wasNull()) {
            notification.setRelatedClaimId(claimId);
        }

        int nationId = rs.getInt("related_nation_id");
        if (!rs.wasNull()) {
            notification.setRelatedNationId(nationId);
        }

        notification.setActionButton(rs.getString("action_button"));

        Timestamp expiresAt = rs.getTimestamp("expires_at");
        if (expiresAt != null) {
            notification.setExpiresAt(expiresAt.toInstant());
        }

        Timestamp readAt = rs.getTimestamp("read_at");
        if (readAt != null) {
            notification.setReadAt(readAt.toInstant());
        }

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            notification.setCreatedAt(createdAt.toInstant());
        }

        return notification;
    }
}
