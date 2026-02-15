package net.serverplugins.api.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public class GuiListener implements Listener {

    private final GuiManager guiManager;

    public GuiListener(GuiManager guiManager) {
        this.guiManager = guiManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Try to get GUI from holder first, then fallback to GuiManager tracking
        Gui gui = null;
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof Gui) {
            gui = (Gui) holder;
        } else {
            // Fallback: check if player has a GUI open via GuiManager
            gui = guiManager.getOpenGui(player);
        }

        if (gui == null) {
            return;
        }

        // Block ALL item movement in GUI inventories
        event.setCancelled(true);
        event.setResult(Event.Result.DENY);

        // Block shift-clicks, number keys, double-clicks, offhand swaps, and creative actions
        ClickType click = event.getClick();
        if (event.isShiftClick()
                || click.isKeyboardClick()
                || click == ClickType.DOUBLE_CLICK
                || click == ClickType.SWAP_OFFHAND
                || click.isCreativeAction()) {
            return;
        }

        int slot = event.getRawSlot();

        // Only process clicks within the GUI bounds
        if (slot >= 0 && slot < gui.getSize()) {
            GuiItem item = gui.getItem(slot);
            if (item != null && item.isClickable()) {
                gui.handleClick(player, slot, event.getClick());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Check holder or GuiManager tracking
        InventoryHolder holder = event.getInventory().getHolder();
        boolean isGui = holder instanceof Gui || guiManager.hasGuiOpen(player);

        if (!isGui) {
            return;
        }

        // Block ALL drag operations in GUI inventories
        event.setCancelled(true);
        event.setResult(Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof Gui gui)) return;

        gui.onClose(player);
        guiManager.removePlayer(player);
    }
}
