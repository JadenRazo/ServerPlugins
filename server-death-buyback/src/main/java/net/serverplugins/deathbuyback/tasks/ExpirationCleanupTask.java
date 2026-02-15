package net.serverplugins.deathbuyback.tasks;

import net.serverplugins.deathbuyback.ServerDeathBuyback;
import org.bukkit.scheduler.BukkitTask;

public class ExpirationCleanupTask implements Runnable {

    private final ServerDeathBuyback plugin;
    private BukkitTask task;

    public ExpirationCleanupTask(ServerDeathBuyback plugin) {
        this.plugin = plugin;
    }

    public void start(long intervalTicks) {
        // Run async to avoid blocking main thread
        task =
                plugin.getServer()
                        .getScheduler()
                        .runTaskTimerAsynchronously(
                                plugin,
                                this,
                                intervalTicks, // Initial delay
                                intervalTicks // Repeat interval
                                );
        plugin.getLogger()
                .info(
                        "Expiration cleanup task started (interval: "
                                + (intervalTicks / 20 / 60 / 60)
                                + " hours)");
    }

    public void stop() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
            task = null;
        }
    }

    @Override
    public void run() {
        try {
            int deleted = plugin.getRepository().deleteExpiredInventories();
            if (deleted > 0) {
                plugin.getLogger().info("Cleaned up " + deleted + " expired death inventories");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to clean up expired inventories: " + e.getMessage());
        }
    }
}
