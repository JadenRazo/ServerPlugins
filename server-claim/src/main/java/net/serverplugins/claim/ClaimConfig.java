package net.serverplugins.claim;

import net.serverplugins.api.messages.Placeholder;
import net.serverplugins.api.messages.PluginMessenger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class ClaimConfig {

    private final ServerClaim plugin;
    private final FileConfiguration config;
    private PluginMessenger messenger;

    public ClaimConfig(ServerClaim plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        reload();
    }

    /** Reloads the configuration and reinitializes the messenger. */
    public final void reload() {
        plugin.reloadConfig();

        // Initialize PluginMessenger with prefix from config
        this.messenger =
                new PluginMessenger(
                        plugin.getConfig(),
                        "messages",
                        "<gradient:#ff6b6b:#feca57>[ServerClaim]</gradient> ");
    }

    public int getStartingChunks() {
        return config.getInt("starting-chunks", 2);
    }

    public double getBasePrice() {
        return config.getDouble("pricing.base-price", 50000);
    }

    public double getGrowthRate() {
        return config.getDouble("pricing.growth-rate", 1.35);
    }

    public int getMaxChunks() {
        return config.getInt("pricing.max-chunks", 50);
    }

    /**
     * Get the max chunks allowed per individual claim. Deprecated: Use getMaxChunksPerProfile()
     * instead.
     */
    @Deprecated
    public int getMaxChunksPerClaim() {
        // Use new config value, fall back to old value for backwards compatibility
        int newValue = config.getInt("pricing.max-chunks-per-profile", -1);
        if (newValue != -1) {
            return newValue;
        }
        return config.getInt(
                "pricing.max-chunks-per-claim", config.getInt("pricing.max-chunks", 50));
    }

    /**
     * Get the claim multiplier (each additional claim costs this much more per chunk). Default is
     * 1.5x, so 2nd claim = 1.5x, 3rd = 2.25x, etc.
     */
    public double getClaimMultiplier() {
        return config.getDouble("pricing.claim-multiplier", 1.5);
    }

    /** Check if unlimited chunks per profile is enabled. */
    public boolean isUnlimitedChunks() {
        return config.getBoolean("chunks.unlimited", true);
    }

    /**
     * Get the maximum chunks allowed per profile. Returns 999999 if unlimited is enabled, otherwise
     * returns configured value.
     */
    public int getMaxChunksPerProfile() {
        if (isUnlimitedChunks()) {
            return 999999; // Effectively unlimited
        }

        int maxChunks = config.getInt("chunks.max-per-profile", 999999);
        if (maxChunks == -1) {
            return Integer.MAX_VALUE; // True unlimited
        }
        return maxChunks;
    }

    /** Get the starting chunks for a new claim. */
    public int getStartingChunksPerClaim() {
        return config.getInt("starting-chunks-per-claim", config.getInt("starting-chunks", 2));
    }

    /**
     * Get the starting chunks for a new profile. This is how many free chunks each new profile
     * gets.
     */
    public int getStartingChunksPerProfile() {
        return config.getInt("starting-chunks-per-profile", 2);
    }

    /**
     * Check if global exponential pricing is enabled.
     *
     * @return true if exponential pricing based on global chunks is enabled
     */
    public boolean isGlobalChunkPricingEnabled() {
        return config.getBoolean("global-chunk-pricing.enabled", true);
    }

    /**
     * Get the base price for global chunk pricing (first chunk cost).
     *
     * @return Base price for exponential pricing
     */
    public double getGlobalChunkBasePrice() {
        return config.getDouble("global-chunk-pricing.base-price", 5000.0);
    }

    /**
     * Get the growth rate for global chunk pricing (exponential multiplier).
     *
     * @return Growth rate (e.g., 1.15 = 15% increase per chunk)
     */
    public double getGlobalChunkGrowthRate() {
        return config.getDouble("global-chunk-pricing.growth-rate", 1.15);
    }

    /**
     * Get the soft cap for global chunks (purchases still allowed but very expensive).
     *
     * @return Maximum recommended global chunks
     */
    public int getMaxGlobalChunks() {
        return config.getInt("global-chunk-pricing.max-global-chunks", 100);
    }

    /**
     * Get the maximum price cap per chunk (prevents astronomical prices).
     *
     * @return Maximum price per chunk (default: $1,000,000)
     */
    public double getMaxPricePerChunk() {
        return config.getDouble("global-chunk-pricing.max-per-chunk", 1000000.0);
    }

    /**
     * Calculate the price for the next N chunks using exponential pricing with cap. Formula: sum of
     * min(base_price * growth_rate^(current+i), max_per_chunk) for i=0 to amount-1
     *
     * @param currentGlobalChunks Player's current global chunk count
     * @param amount Number of chunks to purchase
     * @return Total price for purchasing this many chunks
     */
    public double calculateGlobalChunkPrice(int currentGlobalChunks, int amount) {
        if (!isGlobalChunkPricingEnabled()) {
            // Fallback to bulk pricing
            return getBulkChunkPrice(amount) * amount;
        }

        double basePrice = getGlobalChunkBasePrice();
        double growthRate = getGlobalChunkGrowthRate();
        double maxPerChunk = getMaxPricePerChunk();
        double totalPrice = 0.0;

        for (int i = 0; i < amount; i++) {
            int chunkNumber = currentGlobalChunks + i + 1;
            double uncappedPrice = basePrice * Math.pow(growthRate, chunkNumber - 1);
            double chunkPrice = Math.min(uncappedPrice, maxPerChunk);
            totalPrice += chunkPrice;
        }

        return totalPrice;
    }

    /**
     * Get bulk chunk price per chunk based on quantity. Supports discounts for bulk purchases (1,
     * 5, 10, 50, 100 chunks).
     *
     * @param amount Number of chunks being purchased
     * @return Price per chunk for this quantity
     * @deprecated Use calculateGlobalChunkPrice() with exponential pricing instead
     */
    @Deprecated
    public double getBulkChunkPrice(int amount) {
        String key = "bulk-pricing.price-" + amount;
        return config.getDouble(key, 10000.0); // Default: $10,000 per chunk
    }

    /**
     * Get default max chunks per profile for players without permissions. This is the capacity
     * limit for how many chunks can be allocated to a single profile.
     *
     * @return Default maximum chunks per profile
     */
    public int getDefaultMaxChunksPerProfile() {
        return config.getInt("chunks.default-max-per-profile", 100);
    }

    /**
     * Get max chunks check limit for LuckPerms permissions. The system will check permissions from
     * this number down to 1.
     *
     * @return Maximum value to check for serverclaim.chunks.<number> permissions
     */
    public int getMaxChunksCheck() {
        return config.getInt("chunks.max-chunks-check", 1000);
    }

    /** Get the default max profiles for players without explicit permissions. */
    @Deprecated
    public int getDefaultMaxProfiles() {
        return config.getInt("defaults.max-profiles", 2); // Updated default from 1 to 2
    }

    /** Get the default max claims for players without explicit permissions. */
    public int getDefaultMaxClaims() {
        return config.getInt("defaults.max-claims", 1);
    }

    /** Get the maximum profile count to check permissions for. */
    public int getMaxProfileCheck() {
        return config.getInt("limits.max-profile-check", 10);
    }

    /** Get the maximum claim count to check permissions for. */
    public int getMaxClaimsCheck() {
        return config.getInt("limits.max-claims-check", 10);
    }

    public boolean areParticlesEnabled() {
        return config.getBoolean("particles.enabled", true);
    }

    public String getParticleType() {
        return config.getString("particles.type", "DUST");
    }

    public double getParticleDensity() {
        return config.getDouble("particles.density", 0.5);
    }

    public int getParticleHeight() {
        return config.getInt("particles.height", 2);
    }

    public int getParticleViewDistance() {
        return config.getInt("particles.view-distance", 32);
    }

    public int getParticleUpdateInterval() {
        return config.getInt("particles.update-interval", 5);
    }

    public boolean getDefaultPvp() {
        return config.getBoolean("claim-settings.pvp", false);
    }

    public boolean getDefaultFireSpread() {
        return config.getBoolean("claim-settings.fire-spread", false);
    }

    public boolean getDefaultExplosions() {
        return config.getBoolean("claim-settings.explosions", false);
    }

    public boolean getDefaultHostileSpawns() {
        return config.getBoolean("claim-settings.hostile-spawns", true);
    }

    public boolean getDefaultMobGriefing() {
        return config.getBoolean("claim-settings.mob-griefing", false);
    }

    public boolean getDefaultPassiveSpawns() {
        return config.getBoolean("claim-settings.passive-spawns", true);
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
     * Gets a message from config with prefix.
     *
     * @param key The message key
     * @return The formatted message with prefix
     * @deprecated Use {@link #getMessenger()}.{@link PluginMessenger#getMessage(String)} instead
     */
    @Deprecated
    public String getMessage(String key) {
        return messenger.getMessage(key);
    }

    /**
     * Gets a raw message from config without prefix.
     *
     * @param key The message key
     * @return The raw message string
     * @deprecated Use {@link #getMessenger()}.{@link PluginMessenger#getRawMessage(String)} instead
     */
    @Deprecated
    public String getRawMessage(String key) {
        return messenger.getRawMessage(key);
    }

    /**
     * Sends a configured message to a player with replacements.
     *
     * @param player The recipient player
     * @param key The message key
     * @param placeholders Placeholders to replace
     * @deprecated Use {@link #getMessenger()}.{@link PluginMessenger#send(Player, String,
     *     Placeholder...)} instead
     */
    @Deprecated
    public void sendMessage(Player player, String key, Placeholder... placeholders) {
        messenger.send(player, key, placeholders);
    }

    public String getDatabaseType() {
        return config.getString("database.type", "H2");
    }

    public String getDatabaseFile() {
        return config.getString("database.file", "claims");
    }

    public String getDatabaseHost() {
        // Check environment variable first
        String envHost = System.getenv("SERVERCLAIM_DB_HOST");
        if (envHost != null && !envHost.isEmpty()) {
            return envHost;
        }
        return config.getString("database.host", "localhost");
    }

    public int getDatabasePort() {
        // Check environment variable first
        String envPort = System.getenv("SERVERCLAIM_DB_PORT");
        if (envPort != null && !envPort.isEmpty()) {
            try {
                return Integer.parseInt(envPort);
            } catch (NumberFormatException ignored) {
            }
        }
        return config.getInt("database.port", 3306);
    }

    public String getDatabaseName() {
        // Check environment variable first
        String envName = System.getenv("SERVERCLAIM_DB_NAME");
        if (envName != null && !envName.isEmpty()) {
            return envName;
        }
        return config.getString("database.name", "serverclaim");
    }

    public String getDatabaseUsername() {
        // Check environment variable first
        String envUser = System.getenv("SERVERCLAIM_DB_USER");
        if (envUser != null && !envUser.isEmpty()) {
            return envUser;
        }
        return config.getString("database.username", "root");
    }

    public String getDatabasePassword() {
        // Check environment variable first (ALWAYS prefer env for passwords)
        String envPass = System.getenv("SERVERCLAIM_DB_PASSWORD");
        if (envPass != null && !envPass.isEmpty()) {
            return envPass;
        }
        return config.getString("database.password", "");
    }

    public int getSpawnProtectionRadius() {
        // DEFAULT: 0 (disabled) instead of 100 for safety
        // If config fails to load, safer to allow claims everywhere (except spawn world)
        return config.getInt("spawn-protection-radius", 0);
    }

    /**
     * Get the list of worlds where claiming is allowed.
     *
     * @deprecated Use isWorldAllowed(String) instead
     */
    @Deprecated
    public String getSpawnWorld() {
        return config.getString("spawn-world", "world");
    }

    /** Get the list of worlds where claiming is allowed. */
    public java.util.List<String> getAllowedWorlds() {
        java.util.List<String> worlds = config.getStringList("allowed-worlds");
        if (worlds.isEmpty()) {
            // Fallback for old config format
            return java.util.List.of("playworld", "playworld_nether", "playworld_the_end");
        }
        return worlds;
    }

    /** Check if a world allows claiming. */
    public boolean isWorldAllowed(String worldName) {
        if (worldName == null) {
            return false;
        }
        return getAllowedWorlds().stream().anyMatch(allowed -> allowed.equalsIgnoreCase(worldName));
    }

    // ==================== CHUNK TELEPORT SETTINGS ====================

    public boolean isChunkTeleportEnabled() {
        return config.getBoolean("chunk-teleport.enabled", true);
    }

    public double getChunkTeleportOwnerCost() {
        return config.getDouble("chunk-teleport.owner-cost", 0);
    }

    public double getChunkTeleportMemberCost() {
        return config.getDouble("chunk-teleport.member-cost", 25);
    }

    public int getChunkTeleportCooldown() {
        return config.getInt("chunk-teleport.cooldown-seconds", 30);
    }

    // ==================== CHUNK TRANSFER SETTINGS ====================

    public boolean isChunkTransferEnabled() {
        return config.getBoolean("chunk-transfer.enabled", true);
    }

    public double getChunkTransferCostPerChunk() {
        return config.getDouble("chunk-transfer.cost-per-chunk", 100);
    }

    public boolean isChunkTransferAllowOffline() {
        return config.getBoolean("chunk-transfer.allow-offline-recipients", true);
    }

    public boolean isChunkTransferRequireConfirmation() {
        return config.getBoolean("chunk-transfer.require-confirmation", true);
    }

    // ==================== WAR SETTINGS ====================

    public boolean isWarsEnabled() {
        return config.getBoolean("wars.enabled", true);
    }

    public int getWarDeclarationNoticeHours() {
        return config.getInt("wars.declaration-notice-hours", 24);
    }

    public int getWarCaptureTimeMinutes() {
        return config.getInt("wars.capture-time-minutes", 10);
    }

    public int getWarShieldDays() {
        return config.getInt("wars.war-shield-days", 7);
    }

    public double getWarMaxTributeAmount() {
        return config.getDouble("wars.max-tribute-amount", 1000000);
    }

    // ==================== UPKEEP SETTINGS ====================

    public boolean isUpkeepEnabled() {
        return config.getBoolean("upkeep.enabled", true);
    }

    public double getUpkeepCostPerChunk() {
        return config.getDouble("upkeep.cost-per-chunk", 10.0);
    }

    public int getUpkeepPaymentIntervalHours() {
        return config.getInt("upkeep.payment-interval-hours", 24);
    }

    public int getUpkeepGracePeriodDays() {
        return config.getInt("upkeep.grace-period-days", 7);
    }

    public boolean isUpkeepAutoUnclaimEnabled() {
        return config.getBoolean("upkeep.auto-unclaim-enabled", true);
    }

    public String getUpkeepUnclaimPriority() {
        return config.getString("upkeep.unclaim-priority", "furthest_first");
    }

    public java.util.List<Integer> getUpkeepNotificationTimes() {
        java.util.List<Integer> times = config.getIntegerList("upkeep.notification-times");
        if (times.isEmpty()) {
            return java.util.List.of(72, 24, 12);
        }
        return times;
    }

    public int getUpkeepBatchSize() {
        return config.getInt("upkeep.batch-size", 100);
    }

    // ==================== LAND BANK SETTINGS ====================

    public boolean isLandBankEnabled() {
        return config.getBoolean("land-bank.enabled", true);
    }

    public double getLandBankDefaultMinimumWarning() {
        return config.getDouble("land-bank.default-minimum-warning", 100);
    }

    // ==================== LEVEL SETTINGS ====================

    public boolean isLevelsEnabled() {
        return config.getBoolean("levels.enabled", true);
    }

    public int getXpPerMinutePlaytime() {
        return config.getInt("levels.xp-per-minute-playtime", 1);
    }

    public int getXpPer100Blocks() {
        return config.getInt("levels.xp-per-100-blocks", 2);
    }

    public int getXpPerMemberAdded() {
        return config.getInt("levels.xp-per-member-added", 50);
    }

    public int getXpPerUpkeepPaid() {
        return config.getInt("levels.xp-per-upkeep-paid", 100);
    }

    public int getXpPerChunkClaimed() {
        return config.getInt("levels.xp-per-chunk-claimed", 25);
    }

    // ==================== NATION SETTINGS ====================

    public boolean isNationsEnabled() {
        return config.getBoolean("nations.enabled", true);
    }

    public double getNationCreationCost() {
        return config.getDouble("nations.creation-cost", 10000);
    }

    public int getNationMaxMembers() {
        return config.getInt("nations.max-members", 50);
    }

    public double getNationMaxTaxRate() {
        return config.getDouble("nations.tax-rate-max", 25.0);
    }

    public int getNationInviteExpiryHours() {
        return config.getInt("nations.invite-expiry-hours", 72);
    }

    public int getNationTaxCollectionIntervalHours() {
        return config.getInt("nations.tax-collection-interval-hours", 24);
    }

    // ==================== CONFIGURATION VALIDATION ====================

    /**
     * Validates all configuration values to prevent server issues. Logs warnings for suspicious
     * values and errors for invalid values.
     *
     * @return true if config is valid, false if critical errors exist
     */
    public boolean validateConfig() {
        boolean hasErrors = false;

        // Validate pricing configuration
        if (getBasePrice() <= 0) {
            plugin.getLogger()
                    .severe(
                            "INVALID CONFIG: pricing.base-price must be greater than 0 (current: "
                                    + getBasePrice()
                                    + ")");
            hasErrors = true;
        }
        if (getGrowthRate() <= 1.0) {
            plugin.getLogger()
                    .severe(
                            "INVALID CONFIG: pricing.growth-rate must be greater than 1.0 (current: "
                                    + getGrowthRate()
                                    + ")");
            hasErrors = true;
        }
        if (getMaxChunks() <= 0 || getMaxChunks() > 1000) {
            plugin.getLogger()
                    .warning(
                            "SUSPICIOUS CONFIG: pricing.max-chunks is unusual (current: "
                                    + getMaxChunks()
                                    + ", recommended: 50-200)");
        }
        if (getClaimMultiplier() <= 0) {
            plugin.getLogger()
                    .severe(
                            "INVALID CONFIG: pricing.claim-multiplier must be greater than 0 (current: "
                                    + getClaimMultiplier()
                                    + ")");
            hasErrors = true;
        }

        // Validate starting values
        if (getStartingChunks() <= 0) {
            plugin.getLogger()
                    .severe(
                            "INVALID CONFIG: starting-chunks must be greater than 0 (current: "
                                    + getStartingChunks()
                                    + ")");
            hasErrors = true;
        }
        if (getStartingChunksPerClaim() <= 0) {
            plugin.getLogger()
                    .severe(
                            "INVALID CONFIG: starting-chunks-per-claim must be greater than 0 (current: "
                                    + getStartingChunksPerClaim()
                                    + ")");
            hasErrors = true;
        }

        // Validate limits
        if (getDefaultMaxClaims() <= 0) {
            plugin.getLogger()
                    .severe(
                            "INVALID CONFIG: defaults.max-claims must be greater than 0 (current: "
                                    + getDefaultMaxClaims()
                                    + ")");
            hasErrors = true;
        }
        if (getMaxClaimsCheck() <= 0 || getMaxClaimsCheck() > 100) {
            plugin.getLogger()
                    .warning(
                            "SUSPICIOUS CONFIG: limits.max-claims-check is unusual (current: "
                                    + getMaxClaimsCheck()
                                    + ", recommended: 5-20)");
        }

        // Validate upkeep configuration
        if (isUpkeepEnabled()) {
            if (getUpkeepCostPerChunk() < 0) {
                plugin.getLogger()
                        .severe(
                                "INVALID CONFIG: upkeep.cost-per-chunk cannot be negative (current: "
                                        + getUpkeepCostPerChunk()
                                        + ")");
                hasErrors = true;
            }
            if (getUpkeepPaymentIntervalHours() <= 0) {
                plugin.getLogger()
                        .severe(
                                "INVALID CONFIG: upkeep.payment-interval-hours must be greater than 0 (current: "
                                        + getUpkeepPaymentIntervalHours()
                                        + ")");
                hasErrors = true;
            }
            if (getUpkeepGracePeriodDays() < 0) {
                plugin.getLogger()
                        .severe(
                                "INVALID CONFIG: upkeep.grace-period-days cannot be negative (current: "
                                        + getUpkeepGracePeriodDays()
                                        + ")");
                hasErrors = true;
            }
            if (getUpkeepBatchSize() <= 0) {
                plugin.getLogger()
                        .severe(
                                "INVALID CONFIG: upkeep.batch-size must be greater than 0 (current: "
                                        + getUpkeepBatchSize()
                                        + ")");
                hasErrors = true;
            }
            if (getUpkeepBatchSize() > 10000) {
                plugin.getLogger()
                        .warning(
                                "SUSPICIOUS CONFIG: upkeep.batch-size is very high (current: "
                                        + getUpkeepBatchSize()
                                        + ", recommended: 100-1000)");
            }
        }

        // Validate nation configuration
        if (isNationsEnabled()) {
            if (getNationCreationCost() < 0) {
                plugin.getLogger()
                        .severe(
                                "INVALID CONFIG: nations.creation-cost cannot be negative (current: "
                                        + getNationCreationCost()
                                        + ")");
                hasErrors = true;
            }
            if (getNationMaxMembers() <= 0) {
                plugin.getLogger()
                        .severe(
                                "INVALID CONFIG: nations.max-members must be greater than 0 (current: "
                                        + getNationMaxMembers()
                                        + ")");
                hasErrors = true;
            }
            if (getNationMaxTaxRate() < 0 || getNationMaxTaxRate() > 100) {
                plugin.getLogger()
                        .severe(
                                "INVALID CONFIG: nations.tax-rate-max must be between 0 and 100 (current: "
                                        + getNationMaxTaxRate()
                                        + ")");
                hasErrors = true;
            }
        }

        // Validate war configuration
        if (isWarsEnabled()) {
            if (getWarDeclarationNoticeHours() < 0) {
                plugin.getLogger()
                        .severe(
                                "INVALID CONFIG: wars.declaration-notice-hours cannot be negative (current: "
                                        + getWarDeclarationNoticeHours()
                                        + ")");
                hasErrors = true;
            }
            if (getWarShieldDays() < 0) {
                plugin.getLogger()
                        .severe(
                                "INVALID CONFIG: wars.shield-days cannot be negative (current: "
                                        + getWarShieldDays()
                                        + ")");
                hasErrors = true;
            }
            if (getWarMaxTributeAmount() < 0) {
                plugin.getLogger()
                        .severe(
                                "INVALID CONFIG: wars.max-tribute-amount cannot be negative (current: "
                                        + getWarMaxTributeAmount()
                                        + ")");
                hasErrors = true;
            }
        }

        // Validate levels configuration
        if (isLevelsEnabled()) {
            if (getXpPerMinutePlaytime() < 0) {
                plugin.getLogger()
                        .warning(
                                "SUSPICIOUS CONFIG: levels.xp-per-minute-playtime is negative (current: "
                                        + getXpPerMinutePlaytime()
                                        + ")");
            }
            if (getXpPerChunkClaimed() < 0) {
                plugin.getLogger()
                        .warning(
                                "SUSPICIOUS CONFIG: levels.xp-per-chunk-claimed is negative (current: "
                                        + getXpPerChunkClaimed()
                                        + ")");
            }
        }

        // Check for integer overflow risks in pricing calculation
        try {
            double maxPrice = getBasePrice() * Math.pow(getGrowthRate(), getMaxChunks());
            if (maxPrice > Double.MAX_VALUE / 2) {
                plugin.getLogger()
                        .warning(
                                "OVERFLOW RISK: pricing configuration may cause overflow at max chunks (max calculated price: "
                                        + maxPrice
                                        + ")");
            }
        } catch (ArithmeticException e) {
            plugin.getLogger()
                    .severe(
                            "OVERFLOW: pricing calculation causes arithmetic overflow - reduce growth-rate or max-chunks");
            hasErrors = true;
        }

        if (hasErrors) {
            plugin.getLogger().severe("=".repeat(60));
            plugin.getLogger().severe("CONFIG VALIDATION FAILED - Please fix the errors above");
            plugin.getLogger().severe("=".repeat(60));
        } else {
            plugin.getLogger().info("Configuration validation passed");
        }

        return !hasErrors;
    }
}
