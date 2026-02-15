package net.serverplugins.admin.punishment;

import java.util.UUID;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class PunishmentContext {
    private UUID targetUuid;
    private String targetName;
    private UUID staffUuid;
    private String staffName;
    private PunishmentType type;
    private String category;
    private String reasonId;
    private Integer offenseNumber;
    private String reason;
    private Long durationMs;

    public PunishmentContext() {}

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

    public UUID getStaffUuid() {
        return staffUuid;
    }

    public void setStaffUuid(UUID staffUuid) {
        this.staffUuid = staffUuid;
    }

    public String getStaffName() {
        return staffName;
    }

    public void setStaffName(String staffName) {
        this.staffName = staffName;
    }

    public PunishmentType getType() {
        return type;
    }

    public void setType(PunishmentType type) {
        this.type = type;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getReasonId() {
        return reasonId;
    }

    public void setReasonId(String reasonId) {
        this.reasonId = reasonId;
    }

    public Integer getOffenseNumber() {
        return offenseNumber;
    }

    public void setOffenseNumber(Integer offenseNumber) {
        this.offenseNumber = offenseNumber;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final PunishmentContext context = new PunishmentContext();

        public Builder target(OfflinePlayer target) {
            context.targetUuid = target.getUniqueId();
            context.targetName = target.getName();
            return this;
        }

        public Builder targetUuid(UUID uuid) {
            context.targetUuid = uuid;
            return this;
        }

        public Builder targetName(String name) {
            context.targetName = name;
            return this;
        }

        public Builder staff(Player staff) {
            if (staff != null) {
                context.staffUuid = staff.getUniqueId();
                context.staffName = staff.getName();
            }
            return this;
        }

        public Builder staffName(String name) {
            context.staffName = name;
            return this;
        }

        public Builder type(PunishmentType type) {
            context.type = type;
            return this;
        }

        public Builder category(String category) {
            context.category = category;
            return this;
        }

        public Builder reasonId(String reasonId) {
            context.reasonId = reasonId;
            return this;
        }

        public Builder offenseNumber(Integer number) {
            context.offenseNumber = number;
            return this;
        }

        public Builder reason(String reason) {
            context.reason = reason;
            return this;
        }

        public Builder durationMs(Long duration) {
            context.durationMs = duration;
            return this;
        }

        public Builder duration(String duration) {
            context.durationMs = EscalationPreset.parseDuration(duration);
            return this;
        }

        public Builder fromPreset(EscalationPreset preset) {
            context.type = preset.getType();
            context.durationMs = preset.getDurationMs();
            context.reason = preset.getReason();
            return this;
        }

        public Builder fromReasonPreset(ReasonPreset preset, ReasonPreset.EscalationLevel level) {
            context.reasonId = preset.getId();
            context.type = level.getType();
            context.durationMs = level.getDurationMs();
            context.reason = preset.getDisplayName();
            return this;
        }

        public PunishmentContext build() {
            return context;
        }
    }
}
