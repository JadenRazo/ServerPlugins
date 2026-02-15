package net.serverplugins.commands.data.punishment;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Represents a punishment record stored in the database. */
public class PunishmentRecord {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private int id;
    private UUID targetUuid;
    private String targetName;
    private UUID staffUuid; // null for Console
    private String staffName;
    private PunishmentType type;
    private String reason;
    private Long durationMs; // null for permanent/instant
    private long issuedAt;
    private Long expiresAt; // null for permanent/instant
    private Long liftedAt;
    private UUID liftedByUuid;
    private String liftedByName;
    private boolean active;
    private String serverId;

    // Private constructor - use Builder
    private PunishmentRecord() {}

    // Getters
    public int getId() {
        return id;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public String getTargetName() {
        return targetName;
    }

    public UUID getStaffUuid() {
        return staffUuid;
    }

    public String getStaffName() {
        return staffName;
    }

    public PunishmentType getType() {
        return type;
    }

    public String getReason() {
        return reason;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public long getIssuedAt() {
        return issuedAt;
    }

    public Long getExpiresAt() {
        return expiresAt;
    }

    public Long getLiftedAt() {
        return liftedAt;
    }

    public UUID getLiftedByUuid() {
        return liftedByUuid;
    }

    public String getLiftedByName() {
        return liftedByName;
    }

    public boolean isActive() {
        return active;
    }

    public String getServerId() {
        return serverId;
    }

    // Setters for mutable fields
    public void setId(int id) {
        this.id = id;
    }

    public void setLiftedAt(Long liftedAt) {
        this.liftedAt = liftedAt;
    }

    public void setLiftedByUuid(UUID liftedByUuid) {
        this.liftedByUuid = liftedByUuid;
    }

    public void setLiftedByName(String liftedByName) {
        this.liftedByName = liftedByName;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /** Check if this punishment has expired based on current time */
    public boolean isExpired() {
        if (expiresAt == null) return false; // Permanent
        return System.currentTimeMillis() > expiresAt;
    }

    /** Get remaining time in milliseconds */
    public long getRemainingTime() {
        if (expiresAt == null) return -1; // Permanent
        return Math.max(0, expiresAt - System.currentTimeMillis());
    }

    /** Get formatted issued date */
    public String getFormattedIssuedDate() {
        return DATE_FORMAT.format(new Date(issuedAt));
    }

    /** Get formatted expiry date */
    public String getFormattedExpiryDate() {
        if (expiresAt == null) return "Never";
        return DATE_FORMAT.format(new Date(expiresAt));
    }

    /** Get formatted duration string */
    public String getFormattedDuration() {
        if (durationMs == null) return "Permanent";
        return formatDuration(durationMs);
    }

    /** Get formatted remaining time string */
    public String getFormattedRemainingTime() {
        long remaining = getRemainingTime();
        if (remaining < 0) return "Permanent";
        if (remaining == 0) return "Expired";
        return formatDuration(remaining);
    }

    /** Format a duration in milliseconds to human-readable string */
    public static String formatDuration(long ms) {
        long seconds = ms / 1000;
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

    /** Check if punishment was issued by console */
    public boolean isFromConsole() {
        return staffUuid == null;
    }

    // Builder
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final PunishmentRecord record = new PunishmentRecord();

        public Builder() {
            record.issuedAt = System.currentTimeMillis();
            record.active = true;
            record.serverId = "main";
            record.reason = "No reason provided";
        }

        public Builder id(int id) {
            record.id = id;
            return this;
        }

        public Builder target(UUID uuid, String name) {
            record.targetUuid = uuid;
            record.targetName = name;
            return this;
        }

        public Builder target(Player player) {
            record.targetUuid = player.getUniqueId();
            record.targetName = player.getName();
            return this;
        }

        public Builder staff(UUID uuid, String name) {
            record.staffUuid = uuid;
            record.staffName = name;
            return this;
        }

        public Builder staff(CommandSender sender) {
            if (sender instanceof Player player) {
                record.staffUuid = player.getUniqueId();
                record.staffName = player.getName();
            } else {
                record.staffUuid = null;
                record.staffName = "Console";
            }
            return this;
        }

        public Builder type(PunishmentType type) {
            record.type = type;
            return this;
        }

        public Builder reason(String reason) {
            if (reason != null && !reason.isEmpty()) {
                record.reason = reason;
            }
            return this;
        }

        public Builder duration(Long durationMs) {
            record.durationMs = durationMs;
            if (durationMs != null && durationMs > 0) {
                record.expiresAt = record.issuedAt + durationMs;
            }
            return this;
        }

        public Builder issuedAt(long timestamp) {
            record.issuedAt = timestamp;
            // Recalculate expiry if duration was set
            if (record.durationMs != null && record.durationMs > 0) {
                record.expiresAt = record.issuedAt + record.durationMs;
            }
            return this;
        }

        public Builder expiresAt(Long timestamp) {
            record.expiresAt = timestamp;
            return this;
        }

        public Builder liftedAt(Long timestamp) {
            record.liftedAt = timestamp;
            return this;
        }

        public Builder liftedBy(UUID uuid, String name) {
            record.liftedByUuid = uuid;
            record.liftedByName = name;
            return this;
        }

        public Builder active(boolean active) {
            record.active = active;
            return this;
        }

        public Builder serverId(String serverId) {
            record.serverId = serverId;
            return this;
        }

        public PunishmentRecord build() {
            if (record.targetUuid == null || record.targetName == null) {
                throw new IllegalStateException("Target UUID and name are required");
            }
            if (record.staffName == null) {
                throw new IllegalStateException("Staff name is required");
            }
            if (record.type == null) {
                throw new IllegalStateException("Punishment type is required");
            }
            return record;
        }
    }

    @Override
    public String toString() {
        return "PunishmentRecord{"
                + "id="
                + id
                + ", type="
                + type
                + ", target="
                + targetName
                + ", staff="
                + staffName
                + ", reason='"
                + reason
                + '\''
                + ", issuedAt="
                + getFormattedIssuedDate()
                + ", active="
                + active
                + '}';
    }
}
