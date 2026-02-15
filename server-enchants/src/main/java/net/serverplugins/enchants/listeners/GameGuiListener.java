package net.serverplugins.enchants.listeners;

import net.serverplugins.enchants.ServerEnchants;
import net.serverplugins.enchants.games.EnchantGame;
import net.serverplugins.enchants.managers.GameSessionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Handles GUI interactions for enchantment mini-games. Intercepts inventory clicks and delegates to
 * the appropriate game handler.
 */
public class GameGuiListener implements Listener {

    private final ServerEnchants plugin;
    private final GameSessionManager sessionManager;

    public GameGuiListener(ServerEnchants plugin, GameSessionManager sessionManager) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
    }

    /** Handle inventory clicks in game GUIs */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Check if player has an active game
        if (!sessionManager.hasActiveGame(player)) {
            return;
        }

        EnchantGame game = sessionManager.getActiveGame(player);

        // Check if the clicked inventory belongs to the game
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder != game) {
            return;
        }

        // Cancel the event to prevent default behavior
        event.setCancelled(true);

        // Ignore clicks outside the inventory or in player inventory
        if (event.getClickedInventory() == null
                || event.getClickedInventory() != event.getInventory()) {
            return;
        }

        // Delegate to the game's click handler
        int slot = event.getSlot();
        game.handleGameClick(player, slot, event.getClick());
    }

    /** Prevent item dragging in game GUIs */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Check if player has an active game
        if (!sessionManager.hasActiveGame(player)) {
            return;
        }

        EnchantGame game = sessionManager.getActiveGame(player);

        // Check if any of the dragged slots are in the game inventory
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder == game) {
            event.setCancelled(true);
        }
    }

    /** Clean up active games when a player quits */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (sessionManager.hasActiveGame(player)) {
            sessionManager.endGame(player);
        }
    }
}
