package net.serverplugins.core.features;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.core.ServerCore;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.persistence.PersistentDataType;

/**
 * Custom Inventory Feature - Replaces inventory titles for resource pack compatibility
 *
 * <p>This feature supports multiple types of inventories: 1. Standard Minecraft inventories
 * (ender_chest, shulker, chest, bookshelf) 2. Custom furniture inventories from
 * FurnitureLib/DiceFurniture plugins (washing_machine, fridge) 3. Custom title mappings based on
 * original inventory title patterns
 *
 * <p>Configuration: - Supports both legacy format and new titles format - New format (preferred):
 * settings.custom-inventory.titles.<type> - Legacy format: settings.custom-inventory.<type> -
 * Custom mappings: settings.custom-inventory.custom.<key>
 *
 * <p>Furniture Support: - Automatically detects FurnitureLib and DiceFurniture plugins - Matches
 * furniture inventories by title keywords - Attempts PDC-based furniture detection (future
 * enhancement) - Falls back to title-based matching if PDC unavailable
 */
public class CustomInventoryFeature extends Feature implements Listener {

    private String enderchestTitle;
    private String shulkerTitle;
    private String chestTitle;
    private String bookshelfTitle;
    private final Map<String, CustomTitleConfig> customTitles = new HashMap<>();
    private final Map<String, String> furnitureTitles = new HashMap<>();

    // Furniture plugin integration keys
    private NamespacedKey furnitureLibKey;
    private NamespacedKey diceFurnitureKey;
    private boolean furniturePluginPresent = false;

    public CustomInventoryFeature(ServerCore plugin) {
        super(plugin);
        loadConfig();
    }

    @Override
    public String getName() {
        return "Custom Inventory";
    }

    @Override
    public String getDescription() {
        return "Replaces inventory titles for texture pack compatibility";
    }

