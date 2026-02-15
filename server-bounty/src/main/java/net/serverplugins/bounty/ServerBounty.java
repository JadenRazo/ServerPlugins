package net.serverplugins.bounty;

import java.lang.reflect.Method;
import net.serverplugins.api.ServerAPI;
import net.serverplugins.api.database.Database;
import net.serverplugins.api.economy.EconomyProvider;
import net.serverplugins.bounty.commands.BountyCommand;
import net.serverplugins.bounty.listeners.DeathListener;
import net.serverplugins.bounty.listeners.JoinListener;
import net.serverplugins.bounty.managers.BountyManager;
import net.serverplugins.bounty.managers.HeadManager;
import net.serverplugins.bounty.repository.BountyRepository;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class ServerBounty extends JavaPlugin {

    private static ServerBounty instance;
    private BountyConfig config;
    private Database database;
    private BountyRepository repository;
    private BountyManager bountyManager;
    private HeadManager headManager;
    private EconomyProvider economyProvider;

    // Redis integration (via server-bridge if available)
    private Object redisClient;
    private Method publishBountyPlacedMethod;
    private Method publishBountyClaimedMethod;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        config = new BountyConfig(this);

        initializeDatabase();

        economyProvider = ServerAPI.getInstance().getEconomyProvider();
        if (economyProvider == null || !economyProvider.isAvailable()) {
            getLogger().severe("Vault economy not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        bountyManager = new BountyManager(this);
        headManager = new HeadManager(this);

        initializeRedisIntegration();
        registerCommands();
        registerListeners();

        getLogger().info("ServerBounty enabled!");
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
            publishBountyPlacedMethod =
                    redisClient
                            .getClass()
                            .getMethod(
                                    "publishBountyPlaced",
                                    String.class,
                                    String.class,
                                    String.class,
                                    String.class,
                                    double.class,
                                    double.class);
            publishBountyClaimedMethod =
                    redisClient
                            .getClass()
                            .getMethod(
                                    "publishBountyClaimed",
                                    String.class,
                                    String.class,
                                    String.class,
                                    String.class,
                                    double.class);

            getLogger().info("Redis integration enabled for Discord notifications");
        } catch (Exception e) {
            getLogger().warning("Failed to initialize Redis integration: " + e.getMessage());
        }
    }

    public void publishBountyPlaced(
            String placerName,
            String placerUuid,
            String targetName,
            String targetUuid,
            double amount,
            double total) {
        if (redisClient == null || publishBountyPlacedMethod == null) return;

        try {
            publishBountyPlacedMethod.invoke(
                    redisClient, placerName, placerUuid, targetName, targetUuid, amount, total);
        } catch (Exception e) {
            getLogger().warning("Failed to publish bounty placed: " + e.getMessage());
        }
    }

    public void publishBountyClaimed(
            String killerName,
            String killerUuid,
            String victimName,
            String victimUuid,
            double payout) {
        if (redisClient == null || publishBountyClaimedMethod == null) return;

        try {
            publishBountyClaimedMethod.invoke(
                    redisClient, killerName, killerUuid, victimName, victimUuid, payout);
        } catch (Exception e) {
            getLogger().warning("Failed to publish bounty claimed: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        instance = null;
        getLogger().info("ServerBounty disabled!");
    }

    private void initializeDatabase() {
        database = ServerAPI.getInstance().getDatabase();

        repository = new BountyRepository(database, getLogger());
        repository.createTables(getResource("schema.sql"));
        getLogger().info("Database initialized");
    }

    private void registerCommands() {
        BountyCommand bountyCommand = new BountyCommand(this);
        getCommand("bounty").setExecutor(bountyCommand);
        getCommand("bounty").setTabCompleter(bountyCommand);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new DeathListener(this), this);
        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
    }

    public void reloadConfiguration() {
        reloadConfig();
        config.reload();
    }

    public String formatCurrency(double amount) {
        return economyProvider.format(amount);
    }

    public static ServerBounty getInstance() {
        return instance;
    }

    public BountyConfig getBountyConfig() {
        return config;
    }

    public Database getDatabase() {
        return database;
    }

    public BountyRepository getRepository() {
        return repository;
    }

    public BountyManager getBountyManager() {
        return bountyManager;
    }

    public HeadManager getHeadManager() {
        return headManager;
    }

    public EconomyProvider getEconomyProvider() {
        return economyProvider;
    }
}
