package net.serverplugins.backpacks.tasks;

import net.serverplugins.backpacks.ServerBackpacks;
import net.serverplugins.backpacks.managers.BackpackManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Periodic task that auto-saves all open backpack contents. This prevents data loss in case of
 * crashes or unexpected shutdowns.
 *
 * <p>Runs every 60 ticks (3 seconds) by default and syncs backpack inventory contents to the
 * ItemStack's persistent data.
 */
public class AutoSaveTask extends BukkitRunnable {

    private final ServerBackpacks plugin;
    private final BackpackManager backpackManager;

    /**
     * Create a new auto-save task.
     *
     * @param plugin The plugin instance
     */
    public AutoSaveTask(ServerBackpacks plugin) {
        this.plugin = plugin;
        this.backpackManager = plugin.getBackpackManager();
    }

    /** Start the auto-save task with default interval (60 ticks = 3 seconds). */
    public void start() {
        start(60L);
    }

    /**
     * Start the auto-save task with a custom interval.
     *
     * @param intervalTicks The interval in server ticks
     */
    public void start(long intervalTicks) {
        this.runTaskTimerAsynchronously(plugin, intervalTicks, intervalTicks);
        plugin.getLogger().info("Auto-save task started (interval: " + intervalTicks + " ticks)");
    }

    @Override
    public void run() {
        // Get all players with open backpacks and sync their contents
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (backpackManager.hasOpenBackpack(player)) {
                // Schedule sync on main thread since we're running async
                Bukkit.getScheduler()
                        .runTask(
                                plugin,
                                () -> {
                                    syncBackpack(player);
                                });
            }
        }
    }

    /**
     * Sync a player's open backpack contents to the ItemStack. This is called on the main thread.
     *
     * @param player The player whose backpack to sync
     */
    private void syncBackpack(Player player) {
        if (!backpackManager.hasOpenBackpack(player)) {
            return;
        }

        BackpackManager.OpenBackpack open = backpackManager.getOpenBackpack(player);
        if (open == null) {
            return;
        }

        try {
            // The BackpackManager's saveContents is called when closing,
            // but we can trigger a save by calling the manager's method
            // We need to access the save functionality - in this case,
            // we sync the inventory contents to the item
            backpackManager.syncOpenBackpack(player);
        } catch (Exception e) {
            plugin.getLogger()
                    .warning(
                            "Failed to auto-save backpack for "
                                    + player.getName()
                                    + ": "
                                    + e.getMessage());
        }
    }

    /** Stop the auto-save task. */
    public void stop() {
        try {
            this.cancel();
            plugin.getLogger().info("Auto-save task stopped");
        } catch (IllegalStateException ignored) {
            // Task was never scheduled
        }
    }
}