    @Override
    protected void onEnable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        initializeFurnitureSupport();
    }

    private void initializeFurnitureSupport() {
        // Check if FurnitureLib or DiceFurniture plugins are present
        furniturePluginPresent =
                plugin.getServer().getPluginManager().isPluginEnabled("FurnitureLib")
                        || plugin.getServer().getPluginManager().isPluginEnabled("DiceFurniture");

        if (furniturePluginPresent) {
            plugin.getLogger()
                    .info(
                            "Furniture plugin detected - enabling custom furniture inventory support");

            // Initialize PDC keys for furniture detection
            try {
                furnitureLibKey = new NamespacedKey("furniturelib", "id");
                diceFurnitureKey = new NamespacedKey("dicefurniture", "id");
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to initialize furniture PDC keys", e);
            }
        } else if (!furnitureTitles.isEmpty()) {
            plugin.getLogger()
                    .warning(
                            "Furniture titles configured but no furniture plugin (FurnitureLib/DiceFurniture) detected!");
        }
    }

    private void loadConfig() {
        customTitles.clear();
        furnitureTitles.clear();

        // Load legacy format (direct title strings)
        enderchestTitle = plugin.getConfig().getString("settings.custom-inventory.enderchest", "");
        shulkerTitle = plugin.getConfig().getString("settings.custom-inventory.shulker", "");
        chestTitle = plugin.getConfig().getString("settings.custom-inventory.chest", "");

        // Load new format from titles section (preferred)
        ConfigurationSection titles =
                plugin.getConfig().getConfigurationSection("settings.custom-inventory.titles");
        if (titles != null) {
            // Override with titles if present
            enderchestTitle = titles.getString("ender_chest", enderchestTitle);
            shulkerTitle = titles.getString("shulker", shulkerTitle);
            chestTitle = titles.getString("chest", chestTitle);
            bookshelfTitle = titles.getString("bookshelf", "");

            // Load furniture-specific titles
            String washingMachineTitle = titles.getString("washing_machine", "");
            String fridgeTitle = titles.getString("fridge", "");

            if (!washingMachineTitle.isEmpty()) {
                furnitureTitles.put("washing_machine", washingMachineTitle);
                furnitureTitles.put("washingmachine", washingMachineTitle); // Alternative format
            }

            if (!fridgeTitle.isEmpty()) {
                furnitureTitles.put("fridge", fridgeTitle);
                furnitureTitles.put("refrigerator", fridgeTitle); // Alternative format
            }
        }

        // Load custom title mappings (legacy format)
        ConfigurationSection custom =
                plugin.getConfig().getConfigurationSection("settings.custom-inventory.custom");
        if (custom != null) {
            for (String key : custom.getKeys(false)) {
                ConfigurationSection section = custom.getConfigurationSection(key);
                if (section == null) continue;

                boolean stripColors = section.getBoolean("strip-colors", false);
                String originalTitle = section.getString("original-title", "");
                String newTitle = section.getString("new-title", "");

                if (!originalTitle.isEmpty() && !newTitle.isEmpty()) {
                    customTitles.put(
                            originalTitle.toLowerCase(),
                            new CustomTitleConfig(stripColors, newTitle));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!isEnabled()) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        Inventory inventory = event.getInventory();
        InventoryType type = inventory.getType();

        // Get current title as string
        Component titleComponent = event.getView().title();
        String titlePlain = PlainTextComponentSerializer.plainText().serialize(titleComponent);

        Component newTitle = null;

        // Check inventory type
        switch (type) {
            case SHULKER_BOX:
                if (!shulkerTitle.isEmpty()) {
                    newTitle = TextUtil.parseLegacy(shulkerTitle);
                }
                break;
            case CHEST:
                // Check if this is a bookshelf inventory
                if (type == InventoryType.CHEST
                        && !bookshelfTitle.isEmpty()
                        && titlePlain.toLowerCase().contains("bookshelf")) {
                    newTitle = TextUtil.parseLegacy(bookshelfTitle);
                } else if (!chestTitle.isEmpty()) {
                    newTitle = TextUtil.parseLegacy(chestTitle);
                }
                break;
            default:
                break;
        }

        // Check for furniture-based inventories
        if (newTitle == null && furniturePluginPresent) {
            String furnitureTitle = detectFurnitureInventory(inventory, titlePlain);
            if (furnitureTitle != null) {
                newTitle = TextUtil.parseLegacy(furnitureTitle);
            }
        }

        // Check custom title mappings
        if (newTitle == null) {
            String checkTitle = titlePlain.toLowerCase();
            for (Map.Entry<String, CustomTitleConfig> entry : customTitles.entrySet()) {
                String pattern = entry.getKey();
                if (checkTitle.contains(pattern) || pattern.contains(checkTitle)) {
                    newTitle = TextUtil.parseLegacy(entry.getValue().newTitle);
                    break;
                }
            }
        }

        // Apply new title if set
        if (newTitle != null) {
            // We need to use a different approach since InventoryOpenEvent
            // doesn't directly allow title changes
            // Instead, we'll use a scheduled task to reopen with the new title
            final Component finalTitle = newTitle;

            // For chest/shulker/enderchest, we can work around by
            // sending a custom packet or using Paper's API
            // Paper provides openInventory with title
            plugin.getServer()
                    .getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                if (player.isOnline()
                                        && player.getOpenInventory()
                                                .getTopInventory()
                                                .equals(inventory)) {
                                    // Paper API allows setting inventory title
                                    try {
                                        // Use reflection to call Paper-specific method if available
                                        player.getOpenInventory()
                                                .setTitle(TextUtil.serializeLegacy(finalTitle));
                                    } catch (Exception ignored) {
                                        // API might not be available
                                    }
                                }
                            });
        }
    }

    /**
     * Detects furniture inventory type and returns appropriate title
     *
     * @param inventory The inventory being opened
     * @param currentTitle The current inventory title
     * @return Custom title for furniture inventory, or null if not a furniture inventory
     */
    private String detectFurnitureInventory(Inventory inventory, String currentTitle) {
        if (furnitureTitles.isEmpty()) {
            return null;
        }

        // Check title for furniture type keywords
        String lowerTitle = currentTitle.toLowerCase();

        // Check for washing machine
        if (lowerTitle.contains("washing") || lowerTitle.contains("machine")) {
            String title = furnitureTitles.get("washing_machine");
            if (title != null) return title;
        }

        // Check for fridge/refrigerator
        if (lowerTitle.contains("fridge") || lowerTitle.contains("refrigerator")) {
            String title = furnitureTitles.get("fridge");
            if (title != null) return title;
        }

        // Try to detect furniture from inventory holder if it's a block
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof Block block) {
            String furnitureType = getFurnitureType(block);
            if (furnitureType != null) {
                return furnitureTitles.get(furnitureType);
            }
        }

        return null;
    }

    /**
     * Gets the furniture type from a block's PDC data
     *
     * @param block The block to check
     * @return Furniture type identifier, or null if not furniture
     */
    private String getFurnitureType(Block block) {
        // Furniture is typically stored as NOTE_BLOCK or BARRIER with custom model data
        Material type = block.getType();
        if (type != Material.NOTE_BLOCK && type != Material.BARRIER) {
            return null;
        }

        try {
            // Try FurnitureLib key
            if (furnitureLibKey != null) {
                var pdc = block.getChunk().getPersistentDataContainer();
                if (pdc.has(furnitureLibKey, PersistentDataType.STRING)) {
                    String furnitureId = pdc.get(furnitureLibKey, PersistentDataType.STRING);
                    return mapFurnitureIdToType(furnitureId);
                }
            }

            // Try DiceFurniture key
            if (diceFurnitureKey != null) {
                var pdc = block.getChunk().getPersistentDataContainer();
                if (pdc.has(diceFurnitureKey, PersistentDataType.STRING)) {
                    String furnitureId = pdc.get(diceFurnitureKey, PersistentDataType.STRING);
                    return mapFurnitureIdToType(furnitureId);
                }
            }
        } catch (Exception e) {
            // PDC access might fail, silently ignore
        }

        return null;
    }

    /**
     * Maps furniture plugin ID to our internal furniture type
     *
     * @param furnitureId The furniture ID from the plugin
     * @return Internal furniture type identifier
     */
    private String mapFurnitureIdToType(String furnitureId) {
        if (furnitureId == null) return null;

        String lowerId = furnitureId.toLowerCase();

        // Map common furniture IDs to types
        if (lowerId.contains("washing") || lowerId.contains("machine")) {
            return "washing_machine";
        } else if (lowerId.contains("fridge") || lowerId.contains("refrigerator")) {
            return "fridge";
        }

        return null;
    }

    public void reload() {
        loadConfig();
        initializeFurnitureSupport();
    }

    private record CustomTitleConfig(boolean stripColors, String newTitle) {}
}
