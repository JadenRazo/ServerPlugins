package net.serverplugins.enchants.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.serverplugins.enchants.ServerEnchants;
import net.serverplugins.enchants.enchantments.EnchantTier;
import net.serverplugins.enchants.games.EnchantGame;
import net.serverplugins.enchants.games.GameType;
import net.serverplugins.enchants.games.impl.AlchemyGame;
import net.serverplugins.enchants.games.impl.DecryptionGame;
import net.serverplugins.enchants.games.impl.ForgeGame;
import net.serverplugins.enchants.games.impl.MemoryGame;
import org.bukkit.entity.Player;

/**
 * Manages active game sessions for players. Ensures only one game per player at a time and handles
 * session lifecycle.
 */
public class GameSessionManager {

    private final ServerEnchants plugin;
    private final Map<UUID, EnchantGame> activeSessions;

    public GameSessionManager(ServerEnchants plugin) {
        this.plugin = plugin;
        this.activeSessions = new HashMap<>();
    }

    /**
     * Start a new game session for a player
     *
     * @param player the player starting the game
     * @param gameType the type of game to start
     * @param tier the difficulty tier
     * @return the created game instance
     */
    public EnchantGame startGame(Player player, GameType gameType, EnchantTier tier) {
        // End any existing game for this player
        if (hasActiveGame(player)) {
            endGame(player);
        }

        // Create the appropriate game instance
        EnchantGame game = createGame(gameType, player, tier);

        // Set up game end callback to clean up the session
        game.setOnGameEnd(
                result -> {
                    // Remove from active sessions after game ends
                    activeSessions.remove(player.getUniqueId());
                });

        // Store and open the game
        activeSessions.put(player.getUniqueId(), game);
        game.open(player);

        return game;
    }

    /** Create a game instance based on type */
    private EnchantGame createGame(GameType gameType, Player player, EnchantTier tier) {
        return switch (gameType) {
            case MEMORY -> new MemoryGame(plugin, player, tier);
            case FORGE -> new ForgeGame(plugin, player, tier);
            case ALCHEMY -> new AlchemyGame(plugin, player, tier);
            case DECRYPTION -> new DecryptionGame(plugin, player, tier);
        };
    }

    /**
     * End a player's active game session
     *
     * @param player the player whose game to end
     */
    public void endGame(Player player) {
        EnchantGame game = activeSessions.remove(player.getUniqueId());
        if (game != null && !game.isGameOver()) {
            game.endGame(false); // Count as loss if manually ended
            game.cancelTimer();
        }
    }

    /**
     * Get a player's active game
     *
     * @param player the player
     * @return the active game, or null if none
     */
    public EnchantGame getActiveGame(Player player) {
        return activeSessions.get(player.getUniqueId());
    }

    /**
     * Check if a player has an active game
     *
     * @param player the player
     * @return true if the player has an active game
     */
    public boolean hasActiveGame(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    /** Clean up all active game sessions. Called on plugin disable. */
    public void cleanup() {
        // End all active games
        for (EnchantGame game : activeSessions.values()) {
            if (!game.isGameOver()) {
                game.cancelTimer();
            }
        }
        activeSessions.clear();
    }

    /**
     * Get the number of active game sessions
     *
     * @return number of active sessions
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }
}
