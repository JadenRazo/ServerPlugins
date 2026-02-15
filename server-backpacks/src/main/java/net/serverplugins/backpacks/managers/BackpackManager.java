package net.serverplugins.backpacks.managers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.serverplugins.api.ui.ResourcePackIcons;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.backpacks.BackpackTier;
import net.serverplugins.backpacks.BackpacksConfig;
import net.serverplugins.backpacks.ServerBackpacks;
import net.serverplugins.backpacks.utils.ItemNameFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

public class BackpackManager {

    private final ServerBackpacks plugin;
    private final NamespacedKey backpackTypeKey;
    private final NamespacedKey backpackContentsKey;
    private final NamespacedKey backpackIdKey;
    private final Map<UUID, OpenBackpack> openBackpacks = new HashMap<>();

    // Cache of loaded inventories keyed by backpack UUID
    private final Map<String, ItemStack[]> inventoryCache = new ConcurrentHashMap<>();

    public BackpackManager(ServerBackpacks plugin) {
        this.plugin = plugin;
        this.backpackTypeKey = new NamespacedKey(plugin, "backpack_type");
        this.backpackContentsKey = new NamespacedKey(plugin, "backpack_contents");
        this.backpackIdKey = new NamespacedKey(plugin, "backpack_id");
    }

    public ItemStack createBackpack(BackpacksConfig.BackpackType type) {
        ItemStack item = new ItemBuilder(type.material()).name(type.displayName()).build();

        if (type.customModelData() > 0) {
            ItemMeta meta = item.getItemMeta();
            meta.setCustomModelData(type.customModelData());
            item.setItemMeta(meta);
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(backpackTypeKey, PersistentDataType.STRING, type.id());
        pdc.set(backpackIdKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        item.setItemMeta(meta);

        // Update lore with preview (will show "Empty" for new backpacks)
        updateBackpackLore(item);

        return item;
    }

    public boolean isBackpack(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta()
                .getPersistentDataContainer()
                .has(backpackTypeKey, PersistentDataType.STRING);
    }

    public String getBackpackType(ItemStack item) {
        if (!isBackpack(item)) return null;
        return item.getItemMeta()
                .getPersistentDataContainer()
                .get(backpackTypeKey, PersistentDataType.STRING);
    }

    public String getBackpackId(ItemStack item) {
        if (!isBackpack(item)) return null;
        return item.getItemMeta()
                .getPersistentDataContainer()
                .get(backpackIdKey, PersistentDataType.STRING);
    }

    /**
     * Get inventory contents from cache or load from NBT if not cached. This method significantly
     * improves performance by avoiding repeated NBT deserialization.
     *
     * @param backpackItem The backpack item
     * @param size The expected inventory size
     * @return The cached or loaded contents array
     */
    public ItemStack[] getOrLoadContents(ItemStack backpackItem, int size) {
        // Check if caching is enabled
        if (!plugin.getBackpacksConfig().isCacheInventoriesEnabled()) {
            return loadContents(backpackItem, size);
        }

        String backpackId = getBackpackId(backpackItem);
        if (backpackId == null) return new ItemStack[size];

        // Check cache first
        ItemStack[] cached = inventoryCache.get(backpackId);
        if (cached != null) {
            // Return a copy to prevent external modification of cached array
            return Arrays.copyOf(cached, cached.length);
        }

        // Not in cache, load from NBT and cache it
        ItemStack[] loaded = loadContents(backpackItem, size);
        if (loaded != null) {
            // Store a copy in cache to prevent external modification
            inventoryCache.put(backpackId, Arrays.copyOf(loaded, loaded.length));
        }
        return loaded;
    }

    /**
     * Save contents and update cache. This ensures the cache stays synchronized with the NBT data.
     *
     * @param backpackItem The backpack item
     * @param contents The inventory contents to save
     */
    public void saveAndCacheContents(ItemStack backpackItem, ItemStack[] contents) {
        String backpackId = getBackpackId(backpackItem);
        if (backpackId == null) return;

        // Save to NBT
        saveContents(backpackItem, contents);

        // Update cache if caching is enabled
        if (plugin.getBackpacksConfig().isCacheInventoriesEnabled()) {
            // Store a copy to prevent external modification
            inventoryCache.put(backpackId, Arrays.copyOf(contents, contents.length));
        }
    }

    /**
     * Remove inventory from cache. Should be called when a backpack is deleted or destroyed.
     *
     * @param backpackId The unique ID of the backpack
     */
    public void removeFromCache(String backpackId) {
        inventoryCache.remove(backpackId);
    }

    /** Clear all cached inventories. Called on plugin disable or reload. */
    public void clearCache() {
        int size = inventoryCache.size();
        inventoryCache.clear();
        if (size > 0) {
            plugin.getLogger()
                    .info(
                            "Cleared "
                                    + size
                                    + " cached backpack inventor"
                                    + (size == 1 ? "y" : "ies"));
        }
    }

    /**
     * Get cache statistics for debugging.
     *
     * @return Map containing cache statistics
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cached_items", inventoryCache.size());
        stats.put("enabled", plugin.getBackpacksConfig().isCacheInventoriesEnabled());
        return stats;
    }

    public void openBackpack(Player player, ItemStack backpackItem, int slot) {
        String typeId = getBackpackType(backpackItem);
        BackpacksConfig.BackpackType type = plugin.getBackpacksConfig().getBackpackType(typeId);

        if (type == null) {
            plugin.getBackpacksConfig().getMessenger().sendError(player, "Invalid backpack type!");
            return;
        }

        if (!player.hasPermission(type.permission())) {
            TextUtil.send(player, plugin.getBackpacksConfig().getMessage("no-permission"));
            return;
        }

        // Use cached contents if available
        ItemStack[] contents = getOrLoadContents(backpackItem, type.size());

        if (contents == null) {
            plugin.getBackpacksConfig()
                    .getMessenger()
                    .sendError(player, "This backpack's data is corrupted and cannot be opened.");
            plugin.getLogger()
                    .severe(
                            "Corrupted backpack data for player "
                                    + player.getName()
                                    + " - refusing to open to prevent data loss");
            return;
        }

        // Determine tier number from typeId (e.g., "tier1" → 1)
        BackpackTier tier = BackpackTier.fromId(typeId);
        int tierNumber = (tier != null) ? tier.getTierNumber() : 1;

        // Use MenuTitles helper to create title with custom background
        String backpackChar = ResourcePackIcons.MenuTitles.getBackpackByTier(tierNumber);
        Component title =
                TextUtil.parse(ResourcePackIcons.MenuTitles.createContainerTitle(backpackChar));

        Inventory inventory = Bukkit.createInventory(null, type.size(), title);
        inventory.setContents(contents);

        openBackpacks.put(player.getUniqueId(), new OpenBackpack(backpackItem, inventory, slot));

        player.openInventory(inventory);
        player.playSound(
                player.getLocation(), plugin.getBackpacksConfig().getOpenSound(), 1.0f, 1.0f);
    }

    public void closeBackpack(Player player) {
        OpenBackpack open = openBackpacks.remove(player.getUniqueId());
        if (open == null) return;

        String openUuid = getBackpackId(open.item());
        ItemStack[] guiContents = open.inventory().getContents();

        // Try the original slot first
        ItemStack currentItem = player.getInventory().getItem(open.slot());
        if (currentItem != null && isBackpack(currentItem)) {
            String currentUuid = getBackpackId(currentItem);
            if (openUuid != null && openUuid.equals(currentUuid)) {
                saveAndCacheContents(open.item(), guiContents);
                player.getInventory().setItem(open.slot(), open.item());
                player.playSound(
                        player.getLocation(),
                        plugin.getBackpacksConfig().getCloseSound(),
                        1.0f,
                        1.0f);
                return;
            }
        }

        // Backpack not at original slot - search entire inventory for it
        if (openUuid != null) {
            int foundSlot = findBackpackSlotByUuid(player, openUuid);
            if (foundSlot >= 0) {
                saveAndCacheContents(open.item(), guiContents);
                player.getInventory().setItem(foundSlot, open.item());
                plugin.getLogger()
                        .info(
                                "Backpack for "
                                        + player.getName()
                                        + " moved from slot "
                                        + open.slot()
                                        + " to "
                                        + foundSlot
                                        + " during use - saved successfully");
                player.playSound(
                        player.getLocation(),
                        plugin.getBackpacksConfig().getCloseSound(),
                        1.0f,
                        1.0f);
                return;
            }
        }

        // Backpack not found anywhere - drop contents at player's feet to prevent loss
        boolean hasItems = false;
        for (ItemStack item : guiContents) {
            if (item != null && !item.getType().isAir()) {
                hasItems = true;
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }

        if (hasItems) {
            plugin.getLogger()
                    .warning(
                            "Backpack not found in inventory for "
                                    + player.getName()
                                    + " - dropped contents at player location to prevent item loss");
            plugin.getBackpacksConfig()
                    .getMessenger()
                    .sendError(
                            player,
                            "Your backpack was removed during use. Items have been dropped at your feet.");
        }
    }

    /**
     * Search a player's entire inventory for a backpack with a specific UUID.
     *
     * @param player The player to search
     * @param uuid The backpack UUID to find
     * @return The slot index (0-40), or -1 if not found
     */
    private int findBackpackSlotByUuid(Player player, String uuid) {
        for (int i = 0; i <= 40; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && isBackpack(item)) {
                String itemUuid = getBackpackId(item);
                if (uuid.equals(itemUuid)) {
                    return i;
                }
            }
        }
        return -1;
    }

    public boolean hasOpenBackpack(Player player) {
        return openBackpacks.containsKey(player.getUniqueId());
    }

    public OpenBackpack getOpenBackpack(Player player) {
        return openBackpacks.get(player.getUniqueId());
    }

    public void saveAllOpenBackpacks() {
        for (UUID playerId : new HashSet<>(openBackpacks.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.closeInventory();
            }
        }
    }

    /**
     * Sync the contents of a player's open backpack to the ItemStack. This is used for auto-saving
     * without closing the inventory.
     *
     * @param player The player whose backpack to sync
     */
    public void syncOpenBackpack(Player player) {
        OpenBackpack open = openBackpacks.get(player.getUniqueId());
        if (open == null) return;

        // Use cache-aware save
        saveAndCacheContents(open.item(), open.inventory().getContents());
        player.getInventory().setItem(open.slot(), open.item());
    }

    /**
     * Get all players with open backpacks.
     *
     * @return Collection of player UUIDs with open backpacks
     */
    public Collection<UUID> getOpenBackpackPlayers() {
        return new HashSet<>(openBackpacks.keySet());
    }

    private ItemStack[] loadContents(ItemStack backpackItem, int size) {
        if (!backpackItem.hasItemMeta()) return new ItemStack[size];

        String base64 =
                backpackItem
                        .getItemMeta()
                        .getPersistentDataContainer()
                        .get(backpackContentsKey, PersistentDataType.STRING);

        if (base64 == null || base64.isEmpty()) return new ItemStack[size];

        try {
            byte[] data = Base64.getDecoder().decode(base64);
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            BukkitObjectInputStream ois = new BukkitObjectInputStream(bais);

            int length = ois.readInt();
            ItemStack[] contents = new ItemStack[size];

            for (int i = 0; i < length && i < size; i++) {
                contents[i] = (ItemStack) ois.readObject();
            }

            ois.close();
            return contents;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load backpack contents: " + e.getMessage());
            return null;
        }
    }

    private void saveContents(ItemStack backpackItem, ItemStack[] contents) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BukkitObjectOutputStream oos = new BukkitObjectOutputStream(baos);

            oos.writeInt(contents.length);
            for (ItemStack content : contents) {
                oos.writeObject(content);
            }

            oos.close();
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());

