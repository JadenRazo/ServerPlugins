package net.serverplugins.arcade;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.serverplugins.api.messages.PluginMessenger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;

public class ArcadeConfig {

    private final ServerArcade plugin;
    private final FileConfiguration config;
    private final PluginMessenger messenger;

    public ArcadeConfig(ServerArcade plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.messenger = new PluginMessenger(config, "messages", "<rainbow>[Arcade]</rainbow> ");
    }

    /**
     * Validate critical configuration values. Throws InvalidConfigurationException if any critical
     * issues are found.
     */
    public void validateConfig() throws InvalidConfigurationException {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Validate bet limits
        double minBet = config.getDouble("settings.min-bet", 100);
        double maxBet = config.getDouble("settings.max-bet", 1000000);

        if (minBet <= 0) {
            errors.add("settings.min-bet must be greater than 0 (current: " + minBet + ")");
        }

        if (maxBet <= 0) {
            errors.add("settings.max-bet must be greater than 0 (current: " + maxBet + ")");
        }

        if (minBet >= maxBet) {
            errors.add(
                    "settings.min-bet ("
                            + minBet
                            + ") must be less than settings.max-bet ("
                            + maxBet
                            + ")");
        }

        if (maxBet > Integer.MAX_VALUE / 100) {
            warnings.add(
                    "settings.max-bet is very high ("
                            + maxBet
                            + ") - may cause integer overflow in calculations");
        }

        // Validate cooldown
        int cooldown = config.getInt("settings.cooldown", 5);
        if (cooldown < 0) {
            errors.add("settings.cooldown must be >= 0 (current: " + cooldown + ")");
        }

        // Validate API port if API is enabled
        if (config.getBoolean("api.enabled", false)) {
            int port = config.getInt("api.port", 8080);
            if (port < 1024 || port > 65535) {
                errors.add("api.port must be between 1024 and 65535 (current: " + port + ")");
            }

            // Validate API authentication
            boolean requireAuth = config.getBoolean("api.require_auth", true);
            String apiKey = config.getString("api.key", "");
            String envKey = System.getenv("API_KEY");

            if (requireAuth
                    && (apiKey == null || apiKey.isEmpty())
                    && (envKey == null || envKey.isEmpty())) {
                errors.add(
                        "api.require_auth is enabled but no API key is configured (set api.key or API_KEY environment variable)");
            }

            // Validate CORS configuration
            List<String> allowedOrigins = config.getStringList("api.allowed_origins");
            if (allowedOrigins.isEmpty()) {
                warnings.add(
                        "api.allowed_origins is empty - API will not accept any requests. Add allowed origins or use ['*'] to allow all");
            }

            // Validate rate limiting
            if (config.getBoolean("api.rate_limiting.enabled", true)) {
                int requestsPerSecond = config.getInt("api.rate_limiting.requests_per_second", 5);
                int burstSize = config.getInt("api.rate_limiting.burst_size", 10);

                if (requestsPerSecond <= 0) {
                    errors.add(
                            "api.rate_limiting.requests_per_second must be > 0 (current: "
                                    + requestsPerSecond
                                    + ")");
                }

                if (burstSize <= 0) {
                    errors.add(
                            "api.rate_limiting.burst_size must be > 0 (current: "
                                    + burstSize
                                    + ")");
                }
            }
        }

        // Validate performance cache settings
        int statsTtl = config.getInt("performance.cache.stats_ttl_minutes", 10);
        int leaderboardTtl = config.getInt("performance.cache.leaderboard_ttl_minutes", 5);
        int exclusionTtl = config.getInt("performance.cache.exclusion_ttl_minutes", 5);
        int maxEntries = config.getInt("performance.cache.max_entries", 10000);

        if (statsTtl <= 0) {
            errors.add(
                    "performance.cache.stats_ttl_minutes must be > 0 (current: " + statsTtl + ")");
        }

        if (leaderboardTtl <= 0) {
            errors.add(
                    "performance.cache.leaderboard_ttl_minutes must be > 0 (current: "
                            + leaderboardTtl
                            + ")");
        }

        if (exclusionTtl <= 0) {
            errors.add(
                    "performance.cache.exclusion_ttl_minutes must be > 0 (current: "
                            + exclusionTtl
                            + ")");
        }

        if (maxEntries <= 0) {
            errors.add("performance.cache.max_entries must be > 0 (current: " + maxEntries + ")");
        }

        if (maxEntries > 100000) {
            warnings.add(
                    "performance.cache.max_entries is very high ("
                            + maxEntries
                            + ") - may consume excessive memory");
        }

        // Validate game config files exist
        File gamesFolder = new File(plugin.getDataFolder(), "games");
        String[] requiredGameConfigs = {
            "slots.yml", "blackjack.yml", "jackpot.yml", "crash.yml", "dice.yml"
        };

        for (String gameConfig : requiredGameConfigs) {
            File configFile = new File(gamesFolder, gameConfig);
            if (!configFile.exists()) {
                warnings.add(
                        "Game config file missing: games/"
                                + gameConfig
                                + " (will be created on startup)");
            }
        }

        // Validate machine seat timeout
        if (config.getBoolean("machines.enabled", true)) {
            int seatTimeout = config.getInt("machines.seat_timeout_seconds", 300);
            if (seatTimeout <= 0) {
                errors.add(
                        "machines.seat_timeout_seconds must be > 0 (current: " + seatTimeout + ")");
            }

            if (seatTimeout < 60) {
                warnings.add(
                        "machines.seat_timeout_seconds is very low ("
                                + seatTimeout
                                + ") - players may be ejected too quickly");
            }
        }

        // Log warnings
        if (!warnings.isEmpty()) {
            plugin.getLogger().warning("Configuration validation warnings:");
            for (String warning : warnings) {
                plugin.getLogger().warning("  - " + warning);
            }
        }

        // Throw exception if there are critical errors
        if (!errors.isEmpty()) {
            StringBuilder errorMessage = new StringBuilder("Configuration validation failed:\n");
            for (String error : errors) {
                errorMessage.append("  - ").append(error).append("\n");
            }
            throw new InvalidConfigurationException(errorMessage.toString());
        }

        plugin.getLogger().info("Configuration validation passed");
    }

