package net.serverplugins.admin.listeners;

import net.serverplugins.admin.ServerAdmin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class VanishListener implements Listener {

    private final ServerAdmin plugin;

    public VanishListener(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onEntityTarget(EntityTargetEvent event) {
        Entity target = event.getTarget();
        if (!(target instanceof Player player)) {
            return;
        }

        // Cancel mob targeting for vanished players
        if (plugin.getVanishManager().isVanished(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }

        if (!plugin.getVanishManager().isVanished(player)) {
            return;
        }

        // Cancel damage if not allowed to attack
        if (!plugin.getAdminConfig().allowAttack()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getVanishManager().isVanished(player)) {
            return;
        }

        // Cancel interaction if not allowed
        if (!plugin.getAdminConfig().allowInteraction()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // Vanished players don't pick up items
        if (plugin.getVanishManager().isVanished(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onXPPickup(org.bukkit.event.player.PlayerExpChangeEvent event) {
        Player player = event.getPlayer();

        // Vanished players don't pick up XP
        if (plugin.getVanishManager().isVanished(player)) {
            event.setAmount(0);
        }
    }
}
