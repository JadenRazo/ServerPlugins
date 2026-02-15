package net.serverplugins.afk.models;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;

public class PlayerAfkSession {

    private final UUID playerId;
    private AfkZone currentZone;
    private long enteredAt;
    private long lastRewardAt;
    private int initialY;

    // New fields for enhanced tracking
    private int rewardsEarnedThisSession;
    private double currencyEarnedThisSession;
    private int xpEarnedThisSession;
    private boolean needsVerification;
    private long lastVerificationTime;
    private int verificationsPassed;
    private int verificationsFailed;
    private List<Location> recentLocations;
    private long lastActivityTime;
    private int sessionRecordId;
    private boolean manuallyAfk;

    public PlayerAfkSession(UUID playerId, AfkZone zone, int initialY) {
        this.playerId = playerId;
        this.currentZone = zone;
        this.enteredAt = System.currentTimeMillis();
        this.lastRewardAt = System.currentTimeMillis();
        this.initialY = initialY;

        // Initialize new fields
        this.rewardsEarnedThisSession = 0;
        this.currencyEarnedThisSession = 0.0;
        this.xpEarnedThisSession = 0;
        this.needsVerification = false;
        this.lastVerificationTime = 0;
        this.verificationsPassed = 0;
        this.verificationsFailed = 0;
        this.recentLocations = new ArrayList<>();
        this.lastActivityTime = System.currentTimeMillis();
        this.sessionRecordId = -1;
        this.manuallyAfk = false;
    }

    public long getTimeInZone() {
        return System.currentTimeMillis() - enteredAt;
    }

    public long getTimeInZoneSeconds() {
        return getTimeInZone() / 1000;
    }

    public long getTimeSinceLastReward() {
        return System.currentTimeMillis() - lastRewardAt;
    }

    public long getTimeSinceLastRewardSeconds() {
        return getTimeSinceLastReward() / 1000;
    }

    public void resetLastReward() {
        this.lastRewardAt = System.currentTimeMillis();
    }

    public void resetProgress() {
        this.enteredAt = System.currentTimeMillis();
        this.lastRewardAt = System.currentTimeMillis();
    }

    public boolean isReadyForReward() {
        if (currentZone == null) return false;
        return getTimeSinceLastRewardSeconds() >= currentZone.getTimeIntervalSeconds();
    }

    // New utility methods

    public void addReward(double currency, int xp) {
        this.rewardsEarnedThisSession++;
        this.currencyEarnedThisSession += currency;
        this.xpEarnedThisSession += xp;
    }

    public void addLocation(Location location) {
        recentLocations.add(location.clone());
        // Keep only last 20 locations
        if (recentLocations.size() > 20) {
            recentLocations.remove(0);
        }
    }

    public void updateActivity() {
        this.lastActivityTime = System.currentTimeMillis();
    }

    public long getTimeSinceLastActivity() {
        return System.currentTimeMillis() - lastActivityTime;
    }

    public long getTimeSinceLastActivitySeconds() {
        return getTimeSinceLastActivity() / 1000;
    }

    public boolean shouldVerify(int verificationIntervalSeconds) {
        if (lastVerificationTime == 0) {
            return getTimeInZoneSeconds() >= verificationIntervalSeconds;
        }
        long timeSinceVerification = (System.currentTimeMillis() - lastVerificationTime) / 1000;
        return timeSinceVerification >= verificationIntervalSeconds;
    }

    public void recordVerificationAttempt(boolean passed) {
        this.lastVerificationTime = System.currentTimeMillis();
        if (passed) {
            this.verificationsPassed++;
            this.needsVerification = false;
        } else {
            this.verificationsFailed++;
        }
    }

    // Getters
    public UUID getPlayerId() {
        return playerId;
    }

    public AfkZone getCurrentZone() {
        return currentZone;
    }

    public void setCurrentZone(AfkZone zone) {
        this.currentZone = zone;
    }

    public long getEnteredAt() {
        return enteredAt;
    }

    public long getLastRewardAt() {
        return lastRewardAt;
    }

    public int getInitialY() {
        return initialY;
    }

    public void setInitialY(int y) {
        this.initialY = y;
    }

    public int getRewardsEarnedThisSession() {
        return rewardsEarnedThisSession;
    }

    public double getCurrencyEarnedThisSession() {
        return currencyEarnedThisSession;
    }

    public int getXpEarnedThisSession() {
        return xpEarnedThisSession;
    }

    public boolean needsVerification() {
        return needsVerification;
    }

    public void setNeedsVerification(boolean needsVerification) {
        this.needsVerification = needsVerification;
    }

    public long getLastVerificationTime() {
        return lastVerificationTime;
    }

    public int getVerificationsPassed() {
        return verificationsPassed;
    }

    public int getVerificationsFailed() {
        return verificationsFailed;
    }

    public List<Location> getRecentLocations() {
        return new ArrayList<>(recentLocations);
    }

    public long getLastActivityTime() {
        return lastActivityTime;
    }

    public int getSessionRecordId() {
        return sessionRecordId;
    }

    public void setSessionRecordId(int sessionRecordId) {
        this.sessionRecordId = sessionRecordId;
    }

    public boolean isManuallyAfk() {
        return manuallyAfk;
    }

    public void setManuallyAfk(boolean manuallyAfk) {
        this.manuallyAfk = manuallyAfk;
    }
}
