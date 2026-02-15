package net.serverplugins.items.api;

import java.util.Collection;
import net.serverplugins.items.ServerItems;
import net.serverplugins.items.managers.ItemManager;
import net.serverplugins.items.models.CustomItem;
import org.bukkit.inventory.ItemStack;

public class ServerItemsAPI {

    private final ItemManager itemManager;

    public ServerItemsAPI(ItemManager itemManager) {
        this.itemManager = itemManager;
    }

    public static ServerItemsAPI getInstance() {
        ServerItems plugin = ServerItems.getInstance();
        return plugin != null ? plugin.getApi() : null;
    }

    public CustomItem getItem(String id) {
        return itemManager.getItem(id);
    }

    public CustomItem getCustomItem(ItemStack stack) {
        return itemManager.getCustomItem(stack);
    }

    public String getItemId(ItemStack stack) {
        return itemManager.getItemId(stack);
    }

    public boolean isCustomItem(ItemStack stack) {
        return itemManager.isCustomItem(stack);
    }

    public ItemStack buildItemStack(String id, int amount) {
        CustomItem item = itemManager.getItem(id);
        if (item == null) return null;
        return itemManager.buildItemStack(item, amount);
    }

    public Collection<CustomItem> getAllItems() {
        return itemManager.getAllItems();
    }

    public int getItemCount() {
        return itemManager.getItemCount();
    }
}
