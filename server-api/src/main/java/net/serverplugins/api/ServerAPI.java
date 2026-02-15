package net.serverplugins.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import net.serverplugins.api.commands.ServerCommand;
import net.serverplugins.api.configuration.ConfigManager;
import net.serverplugins.api.configuration.parsers.Parser;
import net.serverplugins.api.database.Database;
import net.serverplugins.api.database.DatabaseType;
import net.serverplugins.api.database.impl.H2Database;
import net.serverplugins.api.database.impl.MariaDBDatabase;
import net.serverplugins.api.database.impl.SQLiteDatabase;
import net.serverplugins.api.economy.EconomyProvider;
import net.serverplugins.api.gems.GemsPlaceholderExpansion;
import net.serverplugins.api.gems.GemsProvider;
import net.serverplugins.api.gems.GemsRepository;
import net.serverplugins.api.gui.GuiListener;
import net.serverplugins.api.gui.GuiManager;
import net.serverplugins.api.permissions.PermissionProvider;
import net.serverplugins.api.protection.SellGUIProtection;
import net.serverplugins.api.utils.PacketUtils;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class ServerAPI extends JavaPlugin {

    private static final int BSTATS_PLUGIN_ID =
            12345; // Replace with actual bStats ID when registered

    private static ServerAPI instance;
    private ConfigManager configManager;
    private Database database;
    private EconomyProvider economyProvider;
    private PermissionProvider permissionProvider;
    private GuiManager guiManager;
    private GemsProvider gemsProvider;
    private ServerType serverType;
    private PacketUtils packetUtils;

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("Initializing ServerAPI...");

        // Detect server type
        serverType = ServerType.detect();
        getLogger().info("Detected server type: " + serverType.getName());

        // Initialize configuration parsers
        Parser.initializeDefaults();

        configManager = new ConfigManager(this);
        saveDefaultConfig();

        initializeDatabase();
        initializeGems();

        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            economyProvider = new EconomyProvider();
            if (economyProvider.isAvailable()) {
                getLogger().info("Vault economy provider hooked successfully");
            } else {
                getLogger().warning("Vault found but no economy plugin detected");
            }
        }

        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            permissionProvider = new PermissionProvider();
            getLogger().info("LuckPerms permissions provider hooked successfully");
        }

        guiManager = new GuiManager();
        Bukkit.getPluginManager().registerEvents(new GuiListener(guiManager), this);
        getLogger().info("[DEBUG] GuiListener registered successfully");

        // Register SellGUI protection listener
        Bukkit.getPluginManager().registerEvents(new SellGUIProtection(), this);
        getLogger().info("SellGUI protection system enabled");

        // Initialize PacketUtils for inventory hiding (requires ProtocolLib)
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") != null) {
            packetUtils = new PacketUtils(this);
            getLogger().info("PacketUtils initialized with ProtocolLib support");
        } else {
            getLogger().warning("ProtocolLib not found - GUI inventory hiding will be disabled");
        }

        // Initialize bStats metrics
        if (getConfig().getBoolean("metrics.enabled", true)) {
            new Metrics(this, BSTATS_PLUGIN_ID);
            getLogger().info("bStats metrics enabled");
        }

        // Register BungeeCord messaging channel for server transfers
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        // Register commands
        ServerCommand serverCmd = new ServerCommand(this);
        getCommand("server").setExecutor(serverCmd);
        getCommand("server").setTabCompleter(serverCmd);
        // Register transfer command (bypasses Velocity's /server intercept)
        getCommand("transfer").setExecutor(serverCmd);
        getCommand("transfer").setTabCompleter(serverCmd);

        getLogger().info("ServerAPI enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (database != null) {
            database.disconnect();
        }
        if (guiManager != null) {
            guiManager.closeAll();
        }
        getLogger().info("ServerAPI disabled!");
    }

    private void initializeDatabase() {
        String dbTypeString = getConfig().getString("database.type", "H2").toUpperCase();
        DatabaseType dbType;

        try {
            dbType = DatabaseType.valueOf(dbTypeString);
        } catch (IllegalArgumentException e) {
            getLogger().warning("Invalid database type, defaulting to H2");
            dbType = DatabaseType.H2;
        }

        database =
                switch (dbType) {
                    case H2 -> {
                        String path =
                                getConfig()
                                        .getString(
                                                "database.h2.path",
                                                getDataFolder().getAbsolutePath() + "/data");
                        String dbName = getConfig().getString("database.h2.name", "server");
                        yield new H2Database(path, dbName);
                    }
                    case SQLITE -> {
                        String path =
                                getConfig()
                                        .getString(
                                                "database.sqlite.path",
                                                getDataFolder().getAbsolutePath() + "/data.db");
                        yield new SQLiteDatabase(path);
                    }
                    case MYSQL -> {
                        getLogger().severe("MySQL not yet implemented, use MARIADB instead");
                        yield null;
                    }
                    case MARIADB -> {
                        String host = getConfig().getString("database.host", "localhost");
                        int port = getConfig().getInt("database.port", 3306);
                        String dbName = getConfig().getString("database.database", "serverplugins");
                        String username = getConfig().getString("database.username", "root");
                        String password = getConfig().getString("database.password", "");
                        int poolSize = getConfig().getInt("database.pool-size", 10);
                        long timeout = getConfig().getLong("database.connection-timeout", 30000);
                        yield new MariaDBDatabase(
                                host, port, dbName, username, password, poolSize, timeout);
                    }
                };

        if (database != null) {
            try {
                database.connect();
                getLogger().info("Connected to " + dbType + " database");
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Failed to connect to database", e);
            }
        }
    }

    private void initializeGems() {
        if (database == null) {
            getLogger().warning("Database not available - gems system disabled");
            return;
        }

        // Execute gems schema
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
                            getLogger().warning("Gems schema statement failed: " + ex.getMessage());
                        }
                    }
                }
            }
        } catch (IOException e) {
            getLogger().warning("Failed to load gems schema: " + e.getMessage());
            return;
        }

        gemsProvider = new GemsProvider(new GemsRepository(database));
        getLogger().info("Gems currency system initialized");

        // Register PlaceholderAPI expansion
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new GemsPlaceholderExpansion(gemsProvider, getDescription().getVersion()).register();
            getLogger().info("Gems PlaceholderAPI expansion registered");
        }
    }

    public static ServerAPI getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public Database getDatabase() {
        return database;
    }

    public EconomyProvider getEconomyProvider() {
        return economyProvider;
    }

    public PermissionProvider getPermissionProvider() {
        return permissionProvider;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public GemsProvider getGemsProvider() {
        return gemsProvider;
    }

    public ServerType getServerType() {
        return serverType;
    }

    public PacketUtils getPacketUtils() {
        return packetUtils;
    }

    /**
     * Gets the special character used to trigger inventory hiding in GUI titles. When a GUI title
     * contains this character, the player's inventory will be hidden.
     */
    public String getInvisibleInventoryTitle() {
        return getConfig().getString("invisible-inventory-title", "\uF000");
    }
}
