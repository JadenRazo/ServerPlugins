package net.serverplugins.afk.listeners;

import java.util.UUID;
import net.serverplugins.afk.ServerAFK;
import net.serverplugins.afk.managers.GlobalAfkManager;
import net.serverplugins.afk.models.ActivityRecord;
import net.serverplugins.afk.models.PlayerAfkSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;

/**
 * Listens for player activity events and updates the GlobalAfkManager. Also logs activity for
 * anti-exploit pattern analysis.
 */
public class ActivityListener implements Listener {

    private final ServerAFK plugin;
    private final GlobalAfkManager afkManager;
    private final boolean logActivityForAnalysis;

    public ActivityListener(ServerAFK plugin, GlobalAfkManager afkManager) {
        this.plugin = plugin;
        this.afkManager = afkManager;
        this.logActivityForAnalysis = plugin.getAfkConfig().isAntiExploitEnabled();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only count significant movement (not just head rotation)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        updateActivity(player, ActivityRecord.ActivityType.MOVEMENT, event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        updateActivityAsync(player.getUniqueId(), ActivityRecord.ActivityType.CHAT);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().toLowerCase();

        // Ignore AFK commands as activity
        if (command.startsWith("/afk")
                || command.startsWith("/serverafk")
                || command.startsWith("/wa")) {
            return;
        }

        updateActivity(player, ActivityRecord.ActivityType.COMMAND, player.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        updateActivity(
                player, ActivityRecord.ActivityType.BLOCK_BREAK, event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        updateActivity(
                player, ActivityRecord.ActivityType.BLOCK_PLACE, event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        updateActivity(player, ActivityRecord.ActivityType.INTERACT, player.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            updateActivity(
                    player, ActivityRecord.ActivityType.INVENTORY_CLICK, player.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (event.isSneaking()) {
            updateActivity(player, ActivityRecord.ActivityType.SNEAK, player.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerToggleSprint(PlayerToggleSprintEvent event) {
        Player player = event.getPlayer();
        if (event.isSprinting()) {
            updateActivity(player, ActivityRecord.ActivityType.SPRINT, player.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            updateActivity(player, ActivityRecord.ActivityType.DAMAGE_TAKEN, player.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            updateActivity(player, ActivityRecord.ActivityType.DAMAGE_DEALT, player.getLocation());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        afkManager.updateActivity(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        afkManager.removePlayer(player.getUniqueId());
    }

    /** Updates activity for a player and logs it if anti-exploit is enabled. */
    private void updateActivity(
            Player player, ActivityRecord.ActivityType type, org.bukkit.Location location) {
        UUID playerId = player.getUniqueId();

        // Update global AFK manager
        afkManager.updateActivity(playerId);

        // Update session activity time
        PlayerAfkSession session = plugin.getPlayerTracker().getSession(playerId);
        if (session != null) {
            session.updateActivity();
        }

        // Log activity for anti-exploit analysis
        if (logActivityForAnalysis && plugin.getAntiExploitManager() != null) {
            ActivityRecord record = new ActivityRecord(playerId, type, location);
            plugin.getAntiExploitManager().logActivity(record);
        }
    }

    /** Updates activity for async events (like chat). */
    private void updateActivityAsync(UUID playerId, ActivityRecord.ActivityType type) {
        // Update global AFK manager
        afkManager.updateActivity(playerId);

        // Update session activity time
        PlayerAfkSession session = plugin.getPlayerTracker().getSession(playerId);
        if (session != null) {
            session.updateActivity();
        }
    }
}
