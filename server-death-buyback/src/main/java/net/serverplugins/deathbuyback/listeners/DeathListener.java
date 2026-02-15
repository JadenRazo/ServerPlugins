package net.serverplugins.deathbuyback.listeners;

import com.github.sirblobman.combatlogx.api.ICombatLogX;
import com.github.sirblobman.combatlogx.api.manager.ICombatManager;
import com.github.sirblobman.combatlogx.api.object.TagInformation;
import java.util.List;
import net.serverplugins.deathbuyback.ServerDeathBuyback;
import net.serverplugins.deathbuyback.models.DeathInventory;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public class DeathListener implements Listener {

    private final ServerDeathBuyback plugin;
    private ICombatLogX combatLogX;
    private boolean combatLogXAvailable = false;

    public DeathListener(ServerDeathBuyback plugin) {
        this.plugin = plugin;
        initCombatLogX();
    }

    private void initCombatLogX() {
        Plugin clxPlugin = Bukkit.getPluginManager().getPlugin("CombatLogX");
        if (clxPlugin != null && clxPlugin.isEnabled() && clxPlugin instanceof ICombatLogX) {
            this.combatLogX = (ICombatLogX) clxPlugin;
            this.combatLogXAvailable = true;
            plugin.getLogger()
                    .info(
                            "CombatLogX integration enabled - PvP deaths will not be eligible for buyback");
        } else {
            plugin.getLogger()
                    .info("CombatLogX not found - PvP detection will use basic killer check only");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // Check if this is a PvP-related death
        if (isPvPDeath(player)) {
            // PvP death - let items drop normally, no buyback
            plugin.getLogger()
                    .fine("Player " + player.getName() + " died in PvP - buyback disabled");
            return;
        }

        // Check if player has slots available
        int maxSlots = plugin.getDeathInventoryManager().getMaxSlots(player);
        if (maxSlots <= 0) {
            // Feature disabled for this player, let items drop normally
            return;
        }

        // Capture inventory before it's cleared
        ItemStack[] inventory = player.getInventory().getStorageContents().clone();
        ItemStack[] armor = player.getInventory().getArmorContents().clone();
        ItemStack offhand = player.getInventory().getItemInOffHand().clone();
        int xpLevels = player.getLevel();

        // Get death cause from death message
        String deathCause =
                event.getDeathMessage() != null ? event.getDeathMessage() : "Unknown cause";

        // Store the death inventory
        DeathInventory death =
                plugin.getDeathInventoryManager()
                        .storeDeathInventory(
                                player, inventory, armor, offhand, xpLevels, deathCause);

        if (death != null) {
            // Clear drops - we're handling inventory recovery
            event.getDrops().clear();
            event.setDroppedExp(0);
            event.setKeepInventory(false);
            event.setKeepLevel(false);

            // Notify player if enabled
            if (plugin.getDeathBuybackConfig().notifyOnDeath()) {
                String price = plugin.getPricingManager().formatPrice(death.getBuybackPrice());
                plugin.getDeathBuybackConfig().sendMessage(player, "death-stored");
            }
        }
    }

    /** Checks if the death was PvP-related (killed by player or was in combat with player) */
    private boolean isPvPDeath(Player player) {
        // Method 1: Direct killer check - player was killed by another player
        Player killer = player.getKiller();
        if (killer != null && !killer.equals(player)) {
            plugin.getLogger().fine("PvP death detected: killed by " + killer.getName());
            return true;
        }

        // Method 2: Check last damage cause for player involvement
        // This catches cases like being shot by arrow, hit by trident, etc.
        EntityDamageEvent lastDamage = player.getLastDamageCause();
        if (lastDamage instanceof EntityDamageByEntityEvent damageEvent) {
            Entity damager = damageEvent.getDamager();

            // Direct player damage
            if (damager instanceof Player attacker && !attacker.equals(player)) {
                plugin.getLogger()
                        .fine("PvP death detected: last damage from player " + attacker.getName());
                return true;
            }

            // Check for projectiles/pets owned by players
            if (damager instanceof org.bukkit.entity.Projectile projectile) {
                if (projectile.getShooter() instanceof Player shooter && !shooter.equals(player)) {
                    plugin.getLogger()
                            .fine(
                                    "PvP death detected: killed by projectile from "
                                            + shooter.getName());
                    return true;
                }
            }

            // Check for tamed animals (wolves, etc.)
            if (damager instanceof org.bukkit.entity.Tameable tameable) {
                if (tameable.getOwner() instanceof Player owner && !owner.equals(player)) {
                    plugin.getLogger()
                            .fine("PvP death detected: killed by pet owned by " + owner.getName());
                    return true;
                }
            }
        }

        // Method 3: CombatLogX integration - player was in PvP combat (combat logged)
        if (combatLogXAvailable && combatLogX != null) {
            try {
                ICombatManager combatManager = combatLogX.getCombatManager();
                if (combatManager.isInCombat(player)) {
                    TagInformation tagInfo = combatManager.getTagInformation(player);
                    if (tagInfo != null) {
                        // Get all enemies from the tag information
                        List<Entity> enemies = tagInfo.getEnemies();
                        for (Entity enemy : enemies) {
                            if (enemy instanceof Player enemyPlayer) {
                                plugin.getLogger()
                                        .fine(
                                                "PvP death detected: was in combat with "
                                                        + enemyPlayer.getName()
                                                        + " (CombatLogX)");
                                return true;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger()
                        .warning("Error checking CombatLogX combat status: " + e.getMessage());
            }
        }

        return false;
    }
}
