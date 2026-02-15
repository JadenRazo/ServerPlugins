package net.serverplugins.commands.gui;

import net.kyori.adventure.text.Component;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * A GUI wrapper for viewing/editing another player's enderchest. Supports read-only mode based on
 * permissions.
 */
public class EnderchestGui implements Listener {

    // Resource pack unicode: enderchest background
    private static final String TITLE_PREFIX = "&f⻔⻔⻔⻔⻔⻔⻔⻔";

    private final Plugin plugin;
    private final Player viewer;
    private final Player target;
    private final boolean canEdit;
    private final Inventory inventory;
    private boolean closed = false;

    public EnderchestGui(Plugin plugin, Player viewer, Player target, boolean canEdit) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.target = target;
        this.canEdit = canEdit;

        // Create inventory with resource pack title
        Component title = TextUtil.parse(TITLE_PREFIX);
        this.inventory = Bukkit.createInventory(null, 27, title);

        // Copy target's enderchest contents
        ItemStack[] contents = target.getEnderChest().getContents();
        for (int i = 0; i < contents.length && i < 27; i++) {
            if (contents[i] != null) {
                inventory.setItem(i, contents[i].clone());
            }
        }
    }

    public void open() {
        // Register listener
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Open inventory
        viewer.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory() != inventory) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.equals(viewer)) return;

        // If can't edit, cancel all modifications
        if (!canEdit) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory() != inventory) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.equals(viewer)) return;

        // If can't edit, cancel all drags that affect our inventory
        if (!canEdit) {
            for (int slot : event.getRawSlots()) {
                if (slot < inventory.getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory() != inventory) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!player.equals(viewer)) return;
        if (closed) return;

        closed = true;

        // If editing was allowed, sync changes back to target's enderchest
        if (canEdit && target.isOnline()) {
            ItemStack[] newContents = new ItemStack[27];
            for (int i = 0; i < 27; i++) {
                ItemStack item = inventory.getItem(i);
                newContents[i] = item != null ? item.clone() : null;
            }
            target.getEnderChest().setContents(newContents);
        }

        // Unregister listener
        HandlerList.unregisterAll(this);
    }

    public boolean canEdit() {
        return canEdit;
    }
}
