package net.serverplugins.commands.dynamic.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.serverplugins.api.effects.CustomSound;
import net.serverplugins.api.handlers.PlaceholderHandler;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Parser for converting YAML configuration into ConfigurableGui instances. */
public class GuiConfigParser {

    /**
     * Parse a GUI configuration section into a ConfigurableGui instance.
     *
     * @param section The configuration section containing GUI definition
     * @param player The player who will view the GUI (for placeholder parsing)
     * @return A configured GUI instance, or null if parsing fails
     */
    public static ConfigurableGui parseGui(ConfigurationSection section, Player player) {
        if (section == null) {
            return null;
        }

        try {
            // Parse basic properties
            String title = section.getString("title", "Menu");
            title = PlaceholderHandler.parse(player, title);
            int size = section.getInt("size", 27);

            // Validate size
            if (size % 9 != 0 || size < 9 || size > 54) {
                size = 27; // Default to valid size
            }

            // Parse sounds
            CustomSound openSound = parseSound(section.getConfigurationSection("open-sound"));
            CustomSound clickSound = parseSound(section.getConfigurationSection("click-sound"));

            // Parse buttons
            Map<Integer, GuiButton> buttons =
                    parseButtons(section.getConfigurationSection("buttons"), player);

            return new ConfigurableGui(title, size, openSound, clickSound, buttons);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /** Parse a sound configuration section. */
    private static CustomSound parseSound(ConfigurationSection section) {
        if (section == null) {
            return CustomSound.NONE;
        }

        String type = section.getString("type", "");
        if (type.isEmpty() || type.equalsIgnoreCase("none")) {
            return CustomSound.NONE;
        }

        try {
            Sound sound = Sound.valueOf(type.toUpperCase());
            float volume = (float) section.getDouble("volume", 1.0);
            float pitch = (float) section.getDouble("pitch", 1.0);
            return new CustomSound(sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            return CustomSound.NONE;
        }
    }

    /** Parse all buttons from configuration. */
    private static Map<Integer, GuiButton> parseButtons(
            ConfigurationSection section, Player player) {
        Map<Integer, GuiButton> buttons = new HashMap<>();

        if (section == null) {
            return buttons;
        }

        for (String slotStr : section.getKeys(false)) {
            try {
                int slot = Integer.parseInt(slotStr);
                ConfigurationSection buttonSection = section.getConfigurationSection(slotStr);

                if (buttonSection != null) {
                    GuiButton button = parseButton(buttonSection, player);
                    if (button != null) {
                        buttons.put(slot, button);
                    }
                }
            } catch (NumberFormatException e) {
                // Skip invalid slot numbers
            }
        }

        return buttons;
    }

    /** Parse a single button configuration. */
    private static GuiButton parseButton(ConfigurationSection section, Player player) {
        if (section == null) {
            return null;
        }

        // Check permission requirement
        String permission = section.getString("permission", "");
        if (!permission.isEmpty() && !player.hasPermission(permission) && !player.isOp()) {
            return null; // Player doesn't have permission, don't show button
        }

        // Parse item
        ItemStack item = parseItem(section.getConfigurationSection("item"), player);

        // Parse action
        String action = section.getString("action", "");

        // Parse click sound
        CustomSound clickSound = parseSound(section.getConfigurationSection("click-sound"));

        return new GuiButton(item, action, clickSound);
    }

    /** Parse an ItemStack from configuration. */
    private static ItemStack parseItem(ConfigurationSection section, Player player) {
        if (section == null) {
            return new ItemStack(Material.BARRIER);
        }

        try {
            // Parse material
            String materialStr = section.getString("material", "BARRIER");
            Material material;
            try {
                material = Material.valueOf(materialStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                material = Material.BARRIER;
            }

            // Parse amount
            int amount = section.getInt("amount", 1);
            if (amount < 1) amount = 1;
            if (amount > 64) amount = 64;

            // Create item
            ItemStack item = new ItemStack(material, amount);
            ItemMeta meta = item.getItemMeta();

            if (meta != null) {
                // Parse name
                String name = section.getString("name", "");
                if (!name.isEmpty()) {
                    name = PlaceholderHandler.parse(player, name);
                    meta.displayName(TextUtil.parse(name));
                }

                // Parse lore
                List<String> loreStrings = section.getStringList("lore");
                if (!loreStrings.isEmpty()) {
                    List<Component> lore = new ArrayList<>();
                    for (String line : loreStrings) {
                        line = PlaceholderHandler.parse(player, line);
                        lore.add(TextUtil.parse(line));
                    }
                    meta.lore(lore);
                }

                // Parse custom model data
                int customModelData = section.getInt("custom-model-data", 0);
                if (customModelData > 0) {
                    meta.setCustomModelData(customModelData);
                }

                // Parse enchantment glint
                boolean glint = section.getBoolean("glint", false);
                if (glint) {
                    meta.setEnchantmentGlintOverride(true);
                }

                item.setItemMeta(meta);
            }

            return item;
        } catch (Exception e) {
            // Return fallback item on any error
            return new ItemStack(Material.BARRIER);
        }
    }
}
