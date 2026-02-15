package net.serverplugins.enchants;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import net.serverplugins.api.ServerAPI;
import net.serverplugins.api.database.Database;
import net.serverplugins.enchants.commands.EnchanterCommand;
import net.serverplugins.enchants.listeners.EnchantScrollListener;
import net.serverplugins.enchants.listeners.EnchantmentListener;
import net.serverplugins.enchants.listeners.GameGuiListener;
import net.serverplugins.enchants.managers.DailyAttemptManager;
import net.serverplugins.enchants.managers.EnchantmentApplicationManager;
import net.serverplugins.enchants.managers.EnchantmentRegistry;
import net.serverplugins.enchants.managers.EnchantmentTickManager;
import net.serverplugins.enchants.managers.GameSessionManager;
import net.serverplugins.enchants.managers.ProgressionManager;
import net.serverplugins.enchants.repository.EnchanterRepository;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class ServerEnchants extends JavaPlugin {

    private static ServerEnchants instance;
    private EnchantsConfig enchantsConfig;
    private Database database;
    private EnchanterRepository repository;
    private EnchantmentRegistry enchantmentRegistry;
    private EnchantmentApplicationManager applicationManager;
    private GameSessionManager gameSessionManager;
    private ProgressionManager progressionManager;
    private DailyAttemptManager dailyAttemptManager;
    private EnchantmentTickManager tickManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        enchantsConfig = new EnchantsConfig(getConfig());
        database = ServerAPI.getInstance().getDatabase();
        initializeDatabase();

        repository = new EnchanterRepository(database);
        enchantmentRegistry = new EnchantmentRegistry(getLogger());
        enchantmentRegistry.registerDefaults();
        applicationManager = new EnchantmentApplicationManager(enchantmentRegistry);
        progressionManager = new ProgressionManager(this, repository, enchantmentRegistry);
        dailyAttemptManager = new DailyAttemptManager(repository);
        gameSessionManager = new GameSessionManager(this);
        tickManager = new EnchantmentTickManager(this);

        registerListeners();
        registerCommands();

        // Start enchantment tick manager for passive effects (Magnet, etc.)
        tickManager.start();

        getLogger()
                .info(
                        "ServerEnchants enabled with "
                                + enchantmentRegistry.getAll().size()
                                + " enchantments");
    }

    @Override
    public void onDisable() {
        if (tickManager != null) {
            tickManager.stop();
        }
        if (gameSessionManager != null) {
            gameSessionManager.cleanup();
        }
        if (progressionManager != null) {
            progressionManager.saveAll();
        }
        instance = null;
    }

    private void initializeDatabase() {
        try (InputStream is = getResource("schema.sql")) {
            if (is != null) {
                String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                String[] statements = sql.split(";");
                for (String statement : statements) {
                    String trimmed = statement.trim();
                    if (!trimmed.isEmpty()) {
                        database.executeUpdate(trimmed);
                    }
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize database", e);
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new EnchantmentListener(this), this);
        getServer()
                .getPluginManager()
                .registerEvents(new GameGuiListener(this, gameSessionManager), this);
        getServer().getPluginManager().registerEvents(new EnchantScrollListener(this), this);
    }

    private void registerCommands() {
        PluginCommand cmd = getCommand("enchanter");
        if (cmd != null) {
            EnchanterCommand enchanterCmd = new EnchanterCommand(this);
            cmd.setExecutor(enchanterCmd);
        }
    }

    public static ServerEnchants getInstance() {
        return instance;
    }

    public EnchantsConfig getEnchantsConfig() {
        return enchantsConfig;
    }

    public Database getDatabase() {
        return database;
    }

    public EnchanterRepository getRepository() {
        return repository;
    }

    public EnchantmentRegistry getEnchantmentRegistry() {
        return enchantmentRegistry;
    }

    public EnchantmentApplicationManager getEnchantmentApplicationManager() {
        return applicationManager;
    }

    public GameSessionManager getGameSessionManager() {
        return gameSessionManager;
    }

    public ProgressionManager getProgressionManager() {
        return progressionManager;
    }

    public DailyAttemptManager getDailyAttemptManager() {
        return dailyAttemptManager;
    }
}
