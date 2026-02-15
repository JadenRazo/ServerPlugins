package net.serverplugins.mobhealth.listeners;

import net.serverplugins.mobhealth.ServerMobHealth;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public class DamageListener implements Listener {

    private final ServerMobHealth plugin;

    public DamageListener(ServerMobHealth plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }

        if (entity instanceof Player) {
            return;
        }

        if (plugin.getMobHealthConfig().isExcluded(entity.getType())) {
            return;
        }

        if (plugin.getMobHealthConfig().isPlayerDamageOnly()) {
            Player damager = getPlayerDamager(event);
            if (damager == null) {
                return;
            }
            if (plugin.isDisabledFor(damager.getName())) {
                return;
            }
        }

        // One-tick delay: EntityDamageEvent fires before damage is applied
        plugin.getServer()
                .getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            if (entity.isValid() && !entity.isDead()) {
                                plugin.getManager().showHealthBar(entity);
                            }
                        });
    }

    private Player getPlayerDamager(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }

        if (byEntity.getDamager() instanceof Player player) {
            return player;
        }

        // Handle projectiles (arrows, tridents, etc.)
        if (byEntity.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            return player;
        }

        return null;
    }
}
