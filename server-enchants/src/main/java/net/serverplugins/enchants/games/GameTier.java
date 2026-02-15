package net.serverplugins.enchants.games;

import net.serverplugins.enchants.enchantments.EnchantTier;

public enum GameTier {
    COMMON(EnchantTier.COMMON),
    UNCOMMON(EnchantTier.UNCOMMON),
    RARE(EnchantTier.RARE),
    LEGENDARY(EnchantTier.LEGENDARY);

    private final EnchantTier enchantTier;

    GameTier(EnchantTier enchantTier) {
        this.enchantTier = enchantTier;
    }

    public EnchantTier getEnchantTier() {
        return enchantTier;
    }

    public static GameTier fromEnchantTier(EnchantTier tier) {
        return switch (tier) {
            case COMMON -> COMMON;
            case UNCOMMON -> UNCOMMON;
            case RARE -> RARE;
            case LEGENDARY -> LEGENDARY;
        };
    }
}
