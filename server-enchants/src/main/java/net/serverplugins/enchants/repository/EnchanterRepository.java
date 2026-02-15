package net.serverplugins.enchants.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.serverplugins.api.database.Database;
import net.serverplugins.enchants.enchantments.EnchantTier;
import net.serverplugins.enchants.models.GameResult;
import net.serverplugins.enchants.models.PlayerProgression;

public class EnchanterRepository {

    private final Database database;
    private static final Logger LOGGER = Logger.getLogger("ServerEnchants");

    public EnchanterRepository(Database database) {
        this.database = database;
    }

    // ==================== PROGRESSION ====================

    public PlayerProgression getProgression(UUID playerUuid) {
        try {
            ResultSet rs =
                    database.executeQuery(
                            "SELECT * FROM enchanter_progression WHERE player_uuid = ?",
                            playerUuid.toString());
            if (rs.next()) {
                return new PlayerProgression(
                        playerUuid,
                        rs.getInt("level"),
                        rs.getInt("experience"),
                        rs.getInt("total_fragments"),
                        rs.getInt("lifetime_games_played"),
                        rs.getInt("lifetime_games_won"));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load progression for " + playerUuid, e);
        }
        return new PlayerProgression(playerUuid);
    }

    public CompletableFuture<Void> saveProgression(PlayerProgression prog) {
        return database.executeUpdateAsync(
                        "INSERT INTO enchanter_progression (player_uuid, level, experience, total_fragments, lifetime_games_played, lifetime_games_won) "
                                + "VALUES (?, ?, ?, ?, ?, ?) "
                                + "ON DUPLICATE KEY UPDATE level = ?, experience = ?, total_fragments = ?, "
                                + "lifetime_games_played = ?, lifetime_games_won = ?",
                        prog.getPlayerUuid().toString(),
                        prog.getLevel(),
                        prog.getExperience(),
                        prog.getTotalFragments(),
                        prog.getLifetimeGamesPlayed(),
                        prog.getLifetimeGamesWon(),
                        prog.getLevel(),
                        prog.getExperience(),
                        prog.getTotalFragments(),
                        prog.getLifetimeGamesPlayed(),
                        prog.getLifetimeGamesWon())
                .thenApply(i -> null);
    }

    // ==================== UNLOCKS ====================

    public Map<String, Integer> getUnlocks(UUID playerUuid) {
        Map<String, Integer> unlocks = new HashMap<>();
        try {
            ResultSet rs =
                    database.executeQuery(
                            "SELECT enchantment_id, current_level FROM enchanter_unlocks WHERE player_uuid = ?",
                            playerUuid.toString());
            while (rs.next()) {
                unlocks.put(rs.getString("enchantment_id"), rs.getInt("current_level"));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load unlocks for " + playerUuid, e);
        }
        return unlocks;
    }

    public CompletableFuture<Void> saveUnlock(UUID playerUuid, String enchantmentId, int level) {
        return database.executeUpdateAsync(
                        "INSERT INTO enchanter_unlocks (player_uuid, enchantment_id, current_level) "
                                + "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE current_level = ?",
                        playerUuid.toString(),
                        enchantmentId,
                        level,
                        level)
                .thenApply(i -> null);
    }

    public int countUnlocksByTier(UUID playerUuid, EnchantTier tier) {
        // This requires knowing which enchantments belong to which tier,
        // so we'll count from the unlocks map using the registry
        return 0; // Overridden in ProgressionManager with registry context
    }

    // ==================== DAILY ATTEMPTS ====================

    public boolean hasFreeAttempt(UUID playerUuid) {
        try {
            ResultSet rs =
                    database.executeQuery(
                            "SELECT last_free_attempt, attempts_remaining FROM enchanter_daily_attempts WHERE player_uuid = ?",
                            playerUuid.toString());
            if (rs.next()) {
                java.sql.Date lastAttempt = rs.getDate("last_free_attempt");
                int remaining = rs.getInt("attempts_remaining");
                if (lastAttempt == null) {
                    return true;
                }
                java.time.LocalDate today = java.time.LocalDate.now();
                java.time.LocalDate lastDate = lastAttempt.toLocalDate();
                if (!lastDate.equals(today)) {
                    return true;
                }
                return remaining > 0;
            }
            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to check daily attempt for " + playerUuid, e);
            return false;
        }
    }

    public CompletableFuture<Void> useFreeAttempt(UUID playerUuid) {
        return database.executeUpdateAsync(
                        "INSERT INTO enchanter_daily_attempts (player_uuid, last_free_attempt, attempts_remaining) "
                                + "VALUES (?, CURDATE(), 0) "
                                + "ON DUPLICATE KEY UPDATE last_free_attempt = CURDATE(), attempts_remaining = 0",
                        playerUuid.toString())
                .thenApply(i -> null);
    }

    // ==================== GAME STATS ====================

    public CompletableFuture<Void> saveGameResult(GameResult result) {
        return database.executeUpdateAsync(
                        "INSERT INTO enchanter_game_stats (player_uuid, game_type, tier, won, score, fragments_earned) "
                                + "VALUES (?, ?, ?, ?, ?, ?)",
                        result.getPlayerUuid().toString(),
                        result.getGameType().name(),
                        result.getTier().name(),
                        result.isWon(),
                        result.getScore(),
                        result.getFragmentsEarned())
                .thenApply(i -> null);
    }
}
