package net.serverplugins.claim;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import net.serverplugins.api.ServerAPI;
import net.serverplugins.api.database.Database;
import net.serverplugins.api.economy.EconomyProvider;
import net.serverplugins.api.permissions.PermissionProvider;
import net.serverplugins.claim.commands.ClaimAdminCommand;
import net.serverplugins.claim.commands.ClaimCommand;
import net.serverplugins.claim.commands.ClaimLogCommand;
import net.serverplugins.claim.commands.ClaimNotificationsCommand;
import net.serverplugins.claim.commands.ClaimRefundCommand;
import net.serverplugins.claim.commands.ClaimShopCommand;
import net.serverplugins.claim.commands.ClaimStatsCommand;
import net.serverplugins.claim.commands.NationCommand;
import net.serverplugins.claim.commands.NcCommand;
import net.serverplugins.claim.commands.WarCommand;
import net.serverplugins.claim.commands.WarpCommand;
import net.serverplugins.claim.listeners.ChatInputListener;
import net.serverplugins.claim.listeners.ClaimMovementListener;
import net.serverplugins.claim.listeners.ClaimProtectionListener;
import net.serverplugins.claim.listeners.NotificationListener;
import net.serverplugins.claim.listeners.PlayerJoinListener;
import net.serverplugins.claim.managers.BankManager;
import net.serverplugins.claim.managers.ClaimManager;
import net.serverplugins.claim.managers.ClaimStatsManager;
import net.serverplugins.claim.managers.LevelManager;
import net.serverplugins.claim.managers.NationManager;
import net.serverplugins.claim.managers.NotificationManager;
import net.serverplugins.claim.managers.ParticleManager;
import net.serverplugins.claim.managers.ProfileManager;
import net.serverplugins.claim.managers.RewardsManager;
import net.serverplugins.claim.managers.UpkeepManager;
import net.serverplugins.claim.managers.VisitationManager;
import net.serverplugins.claim.managers.WarManager;
import net.serverplugins.claim.pricing.ExponentialPricing;
import net.serverplugins.claim.repository.AuditLogRepository;
import net.serverplugins.claim.repository.ClaimBankRepository;
import net.serverplugins.claim.repository.ClaimGroupRepository;
import net.serverplugins.claim.repository.ClaimRepository;
import net.serverplugins.claim.repository.NationRepository;
import net.serverplugins.claim.repository.NotificationRepository;
import net.serverplugins.claim.repository.WarRepository;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class ServerClaim extends JavaPlugin {

    private static ServerClaim instance;
    private ClaimConfig claimConfig;
    private Database database;
    private ClaimRepository repository;
    private ClaimGroupRepository groupRepository;
    private ClaimBankRepository bankRepository;
    private NationRepository nationRepository;
    private NotificationRepository notificationRepository;
    private WarRepository warRepository;
    private AuditLogRepository auditLogRepository;
    private net.serverplugins.claim.repository.ClaimTemplateRepository templateRepository;
    private ClaimManager claimManager;
    private ProfileManager profileManager;
    private VisitationManager visitationManager;
    private ParticleManager particleManager;
    private net.serverplugins.claim.managers.ParticlePacketListener particlePacketListener;
    private RewardsManager rewardsManager;
    private BankManager bankManager;
    private UpkeepManager upkeepManager;
    private LevelManager levelManager;
    private NationManager nationManager;
    private NotificationManager notificationManager;
    private WarManager warManager;
    private ClaimStatsManager statsManager;
    private ClaimMovementListener movementListener;
    private ExponentialPricing pricing;
    private EconomyProvider economy;
    private PermissionProvider permissions;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        claimConfig = new ClaimConfig(this);

        // Validate configuration before proceeding
        if (!claimConfig.validateConfig()) {
            getLogger().severe("Configuration validation failed! Please fix errors in config.yml");
            getLogger().severe("Plugin will continue loading but may behave unexpectedly");
        }

        // Log critical spawn protection settings
        getLogger().info("=== ServerClaim Spawn Protection Settings ===");
        getLogger().info("Spawn World: " + claimConfig.getSpawnWorld());
        getLogger()
                .info(
                        "Spawn Protection Radius: "
                                + claimConfig.getSpawnProtectionRadius()
                                + " blocks");
        getLogger().info("=============================================");

        if (!setupDatabase()) {
            getLogger().severe("Failed to setup database! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        repository = new ClaimRepository(database);
        groupRepository = new ClaimGroupRepository(database);
        bankRepository = new ClaimBankRepository(database);
        nationRepository = new NationRepository(database);
        notificationRepository = new NotificationRepository(database);
        warRepository = new WarRepository(database);
        auditLogRepository = new AuditLogRepository(database);
        templateRepository = new net.serverplugins.claim.repository.ClaimTemplateRepository(database);
        pricing = new ExponentialPricing(claimConfig);
        claimManager = new ClaimManager(this, repository, groupRepository, pricing);
        profileManager = new ProfileManager(this, repository);
        visitationManager = new VisitationManager(this, repository);
        rewardsManager = new RewardsManager(this);
        particleManager = new ParticleManager(this);

        // Phase 1-4 managers
        bankManager = new BankManager(this, bankRepository, repository);
        levelManager = new LevelManager(this, bankRepository);
        upkeepManager = new UpkeepManager(this, bankRepository, bankManager);
        nationManager = new NationManager(this, nationRepository);
        warManager = new WarManager(this, warRepository);

        // Phase 8 managers
        notificationManager = new NotificationManager(this, notificationRepository);
        statsManager = new ClaimStatsManager(this, database, repository);

        setupEconomy();
        setupPermissions();

        registerCommands();
        registerListeners();

        if (claimConfig.areParticlesEnabled()) {
            // Initialize ProtocolLib packet listener for completely static particle positioning
            if (getServer().getPluginManager().getPlugin("ProtocolLib") != null) {
                particlePacketListener =
                        new net.serverplugins.claim.managers.ParticlePacketListener(this);
                particlePacketListener.register();
            } else {
                getLogger().warning("ProtocolLib not found - particles may have slight jitter");
            }

            particleManager.start();
        }

        // Migrate existing claims to have bank/level entries
        migrateExistingClaims();

        // Start scheduled managers
        upkeepManager.start();
        warManager.start();
        nationManager.start();
        notificationManager.start();

        getLogger().info("ServerClaim enabled!");
    }

    @Override
    public void onDisable() {
        // Stop LevelManager first to flush any pending XP
        if (levelManager != null) {
            levelManager.stop();
        }
        // Stop ClaimManager to cancel pending input cleanup task
        if (claimManager != null) {
            claimManager.stop();
        }
        if (nationManager != null) {
            nationManager.stop();
        }
        if (warManager != null) {
            warManager.stop();
        }
        if (upkeepManager != null) {
            upkeepManager.stop();
        }
        if (notificationManager != null) {
            notificationManager.stop();
        }
        if (particleManager != null) {
            particleManager.stop();
        }
        if (particlePacketListener != null) {
            particlePacketListener.unregister();
        }
        instance = null;
        getLogger().info("ServerClaim disabled!");
    }

    private boolean setupDatabase() {
        // Use shared database connection from ServerAPI
        database = ServerAPI.getInstance().getDatabase();
        if (database == null) {
            getLogger().severe("ServerAPI database not available");
            return false;
        }

        getLogger().info("Using shared database connection from ServerAPI");

        getLogger().info("Loading schema.sql...");
        try (InputStream is = getResource("schema.sql")) {
            if (is != null) {
                String schema = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                getLogger().info("Schema loaded, length: " + schema.length() + " chars");

                // Remove all comment lines before splitting
                StringBuilder cleaned = new StringBuilder();
                for (String line : schema.split("\n")) {
                    String trimmedLine = line.trim();
                    if (!trimmedLine.startsWith("--") && !trimmedLine.isEmpty()) {
                        cleaned.append(line).append("\n");
                    }
                }

                String[] statements = cleaned.toString().split(";");
                getLogger().info("Found " + statements.length + " statements to execute");
                int executed = 0;
                for (String statement : statements) {
                    String trimmed = statement.trim();
                    if (!trimmed.isEmpty()) {
                        try {
                            database.execute(trimmed);
                            executed++;
                            getLogger()
                                    .info(
                                            "Executed SQL: "
                                                    + trimmed.substring(
                                                            0, Math.min(50, trimmed.length()))
                                                    + "...");
                        } catch (Exception ex) {
                            getLogger().warning("Schema statement failed: " + ex.getMessage());
                            // Continue with other statements
                        }
                    }
                }
                getLogger().info("Successfully executed " + executed + " schema statements");
            } else {
                getLogger().severe("schema.sql not found in JAR! getResource returned null");
                return false;
            }
        } catch (IOException e) {
            getLogger().severe("Failed to load schema: " + e.getMessage());
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private void setupEconomy() {
        ServerAPI api = ServerAPI.getInstance();
        if (api != null) {
            economy = api.getEconomyProvider();
        }
    }

    private void setupPermissions() {
        ServerAPI api = ServerAPI.getInstance();
        if (api != null) {
            permissions = api.getPermissionProvider();
        }
    }

    private void migrateExistingClaims() {
        if (!claimConfig.isUpkeepEnabled() && !claimConfig.isLevelsEnabled()) {
            getLogger().info("Upkeep and levels disabled - skipping claim migration");
            return;
        }

        getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        this,
                        () -> {
                            getLogger()
                                    .info("Checking existing claims for bank/level migration...");

                            java.util.List<net.serverplugins.claim.models.Claim> allClaims =
                                    repository.getAllClaims();
                            int banksCreated = 0;
                            int levelsCreated = 0;

                            for (net.serverplugins.claim.models.Claim claim : allClaims) {
                                // Ensure bank exists for upkeep
                                if (claimConfig.isUpkeepEnabled()) {
                                    net.serverplugins.claim.models.ClaimBank bank =
                                            bankRepository.getBank(claim.getId());
                                    if (bank == null) {
                                        bank =
                                                new net.serverplugins.claim.models.ClaimBank(
                                                        claim.getId());
                                        // Schedule first upkeep due in 24 hours (give existing
                                        // claims a grace period)
                                        bank.setNextUpkeepDue(
                                                java.time.Instant.now()
                                                        .plusSeconds(
                                                                claimConfig
                                                                                .getUpkeepPaymentIntervalHours()
                                                                        * 3600L));
                                        bankRepository.saveBank(bank);
                                        banksCreated++;
                                    }
                                }

                                // Ensure level exists
                                if (claimConfig.isLevelsEnabled()) {
                                    net.serverplugins.claim.models.ClaimLevel level =
                                            bankRepository.getLevel(claim.getId());
                                    if (level == null) {
                                        level =
                                                new net.serverplugins.claim.models.ClaimLevel(
                                                        claim.getId());
                                        bankRepository.saveLevel(level);
                                        levelsCreated++;
                                    }
                                }
                            }

                            if (banksCreated > 0 || levelsCreated > 0) {
                                getLogger()
                                        .info(
                                                "Migration complete: Created "
                                                        + banksCreated
                                                        + " banks, "
                                                        + levelsCreated
                                                        + " levels for existing claims");
                            } else {
                                getLogger()
                                        .info(
                                                "No migration needed - all "
                                                        + allClaims.size()
                                                        + " claims already have bank/level entries");
                            }
                        });
    }

    private void registerCommands() {
        ClaimCommand claimCommand = new ClaimCommand(this);
        PluginCommand cmd = getCommand("claim");
        if (cmd != null) {
            cmd.setExecutor(claimCommand);
            cmd.setTabCompleter(claimCommand);
        }

        ClaimRefundCommand refundCommand = new ClaimRefundCommand(this);
        PluginCommand refundCmd = getCommand("claimrefund");
        if (refundCmd != null) {
            refundCmd.setExecutor(refundCommand);
            refundCmd.setTabCompleter(refundCommand);
        }

        ClaimAdminCommand adminCommand = new ClaimAdminCommand(this);
        PluginCommand adminCmd = getCommand("claimadmin");
        if (adminCmd != null) {
            adminCmd.setExecutor(adminCommand);
            adminCmd.setTabCompleter(adminCommand);
        }

        WarpCommand warpCommand = new WarpCommand(this);
        PluginCommand warpCmd = getCommand("playerwarps");
        if (warpCmd != null) {
            warpCmd.setExecutor(warpCommand);
            warpCmd.setTabCompleter(warpCommand);
        }

        NationCommand nationCommand = new NationCommand(this);
        PluginCommand nationCmd = getCommand("nation");
        if (nationCmd != null) {
            nationCmd.setExecutor(nationCommand);
            nationCmd.setTabCompleter(nationCommand);
        }

        WarCommand warCommand = new WarCommand(this);
        PluginCommand warCmd = getCommand("war");
        if (warCmd != null) {
            warCmd.setExecutor(warCommand);
            warCmd.setTabCompleter(warCommand);
        }

        NcCommand ncCommand = new NcCommand(this);
        PluginCommand ncCmd = getCommand("nc");
        if (ncCmd != null) {
            ncCmd.setExecutor(ncCommand);
            ncCmd.setTabCompleter(ncCommand);
        }

        ClaimNotificationsCommand notificationsCommand = new ClaimNotificationsCommand(this);
        PluginCommand notificationsCmd = getCommand("claimnotifications");
        if (notificationsCmd != null) {
            notificationsCmd.setExecutor(notificationsCommand);
            notificationsCmd.setTabCompleter(notificationsCommand);
        }

        ClaimLogCommand logCommand = new ClaimLogCommand(this);
        PluginCommand logCmd = getCommand("claimlog");
        if (logCmd != null) {
            logCmd.setExecutor(logCommand);
            logCmd.setTabCompleter(logCommand);
        }

        ClaimStatsCommand statsCommand = new ClaimStatsCommand(this);
        PluginCommand statsCmd = getCommand("claimstats");
        if (statsCmd != null) {
            statsCmd.setExecutor(statsCommand);
            statsCmd.setTabCompleter(statsCommand);
        }

        ClaimShopCommand shopCommand = new ClaimShopCommand(this);
        PluginCommand shopCmd = getCommand("claimshop");
        if (shopCmd != null) {
            shopCmd.setExecutor(shopCommand);
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new ClaimProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatInputListener(this), this);
        getServer().getPluginManager().registerEvents(new NotificationListener(this), this);

        movementListener = new ClaimMovementListener(this);
        getServer().getPluginManager().registerEvents(movementListener, this);
    }

    public void reloadConfiguration() {
        reloadConfig();
        claimConfig = new ClaimConfig(this);

        // Propagate config changes to managers with cached values
        if (upkeepManager != null) {
            upkeepManager.reloadConfig();
        }

        getLogger().info("=== Configuration Reloaded ===");
        getLogger().info("Spawn World: " + claimConfig.getSpawnWorld());
        getLogger().info("Spawn Radius: " + claimConfig.getSpawnProtectionRadius() + " blocks");
        getLogger().info("==============================");
    }

    public static ServerClaim getInstance() {
        return instance;
    }

    public ClaimConfig getClaimConfig() {
        return claimConfig;
    }

    public Database getDatabase() {
        return database;
    }

    public ClaimRepository getRepository() {
        return repository;
    }

    public ClaimGroupRepository getGroupRepository() {
        return groupRepository;
    }

    public ClaimBankRepository getBankRepository() {
        return bankRepository;
    }

    public NationRepository getNationRepository() {
        return nationRepository;
    }

    public WarRepository getWarRepository() {
        return warRepository;
    }

    public AuditLogRepository getAuditLogRepository() {
        return auditLogRepository;
    }

    public ClaimManager getClaimManager() {
        return claimManager;
    }

    public ProfileManager getProfileManager() {
        return profileManager;
    }

    public VisitationManager getVisitationManager() {
        return visitationManager;
    }

    public ParticleManager getParticleManager() {
        return particleManager;
    }

    public RewardsManager getRewardsManager() {
        return rewardsManager;
    }

    public BankManager getBankManager() {
        return bankManager;
    }

    public UpkeepManager getUpkeepManager() {
        return upkeepManager;
    }

    public LevelManager getLevelManager() {
        return levelManager;
    }

    public NationManager getNationManager() {
        return nationManager;
    }

    public NotificationManager getNotificationManager() {
        return notificationManager;
    }

    public WarManager getWarManager() {
        return warManager;
    }

    public ClaimStatsManager getStatsManager() {
        return statsManager;
    }

    public ClaimMovementListener getMovementListener() {
        return movementListener;
    }

    public ExponentialPricing getPricing() {
        return pricing;
    }

    public EconomyProvider getEconomy() {
        return economy;
    }

    public PermissionProvider getPermissions() {
        return permissions;
    }

    public net.serverplugins.claim.repository.ClaimTemplateRepository getTemplateRepository() {
        return templateRepository;
    }

    public net.serverplugins.claim.repository.ClaimTemplateRepository getClaimTemplateRepository() {
        return templateRepository;
    }

    public ClaimRepository getClaimRepository() {
        return repository;
    }
}
