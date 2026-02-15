package net.serverplugins.backpacks.listeners;

import net.serverplugins.backpacks.ServerBackpacks;
import net.serverplugins.backpacks.managers.BackpackManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

public class AutoRefillListener implements Listener {

    private final ServerBackpacks plugin;
    private final BackpackManager backpackManager;

    public AutoRefillListener(ServerBackpacks plugin) {
        this.plugin = plugin;
        this.backpackManager = plugin.getBackpackManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        // Check if auto-refill is enabled
        if (!plugin.getConfig().getBoolean("auto-refill.enabled", true)) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack itemInHand = event.getItemInHand();

        // Only process block items
        if (itemInHand == null || !itemInHand.getType().isBlock()) {
            return;
        }

        Material materialToRefill = itemInHand.getType();

        // Schedule delayed task (1 tick) to allow block placement to complete
        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            // Check if refill is needed (hand is empty or amount is 0)
                            ItemStack currentHand = player.getInventory().getItemInMainHand();
                            if (currentHand != null && currentHand.getAmount() > 0) {
                                return; // Still has items, no refill needed
                            }

                            // Scan inventory for backpacks
                            for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
                                ItemStack item = player.getInventory().getItem(slot);

                                // Check if item is a backpack
                                if (!backpackManager.isBackpack(item)) {
                                    continue;
                                }

                                // Load backpack contents
                                ItemStack[] contents = backpackManager.getBackpackContents(item);
                                if (contents == null) {
                                    continue;
                                }

                                // Search for matching material
                                for (int i = 0; i < contents.length; i++) {
                                    ItemStack backpackItem = contents[i];

                                    if (backpackItem != null
                                            && backpackItem.getType() == materialToRefill) {
                                        // Found matching item, calculate refill amount
                                        int refillAmount =
                                                Math.min(
                                                        backpackItem.getAmount(),
                                                        materialToRefill.getMaxStackSize());

                                        // Remove from backpack
                                        if (backpackItem.getAmount() <= refillAmount) {
                                            contents[i] = null;
                                        } else {
                                            backpackItem.setAmount(
                                                    backpackItem.getAmount() - refillAmount);
                                        }

                                        // Save updated backpack contents
                                        backpackManager.setBackpackContents(item, contents);

                                        // Update the backpack in player inventory (important for
                                        // persistence)
                                        player.getInventory().setItem(slot, item);

                                        // Place refill in player's hand
                                        ItemStack refill =
                                                new ItemStack(materialToRefill, refillAmount);
                                        player.getInventory().setItemInMainHand(refill);

                                        // Play sound feedback
                                        player.playSound(
                                                player.getLocation(),
                                                Sound.ENTITY_ITEM_PICKUP,
                                                0.5f,
                                                1.5f);

                                        return; // Stop after first successful refill
                                    }
                                }
                            }
                        },
                        1L);
    }
}
