package net.serverplugins.afk.models;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class PlayerStats {

    private UUID playerUuid;
    private long totalAfkTimeSeconds;
    private int totalRewardsReceived;
    private double totalCurrencyEarned;
    private int totalXpEarned;
    private int sessionsCompleted;
    private LocalDateTime firstAfkTime;
    private LocalDateTime lastAfkTime;
    private Integer favoriteZoneId;
    private long longestSessionSeconds;
    private int currentStreakDays;
    private int bestStreakDays;
    private LocalDate lastDailyRewardDate;

    public PlayerStats(UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.totalAfkTimeSeconds = 0;
        this.totalRewardsReceived = 0;
        this.totalCurrencyEarned = 0.0;
        this.totalXpEarned = 0;
        this.sessionsCompleted = 0;
        this.firstAfkTime = LocalDateTime.now();
        this.lastAfkTime = LocalDateTime.now();
        this.favoriteZoneId = null;
        this.longestSessionSeconds = 0;
        this.currentStreakDays = 0;
        this.bestStreakDays = 0;
        this.lastDailyRewardDate = null;
    }

    // Utility methods

    public void addAfkTime(long seconds) {
        this.totalAfkTimeSeconds += seconds;
    }

    public void addReward() {
        this.totalRewardsReceived++;
    }

    public void addCurrency(double amount) {
        this.totalCurrencyEarned += amount;
    }

    public void addXp(int amount) {
        this.totalXpEarned += amount;
    }

    public void incrementSessions() {
        this.sessionsCompleted++;
    }

    public void updateLongestSession(long seconds) {
        if (seconds > this.longestSessionSeconds) {
            this.longestSessionSeconds = seconds;
        }
    }

    public void updateLastAfkTime() {
        this.lastAfkTime = LocalDateTime.now();
    }

    public void updateStreak() {
        LocalDate today = LocalDate.now();

        if (lastDailyRewardDate == null) {
            // First time tracking
            currentStreakDays = 1;
            bestStreakDays = 1;
            lastDailyRewardDate = today;
        } else if (lastDailyRewardDate.equals(today)) {
            // Already counted today, no change
            return;
        } else if (lastDailyRewardDate.plusDays(1).equals(today)) {
            // Consecutive day
            currentStreakDays++;
            if (currentStreakDays > bestStreakDays) {
                bestStreakDays = currentStreakDays;
            }
            lastDailyRewardDate = today;
        } else {
            // Streak broken
            currentStreakDays = 1;
            lastDailyRewardDate = today;
        }
    }

    public String getFormattedTotalTime() {
        long hours = totalAfkTimeSeconds / 3600;
        long minutes = (totalAfkTimeSeconds % 3600) / 60;
        long seconds = totalAfkTimeSeconds % 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    public String getFormattedLongestSession() {
        long hours = longestSessionSeconds / 3600;
        long minutes = (longestSessionSeconds % 3600) / 60;
        long seconds = longestSessionSeconds % 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    // Getters and Setters

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public long getTotalAfkTimeSeconds() {
        return totalAfkTimeSeconds;
    }

    public void setTotalAfkTimeSeconds(long totalAfkTimeSeconds) {
        this.totalAfkTimeSeconds = totalAfkTimeSeconds;
    }

    public int getTotalRewardsReceived() {
        return totalRewardsReceived;
    }

    public void setTotalRewardsReceived(int totalRewardsReceived) {
        this.totalRewardsReceived = totalRewardsReceived;
    }

    public double getTotalCurrencyEarned() {
        return totalCurrencyEarned;
    }

    public void setTotalCurrencyEarned(double totalCurrencyEarned) {
        this.totalCurrencyEarned = totalCurrencyEarned;
    }

    public int getTotalXpEarned() {
        return totalXpEarned;
    }

    public void setTotalXpEarned(int totalXpEarned) {
        this.totalXpEarned = totalXpEarned;
    }

    public int getSessionsCompleted() {
        return sessionsCompleted;
    }

    public void setSessionsCompleted(int sessionsCompleted) {
        this.sessionsCompleted = sessionsCompleted;
    }

    public LocalDateTime getFirstAfkTime() {
        return firstAfkTime;
    }

    public void setFirstAfkTime(LocalDateTime firstAfkTime) {
        this.firstAfkTime = firstAfkTime;
    }

    public LocalDateTime getLastAfkTime() {
        return lastAfkTime;
    }

    public void setLastAfkTime(LocalDateTime lastAfkTime) {
        this.lastAfkTime = lastAfkTime;
    }

    public Integer getFavoriteZoneId() {
        return favoriteZoneId;
    }

    public void setFavoriteZoneId(Integer favoriteZoneId) {
        this.favoriteZoneId = favoriteZoneId;
    }

    public long getLongestSessionSeconds() {
        return longestSessionSeconds;
    }

    public void setLongestSessionSeconds(long longestSessionSeconds) {
        this.longestSessionSeconds = longestSessionSeconds;
    }

    public int getCurrentStreakDays() {
        return currentStreakDays;
    }

    public void setCurrentStreakDays(int currentStreakDays) {
        this.currentStreakDays = currentStreakDays;
    }

    public int getBestStreakDays() {
        return bestStreakDays;
    }

    public void setBestStreakDays(int bestStreakDays) {
        this.bestStreakDays = bestStreakDays;
    }

    public LocalDate getLastDailyRewardDate() {
        return lastDailyRewardDate;
    }

    public void setLastDailyRewardDate(LocalDate lastDailyRewardDate) {
        this.lastDailyRewardDate = lastDailyRewardDate;
    }
}
