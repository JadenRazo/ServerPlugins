package net.serverplugins.claim.models;

import java.time.Instant;
import java.util.UUID;

/** Represents a notification sent to a player. */
public class PlayerNotification {

    private long id;
    private UUID playerUuid;
    private NotificationType type;
    private String title;
    private String message;
    private NotificationPriority priority;
    private Integer relatedClaimId;
    private Integer relatedNationId;
    private String actionButton; // JSON string for button action (e.g., "/claim accept 123")
    private Instant expiresAt;
    private Instant readAt;
    private Instant createdAt;

    public PlayerNotification() {
        this.priority = NotificationPriority.NORMAL;
        this.createdAt = Instant.now();
    }

    public PlayerNotification(
            UUID playerUuid, NotificationType type, String title, String message) {
        this();
        this.playerUuid = playerUuid;
        this.type = type;
        this.title = title;
        this.message = message;
    }

    // Getters and setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public NotificationPriority getPriority() {
        return priority;
    }

    public void setPriority(NotificationPriority priority) {
        this.priority = priority;
    }

    public Integer getRelatedClaimId() {
        return relatedClaimId;
    }

    public void setRelatedClaimId(Integer relatedClaimId) {
        this.relatedClaimId = relatedClaimId;
    }

    public Integer getRelatedNationId() {
        return relatedNationId;
    }

    public void setRelatedNationId(Integer relatedNationId) {
        this.relatedNationId = relatedNationId;
    }

    public String getActionButton() {
        return actionButton;
    }

    public void setActionButton(String actionButton) {
        this.actionButton = actionButton;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    // Helper methods
    public boolean isRead() {
        return readAt != null;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public void markAsRead() {
        this.readAt = Instant.now();
    }
}
