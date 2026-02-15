package net.serverplugins.admin.punishment;

import java.util.UUID;

public class Punishment {
    private int id;
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
    private long issuedAt;
    private Long expiresAt;
    private boolean active;
    private UUID pardonedByUuid;
    private String pardonedByName;
    private Long pardonedAt;
    private String pardonReason;

    public Punishment() {}

    public Punishment(
            UUID targetUuid,
            String targetName,
            UUID staffUuid,
            String staffName,
            PunishmentType type,
            String category,
            Integer offenseNumber,
            String reason,
            Long durationMs) {
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.staffUuid = staffUuid;
        this.staffName = staffName;
        this.type = type;
        this.category = category;
        this.offenseNumber = offenseNumber;
        this.reason = reason;
        this.durationMs = durationMs;
        this.issuedAt = System.currentTimeMillis();
        this.expiresAt = durationMs != null ? issuedAt + durationMs : null;
        this.active = true;
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

    public long getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(long issuedAt) {
        this.issuedAt = issuedAt;
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

    public UUID getPardonedByUuid() {
        return pardonedByUuid;
    }

    public void setPardonedByUuid(UUID pardonedByUuid) {
        this.pardonedByUuid = pardonedByUuid;
    }

    public String getPardonedByName() {
        return pardonedByName;
    }

    public void setPardonedByName(String pardonedByName) {
        this.pardonedByName = pardonedByName;
    }

    public Long getPardonedAt() {
        return pardonedAt;
    }

    public void setPardonedAt(Long pardonedAt) {
        this.pardonedAt = pardonedAt;
    }

    public String getPardonReason() {
        return pardonReason;
    }

    public void setPardonReason(String pardonReason) {
        this.pardonReason = pardonReason;
    }

    public boolean isExpired() {
        if (expiresAt == null) return false;
        return System.currentTimeMillis() >= expiresAt;
    }

    public boolean isPermanent() {
        return type.hasDuration() && expiresAt == null;
    }

    public long getRemainingTime() {
        if (expiresAt == null) return -1;
        return Math.max(0, expiresAt - System.currentTimeMillis());
    }

    public String getFormattedRemainingTime() {
        long remaining = getRemainingTime();
        if (remaining < 0) return "Permanent";
        if (remaining == 0) return "Expired";
        return EscalationPreset.formatDuration(remaining);
    }

    public String getFormattedDuration() {
        if (durationMs == null) return "Permanent";
        return EscalationPreset.formatDuration(durationMs);
    }

    public void pardon(UUID pardonerUuid, String pardonerName, String pardonReason) {
        this.active = false;
        this.pardonedByUuid = pardonerUuid;
        this.pardonedByName = pardonerName;
        this.pardonedAt = System.currentTimeMillis();
        this.pardonReason = pardonReason;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Punishment punishment = new Punishment();

        public Builder targetUuid(UUID uuid) {
            punishment.targetUuid = uuid;
            return this;
        }

        public Builder targetName(String name) {
            punishment.targetName = name;
            return this;
        }

        public Builder staffUuid(UUID uuid) {
            punishment.staffUuid = uuid;
            return this;
        }

        public Builder staffName(String name) {
            punishment.staffName = name;
            return this;
        }

        public Builder type(PunishmentType type) {
            punishment.type = type;
            return this;
        }

        public Builder category(String category) {
            punishment.category = category;
            return this;
        }

        public Builder reasonId(String reasonId) {
            punishment.reasonId = reasonId;
            return this;
        }

        public Builder offenseNumber(Integer number) {
            punishment.offenseNumber = number;
            return this;
        }

        public Builder reason(String reason) {
            punishment.reason = reason;
            return this;
        }

        public Builder durationMs(Long duration) {
            punishment.durationMs = duration;
            punishment.issuedAt = System.currentTimeMillis();
            punishment.expiresAt = duration != null ? punishment.issuedAt + duration : null;
            return this;
        }

        public Punishment build() {
            if (punishment.issuedAt == 0) {
                punishment.issuedAt = System.currentTimeMillis();
            }
            punishment.active = true;
            return punishment;
        }
    }
}
