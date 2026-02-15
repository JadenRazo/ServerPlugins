package net.serverplugins.enchants.models;

import org.bukkit.Material;

public enum RuneType {
    FIRE("Fire Rune", Material.BLAZE_POWDER, "<red>"),
    WATER("Water Rune", Material.PRISMARINE_CRYSTALS, "<aqua>"),
    EARTH("Earth Rune", Material.CLAY_BALL, "<dark_green>"),
    AIR("Air Rune", Material.FEATHER, "<white>"),
    LIGHT("Light Rune", Material.GLOWSTONE_DUST, "<yellow>"),
    SHADOW("Shadow Rune", Material.CHARCOAL, "<dark_gray>"),
    ARCANE("Arcane Rune", Material.AMETHYST_SHARD, "<light_purple>"),
    NATURE("Nature Rune", Material.WHEAT_SEEDS, "<green>"),
    FROST("Frost Rune", Material.SNOWBALL, "<blue>"),
    STORM("Storm Rune", Material.LIGHTNING_ROD, "<gold>"),
    VOID("Void Rune", Material.ENDER_PEARL, "<dark_purple>"),
    SOUL("Soul Rune", Material.ECHO_SHARD, "<dark_aqua>"),
    BLOOD("Blood Rune", Material.REDSTONE, "<dark_red>"),
    CRYSTAL("Crystal Rune", Material.DIAMOND, "<aqua>"),
    IRON("Iron Rune", Material.IRON_NUGGET, "<gray>");

    private final String displayName;
    private final Material material;
    private final String color;

    RuneType(String displayName, Material material, String color) {
        this.displayName = displayName;
        this.material = material;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getMaterial() {
        return material;
    }

    public String getColor() {
        return color;
    }

    public String getColoredName() {
        return color + displayName;
    }
}
