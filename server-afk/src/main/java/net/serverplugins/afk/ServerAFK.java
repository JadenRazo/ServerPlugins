package net.serverplugins.afk;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import net.serverplugins.afk.commands.AfkCommand;
import net.serverplugins.afk.commands.PlayerAfkCommand;
import net.serverplugins.afk.listeners.ActivityListener;
import net.serverplugins.afk.listeners.CombatListener;
import net.serverplugins.afk.listeners.PlayerMoveListener;
import net.serverplugins.afk.managers.AntiExploitManager;
import net.serverplugins.afk.managers.GlobalAfkManager;
import net.serverplugins.afk.managers.HologramManager;
import net.serverplugins.afk.managers.PlayerTracker;
import net.serverplugins.afk.managers.RewardScheduler;
import net.serverplugins.afk.managers.StatsManager;
import net.serverplugins.afk.managers.ZoneManager;
import net.serverplugins.afk.placeholders.AfkPlaceholderExpansion;
import net.serverplugins.afk.repository.AfkRepository;
import net.serverplugins.api.ServerAPI;
import net.serverplugins.api.database.Database;
import net.serverplugins.api.economy.EconomyProvider;
import net.serverplugins.api.permissions.PermissionProvider;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class ServerAFK extends JavaPlugin {

    private static ServerAFK instance;

    private AfkConfig afkConfig;
    private Database database;
    private AfkRepository repository;
    private ZoneManager zoneManager;
    private PlayerTracker playerTracker;
    private RewardScheduler rewardScheduler;
    private EconomyProvider economy;
    private PermissionProvider permissions;
    private HologramManager hologramManager;

    // New managers
    private GlobalAfkManager globalAfkManager;
    private AntiExploitManager antiExploitManager;
    private StatsManager statsManager;
    private CombatListener combatListener;

    // PlaceholderAPI expansion
    private AfkPlaceholderExpansion placeholderExpansion;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        afkConfig = new AfkConfig(this);

        if (!setupDatabase()) {
            getLogger().severe("Failed to setup database! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        repository = new AfkRepository(database);
        zoneManager = new ZoneManager(this, repository);
        playerTracker = new PlayerTracker(this);
        rewardScheduler = new RewardScheduler(this);

        // Initialize new managers
        statsManager = new StatsManager(this);
        globalAfkManager = new GlobalAfkManager(this);
        antiExploitManager = new AntiExploitManager(this);
        combatListener = new CombatListener(this);

        setupEconomy();
        setupPermissions();
        setupHolograms();
        setupPlaceholderAPI();
        registerCommands();
        registerListeners();

        // Start managers
        rewardScheduler.start();
        globalAfkManager.start();
        antiExploitManager.start();

        // Pre-load stats for online players
        if (statsManager != null) {
            statsManager.preloadOnlinePlayers();
        }

        getLogger()
                .info(
                        "ServerAFK enabled with "
                                + (antiExploitManager.isEnabled() ? "anti-exploit" : "basic")
                                + " mode!");
    }

    @Override
    public void onDisable() {
        // Unregister PlaceholderAPI expansion
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }

        // Stop managers in reverse order
        if (antiExploitManager != null) {
            antiExploitManager.stop();
        }
        if (globalAfkManager != null) {
            globalAfkManager.stop();
        }
        if (hologramManager != null) {
            hologramManager.stop();
        }
        if (rewardScheduler != null) {
            rewardScheduler.stop();
        }

        // Save all cached statistics
        if (statsManager != null) {
            statsManager.saveAllCached();
        }

        // Clear sessions
        if (playerTracker != null) {
            playerTracker.clearAllSessions();
        }

        instance = null;
        getLogger().info("ServerAFK disabled!");
    }

    private boolean setupDatabase() {
        // Use shared database connection from ServerAPI
        database = ServerAPI.getInstance().getDatabase();
        if (database == null) {
            getLogger().severe("ServerAPI database not available");
            return false;
        }

        getLogger().info("Using shared database connection from ServerAPI");

        // Execute schema to create tables
        try (InputStream is = getResource("schema.sql")) {
            if (is != null) {
                String schema = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                for (String stmt : schema.split(";")) {
                    String trimmed = stmt.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                        try {
                            database.execute(trimmed);
                            getLogger()
                                    .info(
                                            "Schema executed: "
                                                    + trimmed.substring(
                                                            0, Math.min(50, trimmed.length()))
                                                    + "...");
                        } catch (Exception e) {
                            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                            // Ignore expected errors: already exists, duplicate key/index
                            if (msg.contains("already exists") || msg.contains("duplicate")) {
                                // Expected, table/index already exists
                            } else {
                                // Log actual errors for debugging
                                getLogger().warning("Schema execution error: " + e.getMessage());
                                getLogger().warning("Statement was: " + trimmed);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            getLogger().severe("Schema execution failed: " + e.getMessage());
            return false;
        }

        return true;
    }

    private void setupEconomy() {
        ServerAPI api = ServerAPI.getInstance();
        if (api != null) {
            economy = api.getEconomyProvider();
        }
        if (economy == null || !economy.isAvailable()) {
            getLogger().warning("Economy provider not available. Currency rewards will not work.");
        }
    }

    private void setupPermissions() {
        ServerAPI api = ServerAPI.getInstance();
        if (api != null) {
            permissions = api.getPermissionProvider();
        }
        if (permissions == null) {
            getLogger()
                    .warning("Permission provider not available. Rank multipliers will not work.");
        }
    }

    private void setupHolograms() {
        if (getServer().getPluginManager().getPlugin("DecentHolograms") != null) {
            hologramManager = new HologramManager(this);
            hologramManager.start();
            getLogger().info("DecentHolograms integration enabled!");
        } else {
            getLogger().warning("DecentHolograms not found. Hologram features disabled.");
        }
    }

    private void setupPlaceholderAPI() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderExpansion = new AfkPlaceholderExpansion(this);
            if (placeholderExpansion.register()) {
                getLogger().info("PlaceholderAPI integration enabled!");
            } else {
                getLogger().warning("Failed to register PlaceholderAPI expansion!");
            }
        } else {
            getLogger().warning("PlaceholderAPI not found. Placeholder features disabled.");
        }
    }

    private void registerCommands() {
        // Admin command /serverafk
        AfkCommand adminCmd = new AfkCommand(this);
        PluginCommand adminPluginCmd = getCommand("serverafk");
        if (adminPluginCmd != null) {
            adminPluginCmd.setExecutor(adminCmd);
            adminPluginCmd.setTabCompleter(adminCmd);
        }

        // Player command /afk
        PlayerAfkCommand playerCmd = new PlayerAfkCommand(this);
        PluginCommand playerPluginCmd = getCommand("afk");
        if (playerPluginCmd != null) {
            playerPluginCmd.setExecutor(playerCmd);
            playerPluginCmd.setTabCompleter(playerCmd);
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerMoveListener(this), this);

        // Register new listeners
        if (globalAfkManager != null) {
            getServer()
                    .getPluginManager()
                    .registerEvents(new ActivityListener(this, globalAfkManager), this);
        }

        // Register combat listener
        if (combatListener != null) {
            getServer().getPluginManager().registerEvents(combatListener, this);
        }
    }

    public void reload() {
        afkConfig.reload();
        zoneManager.reload();

        // Reload new managers
        if (globalAfkManager != null) {
            globalAfkManager.loadConfig();
        }
        if (antiExploitManager != null) {
            antiExploitManager.loadConfig();
        }
        if (statsManager != null) {
            statsManager.clearCache();
        }

        getLogger().info("ServerAFK reloaded!");
    }

    // Getters
    public static ServerAFK getInstance() {
        return instance;
    }

    public AfkConfig getAfkConfig() {
        return afkConfig;
    }

    public Database getDatabase() {
        return database;
    }

    public AfkRepository getRepository() {
        return repository;
    }

    public ZoneManager getZoneManager() {
        return zoneManager;
    }

    public PlayerTracker getPlayerTracker() {
        return playerTracker;
    }

    public RewardScheduler getRewardScheduler() {
        return rewardScheduler;
    }

    public EconomyProvider getEconomy() {
        return economy;
    }

    public PermissionProvider getPermissions() {
        return permissions;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    // New getters
    public GlobalAfkManager getGlobalAfkManager() {
        return globalAfkManager;
    }

    public AntiExploitManager getAntiExploitManager() {
        return antiExploitManager;
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }

    public CombatListener getCombatListener() {
        return combatListener;
    }
}
