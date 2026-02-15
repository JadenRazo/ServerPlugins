package net.serverplugins.arcade;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.milkbowl.vault.economy.Economy;
import net.serverplugins.api.ServerAPI;
import net.serverplugins.api.database.Database;
import net.serverplugins.api.economy.EconomyProvider;
import net.serverplugins.arcade.api.ArcadeStatsAPI;
import net.serverplugins.arcade.commands.ArcadeCommand;
import net.serverplugins.arcade.commands.BaccaratCommand;
import net.serverplugins.arcade.commands.BlackjackCommand;
import net.serverplugins.arcade.commands.CoinflipCommand;
import net.serverplugins.arcade.commands.CrashCommand;
import net.serverplugins.arcade.commands.JackpotCommand;
import net.serverplugins.arcade.commands.MachineCommand;
import net.serverplugins.arcade.commands.MigrateCommand;
import net.serverplugins.arcade.commands.RemoveSeatCommand;
import net.serverplugins.arcade.commands.SlotsCommand;
import net.serverplugins.arcade.commands.SpawnSeatCommand;
import net.serverplugins.arcade.games.GameType;
import net.serverplugins.arcade.games.baccarat.BaccaratManager;
import net.serverplugins.arcade.games.blackjack.BlackjackManager;
import net.serverplugins.arcade.games.blackjack.BlackjackQuitListener;
import net.serverplugins.arcade.games.blackjack.BlackjackType;
import net.serverplugins.arcade.games.coinflip.CoinflipManager;
import net.serverplugins.arcade.games.crash.CrashManager;
import net.serverplugins.arcade.games.crash.CrashType;
import net.serverplugins.arcade.games.dice.DiceCommand;
import net.serverplugins.arcade.games.dice.DiceConfig;
import net.serverplugins.arcade.games.dice.DiceGame;
import net.serverplugins.arcade.games.global.GlobalGameType;
import net.serverplugins.arcade.games.jackpot.JackpotType;
import net.serverplugins.arcade.games.slots.SlotsManager;
import net.serverplugins.arcade.games.slots.SlotsType;
import net.serverplugins.arcade.gui.GuiListener;
import net.serverplugins.arcade.holograms.HologramManager;
import net.serverplugins.arcade.integrations.DiscordWebhook;
import net.serverplugins.arcade.machines.Machine;
import net.serverplugins.arcade.machines.MachineListener;
import net.serverplugins.arcade.machines.MachineManager;
import net.serverplugins.arcade.placeholders.ArcadePlaceholderExpansion;
import net.serverplugins.arcade.statistics.StatisticsTracker;
import net.serverplugins.arcade.utils.AuditLogger;
import net.serverplugins.arcade.utils.ChatInputManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class ServerArcade extends JavaPlugin {

    private static ServerArcade instance;
    private static Economy vaultEconomy;

    private ArcadeConfig arcadeConfig;
    private EconomyProvider economy;
    private Database database;
    private MachineManager machineManager;
    private MachineListener machineListener;
    private ChatInputManager chatInputManager;
    private HologramManager hologramManager;

    // New game type system
    private final Map<String, GameType> gameTypes = new HashMap<>();
    private SlotsType slotsType;
    private BlackjackType blackjackType;
    private JackpotType jackpotType;
    private CrashType crashType;

    // Legacy command-based managers
    private SlotsManager slotsManager;
    private BlackjackManager blackjackManager;
    private BaccaratManager baccaratManager;
    private CrashManager crashManager;
    private CoinflipManager coinflipManager;

    // Dice game
    private DiceGame diceGame;
    private DiceConfig diceConfig;

    // Integrations
    private DiscordWebhook discordWebhook;
    private StatisticsTracker statisticsTracker;
    private ArcadeStatsAPI statsAPI;

    // Player protection
    private net.serverplugins.arcade.protection.ExclusionManager exclusionManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        arcadeConfig = new ArcadeConfig(this);

        // Validate configuration
        try {
            arcadeConfig.validateConfig();
        } catch (org.bukkit.configuration.InvalidConfigurationException e) {
            getLogger().severe("Configuration validation failed!");
            getLogger().severe(e.getMessage());
            getLogger()
                    .severe("Plugin will not load. Please fix config.yml and restart the server.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize audit logger with plugin instance for async task scheduling
        AuditLogger.initialize(this);

        setupEconomy();
        setupVaultEconomy();
        setupDatabase();

        // Initialize game types (must run before legacy managers that depend on them)
        initGameTypes();

        // Initialize legacy managers for command-based games
        initLegacyManagers();

        // Initialize integrations
        discordWebhook = new DiscordWebhook(this);

        // Only initialize database-dependent components if database is available
        if (isDatabaseAvailable()) {
            statisticsTracker = new StatisticsTracker(this);
            exclusionManager = new net.serverplugins.arcade.protection.ExclusionManager(this);

            // Start REST API for website integration
            statsAPI = new ArcadeStatsAPI(this);
            statsAPI.start();
        } else {
            getLogger().warning("Database not available - statistics tracking and API disabled");
            getLogger().warning("Machine placement will work but persistence is disabled");
        }

        // Initialize hologram manager for legacy hologram cleanup only
        // Note: Machine labels are baked into 3D models via custom_model_data, not DecentHolograms
        hologramManager = new HologramManager(this);

        // Initialize machine system
        Machine.initKeys(this);
        if (arcadeConfig.areMachinesEnabled()) {
            // Pass database even if null - MachineManager will handle gracefully
            machineManager = new MachineManager(this, database);
            machineManager.initialize();
            machineListener = new MachineListener(this);
            getServer().getPluginManager().registerEvents(machineListener, this);
        }

        // Register GUI listener
        getServer().getPluginManager().registerEvents(new GuiListener(), this);

        // Register Blackjack quit listener for game state cleanup
        getServer().getPluginManager().registerEvents(new BlackjackQuitListener(this), this);

        // Initialize chat input manager for custom bet input
        chatInputManager = new ChatInputManager(this);
        getServer().getPluginManager().registerEvents(chatInputManager, this);

        // Register PlaceholderAPI expansion if available
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ArcadePlaceholderExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion registered");
        }

        registerCommands();

        getLogger().info("ServerArcade enabled!");
    }

    @Override
    public void onDisable() {
        // Flush pending audit logs before shutdown
        AuditLogger.shutdown();

        // Shutdown API
        if (statsAPI != null) {
            statsAPI.stop();
        }

        // Shutdown machine listener to clean up action bar task
        if (machineListener != null) {
            machineListener.shutdown();
        }

        // Note: Holograms are not managed by DecentHolograms anymore - they're part of the 3D
        // models
        // The HologramManager now only cleans up legacy DreamArcade holograms on startup

        if (machineManager != null) {
            machineManager.shutdown();
        }

        // Shutdown game types
        for (GameType type : gameTypes.values()) {
            if (type instanceof GlobalGameType globalGame) {
                globalGame.shutdown();
            }
        }

        // Shutdown legacy managers
        if (coinflipManager != null) coinflipManager.shutdown();
        if (crashManager != null) crashManager.shutdown();

        instance = null;
        getLogger().info("ServerArcade disabled!");
    }

    private void initLegacyManagers() {
        slotsManager = new SlotsManager(this);
        blackjackManager = new BlackjackManager(this);
        baccaratManager = new BaccaratManager(this);
        crashManager = new CrashManager(this);
        coinflipManager = new CoinflipManager(this);

        // Initialize dice game
        diceConfig = new DiceConfig(loadGameConfig("dice"));
        diceGame = new DiceGame(this, diceConfig);
    }

    private void setupEconomy() {
        ServerAPI api = ServerAPI.getInstance();
        if (api != null) {
            economy = api.getEconomyProvider();
        }
        if (economy == null) {
            getLogger().warning("No ServerAPI economy provider found!");
        }
    }

    private void setupVaultEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault not found! Economy features will be limited.");
            return;
        }
        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            vaultEconomy = rsp.getProvider();
            getLogger().info("Vault economy hooked successfully!");
        } else {
            getLogger().warning("No Vault economy provider found!");
        }
    }

    private void setupDatabase() {
        // Use shared database connection from ServerAPI
        database = ServerAPI.getInstance().getDatabase();
        if (database == null) {
            getLogger()
                    .warning(
                            "ServerAPI database not available - machine persistence will use fallback");
            return;
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
    }

    private void initGameTypes() {
        // Save default game config files
        saveGameConfigs();

        // Create and register game types
        slotsType = new SlotsType(this);
        blackjackType = new BlackjackType(this);
        jackpotType = new JackpotType(this);
        crashType = new CrashType(this);

        registerGameType(slotsType);
        registerGameType(blackjackType);
        registerGameType(jackpotType);
        registerGameType(crashType);

        // Load configs from external game files
        slotsType.loadConfig(loadGameConfig("slots"));
        blackjackType.loadConfig(loadGameConfig("blackjack"));
        jackpotType.loadConfig(loadGameConfig("jackpot"));
        crashType.loadConfig(loadGameConfig("crash"));

        // Start global game timers (for games using GlobalGameType)
        jackpotType.start();

        getLogger().info("Registered " + gameTypes.size() + " game types");
    }

    private void saveGameConfigs() {
        File gamesFolder = new File(getDataFolder(), "games");
        if (!gamesFolder.exists()) {
            gamesFolder.mkdirs();
        }

        saveResource("games/slots.yml", false);
        saveResource("games/blackjack.yml", false);
        saveResource("games/jackpot.yml", false);
        saveResource("games/crash.yml", false);
        saveResource("games/dice.yml", false);
        saveResource("games/quick-lottery.yml", false);
        saveResource("games/mega-jackpot.yml", false);
    }

    private YamlConfiguration loadGameConfig(String game) {
        File file = new File(getDataFolder(), "games/" + game + ".yml");
        if (!file.exists()) {
            getLogger().warning("Game config not found: " + game + ".yml");
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    public void registerGameType(GameType gameType) {
        gameTypes.put(gameType.getConfigKey().toLowerCase(), gameType);
    }

    public GameType getGameType(String key) {
        return gameTypes.get(key.toLowerCase());
    }

    private void registerCommands() {
        getCommand("arcade").setExecutor(new ArcadeCommand(this));
        getCommand("slots").setExecutor(new SlotsCommand(this));
        getCommand("blackjack").setExecutor(new BlackjackCommand(this));

        BaccaratCommand baccaratCmd = new BaccaratCommand(this);
        getCommand("baccarat").setExecutor(baccaratCmd);
        getCommand("baccarat").setTabCompleter(baccaratCmd);
        getCommand("crash").setExecutor(new CrashCommand(this));
        getCommand("coinflip").setExecutor(new CoinflipCommand(this));
        getCommand("jackpot").setExecutor(new JackpotCommand(this));

        // Dice command
        DiceCommand diceCmd = new DiceCommand(this, diceGame, diceConfig);
        getCommand("dice").setExecutor(diceCmd);
        getCommand("dice").setTabCompleter(diceCmd);

        // Gamble command (stats & self-exclusion)
        net.serverplugins.arcade.commands.GambleCommand gambleCmd =
                new net.serverplugins.arcade.commands.GambleCommand(this);
        getCommand("gamble").setExecutor(gambleCmd);
        getCommand("gamble").setTabCompleter(gambleCmd);

        MachineCommand machineCmd = new MachineCommand(this);
        getCommand("arcademachine").setExecutor(machineCmd);
        getCommand("arcademachine").setTabCompleter(machineCmd);

        getCommand("arcademigrate").setExecutor(new MigrateCommand(this));
        getCommand("spawnseat").setExecutor(new SpawnSeatCommand(this));
        getCommand("removeseat").setExecutor(new RemoveSeatCommand(this));
    }

    public void reloadConfiguration() {
        reloadConfig();
        arcadeConfig = new ArcadeConfig(this);

        // Validate configuration
        try {
            arcadeConfig.validateConfig();
        } catch (org.bukkit.configuration.InvalidConfigurationException e) {
            getLogger().severe("Configuration validation failed after reload!");
            getLogger().severe(e.getMessage());
            getLogger()
                    .severe(
                            "Configuration changes were NOT applied. Fix config.yml and reload again.");
            return;
        }

        // Reload messenger
        arcadeConfig.reload();

        // Reload game type configs from external files
        slotsType.loadConfig(loadGameConfig("slots"));
        blackjackType.loadConfig(loadGameConfig("blackjack"));
        jackpotType.loadConfig(loadGameConfig("jackpot"));
        crashType.loadConfig(loadGameConfig("crash"));

        getLogger().info("Configuration reloaded successfully");
    }

    // Static accessors
    public static ServerArcade getInstance() {
        return instance;
    }

    public static Economy getEconomy() {
        return vaultEconomy;
    }

    // Instance accessors
    public ArcadeConfig getArcadeConfig() {
        return arcadeConfig;
    }

    public EconomyProvider getEconomyProvider() {
        return economy;
    }

    public MachineManager getMachineManager() {
        return machineManager;
    }

    public MachineListener getMachineListener() {
        return machineListener;
    }

    public ChatInputManager getChatInputManager() {
        return chatInputManager;
    }

    public Map<String, GameType> getGameTypes() {
        return gameTypes;
    }

    // Game type accessors
    public SlotsType getSlotsType() {
        return slotsType;
    }

    public BlackjackType getBlackjackType() {
        return blackjackType;
    }

    public JackpotType getJackpotType() {
        return jackpotType;
    }

    public CrashType getCrashType() {
        return crashType;
    }

    // Legacy manager accessors
    public SlotsManager getSlotsManager() {
        return slotsManager;
    }

    public BlackjackManager getBlackjackManager() {
        return blackjackManager;
    }

    public BaccaratManager getBaccaratManager() {
        return baccaratManager;
    }

    public CrashManager getCrashManager() {
        return crashManager;
    }

    public CoinflipManager getCoinflipManager() {
        return coinflipManager;
    }

    // Dice game accessors
    public DiceGame getDiceGame() {
        return diceGame;
    }

    public DiceConfig getDiceConfig() {
        return diceConfig;
    }

    public Database getDatabase() {
        return database;
    }

    /**
     * Check if database is available and connected. Use this before any database operations to
     * prevent NullPointerException.
     */
    public boolean isDatabaseAvailable() {
        return database != null;
    }

    // Integration accessors
    public DiscordWebhook getDiscordWebhook() {
        return discordWebhook;
    }

    public StatisticsTracker getStatisticsTracker() {
        return statisticsTracker;
    }

    public ArcadeStatsAPI getStatsAPI() {
        return statsAPI;
    }

    public net.serverplugins.arcade.protection.ExclusionManager getExclusionManager() {
        return exclusionManager;
    }
}
