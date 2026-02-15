package net.serverplugins.deathbuyback;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.serverplugins.api.messages.Placeholder;
import net.serverplugins.api.messages.PluginMessenger;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class DeathBuybackConfig {

    private final ServerDeathBuyback plugin;

    // Messaging system
    private PluginMessenger messenger;

    // Pricing
    private double markup;
    private double maxEnchantMultiplier;
    private double minimumPrice;

    // Slots by rank
    private final Map<String, Integer> slotsByRank = new HashMap<>();

    // Expiration
    private int expirationHours;
    private int cleanupIntervalHours;

    // Price sources
    private List<String> priceSources;

    // Notifications
    private boolean notifyOnDeath;
    private boolean notifyOnLogin;

    // Database
    private boolean useApiDatabase;

    // Rare items
    private final Map<Material, Double> rareItemBonuses = new HashMap<>();
    private double netheriteBonusValue;

    // Messages (kept for backward compatibility)
    private final Map<String, String> messages = new HashMap<>();

    public DeathBuybackConfig(ServerDeathBuyback plugin) {
        this.plugin = plugin;
        reload();
    }

    public final void reload() {
        // Pricing
        markup = plugin.getConfig().getDouble("pricing.markup", 1.5);
        maxEnchantMultiplier = plugin.getConfig().getDouble("pricing.max-enchant-multiplier", 3.0);
        minimumPrice = plugin.getConfig().getDouble("pricing.minimum-price", 100.0);

        // Slots
        slotsByRank.clear();
        ConfigurationSection slotsSection = plugin.getConfig().getConfigurationSection("slots");
        if (slotsSection != null) {
            for (String rank : slotsSection.getKeys(false)) {
                slotsByRank.put(rank.toLowerCase(), slotsSection.getInt(rank));
            }
        }

        // Expiration
        expirationHours = plugin.getConfig().getInt("expiration.duration-hours", 72);
        cleanupIntervalHours = plugin.getConfig().getInt("expiration.cleanup-interval", 24);

        // Price sources
        priceSources = plugin.getConfig().getStringList("price-sources");
        if (priceSources.isEmpty()) {
            priceSources = List.of("SELLGUI", "ESSENTIALS", "BUILTIN");
        }

        // Notifications
        notifyOnDeath = plugin.getConfig().getBoolean("notifications.on-death", true);
        notifyOnLogin = plugin.getConfig().getBoolean("notifications.on-login", true);

        // Database
        useApiDatabase = plugin.getConfig().getBoolean("database.use-api-database", true);

        // Rare items
        rareItemBonuses.clear();
        ConfigurationSection rareSection = plugin.getConfig().getConfigurationSection("rare-items");
        if (rareSection != null) {
            for (String key : rareSection.getKeys(false)) {
                try {
                    Material mat = Material.valueOf(key.toUpperCase());
                    rareItemBonuses.put(mat, rareSection.getDouble(key));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Unknown material in rare-items: " + key);
                }
            }
        }
        netheriteBonusValue = plugin.getConfig().getDouble("netherite-bonus", 200.0);

        // Messages (kept for backward compatibility)
        messages.clear();
        ConfigurationSection msgSection = plugin.getConfig().getConfigurationSection("messages");
        if (msgSection != null) {
            for (String key : msgSection.getKeys(false)) {
                messages.put(key, msgSection.getString(key));
            }
        }

        // Initialize PluginMessenger
        this.messenger =
                new PluginMessenger(
                        plugin.getConfig(),
                        "messages",
                        "<gradient:#e74c3c:#9b59b6>[DeathBuyback]</gradient> ");
    }

    public double getMarkup() {
        return markup;
    }

    public double getMaxEnchantMultiplier() {
        return maxEnchantMultiplier;
    }

    public double getMinimumPrice() {
        return minimumPrice;
    }

    public int getSlotsForRank(String rank) {
        return slotsByRank.getOrDefault(rank.toLowerCase(), 0);
    }

    public int getMaxSlotsForPlayer(Player player) {
        // Check permission-based slots first (highest priority)
        for (int i = 10; i >= 1; i--) {
            if (player.hasPermission("deathbuyback.slots." + i)) {
                return i;
            }
        }

        // Fall back to rank-based defaults
        // Try to get player's primary group from LuckPerms
        try {
            net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user =
                    lp.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                String primaryGroup = user.getPrimaryGroup();
                int slots = getSlotsForRank(primaryGroup);
                if (slots > 0) return slots;

                // Check inherited groups
                for (String rank : slotsByRank.keySet()) {
                    if (player.hasPermission("group." + rank)) {
                        int rankSlots = getSlotsForRank(rank);
                        if (rankSlots > slots) slots = rankSlots;
                    }
                }
                return slots;
            }
        } catch (Exception ignored) {
            // LuckPerms not available
        }

        return slotsByRank.getOrDefault("default", 0);
    }

    public int getExpirationHours() {
        return expirationHours;
    }

    public long getExpirationMillis() {
        return expirationHours * 60L * 60L * 1000L;
    }

    public int getCleanupIntervalHours() {
        return cleanupIntervalHours;
    }

    public List<String> getPriceSources() {
        return priceSources;
    }

    public boolean notifyOnDeath() {
        return notifyOnDeath;
    }

    public boolean notifyOnLogin() {
        return notifyOnLogin;
    }

    public boolean useApiDatabase() {
        return useApiDatabase;
    }

    public double getRareItemBonus(Material material) {
        // Check exact match
        Double bonus = rareItemBonuses.get(material);
        if (bonus != null) return bonus;

        // Check for shulker box variants
        if (material.name().contains("SHULKER_BOX")) {
            return rareItemBonuses.getOrDefault(Material.SHULKER_BOX, 1300.0);
        }

        // Check for netherite items
        if (material.name().startsWith("NETHERITE_")) {
            return netheriteBonusValue;
        }

        return 0.0;
    }

    public double getNetheriteBonusValue() {
        return netheriteBonusValue;
    }

    /**
     * Gets the PluginMessenger instance for sending messages.
     *
     * @return The PluginMessenger instance
     */
    public PluginMessenger getMessenger() {
        return messenger;
    }

    /**
     * Gets a raw message from config.
     *
     * @param key The message key
     * @return The raw message string
     * @deprecated Use {@link #getMessenger()} instead
     */
    @Deprecated
    public String getMessage(String key) {
        return messages.getOrDefault(key, "");
    }

    /**
     * Gets the configured prefix.
     *
     * @return The message prefix
     * @deprecated Use {@link #getMessenger()}.{@link PluginMessenger#getPrefix()} instead
     */
    @Deprecated
    public String getPrefix() {
        return messenger.getPrefix();
    }

    /**
     * Sends a configured message to a player.
     *
     * @param player The recipient player
     * @param key The message key
     * @deprecated Use {@link #getMessenger()}.{@link PluginMessenger#send(Player, String,
     *     Placeholder...)} instead
     */
    @Deprecated
    public void sendMessage(Player player, String key) {
        messenger.send(player, key);
    }

    /**
     * Sends a configured message to a player with replacements.
     *
     * @param player The recipient player
     * @param key The message key
     * @param replacements Key-value pairs for replacement (old format)
     * @deprecated Use {@link #getMessenger()}.{@link PluginMessenger#send(Player, String,
     *     Placeholder...)} with Placeholder.of() instead
     */
    @Deprecated
    public void sendMessage(Player player, String key, String... replacements) {
        String msg = messenger.getRawMessage(key);
        if (!msg.isEmpty()) {
            for (int i = 0; i < replacements.length - 1; i += 2) {
                msg = msg.replace(replacements[i], replacements[i + 1]);
            }
            TextUtil.send(player, messenger.getPrefix() + msg);
        }
    }
}
