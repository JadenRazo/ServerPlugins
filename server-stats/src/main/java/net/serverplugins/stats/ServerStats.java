package net.serverplugins.stats;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ServerStats plugin - provides HTTP API for server statistics.
 *
 * <p>Features: - Real-time stats tracking (blocks placed, mobs killed, unique players, peak online)
 * - HTTP API server on configurable port - Daily stats reset at midnight - CORS-enabled JSON
 * endpoint
 */
public class ServerStats extends JavaPlugin {

    private static ServerStats instance;
    private StatsConfig statsConfig;
    private StatsTracker tracker;
    private StatsApiServer apiServer;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        statsConfig = new StatsConfig(this);

        // Initialize tracker and register events
        tracker = new StatsTracker();
        getServer().getPluginManager().registerEvents(tracker, this);

        // Seed initial peak online count with current player count
        tracker.seedInitialOnline();

        // Start HTTP API server
        apiServer = new StatsApiServer(this, tracker);
        apiServer.start();

        // Schedule daily reset at midnight
        scheduleDailyReset();

        getLogger().info("ServerStats enabled - API on port " + statsConfig.getHttpPort());
    }

    @Override
    public void onDisable() {
        if (apiServer != null) {
            apiServer.stop();
        }
        instance = null;
    }

    /**
     * Schedule a daily reset task that runs at midnight.
     *
     * <p>Calculates ticks until midnight, then repeats every 24 hours.
     */
    private void scheduleDailyReset() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = LocalDate.now().plusDays(1).atTime(LocalTime.MIDNIGHT);
        long secondsUntilMidnight = ChronoUnit.SECONDS.between(now, midnight);
        long ticksUntilMidnight = secondsUntilMidnight * 20L;
        long ticksPerDay = 20L * 60 * 60 * 24;

        Bukkit.getScheduler()
                .runTaskTimer(this, () -> tracker.resetDaily(), ticksUntilMidnight, ticksPerDay);

        getLogger()
                .info(
                        "Daily stats reset scheduled for midnight ("
                                + secondsUntilMidnight
                                + " seconds)");
    }

    public static ServerStats getInstance() {
        return instance;
    }

    public StatsConfig getStatsConfig() {
        return statsConfig;
    }
}
