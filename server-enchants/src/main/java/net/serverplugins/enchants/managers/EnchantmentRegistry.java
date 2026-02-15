package net.serverplugins.enchants.managers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import net.serverplugins.enchants.enchantments.CustomEnchantment;
import net.serverplugins.enchants.enchantments.EnchantTier;
import net.serverplugins.enchants.enchantments.impl.AutoSmeltEnchantment;
import net.serverplugins.enchants.enchantments.impl.MagnetEnchantment;
import net.serverplugins.enchants.enchantments.impl.SoulboundEnchantment;
import net.serverplugins.enchants.enchantments.impl.VeinMinerEnchantment;

/**
 * Registry for all custom enchantments. Manages registration, lookup, and categorization of
 * enchantments.
 */
public class EnchantmentRegistry {

    private final Map<String, CustomEnchantment> enchantments = new HashMap<>();
    private final Logger logger;

    public EnchantmentRegistry(Logger logger) {
        this.logger = logger;
    }

    /**
     * Register a custom enchantment.
     *
     * @param enchantment The enchantment to register
     */
    public void register(CustomEnchantment enchantment) {
        if (enchantment == null) {
            logger.warning("Attempted to register null enchantment");
            return;
        }

        String id = enchantment.getId();
        if (enchantments.containsKey(id)) {
            logger.warning("Enchantment with ID '" + id + "' is already registered");
            return;
        }

        enchantments.put(id, enchantment);
        logger.info(
                "Registered enchantment: "
                        + enchantment.getDisplayName()
                        + " ("
                        + enchantment.getTier().getDisplayName()
                        + ")");
    }

    /**
     * Get an enchantment by its ID.
     *
     * @param id The enchantment ID
     * @return The enchantment, or null if not found
     */
    public CustomEnchantment getById(String id) {
        return enchantments.get(id);
    }

    /**
     * Get all registered enchantments.
     *
     * @return Collection of all enchantments
     */
    public Collection<CustomEnchantment> getAll() {
        return enchantments.values();
    }

    /**
     * Get all enchantments of a specific tier.
     *
     * @param tier The tier to filter by
     * @return List of enchantments in that tier
     */
    public List<CustomEnchantment> getByTier(EnchantTier tier) {
        List<CustomEnchantment> result = new ArrayList<>();

        for (CustomEnchantment enchant : enchantments.values()) {
            if (enchant.getTier() == tier) {
                result.add(enchant);
            }
        }

        return result;
    }

    /**
     * Get all enchantments that can be applied to a specific item.
     *
     * @param item The item to check
     * @return List of applicable enchantments
     */
    public List<CustomEnchantment> getApplicable(org.bukkit.inventory.ItemStack item) {
        List<CustomEnchantment> result = new ArrayList<>();

        for (CustomEnchantment enchant : enchantments.values()) {
            if (enchant.canApplyTo(item)) {
                result.add(enchant);
            }
        }

        return result;
    }

    /** Register all default enchantments. Called during plugin initialization. */
    public void registerDefaults() {
        logger.info("Registering default enchantments...");

        // Register all built-in enchantments
        register(new AutoSmeltEnchantment());
        register(new VeinMinerEnchantment());
        register(new MagnetEnchantment());
        register(new SoulboundEnchantment());

        logger.info("Registered " + enchantments.size() + " default enchantments");
    }

    /**
     * Get the number of registered enchantments.
     *
     * @return The count of registered enchantments
     */
    public int size() {
        return enchantments.size();
    }

    /**
     * Check if an enchantment is registered.
     *
     * @param id The enchantment ID
     * @return true if the enchantment is registered
     */
    public boolean isRegistered(String id) {
        return enchantments.containsKey(id);
    }

    /**
     * Unregister an enchantment.
     *
     * @param id The enchantment ID to unregister
     * @return The removed enchantment, or null if not found
     */
    public CustomEnchantment unregister(String id) {
        CustomEnchantment removed = enchantments.remove(id);
        if (removed != null) {
            logger.info("Unregistered enchantment: " + removed.getDisplayName());
        }
        return removed;
    }

    /** Clear all registered enchantments. */
    public void clear() {
        enchantments.clear();
        logger.info("Cleared all registered enchantments");
    }
}
