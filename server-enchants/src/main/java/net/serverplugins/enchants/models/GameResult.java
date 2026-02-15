package net.serverplugins.enchants.models;

import java.util.UUID;
import net.serverplugins.enchants.enchantments.EnchantTier;
import net.serverplugins.enchants.games.GameType;

public class GameResult {

    private final UUID playerUuid;
    private final GameType gameType;
    private final EnchantTier tier;
    private final boolean won;
    private final int score;
    private final int fragmentsEarned;
    private final int xpEarned;

    public GameResult(
            UUID playerUuid,
            GameType gameType,
            EnchantTier tier,
            boolean won,
            int score,
            int fragmentsEarned,
            int xpEarned) {
        this.playerUuid = playerUuid;
        this.gameType = gameType;
        this.tier = tier;
        this.won = won;
        this.score = score;
        this.fragmentsEarned = fragmentsEarned;
        this.xpEarned = xpEarned;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public GameType getGameType() {
        return gameType;
    }

    public EnchantTier getTier() {
        return tier;
    }

    public boolean isWon() {
        return won;
    }

    public int getScore() {
        return score;
    }

    public int getFragmentsEarned() {
        return fragmentsEarned;
    }

    public int getXpEarned() {
        return xpEarned;
    }
}
