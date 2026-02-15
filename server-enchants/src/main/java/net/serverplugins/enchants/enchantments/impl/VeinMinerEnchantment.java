package net.serverplugins.enchants.enchantments.impl;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import net.serverplugins.enchants.enchantments.CustomEnchantment;
import net.serverplugins.enchants.enchantments.EnchantTier;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Vein Miner enchantment breaks connected blocks of the same type. Must be sneaking to activate.
 * Max blocks increases with level.
 */
public class VeinMinerEnchantment extends CustomEnchantment {

    // Max blocks to break at each level (index 0 = level 1)
    private static final int[] MAX_BLOCKS = {5, 10, 20, 35, 50};

    // Offsets for checking adjacent blocks (6 directions + diagonals)
    private static final int[][] OFFSETS = {
        {1, 0, 0},
        {-1, 0, 0}, // X axis
        {0, 1, 0},
        {0, -1, 0}, // Y axis
        {0, 0, 1},
        {0, 0, -1}, // Z axis
        // Diagonal connections for better vein detection
        {1, 1, 0},
        {1, -1, 0},
        {-1, 1, 0},
        {-1, -1, 0},
        {1, 0, 1},
        {1, 0, -1},
        {-1, 0, 1},
        {-1, 0, -1},
        {0, 1, 1},
        {0, 1, -1},
        {0, -1, 1},
        {0, -1, -1}
    };

    public VeinMinerEnchantment() {
        super(
                "vein_miner",
                "Vein Miner",
                EnchantTier.UNCOMMON,
                5,
                Material.IRON_PICKAXE,
                "Mine connected blocks while sneaking");
    }

    @Override
    public boolean canApplyTo(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        Material type = item.getType();

        // Check if it's a pickaxe
        if (type == Material.DIAMOND_PICKAXE
                || type == Material.NETHERITE_PICKAXE
                || type == Material.IRON_PICKAXE
                || type == Material.GOLDEN_PICKAXE
                || type == Material.STONE_PICKAXE
                || type == Material.WOODEN_PICKAXE) {
            return true;
        }

        // Check if it's an axe
        return type == Material.DIAMOND_AXE
                || type == Material.NETHERITE_AXE
                || type == Material.IRON_AXE
                || type == Material.GOLDEN_AXE
                || type == Material.STONE_AXE
                || type == Material.WOODEN_AXE;
    }

    @Override
    public void onBlockBreak(Player player, BlockBreakEvent event, int level) {
        if (event.isCancelled()) {
            return;
        }

        // Only activate when sneaking
        if (!player.isSneaking()) {
            return;
        }

        // Don't activate in creative mode (already instant break)
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        Block origin = event.getBlock();
        Material targetType = origin.getType();

        // Get max blocks for this level
        int levelIndex = Math.min(level - 1, MAX_BLOCKS.length - 1);
        int maxBlocks = MAX_BLOCKS[levelIndex];

        // Find connected blocks using BFS
        Set<Block> connectedBlocks = findConnectedBlocks(origin, targetType, maxBlocks);

        if (connectedBlocks.isEmpty()) {
            return; // No connected blocks found
        }

        ItemStack tool = player.getInventory().getItemInMainHand();

        // Break all connected blocks
        for (Block block : connectedBlocks) {
            // Check if tool has enough durability
            if (!hasEnoughDurability(tool)) {
                break;
            }

            // Break the block and drop items
            block.breakNaturally(tool);

            // Damage the tool (respects Unbreaking enchantment)
            damageTool(tool, player);
        }
    }

    /**
     * Find all connected blocks of the same type using BFS flood-fill.
     *
     * @param origin The starting block
     * @param targetType The material to match
     * @param maxBlocks Maximum blocks to find
     * @return Set of connected blocks (excluding origin)
     */
    private Set<Block> findConnectedBlocks(Block origin, Material targetType, int maxBlocks) {
        Set<Block> found = new HashSet<>();
        Set<Block> visited = new HashSet<>();
        Queue<Block> queue = new ArrayDeque<>();

        queue.add(origin);
        visited.add(origin);

        while (!queue.isEmpty() && found.size() < maxBlocks) {
            Block current = queue.poll();

            // Check all adjacent blocks
            for (int[] offset : OFFSETS) {
                Block neighbor = current.getRelative(offset[0], offset[1], offset[2]);

                // Skip if already visited or wrong type
                if (visited.contains(neighbor) || neighbor.getType() != targetType) {
                    continue;
                }

                visited.add(neighbor);

                // Don't add the origin block to found set
                if (!neighbor.equals(origin)) {
                    found.add(neighbor);

                    // Stop if we've reached the limit
                    if (found.size() >= maxBlocks) {
                        break;
                    }
                }

                queue.add(neighbor);
            }
        }

        return found;
    }

    /**
     * Check if the tool has enough durability to continue breaking blocks.
     *
     * @param tool The tool to check
     * @return true if the tool can break more blocks
     */
    private boolean hasEnoughDurability(ItemStack tool) {
        if (tool == null || tool.getType() == Material.AIR) {
            return false;
        }

        ItemMeta meta = tool.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return true; // Not a damageable item
        }

        // Check if tool is about to break (leave 1 durability)
        int maxDurability = tool.getType().getMaxDurability();
        int currentDamage = damageable.getDamage();

        return currentDamage < maxDurability - 1;
    }

    /**
     * Damage the tool, respecting Unbreaking enchantment.
     *
     * @param tool The tool to damage
     * @param player The player using the tool
     */
    private void damageTool(ItemStack tool, Player player) {
        if (tool == null || tool.getType() == Material.AIR) {
            return;
        }

        ItemMeta meta = tool.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }

        // Check Unbreaking enchantment
        int unbreakingLevel = meta.getEnchantLevel(Enchantment.UNBREAKING);
        if (unbreakingLevel > 0) {
            // Unbreaking gives a chance to not use durability
            // Formula: (100 / (level + 1))% chance to reduce durability
            double chance = 100.0 / (unbreakingLevel + 1);
            if (Math.random() * 100 >= chance) {
                return; // Durability not reduced
            }
        }

        // Apply damage
        int currentDamage = damageable.getDamage();
        int maxDurability = tool.getType().getMaxDurability();

        if (currentDamage < maxDurability - 1) {
            damageable.setDamage(currentDamage + 1);
            tool.setItemMeta(meta);
        } else {
            // Tool is about to break
            tool.setAmount(0);
            player.getInventory().setItemInMainHand(null);
        }
    }
}
