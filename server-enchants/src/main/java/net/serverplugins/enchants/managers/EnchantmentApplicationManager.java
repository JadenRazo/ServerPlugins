package net.serverplugins.enchants.managers;

import java.util.Map;
import net.serverplugins.enchants.enchantments.CustomEnchantment;
import net.serverplugins.enchants.utils.EnchantmentUtils;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Manager for applying, removing, and upgrading custom enchantments on items. Handles PDC storage
 * and lore updates.
 */
public class EnchantmentApplicationManager {

    private final EnchantmentRegistry registry;

    public EnchantmentApplicationManager(EnchantmentRegistry registry) {
        this.registry = registry;
    }

    /**
     * Apply a custom enchantment to an item.
     *
     * @param item The item to enchant
     * @param enchant The enchantment to apply
     * @param level The level to apply
     * @return true if successfully applied
     */
    public boolean applyEnchantment(ItemStack item, CustomEnchantment enchant, int level) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        if (!enchant.canApplyTo(item)) {
            return false;
        }

        if (level < 1 || level > enchant.getMaxLevel()) {
            return false;
        }

        // Write to PDC
        EnchantmentUtils.writeLevel(item, enchant.getId(), level);

        // Update lore
        Map<String, Integer> enchants = EnchantmentUtils.getEnchantments(item, registry);
        EnchantmentUtils.updateItemLore(item, enchants, registry);

        return true;
    }

    /**
     * Remove a custom enchantment from an item.
     *
     * @param item The item to modify
     * @param enchant The enchantment to remove
     * @return true if successfully removed
     */
    public boolean removeEnchantment(ItemStack item, CustomEnchantment enchant) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        if (!hasEnchantment(item, enchant)) {
            return false;
        }

        // Remove from PDC
        EnchantmentUtils.removeKey(item, enchant.getId());

        // Update lore
        Map<String, Integer> enchants = EnchantmentUtils.getEnchantments(item, registry);
        EnchantmentUtils.updateItemLore(item, enchants, registry);

        return true;
    }

    /**
     * Upgrade a custom enchantment on an item by one level.
     *
     * @param item The item to upgrade
     * @param enchant The enchantment to upgrade
     * @return true if successfully upgraded
     */
    public boolean upgradeEnchantment(ItemStack item, CustomEnchantment enchant) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        int currentLevel = getEnchantmentLevel(item, enchant);
        if (currentLevel >= enchant.getMaxLevel()) {
            return false; // Already at max level
        }

        int newLevel = currentLevel + 1;

        // Apply the upgraded level
        return applyEnchantment(item, enchant, newLevel);
    }

    /**
     * Get the level of a custom enchantment on an item.
     *
     * @param item The item to check
     * @param enchant The enchantment to check
     * @return The enchantment level, or 0 if not present
     */
    public int getEnchantmentLevel(ItemStack item, CustomEnchantment enchant) {
        if (item == null || item.getType() == Material.AIR) {
            return 0;
        }

        return EnchantmentUtils.readLevel(item, enchant.getId());
    }

    /**
     * Check if an item has a specific custom enchantment.
     *
     * @param item The item to check
     * @param enchant The enchantment to check
     * @return true if the item has the enchantment
     */
    public boolean hasEnchantment(ItemStack item, CustomEnchantment enchant) {
        return getEnchantmentLevel(item, enchant) > 0;
    }

    /**
     * Get all custom enchantments on an item.
     *
     * @param item The item to check
     * @return Map of enchantments to their levels
     */
    public Map<CustomEnchantment, Integer> getEnchantments(ItemStack item) {
        Map<CustomEnchantment, Integer> result = new java.util.HashMap<>();

        if (item == null || item.getType() == Material.AIR) {
            return result;
        }

        Map<String, Integer> enchantIds = EnchantmentUtils.getEnchantments(item, registry);

        for (Map.Entry<String, Integer> entry : enchantIds.entrySet()) {
            CustomEnchantment enchant = registry.getById(entry.getKey());
            if (enchant != null) {
                result.put(enchant, entry.getValue());
            }
        }

        return result;
    }

    /**
     * Set the level of a custom enchantment on an item. If level is 0, removes the enchantment.
     *
     * @param item The item to modify
     * @param enchant The enchantment to set
     * @param level The level to set (0 to remove)
     * @return true if successfully set
     */
    public boolean setEnchantmentLevel(ItemStack item, CustomEnchantment enchant, int level) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        if (level < 0 || level > enchant.getMaxLevel()) {
            return false;
        }

        if (level == 0) {
            return removeEnchantment(item, enchant);
        }

        return applyEnchantment(item, enchant, level);
    }

    /**
     * Check if an item can accept a specific enchantment.
     *
     * @param item The item to check
     * @param enchant The enchantment to check
     * @return true if the enchantment can be applied
     */
    public boolean canApply(ItemStack item, CustomEnchantment enchant) {
        return enchant.canApplyTo(item);
    }

    /**
     * Get the registry used by this manager.
     *
     * @return The enchantment registry
     */
    public EnchantmentRegistry getRegistry() {
        return registry;
    }
}
