package net.serverplugins.backpacks;

import java.util.*;
import net.serverplugins.api.messages.PluginMessenger;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public class BackpacksConfig {

    private final ServerBackpacks plugin;
    private final FileConfiguration config;
    private final Map<String, BackpackType> backpackTypes = new HashMap<>();
    private final Set<Material> blacklistedItems = new HashSet<>();
    private PluginMessenger messenger;

    public BackpacksConfig(ServerBackpacks plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.messenger =
                new PluginMessenger(
                        config, "messages", "<gradient:#ff6b6b:#feca57>[Backpacks]</gradient> ");
        loadBackpackTypes();
        loadBlacklist();
    }

    private void loadBackpackTypes() {
        backpackTypes.clear();
        ConfigurationSection section = config.getConfigurationSection("backpacks");

        if (section != null) {
            for (String key : section.getKeys(false)) {
                String displayName = section.getString(key + ".display-name", key);
                // GUI title defaults to minimal text if not specified (avoids custom font issues
                // with resource packs)
                String guiTitle = section.getString(key + ".gui-title", "<white>");
                String materialName = section.getString(key + ".material", "CHEST");
                int size = section.getInt(key + ".size", 27);
                String permission =
                        section.getString(key + ".permission", "serverbackpacks." + key);
                int customModelData = section.getInt(key + ".custom-model-data", 0);

                Material material;
                try {
                    material = Material.valueOf(materialName);
                } catch (IllegalArgumentException e) {
                    material = Material.CHEST;
                }

                // Parse crafting section
                boolean craftingEnabled = section.getBoolean(key + ".crafting.enabled", false);
                String[] craftingShape = null;
                Map<Character, Material> craftingIngredients = new HashMap<>();

                if (craftingEnabled && section.contains(key + ".crafting")) {
                    List<String> shapeList = section.getStringList(key + ".crafting.shape");
                    if (!shapeList.isEmpty()) {
                        craftingShape = shapeList.toArray(new String[0]);
                    }

                    ConfigurationSection ingredientsSection =
                            section.getConfigurationSection(key + ".crafting.ingredients");
                    if (ingredientsSection != null) {
                        for (String ingredientKey : ingredientsSection.getKeys(false)) {
                            if (ingredientKey.length() == 1) {
                                char symbol = ingredientKey.charAt(0);
                                String ingredientMaterial =
                                        ingredientsSection.getString(ingredientKey);
                                try {
                                    Material mat = Material.valueOf(ingredientMaterial);
                                    craftingIngredients.put(symbol, mat);
                                } catch (IllegalArgumentException e) {
                                    plugin.getLogger()
                                            .warning(
                                                    "Invalid material '"
                                                            + ingredientMaterial
                                                            + "' for crafting ingredient '"
                                                            + symbol
                                                            + "' in backpack '"
                                                            + key
                                                            + "'");
                                }
                            }
                        }
                    }
                }

                backpackTypes.put(
                        key.toLowerCase(),
                        new BackpackType(
                                key,
                                displayName,
                                guiTitle,
                                material,
                                size,
                                permission,
                                customModelData,
                                craftingEnabled,
                                craftingShape,
                                craftingIngredients));
            }
        }
    }

    private void loadBlacklist() {
        blacklistedItems.clear();
        List<String> list = config.getStringList("blacklist");

        for (String item : list) {
            try {
                blacklistedItems.add(Material.valueOf(item));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public Map<String, BackpackType> getBackpackTypes() {
        return backpackTypes;
    }

    public BackpackType getBackpackType(String name) {
        return backpackTypes.get(name.toLowerCase());
    }

    public boolean isBlacklisted(Material material) {
        if (material.name().contains("SHULKER_BOX")) return true;
        return blacklistedItems.contains(material);
    }

    public boolean preventNesting() {
        return config.getBoolean("settings.prevent-nesting", true);
    }

    public Sound getOpenSound() {
        try {
            return Sound.valueOf(config.getString("settings.open-sound", "BLOCK_CHEST_OPEN"));
        } catch (IllegalArgumentException e) {
            return Sound.BLOCK_CHEST_OPEN;
        }
    }

    public Sound getCloseSound() {
        try {
            return Sound.valueOf(config.getString("settings.close-sound", "BLOCK_CHEST_CLOSE"));
        } catch (IllegalArgumentException e) {
            return Sound.BLOCK_CHEST_CLOSE;
        }
    }

    public String getMessage(String key) {
        return messenger.getMessage(key);
    }

    public PluginMessenger getMessenger() {
        return messenger;
    }

    public void reload() {
        messenger.reload();
        loadBackpackTypes();
        loadBlacklist();
    }

    public boolean isItemPreviewEnabled() {
        return config.getBoolean("item-preview.enabled", true);
    }

    public int getItemPreviewMaxItems() {
        return config.getInt("item-preview.max-items", 5);
    }

    public String getItemPreviewFormat() {
        return config.getString("item-preview.format", "<gray>%amount%x %item%");
    }

    public boolean isCacheInventoriesEnabled() {
        return config.getBoolean("performance.cache-inventories", true);
    }

    public boolean isRightClickToAddEnabled() {
        return config.getBoolean("features.right-click-to-add", true);
    }

    public boolean isAllowInCreative() {
        return config.getBoolean("features.allow-in-creative", false);
    }

    /**
     * Get the upgrade cost for transitioning from one tier to another. Returns a map of Material to
     * required amount.
     */
    public Map<Material, Integer> getUpgradeCost(BackpackTier fromTier, BackpackTier toTier) {
        Map<Material, Integer> cost = new HashMap<>();
        String path = "upgrade-costs." + fromTier.getId() + "-to-" + toTier.getId();

        ConfigurationSection costSection = config.getConfigurationSection(path);
        if (costSection == null) {
            return getDefaultUpgradeCost(toTier);
        }

        for (String materialName : costSection.getKeys(false)) {
            try {
                Material material = Material.valueOf(materialName);
                int amount = costSection.getInt(materialName, 0);
                if (amount > 0) {
                    cost.put(material, amount);
                }
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Invalid material in upgrade cost: " + materialName);
            }
        }

        return cost.isEmpty() ? getDefaultUpgradeCost(toTier) : cost;
    }

    private Map<Material, Integer> getDefaultUpgradeCost(BackpackTier toTier) {
        return switch (toTier) {
            case TIER_2 -> Map.of(Material.IRON_INGOT, 8);
            case TIER_3 -> Map.of(Material.IRON_INGOT, 16);
            case TIER_4 -> Map.of(Material.GOLD_INGOT, 16);
            case TIER_5 -> Map.of(Material.DIAMOND, 8);
            case TIER_6 -> Map.of(Material.NETHERITE_INGOT, 4);
            default -> Map.of();
        };
    }

    public record BackpackType(
            String id,
            String displayName,
            String guiTitle,
            Material material,
            int size,
            String permission,
            int customModelData,
            boolean craftingEnabled,
            String[] craftingShape,
            Map<Character, Material> craftingIngredients) {}
}
