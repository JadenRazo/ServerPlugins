package net.serverplugins.claim.models;

import java.time.Duration;
import java.time.Instant;

public class WarShield {

    private int id;
    private Integer nationId;
    private Integer claimId;
    private Instant shieldEnd;
    private String reason;
    private Instant createdAt;

    public WarShield() {
        this.createdAt = Instant.now();
    }

    public WarShield(Integer nationId, Integer claimId, Instant shieldEnd, String reason) {
        this.nationId = nationId;
        this.claimId = claimId;
        this.shieldEnd = shieldEnd;
        this.reason = reason;
        this.createdAt = Instant.now();
    }

    public WarShield(
            int id,
            Integer nationId,
            Integer claimId,
            Instant shieldEnd,
            String reason,
            Instant createdAt) {
        this.id = id;
        this.nationId = nationId;
        this.claimId = claimId;
        this.shieldEnd = shieldEnd;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Integer getNationId() {
        return nationId;
    }

    public void setNationId(Integer nationId) {
        this.nationId = nationId;
    }

    public Integer getClaimId() {
        return claimId;
    }

    public void setClaimId(Integer claimId) {
        this.claimId = claimId;
    }

    public Instant getShieldEnd() {
        return shieldEnd;
    }

    public void setShieldEnd(Instant shieldEnd) {
        this.shieldEnd = shieldEnd;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isActive() {
        return shieldEnd != null && Instant.now().isBefore(shieldEnd);
    }

    public boolean isExpired() {
        return shieldEnd == null || Instant.now().isAfter(shieldEnd);
    }

    public Duration getRemainingDuration() {
        if (shieldEnd == null || isExpired()) {
            return Duration.ZERO;
        }
        return Duration.between(Instant.now(), shieldEnd);
    }

    public String getFormattedTimeRemaining() {
        Duration remaining = getRemainingDuration();
        if (remaining.isZero() || remaining.isNegative()) {
            return "Expired";
        }

        long days = remaining.toDays();
        long hours = remaining.toHours() % 24;
        long minutes = remaining.toMinutes() % 60;

        if (days > 0) {
            return days + "d " + hours + "h";
        } else if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else {
            return minutes + "m";
        }
    }

    public boolean appliesToNation(int nationId) {
        return this.nationId != null && this.nationId == nationId;
    }

    public boolean appliesToClaim(int claimId) {
        return this.claimId != null && this.claimId == claimId;
    }

    public static WarShield createForNation(int nationId, int daysShield, String reason) {
        return new WarShield(
                nationId, null, Instant.now().plus(Duration.ofDays(daysShield)), reason);
    }

    public static WarShield createForClaim(int claimId, int daysShield, String reason) {
        return new WarShield(
                null, claimId, Instant.now().plus(Duration.ofDays(daysShield)), reason);
    }
}
