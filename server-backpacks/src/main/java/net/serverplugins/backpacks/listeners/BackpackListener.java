package net.serverplugins.backpacks.listeners;

import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.messages.PluginMessenger;
import net.serverplugins.backpacks.BackpacksConfig;
import net.serverplugins.backpacks.ServerBackpacks;
import net.serverplugins.backpacks.managers.BackpackManager;
import net.serverplugins.backpacks.utils.ItemNameFormatter;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class BackpackListener implements Listener {

    private final ServerBackpacks plugin;

    public BackpackListener(ServerBackpacks plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null) return;

        BackpackManager manager = plugin.getBackpackManager();

        if (manager.isBackpack(item)) {
            Player player = event.getPlayer();

            // Check if player is sneaking and feature is enabled - try to add item
            if (player.isSneaking() && plugin.getBackpacksConfig().isRightClickToAddEnabled()) {
                // Check creative mode
                if (player.getGameMode() == GameMode.CREATIVE
                        && !plugin.getBackpacksConfig().isAllowInCreative()) {
                    // In creative mode but feature disabled, just open backpack
                    event.setCancelled(true);
                    openBackpackIfAllowed(player, item, manager);
                    return;
                }

                // Try to add item from other hand
                if (tryAddItemToBackpack(player, item, manager)) {
                    event.setCancelled(true);
                    return;
                }
                // If adding failed or no item to add, fall through to open backpack
            }

            // Normal right-click (not sneaking) or feature disabled - open backpack
            event.setCancelled(true);
            openBackpackIfAllowed(player, item, manager);
        }
    }

    private void openBackpackIfAllowed(
            Player player, ItemStack backpackItem, BackpackManager manager) {
        // Check WorldGuard region permission
        if (plugin.getWorldGuardHandler() != null
                && plugin.getWorldGuardHandler().isInitialized()
                && !plugin.getWorldGuardHandler().canOpenBackpack(player)) {
            plugin.getBackpacksConfig().getMessenger().send(player, "region-denied");
            return;
        }

        // Determine correct slot - check if backpack is in offhand (slot 40)
        int slot;
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && manager.isBackpack(offhand) && offhand.equals(backpackItem)) {
            slot = 40; // Offhand slot
        } else {
            slot = player.getInventory().getHeldItemSlot();
        }

        manager.openBackpack(player, backpackItem, slot);
    }

    private boolean tryAddItemToBackpack(
            Player player, ItemStack backpackItem, BackpackManager manager) {
        PluginMessenger messenger = plugin.getBackpacksConfig().getMessenger();

        // Get item from other hand
        ItemStack itemToAdd = getItemFromOtherHand(player, backpackItem);
        if (itemToAdd == null || itemToAdd.getType() == Material.AIR) {
            return false;
        }

        // Don't allow adding backpacks to backpacks
        if (manager.isBackpack(itemToAdd)) {
            messenger.sendError(player, "You cannot store backpacks inside backpacks!");
            return true; // Return true to prevent opening
        }

        // Don't allow blacklisted items
        if (plugin.getBackpacksConfig().isBlacklisted(itemToAdd.getType())) {
            messenger.send(player, "blacklisted-item");
            return true; // Return true to prevent opening
        }

        // Get backpack type and size
        String typeId = manager.getBackpackType(backpackItem);
        BackpacksConfig.BackpackType type = plugin.getBackpacksConfig().getBackpackType(typeId);
        if (type == null) {
            return false;
        }

        // Load backpack contents
        ItemStack[] contents = manager.getOrLoadContents(backpackItem, type.size());
        if (contents == null) {
            return false;
        }

        // Try to add items
        int amountToAdd = itemToAdd.getAmount();
        int remainingAmount = addItemsToContents(contents, itemToAdd);

        // Check if we added anything
        if (remainingAmount < amountToAdd) {
            // Save updated contents
            manager.saveAndCacheContents(backpackItem, contents);

            // Update player's item
            String itemName = ItemNameFormatter.format(itemToAdd.getType());
            int addedAmount = amountToAdd - remainingAmount;

            if (remainingAmount > 0) {
                itemToAdd.setAmount(remainingAmount);
                messenger.sendRaw(
                        player,
                        ColorScheme.SUCCESS
                                + "Added "
                                + addedAmount
                                + "x "
                                + itemName
                                + " to backpack");
            } else {
                // Remove item from player's hand
                if (player.getInventory().getItemInMainHand().equals(itemToAdd)) {
                    player.getInventory().setItemInMainHand(null);
                } else {
                    player.getInventory().setItemInOffHand(null);
                }
                messenger.sendRaw(
                        player,
                        ColorScheme.SUCCESS
                                + "Added "
                                + addedAmount
                                + "x "
                                + itemName
                                + " to backpack");
            }

            // Play success sound
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
            return true;
        } else {
            messenger.sendError(player, "Backpack is full!");
            return true; // Return true to prevent opening
        }
    }

    private ItemStack getItemFromOtherHand(Player player, ItemStack backpackItem) {
        // Check which hand has the backpack
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        if (mainHand.equals(backpackItem)) {
            return offHand;
        } else if (offHand.equals(backpackItem)) {
            return mainHand;
        }

        return null;
    }

    private int addItemsToContents(ItemStack[] contents, ItemStack itemToAdd) {
        int remainingAmount = itemToAdd.getAmount();

        // First pass: try to stack with existing items
        for (int i = 0; i < contents.length; i++) {
            if (remainingAmount <= 0) break;

            ItemStack slot = contents[i];
            if (slot != null && slot.isSimilar(itemToAdd)) {
                int maxStack = slot.getMaxStackSize();
                int currentAmount = slot.getAmount();
                int canAdd = maxStack - currentAmount;

                if (canAdd > 0) {
                    int adding = Math.min(canAdd, remainingAmount);
                    slot.setAmount(currentAmount + adding);
                    remainingAmount -= adding;
                }
            }
        }

        // Second pass: fill empty slots
        for (int i = 0; i < contents.length; i++) {
            if (remainingAmount <= 0) break;

            if (contents[i] == null || contents[i].getType() == Material.AIR) {
                int adding = Math.min(itemToAdd.getMaxStackSize(), remainingAmount);
                ItemStack newStack = itemToAdd.clone();
                newStack.setAmount(adding);
                contents[i] = newStack;
                remainingAmount -= adding;
            }
        }

        return remainingAmount;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        BackpackManager manager = plugin.getBackpackManager();
        if (manager.hasOpenBackpack(player)) {
            manager.closeBackpack(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        BackpackManager manager = plugin.getBackpackManager();
        if (!manager.hasOpenBackpack(player)) return;

        ItemStack clicked = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (isBlacklistedOrBackpack(clicked) || isBlacklistedOrBackpack(cursor)) {
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
                event.setCancelled(true);
                plugin.getBackpacksConfig().getMessenger().send(player, "blacklisted-item");
                return;
            }
        }

        if (event.isShiftClick() && event.getClickedInventory() == player.getInventory()) {
            if (isBlacklistedOrBackpack(clicked)) {
                event.setCancelled(true);
                plugin.getBackpacksConfig().getMessenger().send(player, "blacklisted-item");
            }
        }

        BackpackManager.OpenBackpack open = manager.getOpenBackpack(player);
        if (open != null) {
            // Prevent clicking the slot where the backpack is stored
            if (event.getClickedInventory() == player.getInventory()
                    && event.getSlot() == open.slot()) {
                event.setCancelled(true);
                return;
            }

            // Prevent hotbar swapping to/from the backpack slot
            if (event.getClick() == ClickType.NUMBER_KEY) {
                int hotbarSlot = event.getHotbarButton();
                if (hotbarSlot == open.slot()
                        || (event.getClickedInventory() == player.getInventory()
                                && event.getSlot() == open.slot())) {
                    event.setCancelled(true);
                    return;
                }
            }

            // Prevent offhand swap if backpack is in offhand (slot 40)
            if (event.getClick() == ClickType.SWAP_OFFHAND && open.slot() == 40) {
                event.setCancelled(true);
                return;
            }

            // Prevent dropping the backpack item via Q/Ctrl+Q
            if ((event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP)
                    && event.getClickedInventory() == player.getInventory()
                    && event.getSlot() == open.slot()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        BackpackManager manager = plugin.getBackpackManager();
        if (!manager.hasOpenBackpack(player)) return;

        if (isBlacklistedOrBackpack(event.getOldCursor())) {
            boolean draggingToTop =
                    event.getRawSlots().stream()
                            .anyMatch(slot -> slot < event.getView().getTopInventory().getSize());

            if (draggingToTop) {
                event.setCancelled(true);
                plugin.getBackpacksConfig().getMessenger().send(player, "blacklisted-item");
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        BackpackManager manager = plugin.getBackpackManager();
        if (manager.hasOpenBackpack(event.getPlayer())) {
            manager.closeBackpack(event.getPlayer());
        }
    }

    private boolean isBlacklistedOrBackpack(ItemStack item) {
        if (item == null) return false;

        if (plugin.getBackpackManager().isBackpack(item)) {
            return plugin.getBackpacksConfig().preventNesting();
        }

        return plugin.getBackpacksConfig().isBlacklisted(item.getType());
    }
}
