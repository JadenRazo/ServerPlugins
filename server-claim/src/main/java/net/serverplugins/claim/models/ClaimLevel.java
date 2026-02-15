package net.serverplugins.claim.models;

public class ClaimLevel {

    public static final int MAX_LEVEL = 10;
    public static final long[] XP_THRESHOLDS = {
        0, // Level 1 (starting)
        1000, // Level 2
        2500, // Level 3
        5000, // Level 4
        10000, // Level 5
        20000, // Level 6
        40000, // Level 7
        80000, // Level 8
        160000, // Level 9
        320000 // Level 10
    };

    private int claimId;
    private int level;
    private long currentXp;
    private long totalXpEarned;

    public ClaimLevel(int claimId) {
        this.claimId = claimId;
        this.level = 1;
        this.currentXp = 0;
        this.totalXpEarned = 0;
    }

    public ClaimLevel(int claimId, int level, long currentXp, long totalXpEarned) {
        this.claimId = claimId;
        this.level = level;
        this.currentXp = currentXp;
        this.totalXpEarned = totalXpEarned;
    }

    public int getClaimId() {
        return claimId;
    }

    public void setClaimId(int claimId) {
        this.claimId = claimId;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, Math.min(MAX_LEVEL, level));
    }

    public long getCurrentXp() {
        return currentXp;
    }

    public void setCurrentXp(long currentXp) {
        this.currentXp = currentXp;
    }

    public long getTotalXpEarned() {
        return totalXpEarned;
    }

    public void setTotalXpEarned(long totalXpEarned) {
        this.totalXpEarned = totalXpEarned;
    }

    public long getXpForNextLevel() {
        if (level >= MAX_LEVEL) return 0;
        return XP_THRESHOLDS[level];
    }

    public long getXpForCurrentLevel() {
        if (level <= 1) return 0;
        return XP_THRESHOLDS[level - 1];
    }

    public long getXpProgressInLevel() {
        return currentXp - getXpForCurrentLevel();
    }

    public long getXpNeededForNextLevel() {
        if (level >= MAX_LEVEL) return 0;
        return getXpForNextLevel() - currentXp;
    }

    public double getProgressPercentage() {
        if (level >= MAX_LEVEL) return 100.0;
        long levelStart = getXpForCurrentLevel();
        long levelEnd = getXpForNextLevel();
        long range = levelEnd - levelStart;
        if (range <= 0) return 100.0;
        return ((double) (currentXp - levelStart) / range) * 100.0;
    }

    public boolean addXp(long amount) {
        if (amount <= 0 || level >= MAX_LEVEL) return false;

        this.currentXp += amount;
        this.totalXpEarned += amount;

        boolean leveledUp = false;
        while (level < MAX_LEVEL && currentXp >= XP_THRESHOLDS[level]) {
            level++;
            leveledUp = true;
        }

        return leveledUp;
    }

    public boolean canLevelUp() {
        return level < MAX_LEVEL && currentXp >= XP_THRESHOLDS[level];
    }

    public static int getLevelForXp(long xp) {
        for (int i = MAX_LEVEL - 1; i >= 0; i--) {
            if (xp >= XP_THRESHOLDS[i]) {
                return i + 1;
            }
        }
        return 1;
    }
}
