package net.serverplugins.items.gui;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.items.ServerItems;
import net.serverplugins.items.managers.ItemManager;
import net.serverplugins.items.models.CustomItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class ItemBrowserGui implements Listener {

    private static final String GUI_TITLE = "Custom Items Browser";
    private static final int PAGE_SIZE = 45; // 5 rows of items

    private final ServerItems plugin;
    private final ItemManager itemManager;

    public ItemBrowserGui(ServerItems plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager();
    }

    public void open(Player player, int page) {
        List<CustomItem> allItems = new ArrayList<>(itemManager.getAllItems());
        int totalPages = Math.max(1, (int) Math.ceil((double) allItems.size() / PAGE_SIZE));
        page = Math.max(1, Math.min(page, totalPages));

        Inventory gui = Bukkit.createInventory(null, 54, GUI_TITLE + " (Page " + page + ")");

        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, allItems.size());

        for (int i = start; i < end; i++) {
            CustomItem item = allItems.get(i);
            ItemStack display = itemManager.buildItemStack(item, 1);
            gui.setItem(i - start, display);
        }

        // Navigation bar (row 6)
        if (page > 1) {
            gui.setItem(45, ItemBuilder.of(Material.ARROW).name("<yellow>Previous Page").build());
        }

        gui.setItem(
                49,
                ItemBuilder.of(Material.PAPER)
                        .name("<gold>Page " + page + "/" + totalPages)
                        .build());

        if (page < totalPages) {
            gui.setItem(53, ItemBuilder.of(Material.ARROW).name("<yellow>Next Page").build());
        }

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.startsWith(GUI_TITLE)) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int slot = event.getSlot();

        // Navigation
        if (slot >= 45) {
            int currentPage = parsePageFromTitle(title);
            if (slot == 45 && currentPage > 1) {
                open(player, currentPage - 1);
            } else if (slot == 53) {
                open(player, currentPage + 1);
            }
            return;
        }

        // Give clicked item
        if (player.hasPermission("serveritems.give")) {
            String itemId = itemManager.getItemId(clicked);
            if (itemId != null) {
                CustomItem item = itemManager.getItem(itemId);
                if (item != null) {
                    ItemStack give = itemManager.buildItemStack(item, 1);
                    player.getInventory().addItem(give);
                }
            }
        }
    }

    private int parsePageFromTitle(String title) {
        try {
            int start = title.indexOf("Page ") + 5;
            int end = title.indexOf("/", start);
            return Integer.parseInt(title.substring(start, end));
        } catch (Exception e) {
            return 1;
        }
    }
}
