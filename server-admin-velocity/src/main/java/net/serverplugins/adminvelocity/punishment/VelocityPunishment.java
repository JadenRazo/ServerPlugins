package net.serverplugins.adminvelocity.punishment;

import java.util.UUID;

/**
 * Data model for punishments on Velocity (no Bukkit dependencies).
 *
 * <p>Represents a punishment record that can be stored in the database and enforced at the proxy
 * level.
 */
public class VelocityPunishment {
    private int id;
    private UUID targetUuid;
    private String targetName;
    private String staffName;
    private VelocityPunishmentType type;
    private String reason;
    private long createdAt;
    private Long expiresAt;
    private boolean active;
    private boolean permanent;
    private String pardonedBy;
    private String pardonReason;
    private String sourceServer;

    public VelocityPunishment() {
        this.createdAt = System.currentTimeMillis();
        this.active = true;
    }

    public VelocityPunishment(
            UUID targetUuid,
            String targetName,
            String staffName,
            VelocityPunishmentType type,
            String reason,
            Long durationMs) {
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.staffName = staffName;
        this.type = type;
        this.reason = reason;
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = durationMs != null ? createdAt + durationMs : null;
        this.active = true;
        this.permanent = durationMs == null;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public void setTargetUuid(UUID targetUuid) {
        this.targetUuid = targetUuid;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public String getStaffName() {
        return staffName;
    }

    public void setStaffName(String staffName) {
        this.staffName = staffName;
    }

    public VelocityPunishmentType getType() {
        return type;
    }

    public void setType(VelocityPunishmentType type) {
        this.type = type;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isPermanent() {
        return permanent;
    }

    public void setPermanent(boolean permanent) {
        this.permanent = permanent;
    }

    public String getPardonedBy() {
        return pardonedBy;
    }

    public void setPardonedBy(String pardonedBy) {
        this.pardonedBy = pardonedBy;
    }

    public String getPardonReason() {
        return pardonReason;
    }

    public void setPardonReason(String pardonReason) {
        this.pardonReason = pardonReason;
    }

    public String getSourceServer() {
        return sourceServer;
    }

    public void setSourceServer(String sourceServer) {
        this.sourceServer = sourceServer;
    }

    /**
     * Checks if this punishment has expired.
     *
     * @return true if the punishment is expired, false otherwise
     */
    public boolean isExpired() {
        if (expiresAt == null) return false;
        return System.currentTimeMillis() >= expiresAt;
    }

    /**
     * Gets the remaining duration in milliseconds.
     *
     * @return remaining time in milliseconds, or -1 if permanent
     */
    public long getRemainingDuration() {
        if (expiresAt == null) return -1;
        return Math.max(0, expiresAt - System.currentTimeMillis());
    }

    /**
     * Formats the remaining duration as a human-readable string.
     *
     * @return formatted duration string
     */
    public String getFormattedRemainingDuration() {
        if (isPermanent()) return "Permanent";
        if (isExpired()) return "Expired";

        long remaining = getRemainingDuration();
        return formatDuration(remaining);
    }

    /**
     * Formats a duration in milliseconds to a readable string.
     *
     * @param durationMs duration in milliseconds
     * @return formatted duration (e.g., "1d 2h 30m")
     */
    private static String formatDuration(long durationMs) {
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours % 24 > 0) sb.append(hours % 24).append("h ");
        if (minutes % 60 > 0) sb.append(minutes % 60).append("m ");
        if (sb.length() == 0 && seconds % 60 > 0) sb.append(seconds % 60).append("s");

        return sb.toString().trim();
    }

    /**
     * Gets the formatted duration string (total duration, not remaining).
     *
     * @return formatted duration string
     */
    public String getFormattedDuration() {
        if (isPermanent()) return "Permanent";
        if (expiresAt == null) return "Unknown";

        long durationMs = expiresAt - createdAt;
        return formatDuration(durationMs);
    }

    /**
     * Gets the formatted expiry date.
     *
     * @return formatted expiry date string
     */
    public String getFormattedExpiryDate() {
        if (expiresAt == null) return "Never";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm");
        return sdf.format(new java.util.Date(expiresAt));
    }

    /**
     * Gets the formatted creation date.
     *
     * @return formatted creation date string
     */
    public String getFormattedCreatedDate() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm");
        return sdf.format(new java.util.Date(createdAt));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final VelocityPunishment punishment = new VelocityPunishment();

        public Builder targetUuid(UUID uuid) {
            punishment.targetUuid = uuid;
            return this;
        }

        public Builder targetName(String name) {
            punishment.targetName = name;
            return this;
        }

        public Builder staffName(String name) {
            punishment.staffName = name;
            return this;
        }

        public Builder type(VelocityPunishmentType type) {
            punishment.type = type;
            return this;
        }

        public Builder reason(String reason) {
            punishment.reason = reason;
            return this;
        }

        public Builder durationMs(Long duration) {
            punishment.expiresAt = duration != null ? punishment.createdAt + duration : null;
            punishment.permanent = duration == null;
            return this;
        }

        public Builder sourceServer(String server) {
            punishment.sourceServer = server;
            return this;
        }

        public VelocityPunishment build() {
            return punishment;
        }
    }
}
