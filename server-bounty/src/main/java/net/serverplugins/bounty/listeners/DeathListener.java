package net.serverplugins.bounty.listeners;

import com.github.sirblobman.combatlogx.api.ICombatLogX;
import com.github.sirblobman.combatlogx.api.manager.ICombatManager;
import com.github.sirblobman.combatlogx.api.object.TagInformation;
import java.util.List;
import net.serverplugins.bounty.ServerBounty;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.Plugin;

public class DeathListener implements Listener {

    private final ServerBounty plugin;
    private ICombatLogX combatLogX;
    private boolean combatLogXAvailable = false;

    public DeathListener(ServerBounty plugin) {
        this.plugin = plugin;
        initCombatLogX();
    }

    private void initCombatLogX() {
        Plugin clxPlugin = Bukkit.getPluginManager().getPlugin("CombatLogX");
        if (clxPlugin != null && clxPlugin.isEnabled() && clxPlugin instanceof ICombatLogX) {
            this.combatLogX = (ICombatLogX) clxPlugin;
            this.combatLogXAvailable = true;
            plugin.getLogger().info("CombatLogX integration enabled for bounty kill detection");
        } else {
            plugin.getLogger().info("CombatLogX not found - using basic killer detection");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = getKiller(victim);

        // No killer found or self-kill
        if (killer == null || killer.equals(victim)) {
            return;
        }

        // Process the bounty kill
        plugin.getBountyManager().processBountyKill(killer, victim);
    }

    /**
     * Determines the killer of a player using multiple detection methods. Supports direct kills,
     * projectiles, tamed animals, and CombatLogX integration.
     *
     * @param victim The player who died
     * @return The killing player, or null if not a PvP death
     */
    private Player getKiller(Player victim) {
        // Method 1: Direct killer check
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            return killer;
        }

        // Method 2: Check last damage cause for player involvement
        EntityDamageEvent lastDamage = victim.getLastDamageCause();
        if (lastDamage instanceof EntityDamageByEntityEvent damageEvent) {
            Entity damager = damageEvent.getDamager();

            // Direct player damage
            if (damager instanceof Player attacker && !attacker.equals(victim)) {
                return attacker;
            }

            // Check for projectiles (arrows, tridents, etc.)
            if (damager instanceof Projectile projectile) {
                if (projectile.getShooter() instanceof Player shooter && !shooter.equals(victim)) {
                    return shooter;
                }
            }

            // Check for tamed animals (wolves, etc.)
            if (damager instanceof Tameable tameable) {
                if (tameable.getOwner() instanceof Player owner && !owner.equals(victim)) {
                    return owner;
                }
            }
        }

        // Method 3: CombatLogX integration for combat logging detection
        if (combatLogXAvailable && combatLogX != null) {
            try {
                ICombatManager combatManager = combatLogX.getCombatManager();
                if (combatManager.isInCombat(victim)) {
                    TagInformation tagInfo = combatManager.getTagInformation(victim);
                    if (tagInfo != null) {
                        List<Entity> enemies = tagInfo.getEnemies();
                        for (Entity enemy : enemies) {
                            if (enemy instanceof Player enemyPlayer
                                    && !enemyPlayer.equals(victim)) {
                                return enemyPlayer;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger()
                        .warning("Error checking CombatLogX combat status: " + e.getMessage());
            }
        }

        return null;
    }
}
