package net.serverplugins.deathbuyback.managers;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.serverplugins.deathbuyback.ServerDeathBuyback;
import net.serverplugins.deathbuyback.models.PricingResult;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public class PricingManager {

    private final ServerDeathBuyback plugin;
    private final Map<Material, Double> itemPrices = new HashMap<>();

    public PricingManager(ServerDeathBuyback plugin) {
        this.plugin = plugin;
        loadPrices();
    }

    public void reloadPrices() {
        itemPrices.clear();
        loadPrices();
    }

    private void loadPrices() {
        List<String> sources = plugin.getDeathBuybackConfig().getPriceSources();

        for (String source : sources) {
            switch (source.toUpperCase()) {
                case "SELLGUI" -> loadSellGuiPrices();
                case "ESSENTIALS" -> loadEssentialsPrices();
                case "BUILTIN" -> loadBuiltinPrices();
            }
        }

        plugin.getLogger().info("Loaded " + itemPrices.size() + " item prices");
    }

    private void loadSellGuiPrices() {
        File sellGuiFile = new File(Bukkit.getPluginsFolder(), "SellGUI/itemprices.yml");
        if (!sellGuiFile.exists()) {
            plugin.getLogger().info("SellGUI itemprices.yml not found, skipping");
            return;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(sellGuiFile);
            for (String key : config.getKeys(false)) {
                if (key.startsWith("flat-enchantment")
                        || key.startsWith("multiplier-enchantment")) {
                    continue;
                }

                try {
                    Material mat = Material.valueOf(key.toUpperCase());
                    double price = config.getDouble(key);
                    if (price > 0 && !itemPrices.containsKey(mat)) {
                        itemPrices.put(mat, price);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Unknown material, skip
                }
            }
            plugin.getLogger().info("Loaded prices from SellGUI");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load SellGUI prices: " + e.getMessage());
        }
    }

    private void loadEssentialsPrices() {
        File essentialsFile = new File(Bukkit.getPluginsFolder(), "Essentials/worth.yml");
        if (!essentialsFile.exists()) {
            plugin.getLogger().info("Essentials worth.yml not found, skipping");
            return;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(essentialsFile);
            for (String key : config.getKeys(false)) {
                try {
                    Material mat = Material.valueOf(key.toUpperCase());
                    double price = config.getDouble(key);
                    if (price > 0 && !itemPrices.containsKey(mat)) {
                        itemPrices.put(mat, price);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Unknown material, skip
                }
            }
            plugin.getLogger().info("Loaded prices from Essentials");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load Essentials prices: " + e.getMessage());
        }
    }

    private void loadBuiltinPrices() {
        // Fallback prices for common items if not loaded from other sources
        Map<Material, Double> builtinPrices =
                Map.ofEntries(
                        Map.entry(Material.DIAMOND, 200.0),
                        Map.entry(Material.DIAMOND_BLOCK, 1800.0),
                        Map.entry(Material.DIAMOND_SWORD, 450.0),
                        Map.entry(Material.DIAMOND_PICKAXE, 650.0),
                        Map.entry(Material.DIAMOND_AXE, 650.0),
                        Map.entry(Material.DIAMOND_SHOVEL, 210.0),
                        Map.entry(Material.DIAMOND_HOE, 400.0),
                        Map.entry(Material.DIAMOND_HELMET, 1000.0),
                        Map.entry(Material.DIAMOND_CHESTPLATE, 1600.0),
                        Map.entry(Material.DIAMOND_LEGGINGS, 1400.0),
                        Map.entry(Material.DIAMOND_BOOTS, 800.0),
                        Map.entry(Material.NETHERITE_INGOT, 500.0),
                        Map.entry(Material.NETHERITE_SWORD, 700.0),
                        Map.entry(Material.NETHERITE_PICKAXE, 900.0),
                        Map.entry(Material.NETHERITE_AXE, 900.0),
                        Map.entry(Material.NETHERITE_SHOVEL, 450.0),
                        Map.entry(Material.NETHERITE_HOE, 600.0),
                        Map.entry(Material.NETHERITE_HELMET, 1300.0),
                        Map.entry(Material.NETHERITE_CHESTPLATE, 2000.0),
                        Map.entry(Material.NETHERITE_LEGGINGS, 1800.0),
                        Map.entry(Material.NETHERITE_BOOTS, 1100.0),
                        Map.entry(Material.IRON_INGOT, 20.0),
                        Map.entry(Material.GOLD_INGOT, 35.0),
                        Map.entry(Material.EMERALD, 100.0),
                        Map.entry(Material.COAL, 15.0),
                        Map.entry(Material.LAPIS_LAZULI, 25.0),
                        Map.entry(Material.REDSTONE, 10.0),
                        Map.entry(Material.QUARTZ, 15.0),
                        Map.entry(Material.TOTEM_OF_UNDYING, 2500.0),
                        Map.entry(Material.ELYTRA, 5000.0),
                        Map.entry(Material.TRIDENT, 3500.0),
                        Map.entry(Material.MACE, 4000.0),
                        Map.entry(Material.NETHER_STAR, 2500.0),
                        Map.entry(Material.ENCHANTED_GOLDEN_APPLE, 1000.0),
                        Map.entry(Material.GOLDEN_APPLE, 150.0));

        for (Map.Entry<Material, Double> entry : builtinPrices.entrySet()) {
            if (!itemPrices.containsKey(entry.getKey())) {
                itemPrices.put(entry.getKey(), entry.getValue());
            }
        }
    }

    /** Calculate the total worth of an inventory. */
    public PricingResult calculateInventoryWorth(
            ItemStack[] inventory, ItemStack[] armor, ItemStack offhand) {
        double baseWorth = 0;
        int itemCount = 0;

        // Calculate inventory worth
        if (inventory != null) {
            for (ItemStack item : inventory) {
                if (item == null || item.getType().isAir()) continue;
                baseWorth += calculateItemWorth(item);
                itemCount += item.getAmount();
            }
        }

        // Calculate armor worth
        if (armor != null) {
            for (ItemStack item : armor) {
                if (item == null || item.getType().isAir()) continue;
                baseWorth += calculateItemWorth(item);
                itemCount += item.getAmount();
            }
        }

        // Calculate offhand worth
        if (offhand != null && !offhand.getType().isAir()) {
            baseWorth += calculateItemWorth(offhand);
            itemCount += offhand.getAmount();
        }

        // Apply markup and minimum
        double buybackPrice = baseWorth * plugin.getDeathBuybackConfig().getMarkup();
        buybackPrice = Math.max(buybackPrice, plugin.getDeathBuybackConfig().getMinimumPrice());

        return new PricingResult(baseWorth, buybackPrice, itemCount);
    }

    /** Calculate the worth of a single item stack. */
    public double calculateItemWorth(ItemStack item) {
        if (item == null || item.getType().isAir()) return 0;

        // Get base price
        double basePrice = getBasePrice(item.getType()) * item.getAmount();

        // Apply enchantment multiplier
        basePrice *= calculateEnchantmentMultiplier(item);

        // Add rare item bonus
        basePrice += plugin.getDeathBuybackConfig().getRareItemBonus(item.getType());

        // Apply durability factor
        basePrice *= getDurabilityFactor(item);

        // Handle shulker boxes specially
        if (item.getType().name().contains("SHULKER_BOX")) {
            basePrice += calculateShulkerContentsWorth(item);
        }

        return basePrice;
    }

    /** Get the base price for a material. */
    public double getBasePrice(Material material) {
        return itemPrices.getOrDefault(material, 0.0);
    }

    /** Calculate enchantment multiplier for an item. */
    private double calculateEnchantmentMultiplier(ItemStack item) {
        if (!item.hasItemMeta()) return 1.0;

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasEnchants()) return 1.0;

        double multiplier = 1.0;
        Map<Enchantment, Integer> enchants = meta.getEnchants();

        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            Enchantment enchant = entry.getKey();
            int level = entry.getValue();
            int maxLevel = enchant.getMaxLevel();

            // Higher levels = more valuable (0.05 per level ratio)
            // Level 1/5 = 1.01x, Level 5/5 = 1.05x per enchant
            multiplier += 0.05 * ((double) level / maxLevel);
        }

        // Cap at maximum multiplier
        return Math.min(multiplier, plugin.getDeathBuybackConfig().getMaxEnchantMultiplier());
    }

    /** Get durability factor for damaged items. */
    private double getDurabilityFactor(ItemStack item) {
        if (!item.hasItemMeta()) return 1.0;

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return 1.0;
        if (!damageable.hasDamage()) return 1.0;

        int maxDurability = item.getType().getMaxDurability();
        if (maxDurability == 0) return 1.0;

        int remaining = maxDurability - damageable.getDamage();
        double factor = (double) remaining / maxDurability;

        // Minimum 30% value for damaged items
        return Math.max(0.3, factor);
    }

    /** Calculate the worth of items inside a shulker box. */
    private double calculateShulkerContentsWorth(ItemStack shulkerItem) {
        if (!shulkerItem.hasItemMeta()) return 0;

        ItemMeta meta = shulkerItem.getItemMeta();
        if (!(meta instanceof BlockStateMeta blockMeta)) return 0;

        if (!(blockMeta.getBlockState() instanceof ShulkerBox shulker)) return 0;

        double contentsWorth = 0;
        for (ItemStack item : shulker.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                contentsWorth += calculateItemWorth(item);
            }
        }

        return contentsWorth;
    }

    /** Get a formatted price string. */
    public String formatPrice(double price) {
        return plugin.getEconomyProvider().format(price);
    }
}
