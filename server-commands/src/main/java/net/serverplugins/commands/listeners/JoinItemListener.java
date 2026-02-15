package net.serverplugins.commands.listeners;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.serverplugins.commands.ServerCommands;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Listener for handling join items - items given to players on join that can open GUIs when clicked
 * and are protected from dropping/moving.
 */
public class JoinItemListener implements Listener {

    private final ServerCommands plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final NamespacedKey joinItemKey;

    // Join item configuration
    private boolean enabled;
    private int slot;
    private Material material;
    private Integer customModelData;
    private String itemName;
    private List<String> itemLore;
    private String command;
    private boolean protectItem;
    private boolean giveOnRespawn;

    public JoinItemListener(ServerCommands plugin) {
        this.plugin = plugin;
        this.joinItemKey = new NamespacedKey(plugin, "join_item");
        loadConfig();
    }

    public final void loadConfig() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("join-item");
        if (section == null) {
            enabled = false;
            return;
        }

        enabled = section.getBoolean("enabled", false);
        slot = section.getInt("slot", 0);

        String materialStr = section.getString("material", "COMPASS");
        try {
            material = Material.valueOf(materialStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger()
                    .warning("Invalid join-item material: " + materialStr + ", using COMPASS");
            material = Material.COMPASS;
        }

        // Load custom model data if specified
        if (section.contains("custom-model-data")) {
            customModelData = section.getInt("custom-model-data");
        } else {
            customModelData = null;
        }

        itemName = section.getString("name", "<gold><bold>Survival Guide</bold></gold>");
        itemLore = section.getStringList("lore");
        command = section.getString("command", "menu");
        plugin.getLogger().info("=== Join item config loaded ===");
        plugin.getLogger().info("Command value: '" + command + "'");
        plugin.getLogger().info("Command length: " + command.length());
        plugin.getLogger().info("Command bytes: " + java.util.Arrays.toString(command.getBytes()));
        protectItem = section.getBoolean("protect", true);
        giveOnRespawn = section.getBoolean("give-on-respawn", true);
    }

