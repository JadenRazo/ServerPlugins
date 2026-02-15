package net.serverplugins.admin.freeze;

import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;

public class FreezeListener implements Listener {

    private final ServerAdmin plugin;

    public FreezeListener(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        if (plugin.getFreezeManager() == null) return;

        Player player = event.getPlayer();
        if (!plugin.getFreezeManager().isFrozen(player)) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();

        // Allow head rotation (yaw/pitch changes)
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            event.setTo(
                    new Location(
                            from.getWorld(),
                            from.getX(),
                            from.getY(),
                            from.getZ(),
                            to.getYaw(),
                            to.getPitch()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (plugin.getFreezeManager() == null) return;

        Player player = event.getPlayer();
        if (plugin.getFreezeManager().isFrozen(player)) {
            event.setCancelled(true);
            TextUtil.sendError(player, "You cannot chat while frozen.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (plugin.getFreezeManager() == null) return;

        Player player = event.getPlayer();
        if (!plugin.getFreezeManager().isFrozen(player)) {
            return;
        }

        String command = event.getMessage();
        if (!plugin.getFreezeManager().isCommandAllowed(command)) {
            event.setCancelled(true);
            TextUtil.sendError(player, "You cannot use commands while frozen.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (plugin.getFreezeManager() == null) return;

        Player player = event.getPlayer();
        if (plugin.getFreezeManager().isFrozen(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (plugin.getFreezeManager() == null) return;

        Player player = event.getPlayer();
        if (plugin.getFreezeManager().isFrozen(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (plugin.getFreezeManager() == null) return;

        if (event.getWhoClicked() instanceof Player player) {
            if (plugin.getFreezeManager().isFrozen(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (plugin.getFreezeManager() == null) return;

        if (event.getPlayer() instanceof Player player) {
            if (plugin.getFreezeManager().isFrozen(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDropItem(PlayerDropItemEvent event) {
        if (plugin.getFreezeManager() == null) return;

        Player player = event.getPlayer();
        if (plugin.getFreezeManager().isFrozen(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickupItem(PlayerAttemptPickupItemEvent event) {
        if (plugin.getFreezeManager() == null) return;

        Player player = event.getPlayer();
        if (plugin.getFreezeManager().isFrozen(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (plugin.getFreezeManager() == null) return;

        if (event.getEntity() instanceof Player player) {
            if (plugin.getFreezeManager().isFrozen(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDealDamage(EntityDamageByEntityEvent event) {
        if (plugin.getFreezeManager() == null) return;

        if (event.getDamager() instanceof Player player) {
            if (plugin.getFreezeManager().isFrozen(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.getFreezeManager() != null) {
            plugin.getFreezeManager().handleDisconnect(event.getPlayer());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.getFreezeManager() != null) {
            plugin.getFreezeManager().handleJoin(event.getPlayer());
        }
    }
}
