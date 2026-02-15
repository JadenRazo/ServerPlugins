package net.serverplugins.afk.managers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.afk.ServerAFK;
import net.serverplugins.afk.models.PlayerStats;
import net.serverplugins.afk.repository.AfkRepository;

/**
 * Manages player statistics with caching. Handles loading, updating, and caching of player AFK
 * statistics.
 */
public class StatsManager {

    private final ServerAFK plugin;
    private final AfkRepository repository;
    private final Map<UUID, PlayerStats> statsCache;
    private final Map<UUID, Long> cacheTimestamps;
    private final long cacheExpirationMs;

    public StatsManager(ServerAFK plugin) {
        this.plugin = plugin;
        this.repository = plugin.getRepository();
        this.statsCache = new ConcurrentHashMap<>();
        this.cacheTimestamps = new ConcurrentHashMap<>();

        // Cache expiration from config (convert minutes to milliseconds)
        this.cacheExpirationMs = plugin.getAfkConfig().getStatsCacheMinutes() * 60 * 1000L;
    }

    /** Gets player statistics, loading from cache or database. */
    public PlayerStats getStats(UUID playerId) {
        // Check cache first
        if (isCacheValid(playerId)) {
            return statsCache.get(playerId);
        }

        // Load from database
        PlayerStats stats = repository.getPlayerStats(playerId);

        if (stats == null) {
            // Create new stats entry
            stats = new PlayerStats(playerId);
            repository.savePlayerStats(stats);
        }

        // Update cache
        updateCache(playerId, stats);

        return stats;
    }

    /** Updates player statistics in cache and database. */
    public void updateStats(PlayerStats stats) {
        if (stats == null) {
            return;
        }

        // Update last AFK time
        stats.updateLastAfkTime();

        // Save to database
        repository.savePlayerStats(stats);

        // Update cache
        updateCache(stats.getPlayerUuid(), stats);
    }

    /** Adds AFK time to a player's statistics. */
    public void addAfkTime(UUID playerId, long seconds) {
        PlayerStats stats = getStats(playerId);
        stats.addAfkTime(seconds);
        updateStats(stats);
    }

    /** Adds a reward to a player's statistics. */
    public void addReward(UUID playerId, double currency, int xp) {
        PlayerStats stats = getStats(playerId);
        stats.addReward();
        stats.addCurrency(currency);
        stats.addXp(xp);
        updateStats(stats);
    }

    /** Increments session count for a player. */
    public void incrementSession(UUID playerId, long sessionSeconds) {
        PlayerStats stats = getStats(playerId);
        stats.incrementSessions();
        stats.updateLongestSession(sessionSeconds);
        stats.updateStreak(); // Update daily streak
        updateStats(stats);
    }

    /** Updates favorite zone for a player based on most time spent. */
    public void updateFavoriteZone(UUID playerId, int zoneId) {
        PlayerStats stats = getStats(playerId);

        // Only update if we have session history to verify
        Integer currentFavorite = stats.getFavoriteZoneId();
        if (currentFavorite == null || currentFavorite != zoneId) {
            stats.setFavoriteZoneId(zoneId);
            updateStats(stats);
        }
    }

    /** Gets top players by total AFK time. */
    public List<PlayerStats> getTopPlayersByAfkTime(int limit) {
        return repository.getTopPlayersByAfkTime(limit);
    }

    /** Gets top players by currency earned. */
    public List<PlayerStats> getTopPlayersByCurrency(int limit) {
        return repository.getTopPlayersByCurrency(limit);
    }

    /** Gets top players by session count. */
    public List<PlayerStats> getTopPlayersBySessions(int limit) {
        return repository.getTopPlayersBySessions(limit);
    }

    /** Gets top players by current streak. */
    public List<PlayerStats> getTopPlayersByStreak(int limit) {
        return repository.getTopPlayersByStreak(limit);
    }

    /** Invalidates cache for a specific player. */
    public void invalidateCache(UUID playerId) {
        statsCache.remove(playerId);
        cacheTimestamps.remove(playerId);
    }

    /** Clears all cached statistics. */
    public void clearCache() {
        statsCache.clear();
        cacheTimestamps.clear();
    }

    /** Pre-loads statistics for online players. */
    public void preloadOnlinePlayers() {
        for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            getStats(player.getUniqueId());
        }
        plugin.getLogger()
                .info("Pre-loaded statistics for " + statsCache.size() + " online players");
    }

    /** Saves all cached statistics to database. */
    public void saveAllCached() {
        int saved = 0;
        for (PlayerStats stats : statsCache.values()) {
            repository.savePlayerStats(stats);
            saved++;
        }
        plugin.getLogger().info("Saved " + saved + " cached statistics to database");
    }

    /** Checks if cached data is still valid for a player. */
    private boolean isCacheValid(UUID playerId) {
        if (!statsCache.containsKey(playerId)) {
            return false;
        }

        Long timestamp = cacheTimestamps.get(playerId);
        if (timestamp == null) {
            return false;
        }

        long age = System.currentTimeMillis() - timestamp;
        return age < cacheExpirationMs;
    }

    /** Updates cache with player statistics. */
    private void updateCache(UUID playerId, PlayerStats stats) {
        statsCache.put(playerId, stats);
        cacheTimestamps.put(playerId, System.currentTimeMillis());
    }

    /** Gets global statistics summary. */
    public Map<String, Object> getGlobalStats() {
        Map<String, Object> stats = new HashMap<>();

        long totalAfkTime = repository.getTotalAfkTimeAllPlayers();
        int totalPlayers = repository.getTotalPlayersWithStats();
        int totalSessions = repository.getTotalSessionsAllPlayers();
        double totalCurrency = repository.getTotalCurrencyEarned();

        stats.put("total_afk_time", totalAfkTime);
        stats.put("total_players", totalPlayers);
        stats.put("total_sessions", totalSessions);
        stats.put("total_currency", totalCurrency);
        stats.put("average_afk_time", totalPlayers > 0 ? totalAfkTime / totalPlayers : 0);

        return stats;
    }
}
