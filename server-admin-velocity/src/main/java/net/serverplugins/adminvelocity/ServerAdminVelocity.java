package net.serverplugins.adminvelocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.adminvelocity.alts.AltDetector;
import net.serverplugins.adminvelocity.commands.*;
import net.serverplugins.adminvelocity.database.AdminDatabase;
import net.serverplugins.adminvelocity.database.AltTable;
import net.serverplugins.adminvelocity.database.PunishmentTable;
import net.serverplugins.adminvelocity.listeners.ChatListener;
import net.serverplugins.adminvelocity.listeners.ConnectionListener;
import net.serverplugins.adminvelocity.punishment.PunishmentEnforcer;
import net.serverplugins.adminvelocity.redis.AdminRedisClient;
import net.serverplugins.adminvelocity.staffchat.StaffChatRouter;
import org.slf4j.Logger;

@Plugin(
        id = "server-admin-velocity",
        name = "ServerAdmin Velocity",
        version = "1.1.0",
        description = "Network-level admin tools for ServerPlugins",
        authors = {"ServerPlugins"})
public class ServerAdminVelocity {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private AdminVelocityConfig config;
    private AdminDatabase database;
    private AdminRedisClient redisClient;
    private PunishmentEnforcer punishmentEnforcer;
    private AltDetector altDetector;
    private StaffChatRouter staffChatRouter;
    private ConnectionListener connectionListener;
    private ChatListener chatListener;

    // Track vanished players across the network
    private final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();

