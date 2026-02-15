package net.serverplugins.claim.managers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.NotificationPriority;
import net.serverplugins.claim.models.NotificationType;
import net.serverplugins.claim.models.PlayerNotification;
import net.serverplugins.claim.repository.NotificationRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/** Manages player notifications for claims, nations, upkeep, and other events. */
public class NotificationManager {

    private final ServerClaim plugin;
    private final NotificationRepository repository;
    private final ConcurrentHashMap<UUID, Integer> unreadCountCache = new ConcurrentHashMap<>();
    private BukkitTask cleanupTask;

    public NotificationManager(ServerClaim plugin, NotificationRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    /** Start scheduled cleanup task. */
    public void start() {
        // Run cleanup every 6 hours
        long ticksPerHour = 20L * 60 * 60;
        cleanupTask =
                plugin.getServer()
                        .getScheduler()
                        .runTaskTimerAsynchronously(
                                plugin,
                                this::cleanupExpiredNotifications,
                                ticksPerHour * 6,
                                ticksPerHour * 6);

        plugin.getLogger().info("Notification manager started - cleanup every 6 hours");
    }

    /** Stop scheduled tasks. */
    public void stop() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
    }

    /** Send a notification to a player. */
    public void notify(UUID playerUuid, NotificationType type, String title, String message) {
        notify(
                playerUuid,
                type,
                title,
                message,
                NotificationPriority.NORMAL,
                null,
                null,
                null,
                null);
    }

    /** Send a notification with priority. */
    public void notify(
            UUID playerUuid,
            NotificationType type,
            String title,
            String message,
            NotificationPriority priority) {
        notify(playerUuid, type, title, message, priority, null, null, null, null);
    }

    /** Send a notification with related claim. */
    public void notifyAboutClaim(
            UUID playerUuid,
            NotificationType type,
            String title,
            String message,
            int claimId,
            NotificationPriority priority) {
        notify(playerUuid, type, title, message, priority, claimId, null, null, null);
    }

    /** Send a notification with related nation. */
    public void notifyAboutNation(
            UUID playerUuid,
            NotificationType type,
            String title,
            String message,
            int nationId,
            NotificationPriority priority) {
        notify(playerUuid, type, title, message, priority, null, nationId, null, null);
    }

    /** Send a notification with action button. */
    public void notifyWithAction(
            UUID playerUuid,
            NotificationType type,
            String title,
            String message,
            String actionButton,
            NotificationPriority priority) {
        notify(playerUuid, type, title, message, priority, null, null, actionButton, null);
    }

    /** Send a temporary notification that expires after a duration. */
    public void notifyTemporary(
            UUID playerUuid,
            NotificationType type,
            String title,
            String message,
            Duration expireDuration,
            NotificationPriority priority) {
        Instant expiresAt = Instant.now().plus(expireDuration);
        notify(playerUuid, type, title, message, priority, null, null, null, expiresAt);
    }

