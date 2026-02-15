package net.serverplugins.claim.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import net.serverplugins.claim.models.Claim;

/**
 * Thread-safe LRU (Least Recently Used) cache for claims with automatic eviction. Limits memory
 * growth by evicting least recently accessed entries when the cache is full.
 *
 * <p>Performance characteristics: - get(): O(1) - HashMap lookup with access-order tracking -
 * put(): O(1) - HashMap insertion with automatic eviction - Thread-safe: Uses ReadWriteLock for
 * concurrent access
 *
 * <p>Metrics: - Tracks cache hits, misses, and evictions for monitoring - Provides hit rate
 * calculation for performance analysis
 */
public class LRUClaimCache extends LinkedHashMap<Integer, Claim> {

    private final int maxEntries;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private long hits = 0;
    private long misses = 0;
    private long evictions = 0;

    /**
     * Create an LRU cache with specified maximum entries.
     *
     * @param maxEntries Maximum number of entries before eviction occurs
     */
    public LRUClaimCache(int maxEntries) {
        // Initialize with access-order (true) for LRU behavior
        // Initial capacity: 16, Load factor: 0.75, Access-order: true
        super(16, 0.75f, true);
        this.maxEntries = maxEntries;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<Integer, Claim> eldest) {
        boolean shouldRemove = size() > maxEntries;
        if (shouldRemove) {
            evictions++;
        }
        return shouldRemove;
    }

    @Override
    public Claim get(Object key) {
        lock.readLock().lock();
        try {
            Claim value = super.get(key);
            if (value != null) {
                hits++;
            } else {
                misses++;
            }
            return value;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Claim put(Integer key, Claim value) {
        lock.writeLock().lock();
        try {
            return super.put(key, value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Claim remove(Object key) {
        lock.writeLock().lock();
        try {
            return super.remove(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean containsKey(Object key) {
        lock.readLock().lock();
        try {
            return super.containsKey(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Get all values in the cache. Returns a snapshot to avoid concurrent modification issues. */
    @Override
    public java.util.Collection<Claim> values() {
        lock.readLock().lock();
        try {
            return new java.util.ArrayList<>(super.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Get the number of cache hits. */
    public long getHits() {
        lock.readLock().lock();
        try {
            return hits;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Get the number of cache misses. */
    public long getMisses() {
        lock.readLock().lock();
        try {
            return misses;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Get the number of evictions (entries removed due to capacity). */
    public long getEvictions() {
        lock.readLock().lock();
        try {
            return evictions;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Calculate the cache hit rate (hits / total accesses).
     *
     * @return Hit rate as a percentage (0.0 to 1.0), or 0.0 if no accesses
     */
    public double getHitRate() {
        lock.readLock().lock();
        try {
            long total = hits + misses;
            if (total == 0) return 0.0;
            return (double) hits / total;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Reset all metrics. */
    public void resetMetrics() {
        lock.writeLock().lock();
        try {
            hits = 0;
            misses = 0;
            evictions = 0;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Get cache statistics as a formatted string. */
    public String getStats() {
        lock.readLock().lock();
        try {
            long total = hits + misses;
            double hitRate = getHitRate() * 100;
            return String.format(
                    "Cache Stats - Size: %d/%d, Hits: %d, Misses: %d, Hit Rate: %.2f%%, Evictions: %d",
                    size(), maxEntries, hits, misses, hitRate, evictions);
        } finally {
            lock.readLock().unlock();
        }
    }
}
