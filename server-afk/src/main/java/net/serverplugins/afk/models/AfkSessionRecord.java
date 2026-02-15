package net.serverplugins.afk.models;

import java.time.LocalDateTime;
import java.util.UUID;

public class AfkSessionRecord {

    private int id;
    private UUID playerUuid;
    private int zoneId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private long durationSeconds;
    private int rewardsEarned;
    private double currencyEarned;
    private int xpEarned;
    private boolean wasVerified;
    private boolean endedByCombat;

    public AfkSessionRecord(UUID playerUuid, int zoneId) {
        this.playerUuid = playerUuid;
        this.zoneId = zoneId;
        this.startTime = LocalDateTime.now();
        this.endTime = null;
        this.durationSeconds = 0;
        this.rewardsEarned = 0;
        this.currencyEarned = 0.0;
        this.xpEarned = 0;
        this.wasVerified = false;
        this.endedByCombat = false;
    }

    public AfkSessionRecord() {
        // Empty constructor for database mapping
    }

    // Utility methods

    public void endSession() {
        this.endTime = LocalDateTime.now();
        if (this.startTime != null) {
            this.durationSeconds = java.time.Duration.between(startTime, endTime).getSeconds();
        }
    }

    public void addReward(double currency, int xp) {
        this.rewardsEarned++;
        this.currencyEarned += currency;
        this.xpEarned += xp;
    }

    public boolean isActive() {
        return endTime == null;
    }

    public String getFormattedDuration() {
        long hours = durationSeconds / 3600;
        long minutes = (durationSeconds % 3600) / 60;
        long seconds = durationSeconds % 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    // Getters and Setters

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public int getZoneId() {
        return zoneId;
    }

    public void setZoneId(int zoneId) {
        this.zoneId = zoneId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public int getRewardsEarned() {
        return rewardsEarned;
    }

    public void setRewardsEarned(int rewardsEarned) {
        this.rewardsEarned = rewardsEarned;
    }

    public double getCurrencyEarned() {
        return currencyEarned;
    }

    public void setCurrencyEarned(double currencyEarned) {
        this.currencyEarned = currencyEarned;
    }

    public int getXpEarned() {
        return xpEarned;
    }

    public void setXpEarned(int xpEarned) {
        this.xpEarned = xpEarned;
    }

    public boolean isWasVerified() {
        return wasVerified;
    }

    public void setWasVerified(boolean wasVerified) {
        this.wasVerified = wasVerified;
    }

    public boolean isEndedByCombat() {
        return endedByCombat;
    }

    public void setEndedByCombat(boolean endedByCombat) {
        this.endedByCombat = endedByCombat;
    }
}
