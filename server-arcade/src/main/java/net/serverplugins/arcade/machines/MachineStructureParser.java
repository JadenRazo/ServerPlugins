package net.serverplugins.arcade.machines;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Parses machine structure definitions from YAML configuration. */
public class MachineStructureParser {

    /** Parse a complete machine section including item and structure. */
    public static MachineStructure parse(ConfigurationSection machineSection) {
        if (machineSection == null) return null;

        ItemStack placementItem = parseItem(machineSection.getConfigurationSection("item"));
        if (placementItem == null) {
            placementItem = new ItemStack(Material.STICK);
        }

        List<MachineStructure.StructureElement> elements = new ArrayList<>();

        ConfigurationSection structureSection = machineSection.getConfigurationSection("structure");
        if (structureSection != null) {
            for (String key : structureSection.getKeys(false)) {
                ConfigurationSection elementSection = structureSection.getConfigurationSection(key);
                if (elementSection != null) {
                    MachineStructure.StructureElement element = parseElement(elementSection);
                    if (element != null) {
                        elements.add(element);
                    }
                }
            }
        }

        return new MachineStructure(placementItem, elements);
    }

    /** Parse an item from configuration. */
    public static ItemStack parseItem(ConfigurationSection itemSection) {
        if (itemSection == null) return null;

        String materialName = itemSection.getString("material", "STICK");
        Material material = Material.matchMaterial(materialName.toUpperCase());
        if (material == null) {
            material = Material.STICK;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Custom model data
            if (itemSection.contains("custom_model_data")) {
                meta.setCustomModelData(itemSection.getInt("custom_model_data"));
            }

            // Display name
            String name = itemSection.getString("name");
            if (name != null) {
                meta.setDisplayName(name.replace("&", "§"));
            }

            // Lore
            List<String> lore = itemSection.getStringList("lore");
            if (!lore.isEmpty()) {
                meta.setLore(lore.stream().map(line -> line.replace("&", "§")).toList());
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    /** Parse a single structure element. */
    private static MachineStructure.StructureElement parseElement(ConfigurationSection section) {
        String type = section.getString("type", "block").toLowerCase();
        MachineStructure.Offset offset = parseOffset(section.getString("location", "0 0 0"));
        Direction direction = parseDirection(section.getString("direction"));

        switch (type) {
            case "block" -> {
                Material material =
                        Material.matchMaterial(
                                section.getString("material", "BARRIER").toUpperCase());
                if (material == null) material = Material.BARRIER;
                return new MachineStructure.BlockElement(material, offset, direction);
            }
            case "item" -> {
                ItemStack item = parseItem(section);
                if (item == null) item = new ItemStack(Material.STICK);
                return new MachineStructure.ItemElement(item, offset, direction);
            }
            case "seat", "seat1", "seat2", "seat3" -> {
                int seatNum = 1;
                if (type.length() > 4) {
                    try {
                        seatNum = Integer.parseInt(type.substring(4));
                    } catch (NumberFormatException ignored) {
                    }
                }
                ItemStack item = parseItem(section);
                if (item != null && item.getType() != Material.AIR) {
                    return MachineStructure.ItemElement.seat(seatNum, item, offset, direction);
                }
                return MachineStructure.ItemElement.seat(seatNum, offset, direction);
            }
            case "hologram" -> {
                String text = section.getString("text", section.getString("name", ""));
                if (text != null) {
                    text = text.replace("&", "§");
                }
                return MachineStructure.ItemElement.hologram(text, offset);
            }
            default -> {
                return null;
            }
        }
    }

    /** Parse an offset from a space-separated string "x y z". */
    private static MachineStructure.Offset parseOffset(String location) {
        if (location == null || location.isEmpty()) {
            return MachineStructure.Offset.ZERO;
        }

        String[] parts = location.trim().split("\\s+");
        if (parts.length < 3) {
            return MachineStructure.Offset.ZERO;
        }

        try {
            float x = Float.parseFloat(parts[0]);
            float y = Float.parseFloat(parts[1]);
            float z = Float.parseFloat(parts[2]);
            return new MachineStructure.Offset(x, y, z);
        } catch (NumberFormatException e) {
            return MachineStructure.Offset.ZERO;
        }
    }

    /** Parse a direction from string. */
    private static Direction parseDirection(String directionStr) {
        if (directionStr == null || directionStr.isEmpty()) {
            return null;
        }

        try {
            return Direction.valueOf(directionStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
