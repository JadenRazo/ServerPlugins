package net.serverplugins.enchants.models;

import java.util.UUID;

public class PlayerProgression {

    private final UUID playerUuid;
    private int level;
    private int experience;
    private int totalFragments;
    private int lifetimeGamesPlayed;
    private int lifetimeGamesWon;

    public PlayerProgression(UUID playerUuid) {
        this(playerUuid, 1, 0, 0, 0, 0);
    }

    public PlayerProgression(
            UUID playerUuid,
            int level,
            int experience,
            int totalFragments,
            int lifetimeGamesPlayed,
            int lifetimeGamesWon) {
        this.playerUuid = playerUuid;
        this.level = level;
        this.experience = experience;
        this.totalFragments = totalFragments;
        this.lifetimeGamesPlayed = lifetimeGamesPlayed;
        this.lifetimeGamesWon = lifetimeGamesWon;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getExperience() {
        return experience;
    }

    public void setExperience(int experience) {
        this.experience = experience;
    }

    public int getTotalFragments() {
        return totalFragments;
    }

    public void setTotalFragments(int totalFragments) {
        this.totalFragments = totalFragments;
    }

    public int getLifetimeGamesPlayed() {
        return lifetimeGamesPlayed;
    }

    public void setLifetimeGamesPlayed(int lifetimeGamesPlayed) {
        this.lifetimeGamesPlayed = lifetimeGamesPlayed;
    }

    public int getLifetimeGamesWon() {
        return lifetimeGamesWon;
    }

    public void setLifetimeGamesWon(int lifetimeGamesWon) {
        this.lifetimeGamesWon = lifetimeGamesWon;
    }

    public void addExperience(int amount) {
        this.experience += amount;
    }

    public void addFragments(int amount) {
        this.totalFragments += amount;
    }

    public boolean removeFragments(int amount) {
        if (this.totalFragments >= amount) {
            this.totalFragments -= amount;
            return true;
        }
        return false;
    }

    public int getXpForNextLevel() {
        return level * 100 + 50;
    }

    public boolean canLevelUp() {
        return experience >= getXpForNextLevel();
    }

    public boolean tryLevelUp() {
        if (canLevelUp()) {
            experience -= getXpForNextLevel();
            level++;
            return true;
        }
        return false;
    }
}
