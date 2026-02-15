package net.serverplugins.commands;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import net.serverplugins.api.ServerAPI;
import net.serverplugins.api.broadcast.BroadcastManager;
import net.serverplugins.api.database.Database;
import net.serverplugins.commands.commands.AdminMenuCommand;
import net.serverplugins.commands.commands.BackCommand;
import net.serverplugins.commands.commands.BanCommand;
import net.serverplugins.commands.commands.BroadcastCommand;
import net.serverplugins.commands.commands.ClearCommand;
import net.serverplugins.commands.commands.DelHomeCommand;
import net.serverplugins.commands.commands.DelWarpCommand;
import net.serverplugins.commands.commands.EcoCommand;
import net.serverplugins.commands.commands.EnderchestCommand;
import net.serverplugins.commands.commands.FeedCommand;
import net.serverplugins.commands.commands.FlyCommand;
import net.serverplugins.commands.commands.GamemodeCommand;
import net.serverplugins.commands.commands.GemsCommand;
import net.serverplugins.commands.commands.GiveKeyCommand;
import net.serverplugins.commands.commands.GodCommand;
import net.serverplugins.commands.commands.GuideCommand;
import net.serverplugins.commands.commands.HealCommand;
import net.serverplugins.commands.commands.HistoryCommand;
import net.serverplugins.commands.commands.HomeCommand;
import net.serverplugins.commands.commands.HomesCommand;
import net.serverplugins.commands.commands.InvseeCommand;
import net.serverplugins.commands.commands.KickCommand;
import net.serverplugins.commands.commands.LinksCommand;
import net.serverplugins.commands.commands.MuteCommand;
import net.serverplugins.commands.commands.PlaytimeCommand;
import net.serverplugins.commands.commands.RenameHomeCommand;
import net.serverplugins.commands.commands.RepairCommand;
import net.serverplugins.commands.commands.RtpMenuCommand;
import net.serverplugins.commands.commands.RulesCommand;
import net.serverplugins.commands.commands.SeenCommand;
import net.serverplugins.commands.commands.SetHomeCommand;
import net.serverplugins.commands.commands.SetHomeDescCommand;
import net.serverplugins.commands.commands.SetWarpCommand;
import net.serverplugins.commands.commands.SpawnCommand;
import net.serverplugins.commands.commands.SpeedCommand;
import net.serverplugins.commands.commands.StaffHistoryCommand;
import net.serverplugins.commands.commands.TempbanCommand;
import net.serverplugins.commands.commands.TimeCommand;
import net.serverplugins.commands.commands.ToggleGuideCommand;
import net.serverplugins.commands.commands.TpAcceptCommand;
import net.serverplugins.commands.commands.TpDenyCommand;
import net.serverplugins.commands.commands.TpaCommand;
import net.serverplugins.commands.commands.UnbanCommand;
import net.serverplugins.commands.commands.UnmuteCommand;
import net.serverplugins.commands.commands.VoteCommand;
import net.serverplugins.commands.commands.WarnCommand;
import net.serverplugins.commands.commands.WarpCommand;
import net.serverplugins.commands.commands.WarpsCommand;
import net.serverplugins.commands.commands.WeatherCommand;
import net.serverplugins.commands.data.BanManager;
import net.serverplugins.commands.data.MuteManager;
import net.serverplugins.commands.data.PlayerDataManager;
import net.serverplugins.commands.data.TpaManager;
import net.serverplugins.commands.data.WarpManager;
import net.serverplugins.commands.data.punishment.PunishmentHistoryManager;
import net.serverplugins.commands.dynamic.DynamicCommandManager;
import net.serverplugins.commands.listeners.ChatListener;
import net.serverplugins.commands.listeners.GodModeListener;
import net.serverplugins.commands.listeners.JoinItemListener;
import net.serverplugins.commands.listeners.PlayerListener;
import net.serverplugins.commands.repository.CommandsRepository;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class ServerCommands extends JavaPlugin {

    private static ServerCommands instance;
    private CommandsConfig commandsConfig;
    private Database database;
    private CommandsRepository repository;
    private DynamicCommandManager dynamicCommandManager;
    private PlayerDataManager playerDataManager;
    private WarpManager warpManager;
    private TpaManager tpaManager;
    private MuteManager muteManager;
    private BanManager banManager;
    private PunishmentHistoryManager punishmentHistoryManager;
    private JoinItemListener joinItemListener;

    // Redis integration (via server-bridge if available)
    private Object redisClient;
    private Method publishModerationLogMethod;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        commandsConfig = new CommandsConfig(this);

        // Register with BroadcastManager
        BroadcastManager.registerPlugin(this);

        // Initialize database
        if (!setupDatabase()) {
            getLogger().warning("Database not configured - using YAML file storage");
            repository = null;
        } else {
            repository = new CommandsRepository(database, getLogger());
        }

        // Initialize managers (pass repository for database-backed storage)
        playerDataManager = new PlayerDataManager(this, repository);
        warpManager = new WarpManager(this, repository);
        tpaManager = new TpaManager(this, getConfig().getInt("tpa.expiration-seconds", 60));
        muteManager = new MuteManager(this, repository);
        banManager = new BanManager(this);

        // Initialize punishment history manager (connects to database if configured)
        punishmentHistoryManager = new PunishmentHistoryManager(this);
        punishmentHistoryManager.initialize();

        // Initialize Redis integration
        initializeRedisIntegration();

        registerCommands();
        registerListeners();

        // Load dynamic commands from config
        dynamicCommandManager = new DynamicCommandManager(this);
        dynamicCommandManager.loadCommands();

        // Register BungeeCord messaging channel for server transfers
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        // Start periodic playtime auto-save (every 5 minutes)
        // This minimizes data loss from crashes or unexpected shutdowns
        startPeriodicPlaytimeSave();

        getLogger()
                .info(
                        "ServerCommands enabled! (plugin@"
                                + System.identityHashCode(this)
                                + ", dcm@"
                                + System.identityHashCode(dynamicCommandManager)
                                + ")");
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) {
            playerDataManager.saveAll();
        }
        if (dynamicCommandManager != null) {
            dynamicCommandManager.unloadCommands();
        }
        if (punishmentHistoryManager != null) {
            punishmentHistoryManager.shutdown();
        }

        // Unregister from BroadcastManager
        BroadcastManager.unregisterPlugin(this);

        instance = null;
        getLogger().info("ServerCommands disabled!");
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
                int executed = 0;
                for (String statement : statements) {
                    String trimmed = statement.trim();
                    if (!trimmed.isEmpty()) {
                        try {
                            database.execute(trimmed);
                            executed++;
                        } catch (Exception ex) {
                            getLogger().warning("Schema statement failed: " + ex.getMessage());
                        }
                    }
                }
                getLogger().info("Executed " + executed + " schema statements");
            }
        } catch (IOException e) {
            getLogger().warning("Failed to load schema: " + e.getMessage());
        }

        return true;
    }

    private void registerCommands() {
        // Basic commands
        getCommand("links").setExecutor(new LinksCommand(this));
        getCommand("spawn").setExecutor(new SpawnCommand(this));
        getCommand("rules").setExecutor(new RulesCommand(this));
        getCommand("vote").setExecutor(new VoteCommand(this));
        getCommand("rtpmenu").setExecutor(new RtpMenuCommand(this));
        GuideCommand guideCommand = new GuideCommand(this);
        getCommand("survivalmenu").setExecutor(guideCommand);
        getCommand("survivalmenu").setTabCompleter(guideCommand);
        getCommand("toggleguide").setExecutor(new ToggleGuideCommand(this));

        // Home commands
        HomeCommand homeCommand = new HomeCommand(this);
        getCommand("home").setExecutor(homeCommand);
        getCommand("home").setTabCompleter(homeCommand);
        SetHomeCommand setHomeCommand = new SetHomeCommand(this);
        getCommand("sethome").setExecutor(setHomeCommand);
        getCommand("sethome").setTabCompleter(setHomeCommand);
        DelHomeCommand delHomeCommand = new DelHomeCommand(this);
        getCommand("delhome").setExecutor(delHomeCommand);
        getCommand("delhome").setTabCompleter(delHomeCommand);
        getCommand("homes").setExecutor(new HomesCommand(this));
        RenameHomeCommand renameHomeCommand = new RenameHomeCommand(this);
        getCommand("renamehome").setExecutor(renameHomeCommand);
        getCommand("renamehome").setTabCompleter(renameHomeCommand);
        SetHomeDescCommand setHomeDescCommand = new SetHomeDescCommand(this);
        getCommand("sethomedesc").setExecutor(setHomeDescCommand);
        getCommand("sethomedesc").setTabCompleter(setHomeDescCommand);

        // Warp commands
        WarpCommand warpCommand = new WarpCommand(this);
        getCommand("warp").setExecutor(warpCommand);
        getCommand("warp").setTabCompleter(warpCommand);
        getCommand("setwarp").setExecutor(new SetWarpCommand(this));
        DelWarpCommand delWarpCommand = new DelWarpCommand(this);
        getCommand("delwarp").setExecutor(delWarpCommand);
        getCommand("delwarp").setTabCompleter(delWarpCommand);
        getCommand("warps").setExecutor(new WarpsCommand(this));

        // Teleport commands
        getCommand("back").setExecutor(new BackCommand(this));
        TpaCommand tpaCommand = new TpaCommand(this, false);
        getCommand("tpa").setExecutor(tpaCommand);
        getCommand("tpa").setTabCompleter(tpaCommand);
        TpaCommand tpaHereCommand = new TpaCommand(this, true);
        getCommand("tpahere").setExecutor(tpaHereCommand);
        getCommand("tpahere").setTabCompleter(tpaHereCommand);
        getCommand("tpaccept").setExecutor(new TpAcceptCommand(this));
        getCommand("tpdeny").setExecutor(new TpDenyCommand(this));

        // Moderation commands
        KickCommand kickCommand = new KickCommand(this);
        getCommand("kick").setExecutor(kickCommand);
        getCommand("kick").setTabCompleter(kickCommand);
        BanCommand banCommand = new BanCommand(this);
        getCommand("ban").setExecutor(banCommand);
        getCommand("ban").setTabCompleter(banCommand);
        TempbanCommand tempbanCommand = new TempbanCommand(this);
        getCommand("tempban").setExecutor(tempbanCommand);
        getCommand("tempban").setTabCompleter(tempbanCommand);
        UnbanCommand unbanCommand = new UnbanCommand(this);
        getCommand("unban").setExecutor(unbanCommand);
        getCommand("unban").setTabCompleter(unbanCommand);
        MuteCommand muteCommand = new MuteCommand(this);
        getCommand("mute").setExecutor(muteCommand);
        getCommand("mute").setTabCompleter(muteCommand);
        UnmuteCommand unmuteCommand = new UnmuteCommand(this);
        getCommand("unmute").setExecutor(unmuteCommand);
        getCommand("unmute").setTabCompleter(unmuteCommand);
        WarnCommand warnCommand = new WarnCommand(this);
        getCommand("warn").setExecutor(warnCommand);
        getCommand("warn").setTabCompleter(warnCommand);

        // Admin commands
        GamemodeCommand gamemodeCommand = new GamemodeCommand(this);
        getCommand("gamemode").setExecutor(gamemodeCommand);
        getCommand("gamemode").setTabCompleter(gamemodeCommand);
        GamemodeCommand gmcCommand = new GamemodeCommand(this, GameMode.CREATIVE);
        getCommand("gmc").setExecutor(gmcCommand);
        getCommand("gmc").setTabCompleter(gmcCommand);
        GamemodeCommand gmsCommand = new GamemodeCommand(this, GameMode.SURVIVAL);
        getCommand("gms").setExecutor(gmsCommand);
        getCommand("gms").setTabCompleter(gmsCommand);
        GamemodeCommand gmaCommand = new GamemodeCommand(this, GameMode.ADVENTURE);
        getCommand("gma").setExecutor(gmaCommand);
        getCommand("gma").setTabCompleter(gmaCommand);
        GamemodeCommand gmspCommand = new GamemodeCommand(this, GameMode.SPECTATOR);
        getCommand("gmsp").setExecutor(gmspCommand);
        getCommand("gmsp").setTabCompleter(gmspCommand);

        FlyCommand flyCommand = new FlyCommand(this);
        getCommand("fly").setExecutor(flyCommand);
        getCommand("fly").setTabCompleter(flyCommand);
        HealCommand healCommand = new HealCommand(this);
        getCommand("heal").setExecutor(healCommand);
        getCommand("heal").setTabCompleter(healCommand);
        FeedCommand feedCommand = new FeedCommand(this);
        getCommand("feed").setExecutor(feedCommand);
        getCommand("feed").setTabCompleter(feedCommand);
        BroadcastCommand announceCommand = new BroadcastCommand(this);
        getCommand("announce").setExecutor(announceCommand);
        getCommand("announce").setTabCompleter(announceCommand);
        GodCommand godCommand = new GodCommand(this);
        getCommand("god").setExecutor(godCommand);
        getCommand("god").setTabCompleter(godCommand);
        SpeedCommand speedCommand = new SpeedCommand(this);
        getCommand("speed").setExecutor(speedCommand);
        getCommand("speed").setTabCompleter(speedCommand);

        // Inventory commands
        InvseeCommand invseeCommand = new InvseeCommand(this);
        getCommand("invsee").setExecutor(invseeCommand);
        getCommand("invsee").setTabCompleter(invseeCommand);
        EnderchestCommand enderchestCommand = new EnderchestCommand(this);
        getCommand("enderchest").setExecutor(enderchestCommand);
        getCommand("enderchest").setTabCompleter(enderchestCommand);
        ClearCommand clearCommand = new ClearCommand(this);
        getCommand("clear").setExecutor(clearCommand);
        getCommand("clear").setTabCompleter(clearCommand);
        getCommand("repair").setExecutor(new RepairCommand(this));

        // World commands
        TimeCommand timeCommand = new TimeCommand(this);
        getCommand("time").setExecutor(timeCommand);
        getCommand("time").setTabCompleter(timeCommand);
        WeatherCommand weatherCommand = new WeatherCommand(this);
        getCommand("weather").setExecutor(weatherCommand);
        getCommand("weather").setTabCompleter(weatherCommand);

        // Info commands
        PlaytimeCommand playtimeCommand = new PlaytimeCommand(this);
        getCommand("playtime").setExecutor(playtimeCommand);
        getCommand("playtime").setTabCompleter(playtimeCommand);
        SeenCommand seenCommand = new SeenCommand(this);
        getCommand("seen").setExecutor(seenCommand);
        getCommand("seen").setTabCompleter(seenCommand);

        // Economy commands
        EcoCommand ecoCommand = new EcoCommand(this);
        getCommand("eco").setExecutor(ecoCommand);
        getCommand("eco").setTabCompleter(ecoCommand);
        GemsCommand gemsCommand = new GemsCommand(this);
        getCommand("gems").setExecutor(gemsCommand);
        getCommand("gems").setTabCompleter(gemsCommand);
        GiveKeyCommand giveKeyCommand = new GiveKeyCommand(this);
        getCommand("givekey").setExecutor(giveKeyCommand);
        getCommand("givekey").setTabCompleter(giveKeyCommand);

        // Admin menu
        getCommand("adminmenu").setExecutor(new AdminMenuCommand(this));

        // Punishment history commands
        HistoryCommand historyCommand = new HistoryCommand(this);
        getCommand("history").setExecutor(historyCommand);
        getCommand("history").setTabCompleter(historyCommand);
        StaffHistoryCommand staffHistoryCommand = new StaffHistoryCommand(this);
        getCommand("staffhistory").setExecutor(staffHistoryCommand);
        getCommand("staffhistory").setTabCompleter(staffHistoryCommand);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new GodModeListener(this), this);
        joinItemListener = new JoinItemListener(this);
        getServer().getPluginManager().registerEvents(joinItemListener, this);
    }

    public void reloadConfiguration() {
        reloadConfig();
        commandsConfig = new CommandsConfig(this);
        commandsConfig.reload();
        if (warpManager != null) {
            warpManager.reload();
        }
        if (muteManager != null) {
            muteManager.reload();
        }
        if (dynamicCommandManager != null) {
            dynamicCommandManager.reload();
        }
        if (joinItemListener != null) {
            joinItemListener.reload();
        }
    }

    private void initializeRedisIntegration() {
        Plugin bridgePlugin = Bukkit.getPluginManager().getPlugin("server-bridge");
        if (bridgePlugin == null || !bridgePlugin.isEnabled()) {
            getLogger().info("server-bridge not found, Discord moderation notifications disabled");
            return;
        }

        try {
            Method getRedisClientMethod = bridgePlugin.getClass().getMethod("getRedisClient");
            redisClient = getRedisClientMethod.invoke(bridgePlugin);

            if (redisClient == null) {
                getLogger()
                        .info(
                                "server-bridge RedisClient not available, Discord moderation notifications disabled");
                return;
            }

            // Get the publish method via reflection
            publishModerationLogMethod =
                    redisClient
                            .getClass()
                            .getMethod(
                                    "publishModerationLog",
                                    String.class,
                                    String.class,
                                    String.class,
                                    String.class,
                                    String.class,
                                    String.class,
                                    String.class);

            getLogger().info("Redis integration enabled for Discord moderation notifications");
        } catch (Exception e) {
            getLogger().warning("Failed to initialize Redis integration: " + e.getMessage());
        }
    }

    public void publishModerationLog(
            String targetName,
            String targetUuid,
            String staffName,
            String staffUuid,
            String punishmentType,
            String reason,
            String duration) {
        if (redisClient == null || publishModerationLogMethod == null) return;

        try {
            publishModerationLogMethod.invoke(
                    redisClient,
                    targetName,
                    targetUuid,
                    staffName,
                    staffUuid,
                    punishmentType,
                    reason,
                    duration);
        } catch (Exception e) {
            getLogger().warning("Failed to publish moderation log: " + e.getMessage());
        }
    }

    /**
     * Start periodic auto-save of playtime for online players. Saves every 5 minutes to minimize
     * data loss from crashes.
     */
    private void startPeriodicPlaytimeSave() {
        long interval = 20 * 60 * 5; // 5 minutes in ticks
        Bukkit.getScheduler()
                .runTaskTimerAsynchronously(
                        this,
                        () -> {
                            for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                                try {
                                    // Update session playtime without resetting session
                                    Long sessionStart =
                                            playerDataManager.getActiveSessionStart(
                                                    player.getUniqueId());
                                    if (sessionStart != null && sessionStart > 0) {
                                        PlayerDataManager.PlayerData data =
                                                playerDataManager.getPlayerData(
                                                        player.getUniqueId());

                                        // Calculate current session time
                                        long currentSessionTime =
                                                System.currentTimeMillis() - sessionStart;

                                        // Update the session start to now (so we don't
                                        // double-count)
                                        data.addPlaytime(currentSessionTime);
                                        playerDataManager.startSession(
                                                player.getUniqueId(), System.currentTimeMillis());
                                        data.setSessionStart(System.currentTimeMillis());

                                        // Save the data
                                        playerDataManager.savePlayerData(player.getUniqueId());
                                    }
                                } catch (Exception e) {
                                    getLogger()
                                            .warning(
                                                    "Failed to auto-save playtime for "
                                                            + player.getName()
                                                            + ": "
                                                            + e.getMessage());
                                }
                            }
                        },
                        interval,
                        interval);

        getLogger().info("Periodic playtime auto-save enabled (interval: 5 minutes)");
    }

    public static ServerCommands getInstance() {
        return instance;
    }

    public CommandsConfig getCommandsConfig() {
        return commandsConfig;
    }

    public Database getDatabase() {
        return database;
    }

    public CommandsRepository getRepository() {
        return repository;
    }

    public DynamicCommandManager getDynamicCommandManager() {
        return dynamicCommandManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public WarpManager getWarpManager() {
        return warpManager;
    }

    public TpaManager getTpaManager() {
        return tpaManager;
    }

    public MuteManager getMuteManager() {
        return muteManager;
    }

    public BanManager getBanManager() {
        return banManager;
    }

    public PunishmentHistoryManager getPunishmentHistoryManager() {
        return punishmentHistoryManager;
    }

    public JoinItemListener getJoinItemListener() {
        return joinItemListener;
    }
}
