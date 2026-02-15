package net.serverplugins.enchants.games;

import org.bukkit.Material;

public enum GameType {
    MEMORY("Rune Memory Matrix", Material.BOOK, "memory"),
    FORGE("Enchantment Forge", Material.ANVIL, "forge"),
    ALCHEMY("Alchemical Synthesis", Material.BREWING_STAND, "alchemy"),
    DECRYPTION("Rune Decryption", Material.MAP, "decryption");

    private final String displayName;
    private final Material icon;
    private final String configKey;

    GameType(String displayName, Material icon, String configKey) {
        this.displayName = displayName;
        this.icon = icon;
        this.configKey = configKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public String getConfigKey() {
        return configKey;
    }
}
