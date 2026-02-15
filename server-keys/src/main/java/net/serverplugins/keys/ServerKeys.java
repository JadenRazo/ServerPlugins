package net.serverplugins.keys;

import net.serverplugins.api.ServerAPI;
import net.serverplugins.api.database.Database;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.keys.cache.StatsCache;
import net.serverplugins.keys.commands.ClaimKeysCommand;
import net.serverplugins.keys.commands.KeyAdminCommand;
import net.serverplugins.keys.commands.KeyAllCommand;
import net.serverplugins.keys.commands.KeysCommand;
import net.serverplugins.keys.managers.KeyManager;
import net.serverplugins.keys.managers.ScheduleManager;
import net.serverplugins.keys.placeholders.KeysExpansion;
import net.serverplugins.keys.repository.KeysRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ServerKeys plugin - Key distribution and tracking system.
 *
 * <p>Optimizations: - All database writes are async (fire-and-forget) - All database reads use
 * CompletableFuture - StatsCache provides in-memory caching for placeholders - Cache is warmed on
 * player join, invalidated on quit - Placeholder lookups are O(1) from cache, never block
 */
public final class ServerKeys extends JavaPlugin implements Listener {

    private static ServerKeys instance;
    private KeysConfig keysConfig;
    private Database database;
    private KeysRepository repository;
    private StatsCache statsCache;
    private KeyManager keyManager;
    private ScheduleManager scheduleManager;
    private KeysExpansion placeholderExpansion;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config
        saveDefaultConfig();
        keysConfig = new KeysConfig(getConfig());

        // Initialize database via ServerAPI
        ServerAPI api = (ServerAPI) Bukkit.getPluginManager().getPlugin("ServerAPI");
        if (api == null) {
            getLogger().severe("ServerAPI not found! Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        database = api.getDatabase();
        if (database == null) {
            getLogger().severe("Database not available! Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize repository and create tables
        repository = new KeysRepository(database, getLogger(), this);
        repository.createTables(getResource("schema.sql"));

        // Initialize cache (registers as listener for join/quit events)
        statsCache = new StatsCache(this, repository);

        // Initialize managers with cache
        keyManager = new KeyManager(this, keysConfig, repository, statsCache);
        scheduleManager = new ScheduleManager(this, keysConfig, keyManager);

        // Register commands
        registerCommands();

        // Register join listener for unclaimed key notifications
        Bukkit.getPluginManager().registerEvents(this, this);

        // Load schedules
        scheduleManager.loadSchedules();

        // Register PlaceholderAPI expansion if available
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            placeholderExpansion = new KeysExpansion(this, statsCache);
            placeholderExpansion.register();
            getLogger().info("PlaceholderAPI expansion registered!");
        }

        getLogger()
                .info(
                        "ServerKeys enabled! "
                                + scheduleManager.getActiveScheduleCount()
                                + " schedules active.");
    }

    @Override
    public void onDisable() {
        // Cancel all scheduled tasks
        if (scheduleManager != null) {
            scheduleManager.cancelAll();
        }

        // Clear cache
        if (statsCache != null) {
            statsCache.clear();
        }

        // Unregister PlaceholderAPI expansion
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
        }

        getLogger().info("ServerKeys disabled!");
    }

    private void registerCommands() {
        KeyAllCommand keyAllCommand = new KeyAllCommand(this, keysConfig, keyManager);
        getCommand("keyall").setExecutor(keyAllCommand);
        getCommand("keyall").setTabCompleter(keyAllCommand);

        KeysCommand keysCommand = new KeysCommand(this, keysConfig, repository, statsCache);
        getCommand("keys").setExecutor(keysCommand);
        getCommand("keys").setTabCompleter(keysCommand);

        KeyAdminCommand keyAdminCommand =
                new KeyAdminCommand(this, keysConfig, keyManager, repository);
        getCommand("keyadmin").setExecutor(keyAdminCommand);
        getCommand("keyadmin").setTabCompleter(keyAdminCommand);

        ClaimKeysCommand claimKeysCommand = new ClaimKeysCommand(keyManager);
        getCommand("claimkeys").setExecutor(claimKeysCommand);
        getCommand("claimkeys").setTabCompleter(claimKeysCommand);
    }

    /** Reload the plugin configuration. */
    public void reload() {
        reloadConfig();
        keysConfig.reload(getConfig());

        // Clear cache to force reload from DB
        statsCache.clear();

        // Reinitialize managers with new config
        keyManager = new KeyManager(this, keysConfig, repository, statsCache);

        // Reload schedules
        scheduleManager.cancelAll();
        scheduleManager = new ScheduleManager(this, keysConfig, keyManager);
        scheduleManager.loadSchedules();

        getLogger()
                .info(
                        "Configuration reloaded! "
                                + scheduleManager.getActiveScheduleCount()
                                + " schedules active.");
    }

    public static ServerKeys getInstance() {
        return instance;
    }

    public KeysConfig getKeysConfig() {
        return keysConfig;
    }

    public KeysRepository getRepository() {
        return repository;
    }

    public StatsCache getStatsCache() {
        return statsCache;
    }

    public KeyManager getKeyManager() {
        return keyManager;
    }

    public ScheduleManager getScheduleManager() {
        return scheduleManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Check for unclaimed keys after a short delay (let other join tasks complete first)
        Bukkit.getScheduler()
                .runTaskLater(
                        this,
                        () -> {
                            if (!player.isOnline()) return;
                            repository
                                    .getUnclaimedCountAsync(player.getUniqueId())
                                    .thenAccept(
                                            count -> {
                                                if (count > 0) {
                                                    Bukkit.getScheduler()
                                                            .runTask(
                                                                    this,
                                                                    () -> {
                                                                        if (player.isOnline()) {
                                                                            TextUtil.sendWarning(
                                                                                    player,
                                                                                    "You have <white>"
                                                                                            + count
                                                                                            + "</white> unclaimed key(s)! Use <white>/claimkeys</white> to claim them.");
                                                                        }
                                                                    });
                                                }
                                            });
                        },
                        60L); // 3 second delay
    }
}
