package net.serverplugins.items.managers;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.items.mechanics.Mechanic;
import net.serverplugins.items.mechanics.MechanicRegistry;
import net.serverplugins.items.mechanics.impl.DurabilityMechanic;
import net.serverplugins.items.models.CustomItem;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class ItemManager {

    public static final NamespacedKey ITEM_ID_KEY = new NamespacedKey("serveritems", "item_id");

    private final Logger logger;
    private final MechanicRegistry mechanicRegistry;
    private final Map<String, CustomItem> items = new LinkedHashMap<>();

    public ItemManager(Logger logger) {
        this.logger = logger;
        this.mechanicRegistry = new MechanicRegistry();
    }

    public MechanicRegistry getMechanicRegistry() {
        return mechanicRegistry;
    }

    public void loadItems(File itemsFolder) {
        items.clear();

        if (!itemsFolder.exists()) {
            itemsFolder.mkdirs();
            return;
        }

        loadFromDirectory(itemsFolder);
        logger.info("Loaded " + items.size() + " custom items.");
    }

    private void loadFromDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                loadFromDirectory(file);
            } else if (file.getName().endsWith(".yml") || file.getName().endsWith(".yaml")) {
                loadFromFile(file);
            }
        }
    }

    private void loadFromFile(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        for (String id : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(id);
            if (section == null) continue;

            try {
                CustomItem item = parseItem(id, section);
                items.put(id, item);
            } catch (Exception e) {
                logger.warning(
                        "Failed to load item '"
                                + id
                                + "' from "
                                + file.getName()
                                + ": "
                                + e.getMessage());
            }
        }
    }

    private CustomItem parseItem(String id, ConfigurationSection section) {
        String materialName = section.getString("material", "STONE");
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            throw new IllegalArgumentException("Unknown material: " + materialName);
        }

        String displayName = section.getString("display_name", id);
        List<String> lore = section.getStringList("lore");
        int customModelData = section.getInt("custom_model_data", 0);
        boolean unbreakable = section.getBoolean("unbreakable", false);
        boolean glow = section.getBoolean("glow", false);

        // Parse enchantments
        Map<Enchantment, Integer> enchantments = new HashMap<>();
        ConfigurationSection enchantSection = section.getConfigurationSection("enchants");
        if (enchantSection != null) {
            for (String enchantName : enchantSection.getKeys(false)) {
                Enchantment enchantment =
                        Enchantment.getByKey(NamespacedKey.minecraft(enchantName.toLowerCase()));
                if (enchantment != null) {
                    enchantments.put(enchantment, enchantSection.getInt(enchantName, 1));
                } else {
                    logger.warning(
                            "Unknown enchantment '" + enchantName + "' on item '" + id + "'");
                }
            }
        }

        // Parse item flags
        List<ItemFlag> itemFlags = new ArrayList<>();
        for (String flagName : section.getStringList("item_flags")) {
            try {
                itemFlags.add(ItemFlag.valueOf(flagName.toUpperCase()));
            } catch (IllegalArgumentException e) {
                logger.warning("Unknown item flag '" + flagName + "' on item '" + id + "'");
            }
        }

        // Parse mechanics
        List<Mechanic> mechanics = new ArrayList<>();
        ConfigurationSection mechanicsSection = section.getConfigurationSection("mechanics");
        if (mechanicsSection != null) {
            for (String mechanicId : mechanicsSection.getKeys(false)) {
                ConfigurationSection mechanicConfig =
                        mechanicsSection.getConfigurationSection(mechanicId);
                if (mechanicConfig == null) continue;

                Mechanic mechanic = mechanicRegistry.create(mechanicId, mechanicConfig);
                if (mechanic != null) {
                    mechanics.add(mechanic);
                } else {
                    logger.warning("Unknown mechanic '" + mechanicId + "' on item '" + id + "'");
                }
            }
        }

        return new CustomItem(
                id,
                material,
                displayName,
                lore,
                customModelData,
                enchantments,
                itemFlags,
                mechanics,
                unbreakable,
                glow);
    }

    public ItemStack buildItemStack(CustomItem item, int amount) {
        ItemBuilder builder =
                ItemBuilder.of(item.getMaterial()).name(item.getDisplayName()).amount(amount);

        if (!item.getLore().isEmpty()) {
            builder.lore(item.getLore().toArray(new String[0]));
        }

        if (item.getCustomModelData() > 0) {
            builder.customModelData(item.getCustomModelData());
        }

        if (item.isGlow()) {
            builder.glow();
        }

        if (item.isUnbreakable()) {
            builder.unbreakable(true);
        }

        ItemStack stack = builder.build();
        ItemMeta meta = stack.getItemMeta();

        // Apply enchantments
        for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
            meta.addEnchant(entry.getKey(), entry.getValue(), true);
        }

        // Apply item flags
        for (ItemFlag flag : item.getItemFlags()) {
            meta.addItemFlags(flag);
        }

        // Set the item ID in PDC
        meta.getPersistentDataContainer().set(ITEM_ID_KEY, PersistentDataType.STRING, item.getId());

        stack.setItemMeta(meta);

        // Initialize mechanic-specific PDC data
        DurabilityMechanic durability = item.getMechanic(DurabilityMechanic.class);
        if (durability != null) {
            durability.initializeDurability(stack);
        }

        return stack;
    }

    public CustomItem getItem(String id) {
        return items.get(id);
    }

    public String getItemId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(ITEM_ID_KEY, PersistentDataType.STRING);
    }

    public CustomItem getCustomItem(ItemStack stack) {
        String id = getItemId(stack);
        if (id == null) return null;
        return items.get(id);
    }

    public boolean isCustomItem(ItemStack stack) {
        return getItemId(stack) != null;
    }

    public Collection<CustomItem> getAllItems() {
        return Collections.unmodifiableCollection(items.values());
    }

    public int getItemCount() {
        return items.size();
    }
}
