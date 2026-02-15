package net.serverplugins.commands.gui;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

/**
 * A GUI wrapper for viewing/editing another player's inventory. Supports read-only mode based on
 * permissions.
 *
 * <p>Layout (54 slots / 6 rows): Row 0 (0-8): Armor + Offhand + Info Row 1 (9-17): Empty/separator
 * Rows 2-4 (18-44): Main inventory (slots 9-35 of player inv) Row 5 (45-53): Hotbar (slots 0-8 of
 * player inv)
 */
public class InvseeGui implements Listener {

    // Resource pack unicode: inventory background
    private static final String TITLE_PREFIX = "&f⻔⻔⻔⻔⻔⻔⻔⻔";

    // Slot mappings
    private static final int SLOT_HELMET = 0;
    private static final int SLOT_CHESTPLATE = 1;
    private static final int SLOT_LEGGINGS = 2;
    private static final int SLOT_BOOTS = 3;
    private static final int SLOT_OFFHAND = 5;
    private static final int SLOT_INFO = 8;

    private final Plugin plugin;
    private final Player viewer;
    private final Player target;
    private final boolean canEdit;
    private final Inventory inventory;
    private boolean closed = false;

    public InvseeGui(Plugin plugin, Player viewer, Player target, boolean canEdit) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.target = target;
        this.canEdit = canEdit;

        // Create 54-slot inventory with resource pack title
        Component title = TextUtil.parse(TITLE_PREFIX);
        this.inventory = Bukkit.createInventory(null, 54, title);

        // Copy target's inventory contents
        copyFromPlayer();
    }

    private void copyFromPlayer() {
        PlayerInventory playerInv = target.getInventory();

        // Armor slots (row 0)
        inventory.setItem(SLOT_HELMET, cloneOrNull(playerInv.getHelmet()));
        inventory.setItem(SLOT_CHESTPLATE, cloneOrNull(playerInv.getChestplate()));
        inventory.setItem(SLOT_LEGGINGS, cloneOrNull(playerInv.getLeggings()));
        inventory.setItem(SLOT_BOOTS, cloneOrNull(playerInv.getBoots()));

        // Offhand
        inventory.setItem(SLOT_OFFHAND, cloneOrNull(playerInv.getItemInOffHand()));

        // Info item (non-editable indicator)
        ItemStack infoItem = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = infoItem.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.parse("<gold>" + target.getName() + "'s Inventory"));
            meta.lore(
                    List.of(
                            TextUtil.parse(canEdit ? "<green>Edit Mode" : "<red>View Only"),
                            TextUtil.parse(""),
                            TextUtil.parse("<gray>Armor: Slots 1-4"),
                            TextUtil.parse("<gray>Offhand: Slot 6")));
            infoItem.setItemMeta(meta);
        }
        inventory.setItem(SLOT_INFO, infoItem);

        // Separator row (row 1) - gray glass panes
        ItemStack separator = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta sepMeta = separator.getItemMeta();
        if (sepMeta != null) {
            sepMeta.displayName(TextUtil.parse(" "));
            separator.setItemMeta(sepMeta);
        }
        for (int i = 9; i < 18; i++) {
            inventory.setItem(i, separator);
        }

        // Main inventory (player slots 9-35 -> GUI slots 18-44)
        for (int i = 9; i < 36; i++) {
            ItemStack item = playerInv.getItem(i);
            inventory.setItem(i + 9, cloneOrNull(item));
        }

        // Hotbar (player slots 0-8 -> GUI slots 45-53)
        for (int i = 0; i < 9; i++) {
            ItemStack item = playerInv.getItem(i);
            inventory.setItem(i + 45, cloneOrNull(item));
        }
    }

    private void copyToPlayer() {
        if (!target.isOnline()) return;

        PlayerInventory playerInv = target.getInventory();

        // Armor slots
        playerInv.setHelmet(cloneOrNull(inventory.getItem(SLOT_HELMET)));
        playerInv.setChestplate(cloneOrNull(inventory.getItem(SLOT_CHESTPLATE)));
        playerInv.setLeggings(cloneOrNull(inventory.getItem(SLOT_LEGGINGS)));
        playerInv.setBoots(cloneOrNull(inventory.getItem(SLOT_BOOTS)));

        // Offhand
        playerInv.setItemInOffHand(cloneOrNull(inventory.getItem(SLOT_OFFHAND)));

        // Main inventory (GUI slots 18-44 -> player slots 9-35)
        for (int i = 18; i < 45; i++) {
            ItemStack item = inventory.getItem(i);
            playerInv.setItem(i - 9, cloneOrNull(item));
        }

        // Hotbar (GUI slots 45-53 -> player slots 0-8)
        for (int i = 45; i < 54; i++) {
            ItemStack item = inventory.getItem(i);
            playerInv.setItem(i - 45, cloneOrNull(item));
        }
    }

    private ItemStack cloneOrNull(ItemStack item) {
        return item != null ? item.clone() : null;
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

        int slot = event.getRawSlot();

        // Always prevent clicking separator row and info item
        if ((slot >= 9 && slot < 18) || slot == SLOT_INFO || slot == 4 || slot == 6 || slot == 7) {
            event.setCancelled(true);
            return;
        }

        // If can't edit, cancel all modifications to our inventory
        if (!canEdit && slot < inventory.getSize()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory() != inventory) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.equals(viewer)) return;

        // Check if any affected slots are in separator row or protected slots
        for (int slot : event.getRawSlots()) {
            if ((slot >= 9 && slot < 18)
                    || slot == SLOT_INFO
                    || slot == 4
                    || slot == 6
                    || slot == 7) {
                event.setCancelled(true);
                return;
            }
        }

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

        // If editing was allowed, sync changes back to target's inventory
        if (canEdit) {
            copyToPlayer();
        }

        // Unregister listener
        HandlerList.unregisterAll(this);
    }

    public boolean canEdit() {
        return canEdit;
    }
}