    public double getMinBet() {
        return config.getDouble("settings.min-bet", 100);
    }

    public double getMaxBet() {
        return config.getDouble("settings.max-bet", 1000000);
    }

    public int getCooldown() {
        return config.getInt("settings.cooldown", 5);
    }

    public boolean isSlotsEnabled() {
        return config.getBoolean("slots.enabled", true);
    }

    public List<SlotSymbol> getSlotSymbols() {
        List<SlotSymbol> symbols = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("slots.symbols");

        if (section != null) {
            for (String key : section.getKeys(false)) {
                String materialName = section.getString(key + ".material", "STONE");
                int weight = section.getInt(key + ".weight", 10);
                double multiplier = section.getDouble(key + ".multiplier", 2.0);

                Material material;
                try {
                    material = Material.valueOf(materialName);
                } catch (IllegalArgumentException e) {
                    material = Material.STONE;
                }

                symbols.add(new SlotSymbol(key, material, weight, multiplier));
            }
        }

        return symbols;
    }

    public boolean isBlackjackEnabled() {
        return config.getBoolean("blackjack.enabled", true);
    }

    public int getDealerStandsOn() {
        return config.getInt("blackjack.dealer-stands-on", 17);
    }

    public double getBlackjackMultiplier() {
        return config.getDouble("blackjack.blackjack-multiplier", 2.5);
    }

    public double getWinMultiplier() {
        return config.getDouble("blackjack.win-multiplier", 2.0);
    }

    public boolean isBaccaratEnabled() {
        return config.getBoolean("baccarat.enabled", true);
    }

    public double getBaccaratPlayerMultiplier() {
        return config.getDouble("baccarat.player-multiplier", 2.0);
    }

    public double getBaccaratBankerMultiplier() {
        return config.getDouble("baccarat.banker-multiplier", 1.95);
    }

    public double getBaccaratTieMultiplier() {
        return config.getDouble("baccarat.tie-multiplier", 9.0);
    }

    public boolean isCrashEnabled() {
        return config.getBoolean("crash.enabled", true);
    }

    public double getCrashHouseEdge() {
        return config.getDouble("crash.house-edge", 0.05);
    }

    public double getCrashMaxMultiplier() {
        return config.getDouble("crash.max-multiplier", 100.0);
    }

    public int getCrashTickRate() {
        return config.getInt("crash.tick-rate", 2);
    }

    public boolean isCoinflipEnabled() {
        return config.getBoolean("coinflip.enabled", true);
    }

    public int getCoinflipExpiryTime() {
        return config.getInt("coinflip.expiry-time", 60);
    }

    public boolean areMachinesEnabled() {
        return config.getBoolean("machines.enabled", true);
    }

    public String getMessage(String key) {
        return messenger.getMessage(key);
    }

    public PluginMessenger getMessenger() {
        return messenger;
    }

    /** Reload messenger when config is reloaded. */
    public void reload() {
        messenger.reload();
    }

    public record SlotSymbol(String name, Material material, int weight, double multiplier) {}
}
