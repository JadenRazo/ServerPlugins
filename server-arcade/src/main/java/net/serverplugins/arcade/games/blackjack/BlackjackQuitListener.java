package net.serverplugins.arcade.games.blackjack;

import java.util.UUID;
import net.serverplugins.arcade.ServerArcade;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener to handle Blackjack game state cleanup when players disconnect.
 *
 * <p>SECURITY: Prevents memory leaks from abandoned games in the activeGames HashMap. Without this
 * cleanup: 1. Memory leak - Game objects are never removed from map 2. Player stuck state - Players
 * who rejoin cannot start new games 3. PvP blocking - Opponents in PvP games would be stuck waiting
 *
 * <p>This listener ensures all active games are properly cleaned up on player disconnect.
 */
public class BlackjackQuitListener implements Listener {

    private final ServerArcade plugin;
    private final BlackjackManager blackjackManager;

    public BlackjackQuitListener(ServerArcade plugin) {
        this.plugin = plugin;
        this.blackjackManager = plugin.getBlackjackManager();
    }

    /**
     * Handle player quit event - cleanup active blackjack games. Priority: MONITOR to ensure we
     * cleanup after other plugins process the quit.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        // Check if player has an active game
        if (!blackjackManager.hasActiveGame(playerId)) {
            return;
        }

        // Retrieve game before removal for logging
        BlackjackGame game = blackjackManager.getGame(playerId);

        // Remove game from active games map
        blackjackManager.cancelGame(playerId);

        // Log cleanup for audit trail
        plugin.getLogger()
                .info(
                        String.format(
                                "[Blackjack] Cleaned up abandoned game for player %s (bet: $%.2f) - player disconnected",
                                event.getPlayer().getName(), game != null ? game.getBet() : 0.0));

        // Note: In a PvP blackjack implementation, you would need to:
        // 1. Identify if this was a PvP game
        // 2. Notify the opponent they can start a new game
        // 3. Optionally refund the opponent's bet or treat as forfeit win
        // 4. Clean up both players' game states
        //
        // Example PvP cleanup (if implemented):
        // if (game.isPvP()) {
        //     UUID opponentId = game.getOpponentId();
        //     Player opponent = Bukkit.getPlayer(opponentId);
        //     if (opponent != null) {
        //         opponent.sendMessage(ChatColor.YELLOW + "Your opponent disconnected. You can
        // start a new game.");
        //         // Optionally refund or grant win
        //     }
        //     blackjackManager.cancelGame(opponentId);
        // }
    }
}
