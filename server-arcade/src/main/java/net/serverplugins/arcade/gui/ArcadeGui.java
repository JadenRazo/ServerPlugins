package net.serverplugins.arcade.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.serverplugins.arcade.ServerArcade;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** Base class for all arcade GUIs. */
public abstract class ArcadeGui implements InventoryHolder {

    protected final ServerArcade plugin;
    protected Inventory inventory;
    protected Player player;
    protected String title;
    protected int size;
    protected boolean allowClose = true;
    protected boolean handlersInitialized = false;

    private final Map<Integer, Consumer<InventoryClickEvent>> clickHandlers = new HashMap<>();

    public ArcadeGui(ServerArcade plugin, String title, int size) {
        this.plugin = plugin;
        // No trigger character needed - PacketUtils detects fullscreen menu icons automatically
        this.title = title.replace("&", "§");
        this.size = size;
    }

    /** Create and populate the inventory. */
    protected abstract void build();

    /** Called when the GUI is opened. */
    protected void onOpen(Player player) {}

    /** Called when the GUI is closed. */
    protected void onClose(InventoryCloseEvent event) {}

    /** Handle a click event. */
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= size) return;

        Consumer<InventoryClickEvent> handler = clickHandlers.get(slot);
        if (handler != null) {
            handler.accept(event);
        }

        onClick(event);
    }

    /** Override for custom click handling. */
    protected void onClick(InventoryClickEvent event) {}

    /** Handle close event. */
    public void handleClose(InventoryCloseEvent event) {
        if (!allowClose) {
            Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(inventory));
            return;
        }
        onClose(event);
    }

    /** Open the GUI for a player. */
    public void open(Player player) {
        this.player = player;
        this.inventory = Bukkit.createInventory(this, size, title);
        build();
        player.openInventory(inventory);

        // Hide player inventory using packet-based approach
        // This uses ProtocolLib to send packets that hide the bottom inventory slots
        if (net.serverplugins.api.utils.PacketUtils.isEnabled()) {
            net.serverplugins.api.utils.PacketUtils.hidePlayerInventory(player);
        }

        onOpen(player);
    }

    /** Refresh/rebuild the GUI. */
    public void update() {
        if (inventory != null) {
            inventory.clear();
            if (shouldRebuildHandlers()) {
                clickHandlers.clear();
                handlersInitialized = false;
            }
            build();
            if (!handlersInitialized) {
                handlersInitialized = true;
            }
        }
    }

    /**
     * Refresh display without clearing handlers. Use this for frequent updates where click handlers
     * don't change.
     */
    public void refreshDisplay() {
        if (inventory != null) {
            inventory.clear();
            build();
        }
    }

    /**
     * Override this to control when handlers should be rebuilt. Return true to clear and rebuild
     * handlers on update(). Return false to keep existing handlers (better performance).
     */
    protected boolean shouldRebuildHandlers() {
        return true; // Default: always rebuild
    }

    /** Set an item with a click handler. */
    protected void setItem(int slot, ItemStack item, Consumer<InventoryClickEvent> onClick) {
        if (slot >= 0 && slot < size) {
            inventory.setItem(slot, item);
            if (onClick != null) {
                clickHandlers.put(slot, onClick);
            }
        }
    }

    /** Set an item without a click handler. */
    protected void setItem(int slot, ItemStack item) {
        setItem(slot, item, null);
    }

    /** Set the same item in multiple slots. */
    protected void setItems(int[] slots, ItemStack item, Consumer<InventoryClickEvent> onClick) {
        for (int slot : slots) {
            setItem(slot, item, onClick);
        }
    }

    /** Fill empty slots with an item. */
    protected void fillEmpty(ItemStack item) {
        for (int i = 0; i < size; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, item);
            }
        }
    }

    /**
     * Update the GUI title without reopening (packet-based). This prevents click handlers from
     * being lost.
     */
    public void updateTitle(String newTitle) {
        this.title = newTitle.replace("&", "§");
        if (player != null && net.serverplugins.api.utils.PacketUtils.isEnabled()) {
            // Convert legacy color codes to Adventure Component JSON
            net.kyori.adventure.text.Component component =
                    net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                            .legacySection()
                            .deserialize(this.title);
            net.serverplugins.api.utils.PacketUtils.setInventoryTitle(player, component);
        }
    }

    /** Close the GUI. */
    public void close() {
        if (player != null && player.getOpenInventory().getTopInventory().equals(inventory)) {
            player.closeInventory();
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Player getPlayer() {
        return player;
    }

    public ServerArcade getPlugin() {
        return plugin;
    }
}
