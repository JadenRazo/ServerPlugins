package net.serverplugins.enchants;

import java.util.List;
import net.serverplugins.enchants.enchantments.EnchantTier;
import org.bukkit.configuration.file.FileConfiguration;

public class EnchantsConfig {

    private final FileConfiguration config;

    public EnchantsConfig(FileConfiguration config) {
        this.config = config;
    }

    public double getCost(EnchantTier tier) {
        return config.getDouble("costs." + tier.name().toLowerCase(), 500);
    }

    public int getFragmentReward(EnchantTier tier, boolean won) {
        String key = "fragments." + (won ? "win" : "loss") + "." + tier.name().toLowerCase();
        return config.getInt(key, won ? 10 : 2);
    }

    public int getXpReward(EnchantTier tier, boolean won) {
        String key = "experience." + (won ? "win" : "loss") + "." + tier.name().toLowerCase();
        return config.getInt(key, won ? 15 : 3);
    }

    public int getTierLevelRequirement(EnchantTier tier) {
        return config.getInt("tiers." + tier.name().toLowerCase() + ".level", 0);
    }

    public int getTierUnlockRequirement(EnchantTier tier) {
        return switch (tier) {
            case COMMON -> 0;
            case UNCOMMON -> config.getInt("tiers.uncommon.common-unlocks", 2);
            case RARE -> config.getInt("tiers.rare.uncommon-unlocks", 2);
            case LEGENDARY -> config.getInt("tiers.legendary.rare-unlocks", 2);
        };
    }

    public int getDailyAttempts() {
        return config.getInt("daily-attempts", 1);
    }

    public int getShopPrice(String item) {
        return config.getInt("shop." + item, 50);
    }

    // Auto-Smelt
    public List<Integer> getAutoSmeltChances() {
        return config.getIntegerList("enchantments.auto-smelt.chances");
    }

    // Vein Miner
    public List<Integer> getVeinMinerMaxBlocks() {
        return config.getIntegerList("enchantments.vein-miner.max-blocks");
    }

    // Magnet
    public List<Integer> getMagnetRange() {
        return config.getIntegerList("enchantments.magnet.range");
    }

    public int getMagnetTickInterval() {
        return config.getInt("enchantments.magnet.tick-interval", 10);
    }

    // Soulbound
    public int getSoulboundMaxLevel() {
        return config.getInt("enchantments.soulbound.max-level", 3);
    }

    // Game settings
    public int getMemoryPairs(EnchantTier tier) {
        return config.getInt("games.memory.pairs." + tier.name().toLowerCase(), 6);
    }

    public int getMemoryTimeLimit() {
        return config.getInt("games.memory.time-limit", 60);
    }

    public int getMemoryMoveLimitMultiplier() {
        return config.getInt("games.memory.move-limit-multiplier", 3);
    }

    public int getForgeRunes(EnchantTier tier) {
        return config.getInt("games.forge.runes." + tier.name().toLowerCase(), 3);
    }

    public int getForgeTimeLimit() {
        return config.getInt("games.forge.time-limit", 45);
    }

    public int getAlchemyProperties(EnchantTier tier) {
        return config.getInt("games.alchemy.properties." + tier.name().toLowerCase(), 2);
    }

    public int getAlchemyEssences(EnchantTier tier) {
        return config.getInt("games.alchemy.essences." + tier.name().toLowerCase(), 4);
    }

    public int getAlchemyTimeLimit() {
        return config.getInt("games.alchemy.time-limit", 60);
    }

    public int getDecryptionCodeLength(EnchantTier tier) {
        return config.getInt("games.decryption.code-length." + tier.name().toLowerCase(), 4);
    }

    public int getDecryptionGuesses(EnchantTier tier) {
        return config.getInt("games.decryption.guesses." + tier.name().toLowerCase(), 8);
    }

    public int getDecryptionRunePoolSize(EnchantTier tier) {
        return config.getInt("games.decryption.rune-pool-size." + tier.name().toLowerCase(), 6);
    }

    public String getPrefix() {
        return config.getString(
                "messages.prefix", "<gradient:#9B59B6:#8E44AD>[Enchanter]</gradient> ");
    }

    public String getMessage(String key) {
        return config.getString("messages." + key, "");
    }
}
