package net.serverplugins.enchants.listeners;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.serverplugins.enchants.ServerEnchants;
import net.serverplugins.enchants.enchantments.CustomEnchantment;
import net.serverplugins.enchants.managers.EnchantmentRegistry;
import net.serverplugins.enchants.utils.EnchantmentUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;

/**
 * Listener for custom enchantment events. Handles BlockBreakEvent, PlayerDeathEvent, and
 * PlayerRespawnEvent.
 */
public class EnchantmentListener implements Listener {

    private final ServerEnchants plugin;
    private final EnchantmentRegistry registry;

    // Store soulbound items for players who died
    private final Map<UUID, List<ItemStack>> soulboundItems = new HashMap<>();

    public EnchantmentListener(ServerEnchants plugin) {
        this.plugin = plugin;
        this.registry = plugin.getEnchantmentRegistry();
    }

    /** Handle block break events for custom enchantments. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();

        if (tool == null || tool.getType().isAir()) {
            return;
        }

        // Get all enchantments on the tool
        Map<String, Integer> enchants = EnchantmentUtils.getEnchantments(tool, registry);

        // Call onBlockBreak for each enchantment
        for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
            CustomEnchantment enchant = registry.getById(entry.getKey());
            if (enchant == null) {
                continue;
            }

            try {
                enchant.onBlockBreak(player, event, entry.getValue());
            } catch (Exception e) {
                plugin.getLogger()
                        .warning(
                                "Error processing "
                                        + enchant.getDisplayName()
                                        + " enchantment: "
                                        + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /** Handle player death events for Soulbound enchantment. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        List<ItemStack> keptItems = new ArrayList<>();

        // Check all items in drops for soulbound
        for (ItemStack item : event.getDrops()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }

            // Get enchantments on this item
            Map<String, Integer> enchants = EnchantmentUtils.getEnchantments(item, registry);

            // Check for soulbound
            for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
                CustomEnchantment enchant = registry.getById(entry.getKey());
                if (enchant == null) {
                    continue;
                }

                if (enchant.getId().equals("soulbound")) {
                    int level = entry.getValue();

                    // Process soulbound effect
                    if (level == 1) {
                        // Keep only this item
                        keptItems.add(item.clone());
                        break; // Only keep one soulbound item at level 1
                    } else if (level == 2) {
                        // Keep this item + armor
                        keptItems.add(item.clone());
                        addArmorToKept(event, keptItems);
                    } else if (level >= 3) {
                        // Keep entire inventory
                        for (ItemStack drop : event.getDrops()) {
                            if (drop != null && !drop.getType().isAir()) {
                                keptItems.add(drop.clone());
                            }
                        }
                        event.setKeepInventory(true);
                        event.setKeepLevel(true);
                    }

                    // Call the enchantment's onPlayerDeath method
                    try {
                        enchant.onPlayerDeath(player, event, level);
                    } catch (Exception e) {
                        plugin.getLogger()
                                .warning(
                                        "Error processing "
                                                + enchant.getDisplayName()
                                                + " on death: "
                                                + e.getMessage());
                        e.printStackTrace();
                    }

                    break; // Only process one soulbound per item
                }
            }
        }

        // Store kept items for respawn
        if (!keptItems.isEmpty()) {
            soulboundItems.put(player.getUniqueId(), keptItems);

            // Remove kept items from drops
            event.getDrops().removeAll(keptItems);
        }

        // Check for metadata-based kept items (set by enchantment)
        if (player.hasMetadata("soulbound_kept_items")) {
            List<MetadataValue> metadata = player.getMetadata("soulbound_kept_items");
            if (!metadata.isEmpty()) {
                Object value = metadata.get(0).value();
                if (value instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<ItemStack> metaItems = (List<ItemStack>) value;
                    soulboundItems.put(player.getUniqueId(), metaItems);
                }
                player.removeMetadata("soulbound_kept_items", plugin);
            }
        }
    }

    /** Add armor items to the kept items list. */
    private void addArmorToKept(PlayerDeathEvent event, List<ItemStack> keptItems) {
        for (ItemStack item : event.getDrops()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }

            String name = item.getType().name();
            if (name.endsWith("_HELMET")
                    || name.endsWith("_CHESTPLATE")
                    || name.endsWith("_LEGGINGS")
                    || name.endsWith("_BOOTS")
                    || name.equals("ELYTRA")) {
                if (!keptItems.contains(item)) {
                    keptItems.add(item.clone());
                }
            }
        }
    }

    /** Restore soulbound items on player respawn. */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (!soulboundItems.containsKey(playerId)) {
            return;
        }

        List<ItemStack> items = soulboundItems.remove(playerId);

        // Delay restoration by 1 tick to ensure player is fully loaded
        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            for (ItemStack item : items) {
                                // Try to add to inventory, drop if full
                                HashMap<Integer, ItemStack> leftover =
                                        player.getInventory().addItem(item);
                                for (ItemStack extra : leftover.values()) {
                                    player.getWorld()
                                            .dropItemNaturally(player.getLocation(), extra);
                                }
                            }
                        },
                        1L);
    }

    /**
     * Get the map of stored soulbound items (for testing/debugging).
     *
     * @return The soulbound items map
     */
    public Map<UUID, List<ItemStack>> getSoulboundItems() {
        return soulboundItems;
    }
}
