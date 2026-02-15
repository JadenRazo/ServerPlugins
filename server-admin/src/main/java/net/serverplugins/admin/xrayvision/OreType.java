package net.serverplugins.admin.xrayvision;

import org.bukkit.ChatColor;
import org.bukkit.Material;

public enum OreType {
    DIAMOND(ChatColor.AQUA, Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE),
    GOLD(ChatColor.GOLD, Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.NETHER_GOLD_ORE),
    EMERALD(ChatColor.GREEN, Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE),
    ANCIENT_DEBRIS(ChatColor.DARK_RED, Material.ANCIENT_DEBRIS),
    SPAWNER(ChatColor.DARK_PURPLE, Material.SPAWNER),
    IRON(ChatColor.WHITE, Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE),
    COPPER(ChatColor.RED, Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE),
    LAPIS(ChatColor.BLUE, Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE),
    REDSTONE(ChatColor.DARK_RED, Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE),
    COAL(ChatColor.DARK_GRAY, Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE);

    private final ChatColor color;
    private final Material[] materials;

    OreType(ChatColor color, Material... materials) {
        this.color = color;
        this.materials = materials;
    }

    public ChatColor getColor() {
        return color;
    }

    public Material[] getMaterials() {
        return materials;
    }

    public static OreType fromMaterial(Material material) {
        for (OreType type : values()) {
            for (Material m : type.materials) {
                if (m == material) {
                    return type;
                }
            }
        }
        return null;
    }
}
