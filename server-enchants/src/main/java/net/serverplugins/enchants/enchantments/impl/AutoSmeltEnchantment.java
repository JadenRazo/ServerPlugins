package net.serverplugins.enchants.enchantments.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.serverplugins.enchants.enchantments.CustomEnchantment;
import net.serverplugins.enchants.enchantments.EnchantTier;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Auto-Smelt enchantment automatically smelts ore drops. Chance increases with level: 50%, 65%,
 * 80%, 90%, 100%
 */
public class AutoSmeltEnchantment extends CustomEnchantment {

    // Chance to smelt at each level (index 0 = level 1)
    private static final int[] SMELT_CHANCES = {50, 65, 80, 90, 100};

    // Map of raw ore materials to their smelted products
    private static final Map<Material, Material> SMELT_MAP = new HashMap<>();

    static {
        // Vanilla ores
        SMELT_MAP.put(Material.IRON_ORE, Material.IRON_INGOT);
        SMELT_MAP.put(Material.DEEPSLATE_IRON_ORE, Material.IRON_INGOT);
        SMELT_MAP.put(Material.GOLD_ORE, Material.GOLD_INGOT);
        SMELT_MAP.put(Material.DEEPSLATE_GOLD_ORE, Material.GOLD_INGOT);
        SMELT_MAP.put(Material.COPPER_ORE, Material.COPPER_INGOT);
        SMELT_MAP.put(Material.DEEPSLATE_COPPER_ORE, Material.COPPER_INGOT);
        SMELT_MAP.put(Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP);

        // Raw ores (1.18+)
        SMELT_MAP.put(Material.RAW_IRON, Material.IRON_INGOT);
        SMELT_MAP.put(Material.RAW_GOLD, Material.GOLD_INGOT);
        SMELT_MAP.put(Material.RAW_COPPER, Material.COPPER_INGOT);

        // Additional smeltable blocks
        SMELT_MAP.put(Material.COBBLESTONE, Material.STONE);
        SMELT_MAP.put(Material.COBBLED_DEEPSLATE, Material.DEEPSLATE);
        SMELT_MAP.put(Material.SAND, Material.GLASS);
        SMELT_MAP.put(Material.CLAY_BALL, Material.BRICK);
        SMELT_MAP.put(Material.NETHERRACK, Material.NETHER_BRICK);
        SMELT_MAP.put(Material.CACTUS, Material.GREEN_DYE);
    }

    public AutoSmeltEnchantment() {
        super(
                "auto_smelt",
                "Auto-Smelt",
                EnchantTier.COMMON,
                5,
                Material.FURNACE,
                "Automatically smelts ore drops");
    }

    @Override
    public boolean canApplyTo(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        Material type = item.getType();
        return type == Material.DIAMOND_PICKAXE
                || type == Material.NETHERITE_PICKAXE
                || type == Material.IRON_PICKAXE
                || type == Material.GOLDEN_PICKAXE
                || type == Material.STONE_PICKAXE
                || type == Material.WOODEN_PICKAXE;
    }

    @Override
    public void onBlockBreak(Player player, BlockBreakEvent event, int level) {
        if (event.isCancelled()) {
            return;
        }

        // Get the smelt chance for this level (clamp to valid index)
        int levelIndex = Math.min(level - 1, SMELT_CHANCES.length - 1);
        int smeltChance = SMELT_CHANCES[levelIndex];

        // Roll chance
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll >= smeltChance) {
            return; // Failed chance check
        }

        Collection<ItemStack> drops =
                event.getBlock().getDrops(player.getInventory().getItemInMainHand());
        if (drops.isEmpty()) {
            return;
        }

        boolean modified = false;
        for (ItemStack drop : drops) {
            Material smeltedMaterial = SMELT_MAP.get(drop.getType());
            if (smeltedMaterial != null) {
                // Replace the drop with smelted version
                drop.setType(smeltedMaterial);
                modified = true;
            }
        }

        // If we modified any drops, clear original drops and add smelted ones
        if (modified) {
            event.setDropItems(false);
            for (ItemStack drop : drops) {
                event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), drop);
            }
        }
    }

    /**
     * Check if a material can be auto-smelted.
     *
     * @param material The material to check
     * @return true if it has a smelted variant
     */
    public static boolean isSmeltable(Material material) {
        return SMELT_MAP.containsKey(material);
    }

    /**
     * Get the smelted version of a material.
     *
     * @param material The raw material
     * @return The smelted material, or null if not smeltable
     */
    public static Material getSmeltedVersion(Material material) {
        return SMELT_MAP.get(material);
    }
}