            ItemMeta meta = backpackItem.getItemMeta();
            meta.getPersistentDataContainer()
                    .set(backpackContentsKey, PersistentDataType.STRING, base64);
            backpackItem.setItemMeta(meta);

            // Update lore to reflect new contents
            updateBackpackLore(backpackItem);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save backpack contents: " + e.getMessage());
        }
    }

    /**
     * Gets the tier of a backpack item.
     *
     * @param item The backpack item
     * @return The BackpackTier, or null if not a valid tiered backpack
     */
    public BackpackTier getBackpackTier(ItemStack item) {
        String typeId = getBackpackType(item);
        if (typeId == null) {
            return null;
        }
        return BackpackTier.fromId(typeId);
    }

    /**
     * Checks if a backpack can be upgraded to the next tier.
     *
     * @param item The backpack item
     * @return true if the backpack can be upgraded
     */
    public boolean canUpgrade(ItemStack item) {
        BackpackTier tier = getBackpackTier(item);
        if (tier == null) {
            return false;
        }
        return tier.canUpgrade();
    }

    /**
     * Upgrades a backpack to the next tier, preserving contents.
     *
     * @param player The player upgrading the backpack
     * @param backpackItem The backpack to upgrade
     * @return The upgraded backpack, or null if upgrade failed
     */
    public ItemStack upgradeBackpack(Player player, ItemStack backpackItem) {
        BackpackTier currentTier = getBackpackTier(backpackItem);
        if (currentTier == null || !currentTier.canUpgrade()) {
            return null;
        }

        BackpackTier nextTier = currentTier.getNextTier();
        if (nextTier == null) {
            return null;
        }

        BackpacksConfig.BackpackType nextType =
                plugin.getBackpacksConfig().getBackpackType(nextTier.getId());
        if (nextType == null) {
            return null;
        }

        // Check permission
        if (!player.hasPermission(nextType.permission())) {
            TextUtil.send(player, plugin.getBackpacksConfig().getMessage("no-permission"));
            return null;
        }

        // Load current contents (using cache if available)
        ItemStack[] currentContents = getOrLoadContents(backpackItem, currentTier.getDefaultSize());

        // Create new backpack of next tier
        ItemStack upgradedBackpack = createBackpack(nextType);

        // Preserve the backpack ID for tracking
        String backpackId = getBackpackId(backpackItem);
        if (backpackId != null) {
            ItemMeta meta = upgradedBackpack.getItemMeta();
            meta.getPersistentDataContainer()
                    .set(backpackIdKey, PersistentDataType.STRING, backpackId);
            upgradedBackpack.setItemMeta(meta);

            // Invalidate old cache since we're transferring to new backpack
            removeFromCache(backpackId);
        }

        // Save contents to new backpack (will fit in larger inventory) and cache it
        saveAndCacheContents(upgradedBackpack, currentContents);

        return upgradedBackpack;
    }

    /**
     * Gets the next tier for a backpack.
     *
     * @param item The backpack item
     * @return The next BackpackTier, or null if max tier or not a tiered backpack
     */
    public BackpackTier getNextTier(ItemStack item) {
        BackpackTier tier = getBackpackTier(item);
        if (tier == null) {
            return null;
        }
        return tier.getNextTier();
    }

    /**
     * Gets the contents of a backpack without opening it. Used for features like auto-refill.
     *
     * @param backpackItem The backpack item
     * @return The contents array, or null if not a valid backpack
     */
    public ItemStack[] getBackpackContents(ItemStack backpackItem) {
        if (!isBackpack(backpackItem)) {
            return null;
        }

        String typeId = getBackpackType(backpackItem);
        BackpacksConfig.BackpackType type = plugin.getBackpacksConfig().getBackpackType(typeId);

        if (type == null) {
            return null;
        }

        // Use cache-aware loading (returns null if data is corrupted)
        return getOrLoadContents(backpackItem, type.size());
    }

    /**
     * Sets the contents of a backpack without opening it. Used for features like auto-refill.
     *
     * @param backpackItem The backpack item
     * @param contents The new contents
     */
    public void setBackpackContents(ItemStack backpackItem, ItemStack[] contents) {
        if (!isBackpack(backpackItem)) {
            return;
        }

        // Use cache-aware saving
        saveAndCacheContents(backpackItem, contents);
    }

    public NamespacedKey getBackpackIdKey() {
        return backpackIdKey;
    }

    /**
     * Generate preview text showing backpack contents. Format: "3x Diamond, 64x Iron Ingot, 12x
     * Gold Nugget" Shows top N item types by quantity (configurable).
     *
     * @param backpackItem The backpack item
     * @return List of formatted preview components for lore
     */
    public List<Component> generateItemPreview(ItemStack backpackItem) {
        List<Component> preview = new ArrayList<>();

        if (!plugin.getBackpacksConfig().isItemPreviewEnabled()) {
            return preview;
        }

        // Load contents from NBT
        String typeId = getBackpackType(backpackItem);
        BackpacksConfig.BackpackType type = plugin.getBackpacksConfig().getBackpackType(typeId);

        if (type == null) {
            return preview;
        }

        // Use cache-aware loading for preview generation
        ItemStack[] contents = getOrLoadContents(backpackItem, type.size());

        if (contents == null) {
            preview.add(TextUtil.parse("<gray>Empty"));
            return preview;
        }

        // Count items by material type
        Map<Material, Integer> itemCounts = new HashMap<>();
        boolean hasItems = false;

        for (ItemStack item : contents) {
            if (item != null && item.getType() != Material.AIR) {
                hasItems = true;
                itemCounts.merge(item.getType(), item.getAmount(), Integer::sum);
            }
        }

        if (!hasItems) {
            preview.add(TextUtil.parse("<gray>Empty"));
            return preview;
        }

        // Sort by quantity descending and get top N items
        int maxItems = plugin.getBackpacksConfig().getItemPreviewMaxItems();
        String format = plugin.getBackpacksConfig().getItemPreviewFormat();

        List<Map.Entry<Material, Integer>> sortedItems =
                itemCounts.entrySet().stream()
                        .sorted(Map.Entry.<Material, Integer>comparingByValue().reversed())
                        .limit(maxItems)
                        .collect(Collectors.toList());

        // Format as "NxItem" components
        for (Map.Entry<Material, Integer> entry : sortedItems) {
            String itemName = ItemNameFormatter.formatMaterialName(entry.getKey());
            String formatted =
                    format.replace("%amount%", String.valueOf(entry.getValue()))
                            .replace("%item%", itemName);
            preview.add(TextUtil.parse(formatted));
        }

        // If there are more items than shown, add an indicator
        if (itemCounts.size() > maxItems) {
            int remaining = itemCounts.size() - maxItems;
            preview.add(
                    TextUtil.parse(
                            "<gray>... and "
                                    + remaining
                                    + " more type"
                                    + (remaining > 1 ? "s" : "")));
        }

        return preview;
    }

    /**
     * Updates the lore of a backpack item with size, instructions, and content preview.
     *
     * @param backpackItem The backpack item to update
     */
    private void updateBackpackLore(ItemStack backpackItem) {
        if (!isBackpack(backpackItem)) {
            return;
        }

        String typeId = getBackpackType(backpackItem);
        BackpacksConfig.BackpackType type = plugin.getBackpacksConfig().getBackpackType(typeId);

        if (type == null) {
            return;
        }

        ItemMeta meta = backpackItem.getItemMeta();
        if (meta == null) {
            return;
        }

        List<Component> lore = new ArrayList<>();

        // Add static lore (size, instructions)
        lore.add(Component.empty());
        lore.add(TextUtil.parse("<gray>Size: <white>" + type.size() + " slots"));
        lore.add(Component.empty());
        lore.add(TextUtil.parse("<yellow>Right-click to open"));

        // Add shift+right-click instruction if feature is enabled
        if (plugin.getBackpacksConfig().isRightClickToAddEnabled()) {
            lore.add(TextUtil.parse("<yellow>Shift+Right-click to add items"));
        }

        // Add dynamic preview if enabled
        if (plugin.getBackpacksConfig().isItemPreviewEnabled()) {
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<gold>Contents:"));
            List<Component> preview = generateItemPreview(backpackItem);
            lore.addAll(preview);
        }

        meta.lore(lore);
        backpackItem.setItemMeta(meta);
    }

    public record OpenBackpack(ItemStack item, Inventory inventory, int slot) {}
}
