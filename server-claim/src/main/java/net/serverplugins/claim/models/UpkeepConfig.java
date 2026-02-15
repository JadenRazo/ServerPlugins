package net.serverplugins.claim.models;

public class UpkeepConfig {

    private int claimId;
    private double costPerChunk;
    private double discountPercentage;
    private int graceDays;
    private boolean autoUnclaimEnabled;
    private int notificationsSent;

    public UpkeepConfig(int claimId) {
        this.claimId = claimId;
        this.costPerChunk = 10.0;
        this.discountPercentage = 0.0;
        this.graceDays = 7;
        this.autoUnclaimEnabled = true;
        this.notificationsSent = 0;
    }

    public UpkeepConfig(
            int claimId,
            double costPerChunk,
            double discountPercentage,
            int graceDays,
            boolean autoUnclaimEnabled,
            int notificationsSent) {
        this.claimId = claimId;
        this.costPerChunk = costPerChunk;
        this.discountPercentage = discountPercentage;
        this.graceDays = graceDays;
        this.autoUnclaimEnabled = autoUnclaimEnabled;
        this.notificationsSent = notificationsSent;
    }

    public int getClaimId() {
        return claimId;
    }

    public void setClaimId(int claimId) {
        this.claimId = claimId;
    }

    public double getCostPerChunk() {
        return costPerChunk;
    }

    public void setCostPerChunk(double costPerChunk) {
        this.costPerChunk = costPerChunk;
    }

    public double getDiscountPercentage() {
        return discountPercentage;
    }

    public void setDiscountPercentage(double discountPercentage) {
        this.discountPercentage = Math.max(0, Math.min(100, discountPercentage));
    }

    public int getGraceDays() {
        return graceDays;
    }

    public void setGraceDays(int graceDays) {
        this.graceDays = graceDays;
    }

    public boolean isAutoUnclaimEnabled() {
        return autoUnclaimEnabled;
    }

    public void setAutoUnclaimEnabled(boolean autoUnclaimEnabled) {
        this.autoUnclaimEnabled = autoUnclaimEnabled;
    }

    public int getNotificationsSent() {
        return notificationsSent;
    }

    public void setNotificationsSent(int notificationsSent) {
        this.notificationsSent = notificationsSent;
    }

    public void incrementNotificationsSent() {
        this.notificationsSent++;
    }

    public void resetNotifications() {
        this.notificationsSent = 0;
    }

    public double getEffectiveCostPerChunk() {
        return costPerChunk * (1.0 - discountPercentage / 100.0);
    }

    public double calculateUpkeepCost(int chunkCount) {
        return getEffectiveCostPerChunk() * chunkCount;
    }
}
