package net.serverplugins.claim.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Debounced cache for location-based claim ownership lookups. Caches results for a short TTL to
 * reduce database queries during player movement.
 *
 * <p>Performance impact: - Reduces ownership lookups from every player move to once per 5 seconds -
 * Limits memory to 200 active players - Thread-safe for concurrent player movement
 */
public class LocationResultCache {

    private static final int MAX_ENTRIES = 200; // Limit to active players
    private static final long TTL_MILLIS = 5000; // 5 second cache

    private final Map<String, CachedResult> cache = new ConcurrentHashMap<>();

    /** Cached result with timestamp for TTL. */
    public static class CachedResult {
        private final String ownerName;
        private final String claimName;
        private final long timestamp;

        public CachedResult(String ownerName, String claimName) {
            this.ownerName = ownerName;
            this.claimName = claimName;
            this.timestamp = System.currentTimeMillis();
        }

        public String getOwnerName() {
            return ownerName;
        }

        public String getClaimName() {
            return claimName;
        }

        public boolean isExpired(long ttlMillis) {
            return System.currentTimeMillis() - timestamp > ttlMillis;
        }
    }

    /** Get cached result if valid, null otherwise. */
    public CachedResult get(String world, int chunkX, int chunkZ) {
        String key = getKey(world, chunkX, chunkZ);
        CachedResult result = cache.get(key);

        // Check if expired
        if (result != null && result.isExpired(TTL_MILLIS)) {
            cache.remove(key);
            return null;
        }

        return result;
    }

    /**
     * Store a result in the cache. Implements simple eviction by clearing cache when limit is
     * reached.
     */
    public void put(String world, int chunkX, int chunkZ, String ownerName, String claimName) {
        // Simple eviction: clear cache when full (better than unbounded growth)
        if (cache.size() >= MAX_ENTRIES) {
            cache.clear();
        }

        String key = getKey(world, chunkX, chunkZ);
        cache.put(key, new CachedResult(ownerName, claimName));
    }

    /** Invalidate cache for a specific chunk. */
    public void invalidate(String world, int chunkX, int chunkZ) {
        String key = getKey(world, chunkX, chunkZ);
        cache.remove(key);
    }

    /** Clear all cached results. */
    public void clear() {
        cache.clear();
    }

    /** Get cache size. */
    public int size() {
        return cache.size();
    }

    private String getKey(String world, int chunkX, int chunkZ) {
        return world + ":" + chunkX + ":" + chunkZ;
    }
}
