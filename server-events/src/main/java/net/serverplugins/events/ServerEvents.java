package net.serverplugins.events;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import net.milkbowl.vault.economy.Economy;
import net.serverplugins.api.ServerAPI;
import net.serverplugins.api.broadcast.BroadcastManager;
import net.serverplugins.api.database.Database;
import net.serverplugins.events.commands.EventsCommand;
import net.serverplugins.events.data.EventStats;
import net.serverplugins.events.events.EventManager;
import net.serverplugins.events.integration.DiscordWebhook;
import net.serverplugins.events.repository.EventsRepository;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class ServerEvents extends JavaPlugin {

    private static ServerEvents instance;
    private EventsConfig eventsConfig;
    private Database database;
    private EventsRepository repository;
    private EventManager eventManager;
    private Economy economy;
    private EventStats eventStats;
    private DiscordWebhook discordWebhook;

    // Redis integration (via server-bridge if available)
    private Object redisClient;
    private Method publishEventAnnouncementMethod;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Load configuration
        eventsConfig = new EventsConfig(this);

        // Register with BroadcastManager
        BroadcastManager.registerPlugin(this);

        // Setup economy
        if (!setupEconomy()) {
            getLogger().warning("Vault economy not found! Coin rewards will be disabled.");
        }

        // Initialize database
        if (!setupDatabase()) {
            getLogger().warning("Database not configured - using YAML file storage");
            repository = null;
        } else {
            repository = new EventsRepository(database, getLogger());
        }

        // Initialize event stats
        eventStats = new EventStats(this, repository);

        // Initialize Discord webhook
        discordWebhook = new DiscordWebhook(this);

        // Initialize Redis integration
        initializeRedisIntegration();

        // Initialize event manager
        eventManager = new EventManager(this);

        // Register commands
        EventsCommand cmd = new EventsCommand(this);
        getCommand("event").setExecutor(cmd);
        getCommand("event").setTabCompleter(cmd);

        // Start scheduler if enabled
        if (eventsConfig.isSchedulerEnabled()) {
            eventManager.startScheduler();
        }

        // Start keyall scheduler if enabled
        if (eventsConfig.isKeyallEnabled()) {
            eventManager.startKeyallScheduler();
        }

        getLogger().info("ServerEvents enabled!");
    }

    @Override
    public void onDisable() {
        if (eventManager != null) {
            eventManager.shutdown();
        }
        if (eventStats != null) {
            eventStats.save();
        }

        // Unregister from BroadcastManager
        BroadcastManager.unregisterPlugin(this);

        instance = null;
        getLogger().info("ServerEvents disabled!");
    }

    private boolean setupDatabase() {
        // Use shared database connection from ServerAPI
        database = ServerAPI.getInstance().getDatabase();
        if (database == null) {
            getLogger().warning("ServerAPI database not available");
            return false;
        }

        getLogger().info("Using shared database connection from ServerAPI");

        // Execute schema to create tables
        try (InputStream is = getResource("schema.sql")) {
            if (is != null) {
                String schema = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                StringBuilder cleaned = new StringBuilder();
                for (String line : schema.split("\n")) {
                    String trimmedLine = line.trim();
                    if (!trimmedLine.startsWith("--") && !trimmedLine.isEmpty()) {
                        cleaned.append(line).append("\n");
                    }
                }
                String[] statements = cleaned.toString().split(";");
                for (String statement : statements) {
                    String trimmed = statement.trim();
                    if (!trimmed.isEmpty()) {
                        try {
                            database.execute(trimmed);
                        } catch (Exception ex) {
                            getLogger().warning("Schema statement failed: " + ex.getMessage());
                        }
                    }
                }
            }
        } catch (IOException e) {
            getLogger().warning("Failed to load schema: " + e.getMessage());
        }

        return true;
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

    private void initializeRedisIntegration() {
        Plugin bridgePlugin = Bukkit.getPluginManager().getPlugin("server-bridge");
        if (bridgePlugin == null || !bridgePlugin.isEnabled()) {
            getLogger().info("server-bridge not found, Redis event notifications disabled");
            return;
        }

        try {
            Method getRedisClientMethod = bridgePlugin.getClass().getMethod("getRedisClient");
            redisClient = getRedisClientMethod.invoke(bridgePlugin);

            if (redisClient == null) {
                getLogger()
                        .info(
                                "server-bridge RedisClient not available, Redis event notifications disabled");
                return;
            }

            publishEventAnnouncementMethod =
                    redisClient
                            .getClass()
                            .getMethod(
                                    "publishEventAnnouncement",
                                    String.class,
                                    String.class,
                                    String.class);

            getLogger().info("Redis integration enabled for Discord event notifications");
        } catch (Exception e) {
            getLogger().warning("Failed to initialize Redis integration: " + e.getMessage());
        }
    }

    public void publishEventAnnouncement(String eventName, String eventType, String message) {
        if (redisClient == null || publishEventAnnouncementMethod == null) return;

        try {
            publishEventAnnouncementMethod.invoke(redisClient, eventName, eventType, message);
        } catch (Exception e) {
            getLogger().warning("Failed to publish event announcement: " + e.getMessage());
        }
    }

    public void reloadConfiguration() {
        reloadConfig();
        eventsConfig = new EventsConfig(this);

        // Restart keyall scheduler based on new config
        eventManager.stopKeyallScheduler();
        if (eventsConfig.isKeyallEnabled()) {
            eventManager.startKeyallScheduler();
        }
    }

    // Accessors
    public static ServerEvents getInstance() {
        return instance;
    }

    public EventsConfig getEventsConfig() {
        return eventsConfig;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public Economy getEconomy() {
        return economy;
    }

    public boolean hasEconomy() {
        return economy != null;
    }

    public EventStats getEventStats() {
        return eventStats;
    }

    public DiscordWebhook getDiscordWebhook() {
        return discordWebhook;
    }

    public EventsRepository getRepository() {
        return repository;
    }
}
