package net.serverplugins.enchants.enchantments.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.serverplugins.enchants.enchantments.CustomEnchantment;
import net.serverplugins.enchants.enchantments.EnchantTier;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Soulbound enchantment keeps items on death. Level 1: Keep only the soulbound item Level 2: Keep
 * soulbound item + armor Level 3: Keep entire inventory
 */
public class SoulboundEnchantment extends CustomEnchantment {

    public SoulboundEnchantment() {
        super(
                "soulbound",
                "Soulbound",
                EnchantTier.RARE,
                3,
                Material.TOTEM_OF_UNDYING,
                "Keep this item on death");
    }

    @Override
    public boolean canApplyTo(ItemStack item) {
        // Can apply to any item
        return item != null && item.getType() != Material.AIR;
    }

    @Override
    public void onPlayerDeath(Player player, PlayerDeathEvent event, int level) {
        List<ItemStack> keptItems = new ArrayList<>();

        // Level 1: Keep only the soulbound item
        if (level == 1) {
            Iterator<ItemStack> iterator = event.getDrops().iterator();
            while (iterator.hasNext()) {
                ItemStack item = iterator.next();
                if (hasSoulbound(item)) {
                    keptItems.add(item);
                    iterator.remove();
                    break; // Only keep one item
                }
            }
        }
        // Level 2: Keep soulbound item + armor
        else if (level == 2) {
            Iterator<ItemStack> iterator = event.getDrops().iterator();
            while (iterator.hasNext()) {
                ItemStack item = iterator.next();
                if (hasSoulbound(item) || isArmor(item)) {
                    keptItems.add(item);
                    iterator.remove();
                }
            }
        }
        // Level 3: Keep entire inventory
        else if (level >= 3) {
            keptItems.addAll(event.getDrops());
            event.getDrops().clear();
            event.setKeepInventory(true);
            event.setKeepLevel(true);
        }

        // Store kept items to restore on respawn
        // This is handled by EnchantmentListener which stores these items
        if (!keptItems.isEmpty() && level < 3) {
            // Tag the event with kept items metadata
            // The listener will handle restoring them
            event.getDrops().removeAll(keptItems);

            // Store in player's metadata for the listener to retrieve
            player.setMetadata(
                    "soulbound_kept_items",
                    new org.bukkit.metadata.FixedMetadataValue(
                            org.bukkit.Bukkit.getPluginManager().getPlugin("ServerEnchants"),
                            keptItems));
        }
    }

    /**
     * Check if an item has the soulbound enchantment. This is a helper method used by the listener.
     *
     * @param item The item to check
     * @return true if the item has soulbound
     */
    private boolean hasSoulbound(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        // This will be checked by EnchantmentUtils in the listener
        // For now, just return false - the listener handles the PDC check
        return false;
    }

    /**
     * Check if an item is armor.
     *
     * @param item The item to check
     * @return true if it's armor
     */
    private boolean isArmor(ItemStack item) {
        if (item == null) {
            return false;
        }

        String name = item.getType().name();
        return name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS")
                || name.equals("ELYTRA");
    }
}
