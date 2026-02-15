package net.serverplugins.enchants.enchantments;

import org.bukkit.Material;

public enum EnchantTier {
    COMMON("Common", "<green>", Material.LIME_STAINED_GLASS_PANE, 1),
    UNCOMMON("Uncommon", "<aqua>", Material.LIGHT_BLUE_STAINED_GLASS_PANE, 2),
    RARE("Rare", "<light_purple>", Material.MAGENTA_STAINED_GLASS_PANE, 3),
    LEGENDARY("Legendary", "<gold>", Material.ORANGE_STAINED_GLASS_PANE, 4);

    private final String displayName;
    private final String color;
    private final Material guiMaterial;
    private final int weight;

    EnchantTier(String displayName, String color, Material guiMaterial, int weight) {
        this.displayName = displayName;
        this.color = color;
        this.guiMaterial = guiMaterial;
        this.weight = weight;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColor() {
        return color;
    }

    public String getColoredName() {
        return color + displayName;
    }

    public Material getGuiMaterial() {
        return guiMaterial;
    }

    public int getWeight() {
        return weight;
    }
}