    @Inject
    public ServerAdminVelocity(
            ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("Initializing ServerAdmin Velocity...");

        try {
            // 1. Load config
            loadConfig();

            // 2. Connect to MariaDB
            connectDatabase();

            // 3. Run schema initialization
            initializeSchema();

            // 4. Connect to Redis (if enabled)
            connectRedis();

            // 5. Initialize managers
            initializeManagers();

            // 6. Register event listeners
            registerListeners();

            // 7. Register commands
            registerCommands();

            logger.info("ServerAdmin Velocity initialized successfully!");
        } catch (Exception e) {
            logger.error("Failed to initialize ServerAdmin Velocity: {}", e.getMessage(), e);
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("Shutting down ServerAdmin Velocity...");

        // Close Redis
        if (redisClient != null) {
            redisClient.close();
        }

        // Close database
        if (database != null) {
            database.close();
        }

        logger.info("ServerAdmin Velocity shutdown complete.");
    }

    private void loadConfig() {
        try {
            config = new AdminVelocityConfig(dataDirectory, logger);
            config.load();
            logger.info("Configuration loaded successfully");
        } catch (IOException e) {
            logger.error("Failed to load configuration: {}", e.getMessage());
            throw new RuntimeException("Configuration load failed", e);
        }
    }

    private void connectDatabase() {
        try {
            database = new AdminDatabase(logger);
            database.connect(config);
            logger.info("Connected to MariaDB successfully");
        } catch (Exception e) {
            logger.error("Failed to connect to MariaDB: {}", e.getMessage(), e);
            throw new RuntimeException("Database connection failed", e);
        }
    }

    private void initializeSchema() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("schema.sql")) {
            if (is == null) {
                logger.warn("schema.sql not found - tables may not exist");
                return;
            }

            String sql = new String(is.readAllBytes());
            database.executeSchema(sql);
            logger.info("Database schema initialized");
        } catch (Exception e) {
            logger.error("Failed to initialize database schema: {}", e.getMessage());
        }
    }

    private void connectRedis() {
        if (!config.isRedisEnabled()) {
            logger.info("Redis is disabled in configuration");
            return;
        }

        redisClient = new AdminRedisClient(server, logger);
        redisClient.connect(
                config.getRedisHost(), config.getRedisPort(), config.getRedisPassword());

        // Register Redis message handlers
        redisClient.onStaffChat(
                json -> {
                    if (staffChatRouter != null) {
                        staffChatRouter.handleIncomingMessage(json);
                    }
                });

        redisClient.onMuteSync(
                json -> {
                    // Handle mute sync from Bukkit servers
                    if (chatListener != null && json.has("targetUuid")) {
                        // Implementation for syncing mutes from Bukkit
                        logger.debug("Received mute sync: {}", json);
                    }
                });

        redisClient.onVanishSync(
                json -> {
                    // Handle vanish sync from Bukkit servers
                    if (json.has("playerUuid") && json.has("vanished")) {
                        try {
                            UUID playerUuid = UUID.fromString(json.get("playerUuid").getAsString());
                            boolean vanished = json.get("vanished").getAsBoolean();
                            String playerName =
                                    json.has("playerName")
                                            ? json.get("playerName").getAsString()
                                            : "Unknown";

                            if (vanished) {
                                vanishedPlayers.add(playerUuid);
                                logger.debug("Player {} is now vanished", playerName);
                            } else {
                                vanishedPlayers.remove(playerUuid);
                                logger.debug("Player {} is no longer vanished", playerName);
                            }
                        } catch (IllegalArgumentException e) {
                            logger.warn("Invalid UUID in vanish sync message");
                        }
                    }
                });

        logger.info("Redis client initialized successfully");
    }

    private void initializeManagers() {
        PunishmentTable punishmentTable = new PunishmentTable(database, logger);
        AltTable altTable = new AltTable(database, logger);

        punishmentEnforcer = new PunishmentEnforcer(server, logger, punishmentTable, redisClient);
        altDetector =
                new AltDetector(
                        server,
                        logger,
                        altTable,
                        punishmentTable,
                        config.getMaxAccountsPerIp(),
                        config.isNotifyStaffOfAlts());

        if (config.isStaffChatEnabled()) {
            staffChatRouter =
                    new StaffChatRouter(server, logger, redisClient, config.getStaffChatFormat());
        }

        logger.info("Managers initialized successfully");
    }

    private void registerListeners() {
        connectionListener =
                new ConnectionListener(
                        logger,
                        punishmentEnforcer,
                        altDetector,
                        config.isAltsEnabled(),
                        config.isDenyBannedAlts());
        server.getEventManager().register(this, connectionListener);

        if (config.isStaffChatEnabled()) {
            PunishmentTable punishmentTable = new PunishmentTable(database, logger);
            chatListener =
                    new ChatListener(
                            logger, punishmentTable, staffChatRouter, config.isStaffChatEnabled());
            server.getEventManager().register(this, chatListener);

            // Load mutes for online players on startup
            server.getAllPlayers()
                    .forEach(player -> chatListener.loadMuteOnJoin(player.getUniqueId()));
        }

        logger.info("Event listeners registered successfully");
    }

    private void registerCommands() {
        CommandManager cm = server.getCommandManager();

        // /gban
        CommandMeta gbanMeta = cm.metaBuilder("gban").plugin(this).build();
        cm.register(
                gbanMeta,
                new GBanCommand(server, logger, punishmentEnforcer, config.getDefaultBanReason()));

        // /gunban
        CommandMeta gunbanMeta = cm.metaBuilder("gunban").plugin(this).build();
        cm.register(gunbanMeta, new GUnbanCommand(server, logger, punishmentEnforcer));

        // /gkick
        CommandMeta gkickMeta = cm.metaBuilder("gkick").plugin(this).build();
        cm.register(
                gkickMeta,
                new GKickCommand(
                        server, logger, punishmentEnforcer, config.getDefaultKickReason()));

        // /galts
        if (config.isAltsEnabled()) {
            CommandMeta galtsMeta = cm.metaBuilder("galts").plugin(this).build();
            PunishmentTable punishmentTable = new PunishmentTable(database, logger);
            cm.register(galtsMeta, new GAltsCommand(server, logger, altDetector, punishmentTable));
        }

        // /ghistory
        CommandMeta ghistoryMeta = cm.metaBuilder("ghistory").plugin(this).build();
        PunishmentTable punishmentTable = new PunishmentTable(database, logger);
        cm.register(ghistoryMeta, new GHistoryCommand(server, logger, punishmentTable));

        // /sc (staff chat)
        if (config.isStaffChatEnabled() && staffChatRouter != null && chatListener != null) {
            CommandMeta scMeta = cm.metaBuilder("sc").plugin(this).build();
            cm.register(scMeta, new StaffChatCommand(logger, staffChatRouter, chatListener));
        }

        // /gshutdown and /grestart (server control)
        if (config.isServerControlEnabled() && redisClient != null) {
            CommandMeta gshutdownMeta = cm.metaBuilder("gshutdown").plugin(this).build();
            cm.register(
                    gshutdownMeta,
                    new GShutdownCommand(
                            server, logger, redisClient, config.getServerControlDefaultDelay()));

            CommandMeta grestartMeta = cm.metaBuilder("grestart").plugin(this).build();
            cm.register(
                    grestartMeta,
                    new GRestartCommand(
                            server, logger, redisClient, config.getServerControlDefaultDelay()));
        }

        logger.info("Commands registered successfully");
    }

    // ========== GETTERS ==========

    public ProxyServer getServer() {
        return server;
    }

    public Logger getLogger() {
        return logger;
    }

    public AdminVelocityConfig getConfig() {
        return config;
    }

    public AdminDatabase getDatabase() {
        return database;
    }

    public AdminRedisClient getRedisClient() {
        return redisClient;
    }

    public PunishmentEnforcer getPunishmentEnforcer() {
        return punishmentEnforcer;
    }

    public AltDetector getAltDetector() {
        return altDetector;
    }

    public StaffChatRouter getStaffChatRouter() {
        return staffChatRouter;
    }

    public ChatListener getChatListener() {
        return chatListener;
    }

    /**
     * Checks if a player is vanished on any backend server.
     *
     * @param uuid Player's UUID
     * @return true if the player is vanished
     */
    public boolean isVanished(UUID uuid) {
        return vanishedPlayers.contains(uuid);
    }
}
