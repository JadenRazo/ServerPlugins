package net.serverplugins.deathbuyback;

import java.lang.reflect.Method;
import net.serverplugins.api.ServerAPI;
import net.serverplugins.api.database.Database;
import net.serverplugins.api.economy.EconomyProvider;
import net.serverplugins.deathbuyback.commands.BuybackAdminCommand;
import net.serverplugins.deathbuyback.commands.BuybackCommand;
import net.serverplugins.deathbuyback.listeners.DeathListener;
import net.serverplugins.deathbuyback.listeners.LoginListener;
import net.serverplugins.deathbuyback.managers.DeathInventoryManager;
import net.serverplugins.deathbuyback.managers.PricingManager;
import net.serverplugins.deathbuyback.repository.DeathBuybackRepository;
import net.serverplugins.deathbuyback.tasks.ExpirationCleanupTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class ServerDeathBuyback extends JavaPlugin {

    private static ServerDeathBuyback instance;
    private DeathBuybackConfig config;
    private Database database;
    private DeathBuybackRepository repository;
    private PricingManager pricingManager;
    private DeathInventoryManager deathInventoryManager;
    private EconomyProvider economyProvider;
    private ExpirationCleanupTask cleanupTask;

    // Redis integration (via server-bridge if available)
    private Object redisClient;
    private Method publishDeathStoredMethod;
    private Method publishDeathPurchasedMethod;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Load configuration
        config = new DeathBuybackConfig(this);

        // Initialize database
        initializeDatabase();

        // Initialize managers
        economyProvider = ServerAPI.getInstance().getEconomyProvider();
        if (!economyProvider.isAvailable()) {
            getLogger().severe("Vault economy not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        pricingManager = new PricingManager(this);
        deathInventoryManager = new DeathInventoryManager(this);

        // Initialize Redis integration
        initializeRedisIntegration();

        // Register commands
        registerCommands();

        // Register listeners
        registerListeners();

        // Start cleanup task
        startTasks();

        getLogger().info("ServerDeathBuyback enabled!");
    }

    @Override
    public void onDisable() {
        if (cleanupTask != null) {
            cleanupTask.stop();
        }
        instance = null;
        getLogger().info("ServerDeathBuyback disabled!");
    }

    private void initializeDatabase() {
        if (config.useApiDatabase()) {
            database = ServerAPI.getInstance().getDatabase();
        } else {
            // Create standalone database connection if needed
            database = ServerAPI.getInstance().getDatabase();
        }

        // Create tables
        repository = new DeathBuybackRepository(database);
        repository.createTables();
        getLogger().info("Database initialized");
    }

    private void registerCommands() {
        BuybackCommand buybackCommand = new BuybackCommand(this);
        getCommand("buyback").setExecutor(buybackCommand);
        getCommand("buyback").setTabCompleter(buybackCommand);

        BuybackAdminCommand adminCommand = new BuybackAdminCommand(this);
        getCommand("buybackadmin").setExecutor(adminCommand);
        getCommand("buybackadmin").setTabCompleter(adminCommand);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new DeathListener(this), this);
        getServer().getPluginManager().registerEvents(new LoginListener(this), this);
    }

    private void startTasks() {
        cleanupTask = new ExpirationCleanupTask(this);
        long intervalTicks = config.getCleanupIntervalHours() * 60 * 60 * 20L;
        cleanupTask.start(intervalTicks);
    }

    private void initializeRedisIntegration() {
        Plugin bridgePlugin = Bukkit.getPluginManager().getPlugin("server-bridge");
        if (bridgePlugin == null || !bridgePlugin.isEnabled()) {
            getLogger().info("server-bridge not found, Discord notifications disabled");
            return;
        }

        try {
            Method getRedisClientMethod = bridgePlugin.getClass().getMethod("getRedisClient");
            redisClient = getRedisClientMethod.invoke(bridgePlugin);

            if (redisClient == null) {
                getLogger()
                        .info(
                                "server-bridge RedisClient not available, Discord notifications disabled");
                return;
            }

            // Get the publish methods via reflection
            publishDeathStoredMethod =
                    redisClient
                            .getClass()
                            .getMethod(
                                    "publishDeathStored",
                                    String.class,
                                    String.class,
                                    int.class,
                                    double.class);
            publishDeathPurchasedMethod =
                    redisClient
                            .getClass()
                            .getMethod(
                                    "publishDeathPurchased",
                                    String.class,
                                    String.class,
                                    double.class);

            getLogger().info("Redis integration enabled for Discord notifications");
        } catch (Exception e) {
            getLogger().warning("Failed to initialize Redis integration: " + e.getMessage());
        }
    }

    public void publishDeathStored(
            String playerName, String playerUuid, int itemCount, double buybackCost) {
        if (redisClient == null || publishDeathStoredMethod == null) return;

        try {
            publishDeathStoredMethod.invoke(
                    redisClient, playerName, playerUuid, itemCount, buybackCost);
        } catch (Exception e) {
            getLogger().warning("Failed to publish death stored: " + e.getMessage());
        }
    }

    public void publishDeathPurchased(String playerName, String playerUuid, double cost) {
        if (redisClient == null || publishDeathPurchasedMethod == null) return;

        try {
            publishDeathPurchasedMethod.invoke(redisClient, playerName, playerUuid, cost);
        } catch (Exception e) {
            getLogger().warning("Failed to publish death purchased: " + e.getMessage());
        }
    }

    public void reloadConfiguration() {
        reloadConfig();
        config = new DeathBuybackConfig(this);
        pricingManager.reloadPrices();
    }

    public static ServerDeathBuyback getInstance() {
        return instance;
    }

    public DeathBuybackConfig getDeathBuybackConfig() {
        return config;
    }

    public Database getDatabase() {
        return database;
    }

    public DeathBuybackRepository getRepository() {
        return repository;
    }

    public PricingManager getPricingManager() {
        return pricingManager;
    }

    public DeathInventoryManager getDeathInventoryManager() {
        return deathInventoryManager;
    }

    public EconomyProvider getEconomyProvider() {
        return economyProvider;
    }
}
