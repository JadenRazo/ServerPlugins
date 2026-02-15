package net.serverplugins.admin;

import net.serverplugins.admin.alts.AltManager;
import net.serverplugins.admin.alts.AltsCommand;
import net.serverplugins.admin.commands.DisguiseCommand;
import net.serverplugins.admin.commands.FreecamCommand;
import net.serverplugins.admin.commands.ImpersonateCommand;
import net.serverplugins.admin.commands.NightVisionCommand;
import net.serverplugins.admin.commands.PovCommand;
import net.serverplugins.admin.commands.ServerControlCommand;
import net.serverplugins.admin.commands.SpectateCommand;
import net.serverplugins.admin.commands.VanishCommand;
import net.serverplugins.admin.commands.XrayAlertsCommand;
import net.serverplugins.admin.commands.XrayCheckCommand;
import net.serverplugins.admin.commands.XrayVisionCommand;
import net.serverplugins.admin.disguise.DisguiseManager;
import net.serverplugins.admin.effects.NightVisionManager;
import net.serverplugins.admin.freecam.FreecamListener;
import net.serverplugins.admin.freecam.FreecamManager;
import net.serverplugins.admin.freeze.FreezeCommand;
import net.serverplugins.admin.freeze.FreezeListener;
import net.serverplugins.admin.freeze.FreezeManager;
import net.serverplugins.admin.inspect.EcSeeCommand;
import net.serverplugins.admin.inspect.InspectListener;
import net.serverplugins.admin.inspect.InspectManager;
import net.serverplugins.admin.inspect.InvSeeCommand;
import net.serverplugins.admin.listeners.AdminPlayerListener;
import net.serverplugins.admin.listeners.VanishInteractionListener;
import net.serverplugins.admin.listeners.VanishListener;
import net.serverplugins.admin.listeners.XrayBlockListener;
import net.serverplugins.admin.punishment.CategoryManager;
import net.serverplugins.admin.punishment.PunishmentManager;
import net.serverplugins.admin.punishment.PunishmentRepository;
import net.serverplugins.admin.punishment.ReasonManager;
import net.serverplugins.admin.punishment.commands.HistoryCommand;
import net.serverplugins.admin.punishment.commands.PunishCommand;
import net.serverplugins.admin.punishment.commands.UnpunishCommand;
import net.serverplugins.admin.punishment.listeners.PunishmentListener;
import net.serverplugins.admin.redis.AdminRedisHandler;
import net.serverplugins.admin.redis.ServerControlHandler;
import net.serverplugins.admin.redis.VanishSyncPublisher;
import net.serverplugins.admin.reset.ResetCommand;
import net.serverplugins.admin.reset.ResetManager;
import net.serverplugins.admin.spawner.SpawnerManager;
import net.serverplugins.admin.spectate.SpectateManager;
import net.serverplugins.admin.staffchat.StaffChatCommand;
import net.serverplugins.admin.staffchat.StaffChatListener;
import net.serverplugins.admin.staffchat.StaffChatManager;
import net.serverplugins.admin.vanish.VanishManager;
import net.serverplugins.admin.xray.XrayManager;
import net.serverplugins.admin.xrayvision.XrayVisionManager;
import net.serverplugins.api.ServerAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class ServerAdmin extends JavaPlugin {

    private static ServerAdmin instance;

    private AdminConfig adminConfig;
    private VanishManager vanishManager;
    private SpectateManager spectateManager;
    private XrayManager xrayManager;
    private SpawnerManager spawnerManager;
    private FreezeManager freezeManager;
    private StaffChatManager staffChatManager;
    private AltManager altManager;
    private InspectManager inspectManager;
    private NightVisionManager nightVisionManager;
    private XrayVisionManager xrayVisionManager;
    private FreecamManager freecamManager;
    private PunishmentRepository punishmentRepository;
    private CategoryManager categoryManager;
    private PunishmentManager punishmentManager;
    private ReasonManager reasonManager;
    private ResetManager resetManager;
    private DisguiseManager disguiseManager;
    private boolean protocolLibEnabled;
    private AdminRedisHandler adminRedisHandler;
    private ServerControlHandler serverControlHandler;
    private VanishSyncPublisher vanishSyncPublisher;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        try {
            adminConfig = new AdminConfig(this);
        } catch (Exception e) {
            getLogger().severe("Failed to load config: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Check for ProtocolLib
        protocolLibEnabled = Bukkit.getPluginManager().getPlugin("ProtocolLib") != null;
        if (!protocolLibEnabled) {
            getLogger().warning("ProtocolLib not found! Vanish packet hiding will be limited.");
        }

        // Initialize managers with error handling
        try {
            vanishManager = new VanishManager(this);
            spectateManager = new SpectateManager(this);
            freezeManager = new FreezeManager(this);
            staffChatManager = new StaffChatManager(this);
            altManager = new AltManager(this);
            inspectManager = new InspectManager(this);
            nightVisionManager = new NightVisionManager(this);
        } catch (Exception | NoClassDefFoundError e) {
            getLogger().severe("Failed to initialize core managers: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            if (adminConfig.isXrayEnabled()) {
                xrayManager = new XrayManager(this);
            }

            if (adminConfig.isSpawnerEnabled()) {
                spawnerManager = new SpawnerManager(this);
            }
        } catch (Exception e) {
            getLogger().warning("Failed to initialize xray/spawner tracking: " + e.getMessage());
        }

        // Initialize xray vision if ProtocolLib is available and enabled
        if (protocolLibEnabled && adminConfig.isXrayVisionEnabled()) {
            try {
                xrayVisionManager = new XrayVisionManager(this);
            } catch (Exception | NoClassDefFoundError e) {
                getLogger().warning("Failed to initialize xray vision: " + e.getMessage());
            }
        }

        // Initialize freecam manager
        try {
            freecamManager = new FreecamManager(this);
        } catch (Exception | NoClassDefFoundError e) {
            getLogger().warning("Failed to initialize freecam: " + e.getMessage());
        }

        // Initialize disguise manager
        try {
            disguiseManager = new DisguiseManager(this);
        } catch (Exception | NoClassDefFoundError e) {
            getLogger().warning("Failed to initialize disguise: " + e.getMessage());
        }

        // Initialize punishment system
        if (getConfig().getBoolean("punishment.enabled", true)) {
            try {
                ServerAPI api = ServerAPI.getInstance();
                if (api != null && api.getDatabase() != null) {
                    punishmentRepository = new PunishmentRepository(api.getDatabase(), getLogger());
                    categoryManager = new CategoryManager(this, punishmentRepository);
                    reasonManager = new ReasonManager(this, punishmentRepository);
                    punishmentManager =
                            new PunishmentManager(
                                    this, punishmentRepository, categoryManager, reasonManager);
                    getLogger().info("Punishment system initialized!");
                } else {
                    getLogger()
                            .warning(
                                    "ServerAPI database not available - punishment system disabled.");
                }
            } catch (Exception e) {
                getLogger().warning("Failed to initialize punishment system: " + e.getMessage());
            }
        }

        // Initialize reset system
        if (getConfig().getBoolean("reset.enabled", true) && punishmentRepository != null) {
            try {
                resetManager = new ResetManager(this, punishmentRepository);
                getLogger().info("Reset system initialized!");
            } catch (Exception e) {
                getLogger().warning("Failed to initialize reset system: " + e.getMessage());
            }
        }

        // Initialize Redis integration via ServerBridge soft-dependency
        try {
            Plugin bridgePlugin = Bukkit.getPluginManager().getPlugin("ServerBridge");
            if (bridgePlugin != null && bridgePlugin.isEnabled()) {
                adminRedisHandler = new AdminRedisHandler(this);
                boolean redisAvailable = adminRedisHandler.init(bridgePlugin, staffChatManager);

                if (redisAvailable) {
                    // Wire Redis handler to managers
                    if (punishmentManager != null) {
                        punishmentManager.setAdminRedisHandler(adminRedisHandler);
                    }
                    staffChatManager.setAdminRedisHandler(adminRedisHandler);

                    // Initialize server control handler
                    String serverName = getConfig().getString("server-name", "smp");
                    serverControlHandler = new ServerControlHandler(this, serverName);
                    adminRedisHandler.setServerControlHandler(serverControlHandler);

                    // Initialize vanish sync publisher
                    vanishSyncPublisher =
                            new VanishSyncPublisher(adminRedisHandler::publish, serverName);
                    vanishManager.setVanishSyncPublisher(vanishSyncPublisher);

                    getLogger().info("Redis integration enabled via ServerBridge");
                }
            } else {
                getLogger().info("ServerBridge not found - Redis features disabled");
            }
        } catch (Exception | NoClassDefFoundError e) {
            getLogger().warning("Failed to initialize Redis integration: " + e.getMessage());
        }

        // Register listeners
        getServer().getPluginManager().registerEvents(new AdminPlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new VanishListener(this), this);
        getServer().getPluginManager().registerEvents(new VanishInteractionListener(this), this);
        getServer().getPluginManager().registerEvents(new FreezeListener(this), this);
        getServer().getPluginManager().registerEvents(new StaffChatListener(this), this);
        getServer().getPluginManager().registerEvents(new InspectListener(this), this);
        getServer().getPluginManager().registerEvents(new FreecamListener(this), this);

        if (xrayManager != null) {
            getServer().getPluginManager().registerEvents(new XrayBlockListener(this), this);
        }

        if (punishmentManager != null) {
            getServer().getPluginManager().registerEvents(new PunishmentListener(this), this);
        }

        // Register commands
        registerCommands();

        getLogger().info("ServerAdmin enabled!");
    }

    @Override
    public void onDisable() {
        // Shutdown Redis integration
        if (adminRedisHandler != null) {
            adminRedisHandler.shutdown();
        }

        // Unvanish all players before shutdown
        if (vanishManager != null) {
            vanishManager.shutdown();
        }

        if (spectateManager != null) {
            spectateManager.shutdown();
        }

        if (nightVisionManager != null) {
            nightVisionManager.shutdown();
        }

        if (xrayVisionManager != null) {
            xrayVisionManager.shutdown();
        }

        if (freecamManager != null) {
            freecamManager.shutdown();
        }

        if (disguiseManager != null) {
            disguiseManager.shutdown();
        }

        instance = null;
        getLogger().info("ServerAdmin disabled!");
    }

    private void registerCommands() {
        registerCommand("vanish", new VanishCommand(this));
        registerCommand("spectate", new SpectateCommand(this));
        registerCommand("pov", new PovCommand(this));
        registerCommand("freecam", new FreecamCommand(this));
        registerCommand("xrayalerts", new XrayAlertsCommand(this));
        registerCommand("xraycheck", new XrayCheckCommand(this));
        registerCommand("freeze", new FreezeCommand(this));
        registerCommand("unfreeze", new FreezeCommand(this));
        registerCommand("invsee", new InvSeeCommand(this));
        registerCommand("ecsee", new EcSeeCommand(this));
        registerCommand("sc", new StaffChatCommand(this));
        registerCommand("sctoggle", new StaffChatCommand(this));
        registerCommand("alts", new AltsCommand(this));
        registerCommand("nightvision", new NightVisionCommand(this));

        if (xrayVisionManager != null) {
            registerCommand("xray", new XrayVisionCommand(this));
        }

        if (punishmentManager != null) {
            registerCommand("punish", new PunishCommand(this));
            registerCommand("unpunish", new UnpunishCommand(this));
            registerCommand("history", new HistoryCommand(this));
        }

        if (resetManager != null) {
            registerCommand("reset", new ResetCommand(this));
        }

        registerCommand("shutdown", new ServerControlCommand(this));
        registerCommand("restart", new ServerControlCommand(this));
        registerCommand("impersonate", new ImpersonateCommand(this));
        registerCommand("disguise", new DisguiseCommand(this));
    }

    @SuppressWarnings("unchecked")
    private <T extends org.bukkit.command.CommandExecutor> void registerCommand(
            String name, T executor) {
        var cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
            if (executor instanceof org.bukkit.command.TabCompleter tabCompleter) {
                cmd.setTabCompleter(tabCompleter);
            }
        } else {
            getLogger().warning("Command '" + name + "' not found in plugin.yml!");
        }
    }

    public void reloadConfiguration() {
        reloadConfig();
        adminConfig.reload();
    }

    public static ServerAdmin getInstance() {
        return instance;
    }

    public AdminConfig getAdminConfig() {
        return adminConfig;
    }

    public VanishManager getVanishManager() {
        return vanishManager;
    }

    public SpectateManager getSpectateManager() {
        return spectateManager;
    }

    public XrayManager getXrayManager() {
        return xrayManager;
    }

    public SpawnerManager getSpawnerManager() {
        return spawnerManager;
    }

    public FreezeManager getFreezeManager() {
        return freezeManager;
    }

    public StaffChatManager getStaffChatManager() {
        return staffChatManager;
    }

    public AltManager getAltManager() {
        return altManager;
    }

    public InspectManager getInspectManager() {
        return inspectManager;
    }

    public NightVisionManager getNightVisionManager() {
        return nightVisionManager;
    }

    public XrayVisionManager getXrayVisionManager() {
        return xrayVisionManager;
    }

    public FreecamManager getFreecamManager() {
        return freecamManager;
    }

    public boolean isProtocolLibEnabled() {
        return protocolLibEnabled;
    }

    public PunishmentRepository getPunishmentRepository() {
        return punishmentRepository;
    }

    public CategoryManager getCategoryManager() {
        return categoryManager;
    }

    public PunishmentManager getPunishmentManager() {
        return punishmentManager;
    }

    public ReasonManager getReasonManager() {
        return reasonManager;
    }

    public ResetManager getResetManager() {
        return resetManager;
    }

    public DisguiseManager getDisguiseManager() {
        return disguiseManager;
    }

    public AdminRedisHandler getAdminRedisHandler() {
        return adminRedisHandler;
    }

    public ServerControlHandler getServerControlHandler() {
        return serverControlHandler;
    }
}
