package net.serverplugins.stats;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Tracks live server statistics for the stats API.
 *
 * <p>Monitors: - Blocks placed today - Mobs killed today - Unique players today - Peak concurrent
 * players today
 *
 * <p>Resets daily at midnight via scheduled task in ServerStats.
 */
public class StatsTracker implements Listener {

    private final AtomicLong blocksPlaced = new AtomicLong();
    private final AtomicLong mobsKilled = new AtomicLong();
    private final Set<UUID> uniquePlayersToday = ConcurrentHashMap.newKeySet();
    private final AtomicInteger peakOnlineToday = new AtomicInteger();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        blocksPlaced.incrementAndGet();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Monster && event.getEntity().getKiller() != null) {
            mobsKilled.incrementAndGet();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        uniquePlayersToday.add(event.getPlayer().getUniqueId());
        int online = Bukkit.getOnlinePlayers().size();
        peakOnlineToday.updateAndGet(current -> Math.max(current, online));
    }

    /**
     * Seed initial online count when plugin loads.
     *
     * <p>Sets peak online to current player count to avoid showing 0 when server has players.
     */
    public void seedInitialOnline() {
        int currentOnline = Bukkit.getOnlinePlayers().size();
        peakOnlineToday.set(currentOnline);
    }

    /**
     * Reset daily statistics. Called at midnight by scheduled task.
     *
     * <p>Resets: - Unique players today - Peak online today - Blocks placed - Mobs killed
     */
    public void resetDaily() {
        uniquePlayersToday.clear();
        peakOnlineToday.set(Bukkit.getOnlinePlayers().size());
        blocksPlaced.set(0);
        mobsKilled.set(0);
    }

    /**
     * Gets the total blocks placed today.
     *
     * @return The block placement count
     */
    public long getBlocksPlaced() {
        return blocksPlaced.get();
    }

    /**
     * Gets the total mobs killed today.
     *
     * @return The mob kill count
     */
    public long getMobsKilled() {
        return mobsKilled.get();
    }

    /**
     * Gets the number of unique players who joined today.
     *
     * @return The unique player count
     */
    public int getUniquePlayersToday() {
        return uniquePlayersToday.size();
    }

    /**
     * Gets the peak concurrent players today.
     *
     * @return The peak player count
     */
    public int getPeakOnlineToday() {
        return peakOnlineToday.get();
    }
}
