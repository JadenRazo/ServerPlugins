package net.serverplugins.api.gui;

import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class Gui implements InventoryHolder {

    protected final Plugin plugin;
    protected final int size;
    protected final Component title;
    protected final Inventory inventory;
    protected final Map<Integer, GuiItem> items = new HashMap<>();
    protected Player viewer;

    public Gui(int size, Component title) {
        this(null, size, title);
    }

    public Gui(Plugin plugin, String title, int size) {
        this(plugin, size, TextUtil.parse(title));
    }

    public Gui(Plugin plugin, int size, Component title) {
        if (size % 9 != 0 || size > 54 || size < 9) {
            throw new IllegalArgumentException("Size must be a multiple of 9 and between 9 and 54");
        }
        this.plugin = plugin;
        this.size = size;
        this.title = title;
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    public Gui(Plugin plugin, Player viewer, String title, int size) {
        this(plugin, size, TextUtil.parse(title));
        this.viewer = viewer;
    }

    protected void initializeItems() {}

    public void setItem(int slot, GuiItem item) {
        if (slot < 0 || slot >= size) throw new IllegalArgumentException("Invalid slot");
        items.put(slot, item);
        inventory.setItem(slot, item.getItemStack());
    }

    public void removeItem(int slot) {
        items.remove(slot);
        inventory.setItem(slot, null);
    }

    public GuiItem getItem(int slot) {
        return items.get(slot);
    }

    public void clearItems() {
        items.clear();
        inventory.clear();
    }

    public void refresh() {
        clearItems();
        initializeItems();
    }

    public void open(Player player) {
        this.viewer = player;
        initializeItems();
        // Register with GuiManager for tracking
        if (net.serverplugins.api.ServerAPI.getInstance() != null) {
            net.serverplugins.api.ServerAPI.getInstance().getGuiManager().registerGui(player, this);
        }
        player.openInventory(inventory);
    }

    public void open() {
        if (viewer == null) {
            throw new IllegalStateException(
                    "No viewer set. Use open(Player) or set viewer in constructor.");
        }
        open(viewer);
    }

    public Player getViewer() {
        return viewer;
    }

    public void setViewer(Player viewer) {
        this.viewer = viewer;
    }

    public void close(Player player) {
        player.closeInventory();
    }

    public void handleClick(Player player, int slot) {
        handleClick(player, slot, ClickType.LEFT);
    }

    public void handleClick(Player player, int slot, ClickType clickType) {
        GuiItem item = items.get(slot);
        if (item != null) item.onClick(player, clickType, slot);
    }

    public void onClose(Player player) {}

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public int getSize() {
        return size;
    }

    public Component getTitle() {
        return title;
    }

    public void fillEmpty(GuiItem item) {
        for (int i = 0; i < size; i++) {
            if (!items.containsKey(i)) setItem(i, item);
        }
    }

    public void fillEmpty(ItemStack item) {
        fillEmpty(new GuiItem(item, false));
    }

    public void fillBorder(GuiItem item) {
        int rows = size / 9;
        for (int i = 0; i < 9; i++) {
            setItem(i, item);
            setItem(size - 9 + i, item);
        }
        for (int i = 1; i < rows - 1; i++) {
            setItem(i * 9, item);
            setItem(i * 9 + 8, item);
        }
    }
}
