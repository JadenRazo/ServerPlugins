package net.serverplugins.api.protection;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/** Protection system specifically for SellGUI to prevent item theft and exploits */
public class SellGUIProtection implements Listener {

    private final Set<UUID> activeSellGUIs = new HashSet<>();

    // Known decorative materials in SellGUI (glass panes, borders, etc.)
    private static final Set<Material> DECORATIVE_MATERIALS =
            Set.of(
                    Material.GREEN_STAINED_GLASS_PANE,
                    Material.RED_STAINED_GLASS_PANE,
                    Material.GRAY_STAINED_GLASS_PANE,
                    Material.BLACK_STAINED_GLASS_PANE,
                    Material.WHITE_STAINED_GLASS_PANE,
                    Material.LIGHT_GRAY_STAINED_GLASS_PANE);

    // Slots that are for decoration/buttons only (confirm button slots)
    private static final Set<Integer> BUTTON_SLOTS = Set.of(48, 49, 50);

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Inventory inv = event.getInventory();
        String title = getInventoryTitle(event.getView());

        // Detect SellGUI by title pattern or size
        if (isSellGUI(title, inv)) {
            activeSellGUIs.add(player.getUniqueId());
            Bukkit.getLogger()
                    .info("[SellGUIProtection] Player " + player.getName() + " opened SellGUI");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Only protect if this is an active SellGUI
        if (!activeSellGUIs.contains(player.getUniqueId())) {
            return;
        }

        String title = getInventoryTitle(event.getView());
        if (!isSellGUI(title, event.getInventory())) {
            activeSellGUIs.remove(player.getUniqueId());
            return;
        }

        int slot = event.getRawSlot();
        ItemStack clickedItem = event.getCurrentItem();

        // Transaction logging
        if (clickedItem != null && slot >= 0 && slot < event.getInventory().getSize()) {
            Bukkit.getLogger()
                    .info(
                            String.format(
                                    "[SellGUITransaction] Player: %s | Slot: %d | Item: %s x%d | Click: %s",
                                    player.getName(),
                                    slot,
                                    clickedItem.getType(),
                                    clickedItem.getAmount(),
                                    event.getClick()));
        }

        // Block creative mode entirely
        if (player.getGameMode() == GameMode.CREATIVE) {
            event.setCancelled(true);
            TextUtil.sendError(player, "You cannot use SellGUI in creative mode!");
            player.closeInventory();
            activeSellGUIs.remove(player.getUniqueId());
            return;
        }

        // Block shift-clicks entirely (prevents item duplication exploits)
        if (event.isShiftClick()) {
            // Allow shift-clicking FROM player inventory TO sell GUI
            if (event.getClickedInventory() == player.getInventory()) {
                // This is allowed - shift-clicking from player inventory to sell slots
                return;
            } else {
                // Block shift-clicking in the GUI itself
                event.setCancelled(true);
                return;
            }
        }

        // If clicking in the top inventory (SellGUI)
        if (slot >= 0 && slot < event.getInventory().getSize()) {
            if (clickedItem != null) {
                // Prevent taking decorative items (glass panes, borders)
                if (isDecorativeItem(clickedItem, slot)) {
                    event.setCancelled(true);
                    Bukkit.getLogger()
                            .info(
                                    "[SellGUIProtection] Blocked attempt to take decorative item: "
                                            + clickedItem.getType()
                                            + " at slot "
                                            + slot);
                    return;
                }

                // Prevent taking items from button slots
                if (BUTTON_SLOTS.contains(slot)) {
                    // Only allow clicking, not taking
                    if (event.getClick().name().contains("PICKUP")
                            || event.getClick().name().contains("MOVE")) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }

        // Block number key swaps
        if (event.getClick().name().contains("NUMBER_KEY")) {
            event.setCancelled(true);
            return;
        }

        // Block double clicks
        if (event.getClick().name().contains("DOUBLE_CLICK")) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            activeSellGUIs.remove(player.getUniqueId());
        }
    }

    /** Check if an item is decorative (shouldn't be taken) */
    private boolean isDecorativeItem(ItemStack item, int slot) {
        // Glass panes are always decorative
        if (DECORATIVE_MATERIALS.contains(item.getType())) {
            return true;
        }

        // Items in button slots are decorative
        if (BUTTON_SLOTS.contains(slot)) {
            return true;
        }

        // Items with specific names indicating they're buttons/decorations
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String displayName = item.getItemMeta().getDisplayName().toLowerCase();
            if (displayName.contains("confirm")
                    || displayName.contains("no items")
                    || displayName.contains("button")) {
                return true;
            }
        }

        return false;
    }

    /** Detect if an inventory is SellGUI */
    private boolean isSellGUI(String title, Inventory inv) {
        // SellGUI main GUI has size 54 and specific title
        if (inv.getSize() != 54) {
            return false;
        }

        // Check for SellGUI title patterns
        // SellGUI uses "&f" with PUA characters - U+E6B9 is the main background char
        if (title.contains("\uE6B9")) { // SellGUI's background character
            return true;
        }

        // Check for Price Setter GUI
        if (title.contains("Price Setter") || title.contains("🏷")) {
            return true;
        }

        // Check for evaluation GUI
        if (title.contains("Evaluation") || title.contains("⚖")) {
            return true;
        }

        return false;
    }

    /** Get inventory title from InventoryView */
    private String getInventoryTitle(InventoryView view) {
        try {
            return view.getTitle();
        } catch (Exception e) {
            return "";
        }
    }
}
