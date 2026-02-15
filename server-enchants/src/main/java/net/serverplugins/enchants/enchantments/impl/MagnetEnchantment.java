package net.serverplugins.enchants.enchantments.impl;

import net.serverplugins.enchants.enchantments.CustomEnchantment;
import net.serverplugins.enchants.enchantments.EnchantTier;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Magnet enchantment attracts nearby item entities to the player. Range increases with level: 2, 4,
 * 6, 8, 10 blocks
 */
public class MagnetEnchantment extends CustomEnchantment {

    // Range in blocks at each level (index 0 = level 1)
    private static final int[] RANGE = {2, 4, 6, 8, 10};

    // Attraction speed multiplier
    private static final double PULL_STRENGTH = 0.3;

    public MagnetEnchantment() {
        super(
                "magnet",
                "Magnet",
                EnchantTier.UNCOMMON,
                5,
                Material.IRON_INGOT,
                "Attracts nearby items");
    }

    @Override
    public boolean canApplyTo(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        Material type = item.getType();

        // Can apply to any tool
        if (isTool(type)) {
            return true;
        }

        // Can apply to any armor piece
        return isArmor(type);
    }

    @Override
    public void onTick(Player player, int level) {
        // Get range for this level
        int levelIndex = Math.min(level - 1, RANGE.length - 1);
        double range = RANGE[levelIndex];

        // Find nearby item entities
        for (Entity entity : player.getNearbyEntities(range, range, range)) {
            if (!(entity instanceof Item itemEntity)) {
                continue;
            }

            // Skip items that can't be picked up yet
            if (itemEntity.getPickupDelay() > 0) {
                continue;
            }

            // Skip items that are already on the ground for too long (prevents lag)
            if (itemEntity.getTicksLived() < 10) {
                continue; // Let items settle first
            }

            // Calculate direction from item to player
            Vector direction =
                    player.getLocation().toVector().subtract(itemEntity.getLocation().toVector());
            double distance = direction.length();

            // Skip if already very close (let vanilla pickup handle it)
            if (distance < 1.0) {
                continue;
            }

            // Normalize and apply pull strength
            direction.normalize().multiply(PULL_STRENGTH);

            // Apply velocity to pull item toward player
            itemEntity.setVelocity(direction);
        }
    }

    /**
     * Check if the material is a tool.
     *
     * @param type The material to check
     * @return true if it's a tool
     */
    private boolean isTool(Material type) {
        String name = type.name();
        return name.endsWith("_PICKAXE")
                || name.endsWith("_AXE")
                || name.endsWith("_SHOVEL")
                || name.endsWith("_HOE")
                || name.endsWith("_SWORD");
    }

    /**
     * Check if the material is armor.
     *
     * @param type The material to check
     * @return true if it's armor
     */
    private boolean isArmor(Material type) {
        String name = type.name();
        return name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS");
    }
}