    /** Core notification method with all parameters. */
    private void notify(
            UUID playerUuid,
            NotificationType type,
            String title,
            String message,
            NotificationPriority priority,
            Integer claimId,
            Integer nationId,
            String actionButton,
            Instant expiresAt) {

        // Run database operation asynchronously
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            PlayerNotification notification =
                                    new PlayerNotification(playerUuid, type, title, message);
                            notification.setPriority(priority);
                            notification.setRelatedClaimId(claimId);
                            notification.setRelatedNationId(nationId);
                            notification.setActionButton(actionButton);
                            notification.setExpiresAt(expiresAt);

                            repository.saveNotification(notification);

                            // Invalidate unread count cache
                            unreadCountCache.remove(playerUuid);

                            // If player is online, notify them
                            Player player = Bukkit.getPlayer(playerUuid);
                            if (player != null && player.isOnline()) {
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    TextUtil.send(
                                                            player,
                                                            "<gray>[<yellow>!<gray>] "
                                                                    + priority.getColor()
                                                                    + title);
                                                    TextUtil.send(player, "<white>" + message);
                                                    TextUtil.send(
                                                            player,
                                                            "<gray>Use <yellow>/claim notifications <gray>to view all notifications");
                                                });
                            }

                            plugin.getLogger()
                                    .info("Sent notification to " + playerUuid + ": " + title);
                        });
    }

    /** Get unread notification count for a player (cached). */
    public int getUnreadCount(UUID playerUuid) {
        return unreadCountCache.computeIfAbsent(
                playerUuid,
                uuid -> {
                    // Load from database if not in cache
                    return repository.getUnreadCount(uuid);
                });
    }

    /** Get all unread notifications for a player. */
    public List<PlayerNotification> getUnreadNotifications(UUID playerUuid) {
        return repository.getUnreadNotifications(playerUuid);
    }

    /** Get notifications for a player (paginated). */
    public List<PlayerNotification> getNotifications(UUID playerUuid, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return repository.getNotifications(playerUuid, offset, pageSize);
    }

    /** Get notifications by type for a player. */
    public List<PlayerNotification> getNotificationsByType(
            UUID playerUuid, NotificationType type, int limit) {
        return repository.getNotificationsByType(playerUuid, type, limit);
    }

    /** Mark a notification as read. */
    public void markAsRead(long notificationId, UUID playerUuid) {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            repository.markAsRead(notificationId);
                            // Invalidate cache
                            unreadCountCache.remove(playerUuid);
                        });
    }

    /** Mark all notifications as read for a player. */
    public void markAllAsRead(UUID playerUuid) {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            repository.markAllAsRead(playerUuid);
                            // Update cache to 0
                            unreadCountCache.put(playerUuid, 0);
                        });
    }

    /** Delete a notification. */
    public void deleteNotification(long notificationId, UUID playerUuid) {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            repository.deleteNotification(notificationId);
                            // Invalidate cache
                            unreadCountCache.remove(playerUuid);
                        });
    }

    /** Delete all read notifications for a player. */
    public void deleteReadNotifications(UUID playerUuid) {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            repository.deleteReadNotifications(playerUuid);
                        });
    }

    /** Invalidate unread count cache for a player. */
    public void invalidateCache(UUID playerUuid) {
        unreadCountCache.remove(playerUuid);
    }

    /** Cleanup expired and old read notifications. */
    private void cleanupExpiredNotifications() {
        plugin.getLogger().info("Starting notification cleanup...");

        int expiredDeleted = repository.deleteExpiredNotifications();
        int oldReadDeleted = repository.deleteOldReadNotifications();

        plugin.getLogger()
                .info(
                        "Notification cleanup complete: "
                                + expiredDeleted
                                + " expired, "
                                + oldReadDeleted
                                + " old read notifications deleted");

        // Clear unread count cache to force refresh
        unreadCountCache.clear();
    }

    /** Notify player about upkeep warning. */
    public void notifyUpkeepWarning(
            UUID playerUuid,
            int claimId,
            String claimName,
            String timeRemaining,
            double requiredAmount) {
        String title = "Upkeep Payment Due Soon";
        String message =
                "Claim '"
                        + claimName
                        + "' needs $"
                        + String.format("%.2f", requiredAmount)
                        + " for upkeep in "
                        + timeRemaining
                        + "!";

        notifyAboutClaim(
                playerUuid,
                NotificationType.UPKEEP_WARNING,
                title,
                message,
                claimId,
                NotificationPriority.HIGH);
    }

    /** Notify player about transfer received. */
    public void notifyTransferReceived(UUID playerUuid, String claimName, UUID fromPlayer) {
        String title = "Claim Transfer Received";
        String message =
                "You now own the claim '"
                        + claimName
                        + "' transferred from "
                        + Bukkit.getOfflinePlayer(fromPlayer).getName()
                        + "!";

        notify(
                playerUuid,
                NotificationType.TRANSFER_RECEIVED,
                title,
                message,
                NotificationPriority.NORMAL);
    }

    /** Notify player about being removed from a claim. */
    public void notifyMemberRemoved(UUID playerUuid, String claimName) {
        String title = "Removed from Claim";
        String message = "You have been removed from the claim '" + claimName + "'.";

        notify(
                playerUuid,
                NotificationType.MEMBER_REMOVED,
                title,
                message,
                NotificationPriority.NORMAL);
    }

    /** Notify player about permission changes. */
    public void notifyPermissionChanged(
            UUID playerUuid, int claimId, String claimName, String changes) {
        String title = "Permissions Updated";
        String message = "Your permissions in '" + claimName + "' have changed: " + changes;

        notifyAboutClaim(
                playerUuid,
                NotificationType.PERMISSION_CHANGED,
                title,
                message,
                claimId,
                NotificationPriority.LOW);
    }

    /** Notify player about nation invite. */
    public void notifyNationInvite(
            UUID playerUuid, int nationId, String nationName, UUID inviterUuid) {
        String title = "Nation Invite";
        String message =
                "You've been invited to join the nation '"
                        + nationName
                        + "' by "
                        + Bukkit.getOfflinePlayer(inviterUuid).getName()
                        + "!";
        String actionButton = "/nation accept " + nationName;

        // Invite expires in 48 hours
        Instant expiresAt = Instant.now().plus(Duration.ofHours(48));

        PlayerNotification notification =
                new PlayerNotification(playerUuid, NotificationType.NATION_INVITE, title, message);
        notification.setPriority(NotificationPriority.NORMAL);
        notification.setRelatedNationId(nationId);
        notification.setActionButton(actionButton);
        notification.setExpiresAt(expiresAt);

        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            repository.saveNotification(notification);
                            unreadCountCache.remove(playerUuid);

                            // Notify online player
                            Player player = Bukkit.getPlayer(playerUuid);
                            if (player != null && player.isOnline()) {
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    TextUtil.send(
                                                            player,
                                                            "<gray>[<yellow>!<gray>] <white>"
                                                                    + title);
                                                    TextUtil.send(player, "<white>" + message);
                                                    TextUtil.send(
                                                            player,
                                                            "<gray>Type <yellow>"
                                                                    + actionButton
                                                                    + " <gray>to accept");
                                                });
                            }
                        });
    }

    /** Notify player about war declaration. */
    public void notifyWarDeclared(
            UUID playerUuid, String attackerName, String defenderName, String reason) {
        String title = "War Declared!";
        String message =
                attackerName + " has declared war on " + defenderName + "! Reason: " + reason;

        notify(
                playerUuid,
                NotificationType.WAR_DECLARED,
                title,
                message,
                NotificationPriority.URGENT);
    }
}
