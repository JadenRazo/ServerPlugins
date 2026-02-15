package net.serverplugins.enchants.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.serverplugins.enchants.enchantments.CustomEnchantment;
import net.serverplugins.enchants.managers.EnchantmentRegistry;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Utility class for reading and writing custom enchantment data to items. Uses PDC
 * (PersistentDataContainer) for storage and manages lore display.
 */
public class EnchantmentUtils {

    // Zero-width space marker to identify enchantment lore lines
    private static final String ENCHANT_LORE_MARKER = "\u200B";

    /**
     * Get the NamespacedKey for a custom enchantment.
     *
     * @param enchantId The enchantment ID
     * @return The NamespacedKey for PDC storage
     */
    public static NamespacedKey getKey(String enchantId) {
        return new NamespacedKey("server_enchants", "enchant_" + enchantId);
    }

    /**
     * Read the level of a custom enchantment from an item.
     *
     * @param item The item to read from
     * @param enchantId The enchantment ID
     * @return The enchantment level, or 0 if not present
     */
    public static int readLevel(ItemStack item, String enchantId) {
        if (item == null || item.getType() == Material.AIR) {
            return 0;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey key = getKey(enchantId);

        return pdc.getOrDefault(key, PersistentDataType.INTEGER, 0);
    }

    /**
     * Write the level of a custom enchantment to an item.
     *
     * @param item The item to write to
     * @param enchantId The enchantment ID
     * @param level The enchantment level
     */
    public static void writeLevel(ItemStack item, String enchantId, int level) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey key = getKey(enchantId);

        if (level > 0) {
            pdc.set(key, PersistentDataType.INTEGER, level);
        } else {
            pdc.remove(key);
        }

        item.setItemMeta(meta);
    }

    /**
     * Remove a custom enchantment from an item.
     *
     * @param item The item to modify
     * @param enchantId The enchantment ID to remove
     */
    public static void removeKey(ItemStack item, String enchantId) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey key = getKey(enchantId);
        pdc.remove(key);

        item.setItemMeta(meta);
    }

    /**
     * Build lore lines for custom enchantments.
     *
     * @param enchants Map of enchantment IDs to levels
     * @param registry The enchantment registry
     * @return List of formatted lore strings
     */
    public static List<String> buildEnchantLore(
            Map<String, Integer> enchants, EnchantmentRegistry registry) {
        List<String> lore = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
            CustomEnchantment enchant = registry.getById(entry.getKey());
            if (enchant == null) {
                continue;
            }

            int level = entry.getValue();
            String levelDisplay = toRoman(level);

            // Add marker for identification + colored enchantment line
            String line =
                    ENCHANT_LORE_MARKER
                            + enchant.getTier().getColor()
                            + enchant.getDisplayName()
                            + " "
                            + levelDisplay;
            lore.add(line);
        }

        return lore;
    }

    /**
     * Update an item's lore with custom enchantment lines. Preserves existing non-enchantment lore.
     *
     * @param item The item to update
     * @param enchants Map of enchantment IDs to levels
     * @param registry The enchantment registry
     */
    public static void updateItemLore(
            ItemStack item, Map<String, Integer> enchants, EnchantmentRegistry registry) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        // Get existing lore and remove old enchantment lines
        List<String> existingLore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        List<String> newLore = new ArrayList<>();

        // Keep non-enchantment lore
        for (String line : existingLore) {
            if (!line.startsWith(ENCHANT_LORE_MARKER)) {
                newLore.add(line);
            }
        }

        // Add enchantment lore at the beginning
        List<String> enchantLore = buildEnchantLore(enchants, registry);
        enchantLore.addAll(newLore);

        meta.setLore(enchantLore);
        item.setItemMeta(meta);
    }

    /**
     * Get all custom enchantments on an item.
     *
     * @param item The item to read from
     * @param registry The enchantment registry
     * @return Map of enchantment IDs to levels
     */
    public static Map<String, Integer> getEnchantments(
            ItemStack item, EnchantmentRegistry registry) {
        Map<String, Integer> enchants = new HashMap<>();

        if (item == null || item.getType() == Material.AIR) {
            return enchants;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return enchants;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Check all registered enchantments
        for (CustomEnchantment enchant : registry.getAll()) {
            NamespacedKey key = getKey(enchant.getId());
            Integer level = pdc.get(key, PersistentDataType.INTEGER);

            if (level != null && level > 0) {
                enchants.put(enchant.getId(), level);
            }
        }

        return enchants;
    }

    /**
     * Convert a number to Roman numerals (1-5).
     *
     * @param number The number to convert
     * @return Roman numeral string
     */
    public static String toRoman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(number);
        };
    }

    /**
     * Check if an item has a specific custom enchantment.
     *
     * @param item The item to check
     * @param enchantId The enchantment ID
     * @return true if the item has the enchantment
     */
    public static boolean hasEnchantment(ItemStack item, String enchantId) {
        return readLevel(item, enchantId) > 0;
    }

    /**
     * Get the marker string used to identify enchantment lore lines.
     *
     * @return The enchantment lore marker
     */
    public static String getEnchantLoreMarker() {
        return ENCHANT_LORE_MARKER;
    }
}
