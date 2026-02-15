package net.serverplugins.bridge;

import net.milkbowl.vault.economy.Economy;
import net.serverplugins.api.broadcast.BroadcastManager;
import net.serverplugins.bridge.commands.ChangelogCommand;
import net.serverplugins.bridge.commands.DiscordDailyCommand;
import net.serverplugins.bridge.commands.LinkVerifyCommand;
import net.serverplugins.bridge.commands.UnlinkCommand;
import net.serverplugins.bridge.database.DatabaseManager;
import net.serverplugins.bridge.listeners.ChatListener;
import net.serverplugins.bridge.listeners.PlayerJoinQuitListener;
import net.serverplugins.bridge.messaging.RedisClient;
import net.serverplugins.bridge.services.EconomyService;
import net.serverplugins.bridge.tasks.FullBalanceSyncTask;
import net.serverplugins.bridge.tasks.IncrementalBalanceSyncTask;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class ServerBridge extends JavaPlugin {

    private static ServerBridge instance;

    private BridgeConfig bridgeConfig;
    private RedisClient redisClient;
    private EconomyService economyService;
    private Economy economy;
    private DatabaseManager databaseManager;
    private DiscordDailyCommand discordDailyCommand;
    private IncrementalBalanceSyncTask incrementalBalanceSyncTask;
    private FullBalanceSyncTask fullBalanceSyncTask;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        this.bridgeConfig = new BridgeConfig(this);

        // Only initialize Redis if enabled in config
        if (bridgeConfig.isRedisEnabled()) {
            this.redisClient = new RedisClient(this, bridgeConfig);
            redisClient.connect();

            // Enable BroadcastManager cross-server support if Redis connected
            if (redisClient.isConnected() && redisClient.getJedisPool() != null) {
                BroadcastManager.enableRedis(redisClient.getJedisPool());
            }
        } else {
            getLogger()
                    .info(
                            "Redis is disabled in config. Chat bridge and cross-server features won't work.");
        }

        registerListeners();

        // Delay economy setup to allow Essentials to register first
        getServer()
                .getScheduler()
                .runTaskLater(
                        this,
                        () -> {
                            if (!setupEconomy()) {
                                getLogger()
                                        .severe(
                                                "Vault economy not found! Economy features will be disabled.");
                            }

                            this.economyService = new EconomyService(this, economy);

                            // Initialize DatabaseManager after economy (for SMP server)
                            if (bridgeConfig.getServerName().equalsIgnoreCase("smp")
                                    && economy != null) {
                                this.databaseManager = new DatabaseManager(this);
                                databaseManager.initialize();
                            }

                            registerCommands();
                        },
                        1L); // Run 1 tick after server starts

        getLogger().info("ServerBridge enabled - Server: " + bridgeConfig.getServerName());
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.shutdown();
        }

        if (redisClient != null) {
            redisClient.disconnect();
        }

        getLogger().info("ServerBridge disabled.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }

        economy = rsp.getProvider();
        return economy != null;
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();

        if (bridgeConfig.isChatBridgeEnabled()) {
            pm.registerEvents(new ChatListener(this), this);
        }

        pm.registerEvents(new PlayerJoinQuitListener(this), this);
    }

    private void registerCommands() {
        var verifyCmd = getCommand("verifylink");
        if (verifyCmd != null) {
            verifyCmd.setExecutor(new LinkVerifyCommand(this));
        }

        var unlinkCmd = getCommand("discordunlink");
        if (unlinkCmd != null) {
            unlinkCmd.setExecutor(new UnlinkCommand(this));
        }

        // Changelog command - works on any server with Redis
        var changelogCmd = getCommand("changelog");
        if (changelogCmd != null && redisClient != null && redisClient.isConnected()) {
            ChangelogCommand changelogCommand = new ChangelogCommand(this);
            changelogCmd.setExecutor(changelogCommand);
            changelogCmd.setTabCompleter(changelogCommand);
            getLogger().info("Changelog command enabled.");
        }

        // Only register daily command and balance sync on SMP server
        if (bridgeConfig.getServerName().equalsIgnoreCase("smp")) {
            var dailyCmd = getCommand("discorddaily");
            if (dailyCmd != null && economy != null) {
                discordDailyCommand = new DiscordDailyCommand(this, economy);
                dailyCmd.setExecutor(discordDailyCommand);
                getLogger().info("Discord daily rewards command enabled.");
            }

            // Start balance sync tasks (database already initialized)
            if (economy != null && databaseManager != null) {
                // Start incremental balance sync task (every 1 minute)
                incrementalBalanceSyncTask = new IncrementalBalanceSyncTask(this, economy);
                getServer().getPluginManager().registerEvents(incrementalBalanceSyncTask, this);
                // Run after 20 seconds, then every 1 minute (1200 ticks)
                getServer()
                        .getScheduler()
                        .runTaskTimerAsynchronously(this, incrementalBalanceSyncTask, 400L, 1200L);
                getLogger().info("Incremental balance sync task started (every 1 minute).");

                // Start full balance sync task (every 30 minutes)
                fullBalanceSyncTask = new FullBalanceSyncTask(this, economy);
                // Run after 2 minutes, then every 30 minutes (36000 ticks)
                getServer()
                        .getScheduler()
                        .runTaskTimerAsynchronously(this, fullBalanceSyncTask, 2400L, 36000L);
                getLogger().info("Full balance sync task started (every 30 minutes).");
            }
        }
    }

    public static ServerBridge getInstance() {
        return instance;
    }

    public BridgeConfig getBridgeConfig() {
        return bridgeConfig;
    }

    public RedisClient getRedisClient() {
        return redisClient;
    }

    public EconomyService getEconomyService() {
        return economyService;
    }

    public Economy getEconomy() {
        return economy;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}
