package net.serverplugins.keys.cache;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.serverplugins.keys.ServerKeys;
import net.serverplugins.keys.models.KeyStats;
import net.serverplugins.keys.models.KeyType;
import net.serverplugins.keys.repository.KeysRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * In-memory cache for player key statistics. Prevents database queries on every placeholder
 * request.
 *
 * <p>Cache Strategy: - Warm cache on player join (async) - Invalidate on player quit - Update cache
 * when keys are given - TTL-based expiry for stale data protection
 */
public class StatsCache implements Listener {

    private final ServerKeys plugin;
    private final KeysRepository repository;

    // Cache: UUID -> CachedStats
    private final Map<UUID, CachedStats> cache = new ConcurrentHashMap<>();

    // Cache TTL in milliseconds (30 seconds)
    private static final long CACHE_TTL_MS = TimeUnit.SECONDS.toMillis(30);

    public StatsCache(ServerKeys plugin, KeysRepository repository) {
        this.plugin = plugin;
        this.repository = repository;

        // Register listener
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Warm cache for all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            warmCache(player.getUniqueId());
        }

        // Schedule periodic cleanup of expired entries
        Bukkit.getScheduler()
                .runTaskTimerAsynchronously(
                        plugin,
                        this::cleanupExpired,
                        20L * 60, // 1 minute delay
                        20L * 60 // Every minute
                        );
    }

    /** Get total keys received for a player (cached). */
    public int getTotalReceived(UUID uuid) {
        CachedStats cached = getOrLoad(uuid);
        if (cached == null) return 0;
        return cached.stats.stream().mapToInt(KeyStats::getTotalReceived).sum();
    }

    /** Get total keys received by type (cached). */
    public int getTotalReceivedByType(UUID uuid, KeyType type) {
        CachedStats cached = getOrLoad(uuid);
        if (cached == null) return 0;
        return cached.stats.stream()
                .filter(s -> s.getKeyType() == type)
                .mapToInt(KeyStats::getTotalReceived)
                .sum();
    }

    /** Get received count for specific key (cached). */
    public int getReceivedForKey(UUID uuid, KeyType type, String keyName) {
        CachedStats cached = getOrLoad(uuid);
        if (cached == null) return 0;
        return cached.stats.stream()
                .filter(s -> s.getKeyType() == type && s.getKeyName().equalsIgnoreCase(keyName))
                .mapToInt(KeyStats::getTotalReceived)
                .findFirst()
                .orElse(0);
    }

    /** Get all stats for a player (cached). */
    public List<KeyStats> getPlayerStats(UUID uuid) {
        CachedStats cached = getOrLoad(uuid);
        if (cached == null) return Collections.emptyList();
        return cached.stats;
    }

    /**
     * Update cache when keys are given. This updates the in-memory cache immediately without
     * waiting for DB.
     */
    public void updateOnKeyGiven(
            UUID uuid, String username, KeyType type, String keyName, int amount) {
        CachedStats cached = cache.get(uuid);
        if (cached == null) {
            // No cache, create a new entry
            KeyStats newStat = new KeyStats(uuid, username, type, keyName, amount, 0, null);
            cache.put(uuid, new CachedStats(List.of(newStat)));
            return;
        }

        // Find existing stat or create new one
        boolean found = false;
        for (KeyStats stat : cached.stats) {
            if (stat.getKeyType() == type && stat.getKeyName().equalsIgnoreCase(keyName)) {
                stat.addReceived(amount);
                found = true;
                break;
            }
        }

        if (!found) {
            // Add new stat to the list
            List<KeyStats> newList = new java.util.ArrayList<>(cached.stats);
            newList.add(new KeyStats(uuid, username, type, keyName, amount, 0, null));
            cache.put(uuid, new CachedStats(newList));
        } else {
            // Refresh timestamp
            cached.refreshTimestamp();
        }
    }

    /** Invalidate cache for a player. */
    public void invalidate(UUID uuid) {
        cache.remove(uuid);
    }

    /** Clear entire cache. */
    public void clear() {
        cache.clear();
    }

    /** Warm cache for a player asynchronously. */
    public final void warmCache(UUID uuid) {
        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            List<KeyStats> stats = repository.getPlayerStatsSync(uuid);
                            cache.put(uuid, new CachedStats(stats));
                        });
    }

    /**
     * Get cached stats or load from cache. Returns null if not in cache and loading (for
     * non-blocking placeholder calls).
     */
    private CachedStats getOrLoad(UUID uuid) {
        CachedStats cached = cache.get(uuid);

        // Check if cache exists and is fresh
        if (cached != null && !cached.isExpired()) {
            return cached;
        }

        // If expired but exists, return stale data but trigger async refresh
        if (cached != null) {
            warmCache(uuid);
            return cached; // Return stale data while refreshing
        }

        // Not in cache - check if player is online
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            // Online player, trigger async load
            warmCache(uuid);
        }

        // Return null for now (placeholder will show 0)
        return null;
    }

    /** Cleanup expired cache entries for offline players. */
    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        cache.entrySet()
                .removeIf(
                        entry -> {
                            // Keep online players
                            if (Bukkit.getPlayer(entry.getKey()) != null) {
                                return false;
                            }
                            // Remove if expired and offline
                            return entry.getValue().timestamp + CACHE_TTL_MS * 2 < now;
                        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        warmCache(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // Keep in cache briefly for tab list updates during quit
        // Will be cleaned up by periodic cleanup
    }

    /** Cached stats with timestamp. */
    private static class CachedStats {
        final List<KeyStats> stats;
        long timestamp;

        CachedStats(List<KeyStats> stats) {
            this.stats = stats;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }

        void refreshTimestamp() {
            this.timestamp = System.currentTimeMillis();
        }
    }
}
