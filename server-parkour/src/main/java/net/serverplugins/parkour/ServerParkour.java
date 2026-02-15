package net.serverplugins.parkour;

import net.serverplugins.api.ServerAPI;
import net.serverplugins.api.database.Database;
import net.serverplugins.parkour.commands.ParkourAdminCommand;
import net.serverplugins.parkour.commands.ParkourCommand;
import net.serverplugins.parkour.data.ParkourDatabase;
import net.serverplugins.parkour.game.ParkourManager;
import net.serverplugins.parkour.listeners.ParkourListener;
import net.serverplugins.parkour.npc.ParkourHologramManager;
import net.serverplugins.parkour.npc.ParkourNpcManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class ServerParkour extends JavaPlugin {

    private static ServerParkour instance;

    private ParkourConfig config;
    private ParkourManager parkourManager;
    private ParkourDatabase database;
    private ParkourNpcManager npcManager;
    private ParkourHologramManager hologramManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        config = new ParkourConfig(this);

        // Initialize database
        initDatabase();

        // Initialize managers
        parkourManager = new ParkourManager(this);

        // Initialize NPC manager (provides setup instructions)
        npcManager = new ParkourNpcManager(this);
        getServer().getScheduler().runTaskLater(this, () -> npcManager.createNpc(), 40L);

        if (Bukkit.getPluginManager().getPlugin("DecentHolograms") != null) {
            hologramManager = new ParkourHologramManager(this);
            getServer()
                    .getScheduler()
                    .runTaskLater(this, () -> hologramManager.createHologram(), 60L);
        } else {
            getLogger().warning("DecentHolograms not found - Hologram features disabled");
        }

        // Register PlaceholderAPI expansion
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ParkourPlaceholders(this).register();
            getLogger().info("PlaceholderAPI expansion registered");
        }

        // Register listeners
        getServer().getPluginManager().registerEvents(new ParkourListener(this), this);

        // Load cache for already online players (in case of reload)
        if (database != null) {
            for (Player player : getServer().getOnlinePlayers()) {
                database.loadPlayerCache(player.getUniqueId());
            }
            // Load leaderboard cache
            database.refreshLeaderboardCacheAsync();

            // Periodic leaderboard cache refresh every 2 minutes
            getServer()
                    .getScheduler()
                    .runTaskTimerAsynchronously(
                            this,
                            () -> {
                                database.refreshLeaderboardCacheAsync();
                            },
                            2400L,
                            2400L); // 2400 ticks = 2 minutes
        }

        // Register commands
        ParkourCommand parkourCmd = new ParkourCommand(this);
        getCommand("parkour").setExecutor(parkourCmd);
        getCommand("parkour").setTabCompleter(parkourCmd);

        ParkourAdminCommand adminCmd = new ParkourAdminCommand(this);
        getCommand("parkouradmin").setExecutor(adminCmd);
        getCommand("parkouradmin").setTabCompleter(adminCmd);

        getLogger().info("ServerParkour enabled!");
    }

    @Override
    public void onDisable() {
        // End all active games
        if (parkourManager != null) {
            parkourManager.endAllGames();
        }

        // Cleanup NPC and hologram
        if (npcManager != null) {
            npcManager.removeNpc();
        }
        if (hologramManager != null) {
            hologramManager.removeHologram();
        }

        instance = null;
        getLogger().info("ServerParkour disabled!");
    }

    private void initDatabase() {
        ServerAPI api = ServerAPI.getInstance();
        if (api != null) {
            Database db = api.getDatabase();
            database = new ParkourDatabase(db);
            database.initTables();
        } else {
            getLogger().severe("ServerAPI not found! Database features disabled.");
        }
    }

    public void reloadConfiguration() {
        reloadConfig();
        config = new ParkourConfig(this);
        config.getMessenger().reload();

        if (hologramManager != null) {
            hologramManager.updateHologram();
        }
    }

    public static ServerParkour getInstance() {
        return instance;
    }

    public ParkourConfig getParkourConfig() {
        return config;
    }

    public ParkourManager getParkourManager() {
        return parkourManager;
    }

    public ParkourDatabase getDatabase() {
        return database;
    }

    public ParkourNpcManager getNpcManager() {
        return npcManager;
    }

    public ParkourHologramManager getHologramManager() {
        return hologramManager;
    }
}
