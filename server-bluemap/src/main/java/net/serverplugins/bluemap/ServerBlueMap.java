package net.serverplugins.bluemap;

import de.bluecolored.bluemap.api.BlueMapAPI;
import java.io.InputStream;
import net.serverplugins.api.ServerAPI;
import net.serverplugins.api.database.Database;
import net.serverplugins.bluemap.commands.POICommand;
import net.serverplugins.bluemap.markers.ClaimMarkerManager;
import net.serverplugins.bluemap.markers.NationOverlayManager;
import net.serverplugins.bluemap.markers.POIMarkerManager;
import net.serverplugins.bluemap.markers.WarZoneMarkerManager;
import net.serverplugins.bluemap.repository.POIRepository;
import net.serverplugins.claim.ServerClaim;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class ServerBlueMap extends JavaPlugin {

    private static ServerBlueMap instance;
    private BlueMapConfig config;
    private ServerClaim claimPlugin;
    private Database database;
    private POIRepository poiRepository;

    private ClaimMarkerManager claimMarkerManager;
    private NationOverlayManager nationOverlayManager;
    private WarZoneMarkerManager warZoneMarkerManager;
    private POIMarkerManager poiMarkerManager;

    private BukkitTask updateTask;
    private boolean blueMapEnabled = false;

    // Dirty flag to track if markers need updating
    // Set to volatile since it's accessed from multiple threads (scheduler + potential event
    // listeners)
    private volatile boolean markersNeedUpdate = true;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        config = new BlueMapConfig(this);

        if (!config.isEnabled()) {
            getLogger().info("ServerBlueMap is disabled in config.");
            return;
        }

        // Get ServerAPI database
        if (!setupDatabase()) {
            getLogger().severe("Could not get ServerAPI database! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize database schema
        initializeDatabase();

        // Initialize POI repository
        poiRepository = new POIRepository(database);

        // Get ServerClaim
        if (!setupServerClaim()) {
            getLogger().severe("Could not find ServerClaim! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register commands
        getCommand("poi").setExecutor(new POICommand(this, poiRepository));

        // Register BlueMap API listener
        BlueMapAPI.onEnable(
                api -> {
                    getLogger().info("BlueMap API is now available!");
                    blueMapEnabled = true;
                    initializeMarkers(api);
                    startUpdateTask();
                });

        // Register disable listener
        BlueMapAPI.onDisable(
                api -> {
                    getLogger().info("BlueMap API is no longer available!");
                    blueMapEnabled = false;
                    stopUpdateTask();
                });

        getLogger().info("ServerBlueMap enabled! Waiting for BlueMap API...");
    }

    @Override
    public void onDisable() {
        stopUpdateTask();

        if (claimMarkerManager != null) {
            claimMarkerManager.cleanup();
        }
        if (nationOverlayManager != null) {
            nationOverlayManager.cleanup();
        }
        if (warZoneMarkerManager != null) {
            warZoneMarkerManager.cleanup();
        }
        if (poiMarkerManager != null) {
            poiMarkerManager.cleanup();
        }

        BlueMapAPI.unregisterListener(this::initializeMarkers);

        instance = null;
        getLogger().info("ServerBlueMap disabled!");
    }

    private void initializeMarkers(BlueMapAPI api) {
        // Initialize marker managers
        claimMarkerManager = new ClaimMarkerManager(this, api, claimPlugin);
        nationOverlayManager = new NationOverlayManager(this, api, claimPlugin);
        warZoneMarkerManager = new WarZoneMarkerManager(this, api, claimPlugin);
        poiMarkerManager = new POIMarkerManager(this, api, poiRepository);

        // Initialize layers
        claimMarkerManager.initialize();
        nationOverlayManager.initialize();
        warZoneMarkerManager.initialize();
        poiMarkerManager.initialize();

        getLogger().info("BlueMap marker layers initialized!");
    }

    private void startUpdateTask() {
        if (updateTask != null) {
            return;
        }

        int intervalTicks = config.getUpdateIntervalSeconds() * 20;
        updateTask =
                getServer()
                        .getScheduler()
                        .runTaskTimerAsynchronously(
                                this,
                                () -> {
                                    if (blueMapEnabled && markersNeedUpdate) {
                                        claimMarkerManager.updateMarkers();
                                        nationOverlayManager.updateMarkers();
                                        warZoneMarkerManager.updateMarkers();
                                        poiMarkerManager.updateMarkers();
                                        markersNeedUpdate = false;
                                    }
                                },
                                intervalTicks,
                                intervalTicks);

        getLogger()
                .info(
                        "Markers will update every "
                                + config.getUpdateIntervalSeconds()
                                + " seconds.");
    }

    private void stopUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    private boolean setupServerClaim() {
        try {
            claimPlugin = (ServerClaim) getServer().getPluginManager().getPlugin("ServerClaim");
            return claimPlugin != null;
        } catch (Exception e) {
            getLogger().warning("Error getting ServerClaim: " + e.getMessage());
            return false;
        }
    }

    /**
     * Marks the markers as dirty, requiring an update on the next scheduled check. This is more
     * efficient than forcing an immediate update when changes occur. Can be called from claim
     * creation/deletion/modification events to trigger updates.
     *
     * <p>Note: For event-driven updates, consider implementing listeners for: - Claim
     * creation/deletion events - Nation territory changes - War zone activation/deactivation - POI
     * creation/deletion
     */
    public void markDirty() {
        this.markersNeedUpdate = true;
    }

    /**
     * Triggers an immediate marker update, bypassing the dirty flag system. Use this for critical
     * updates that need to be reflected immediately.
     */
    public void triggerUpdate() {
        if (claimMarkerManager != null && blueMapEnabled) {
            getServer()
                    .getScheduler()
                    .runTaskAsynchronously(
                            this,
                            () -> {
                                claimMarkerManager.updateMarkers();
                                nationOverlayManager.updateMarkers();
                                warZoneMarkerManager.updateMarkers();
                                poiMarkerManager.updateMarkers();
                            });
        }
    }

    private boolean setupDatabase() {
        try {
            ServerAPI serverAPI = (ServerAPI) getServer().getPluginManager().getPlugin("ServerAPI");
            if (serverAPI == null) {
                return false;
            }
            database = serverAPI.getDatabase();
            return database != null;
        } catch (Exception e) {
            getLogger().warning("Error getting ServerAPI database: " + e.getMessage());
            return false;
        }
    }

    private void initializeDatabase() {
        try (InputStream is = getResource("schema.sql")) {
            if (is != null) {
                String schema = new String(is.readAllBytes());

                // Remove comment lines
                StringBuilder cleaned = new StringBuilder();
                for (String line : schema.split("\n")) {
                    String trimmedLine = line.trim();
                    if (!trimmedLine.startsWith("--") && !trimmedLine.isEmpty()) {
                        cleaned.append(line).append("\n");
                    }
                }

                // Execute each statement
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
                getLogger().info("Successfully executed " + executed + " schema statements");
            }
        } catch (Exception e) {
            getLogger().warning("Error initializing database schema: " + e.getMessage());
        }
    }

    public static ServerBlueMap getInstance() {
        return instance;
    }

    public BlueMapConfig getBlueMapConfig() {
        return config;
    }

    public ServerClaim getClaimPlugin() {
        return claimPlugin;
    }

    public POIRepository getPOIRepository() {
        return poiRepository;
    }
}