    public void reload() {
        loadConfig();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();

        // Check player's preference before giving the item
        plugin.getServer()
                .getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            var repository = plugin.getRepository();
                            if (repository != null) {
                                boolean prefEnabled =
                                        repository.getSurvivalGuideEnabled(player.getUniqueId());
                                if (prefEnabled) {
                                    giveJoinItem(player);
                                }
                            } else {
                                // No database, give to everyone
                                giveJoinItem(player);
                            }
                        },
                        5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!enabled || !giveOnRespawn) return;

        Player player = event.getPlayer();

        // Check player's preference before giving the item
        plugin.getServer()
                .getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            var repository = plugin.getRepository();
                            if (repository != null) {
                                boolean prefEnabled =
                                        repository.getSurvivalGuideEnabled(player.getUniqueId());
                                if (prefEnabled) {
                                    giveJoinItem(player);
                                }
                            } else {
                                // No database, give to everyone
                                giveJoinItem(player);
                            }
                        },
                        5L);
    }

    /**
     * Gives the join item to a player
     *
     * @param player The player to give the item to
     */
    public void giveJoinItem(Player player) {
        ItemStack item = createJoinItem();

        ItemMeta meta = item.getItemMeta();
        plugin.getLogger().info("=== Creating join item for player: " + player.getName());
        plugin.getLogger()
                .info(
                        "Created: Material="
                                + item.getType()
                                + " | CustomModelData="
                                + (meta != null && meta.hasCustomModelData()
                                        ? meta.getCustomModelData()
                                        : "none")
                                + " | HasPDC="
                                + (meta != null
                                        && meta.getPersistentDataContainer()
                                                .has(joinItemKey, PersistentDataType.BYTE))
                                + " | DisplayName="
                                + (meta != null && meta.displayName() != null
                                        ? meta.displayName()
                                        : "null"));

        // Check if player already has the item in the designated slot
        ItemStack existingItem = player.getInventory().getItem(slot);
        if (existingItem != null && isJoinItem(existingItem)) {
            plugin.getLogger().info("Player already has join item in slot " + slot + ", skipping");
            return; // Already has the item
        }

        // Clear the slot first if it has something else
        if (existingItem != null && !existingItem.getType().isAir()) {
            // Try to find an empty slot for the existing item
            int emptySlot = player.getInventory().firstEmpty();
            if (emptySlot != -1) {
                player.getInventory().setItem(emptySlot, existingItem);
            } else {
                // No empty slot, drop the item
                player.getWorld().dropItemNaturally(player.getLocation(), existingItem);
            }
        }

        player.getInventory().setItem(slot, item);
        plugin.getLogger().info("Join item placed in slot " + slot);
    }

    private ItemStack createJoinItem() {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Set display name using MiniMessage
            Component displayName = miniMessage.deserialize(itemName);
            meta.displayName(displayName);

            // Set lore if configured
            if (itemLore != null && !itemLore.isEmpty()) {
                List<Component> loreComponents = new ArrayList<>();
                for (String loreLine : itemLore) {
                    loreComponents.add(miniMessage.deserialize(loreLine));
                }
                meta.lore(loreComponents);
            }

            // Set custom model data if specified
            if (customModelData != null) {
                meta.setCustomModelData(customModelData);
            }

            // Mark as join item using PDC for reliable identification
            meta.getPersistentDataContainer().set(joinItemKey, PersistentDataType.BYTE, (byte) 1);

            item.setItemMeta(meta);
        }

        return item;
    }

    private boolean isJoinItem(ItemStack item) {
        if (item == null || item.getType() != material) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        // Method 1: Check PDC for join item marker (primary method for new items)
        if (meta.getPersistentDataContainer().has(joinItemKey, PersistentDataType.BYTE)) {
            plugin.getLogger().info("Item matched via PDC marker");
            return true;
        }

        // Method 2: Check custom model data (reliable backup check)
        // If customModelData is set (e.g., 10), check if the item has that exact value
        if (customModelData != null) {
            if (meta.hasCustomModelData() && meta.getCustomModelData() == customModelData) {
                plugin.getLogger().info("Item matched via custom model data: " + customModelData);
                return true;
            }
        } else {
            // If no custom model data is configured, check that the item also has none
            if (!meta.hasCustomModelData()) {
                // Still need to verify it's the right item via display name
                Component displayName = meta.displayName();
                if (displayName != null) {
                    Component expectedName = miniMessage.deserialize(itemName);
                    if (displayName.equals(expectedName)) {
                        plugin.getLogger()
                                .info("Item matched via display name (no custom model data)");
                        return true;
                    }
                }
            }
        }

        return false;
    }

    // Use LOWEST priority to cancel event BEFORE other plugins process it
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!enabled) return;

        // Check for right-click or left-click air/block
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR
                && action != Action.RIGHT_CLICK_BLOCK
                && action != Action.LEFT_CLICK_AIR
                && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (!isJoinItem(item)) {
            return;
        }

        // Cancel event IMMEDIATELY to prevent vanilla compass behavior
        event.setCancelled(true);

        // Only open menu on right-click (not left-click to avoid accidental opens)
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();

        // Execute the configured command
        if (command != null && !command.isEmpty()) {
            plugin.getLogger()
                    .info("=== Executing join item command for player: " + player.getName());
            plugin.getLogger().info("Command from config: '" + command + "'");
            plugin.getLogger().info("Command length: " + command.length());
            plugin.getLogger()
                    .info("Command bytes: " + java.util.Arrays.toString(command.getBytes()));

            // Schedule command execution to next tick to ensure event is fully cancelled
            plugin.getServer()
                    .getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                plugin.getLogger().info("NOW executing command: '" + command + "'");
                                player.performCommand(command);
                            });
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDrop(PlayerDropItemEvent event) {
        if (!enabled || !protectItem) return;

        if (isJoinItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!enabled || !protectItem) return;

        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Check if clicking on the join item
        ItemStack currentItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        if (isJoinItem(currentItem) || isJoinItem(cursorItem)) {
            event.setCancelled(true);
            return;
        }

        // Also check for hotbar swap to the join item slot
        if (event.getHotbarButton() != -1) {
            ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
            if (isJoinItem(hotbarItem)) {
                event.setCancelled(true);
                return;
            }
        }

        // Check if click is in the join item slot
        if (event.getSlot() == slot && event.getClickedInventory() == player.getInventory()) {
            if (isJoinItem(event.getCurrentItem())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (!enabled || !protectItem) return;

        if (isJoinItem(event.getMainHandItem()) || isJoinItem(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    /**
     * Removes the join item from a player's inventory
     *
     * @param player The player to remove the join item from
     * @return true if the item was found and removed, false otherwise
     */
    public boolean removeJoinItem(Player player) {
        boolean removed = false;

        plugin.getLogger()
                .info("=== Attempting to remove join item for player: " + player.getName());
        plugin.getLogger()
                .info(
                        "Looking for: Material="
                                + material
                                + ", CustomModelData="
                                + customModelData
                                + ", Name="
                                + itemName);

        // Check each inventory slot
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && !item.getType().isAir()) {
                ItemMeta meta = item.getItemMeta();
                plugin.getLogger()
                        .info(
                                "Slot "
                                        + i
                                        + ": "
                                        + item.getType()
                                        + " | HasMeta="
                                        + (meta != null)
                                        + " | CustomModelData="
                                        + (meta != null && meta.hasCustomModelData()
                                                ? meta.getCustomModelData()
                                                : "none")
                                        + " | HasPDC="
                                        + (meta != null
                                                && meta.getPersistentDataContainer()
                                                        .has(joinItemKey, PersistentDataType.BYTE))
                                        + " | DisplayName="
                                        + (meta != null && meta.displayName() != null
                                                ? meta.displayName()
                                                : "null"));

                if (isJoinItem(item)) {
                    plugin.getLogger().info("MATCH FOUND at slot " + i + "! Removing...");
                    player.getInventory().setItem(i, null);
                    removed = true;
                }
            }
        }

        plugin.getLogger().info("Remove operation completed. Item removed: " + removed);
        return removed;
    }

    /**
     * Gets the enabled state of join items
     *
     * @return true if join items are enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
}
