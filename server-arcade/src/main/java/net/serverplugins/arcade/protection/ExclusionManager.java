package net.serverplugins.arcade.protection;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.serverplugins.api.database.Database;
import net.serverplugins.arcade.ServerArcade;
import org.bukkit.Bukkit;

/**
 * Manages player self-exclusion from gambling activities. Players can voluntarily exclude
 * themselves for various time periods.
 */
public class ExclusionManager {

    private final ServerArcade plugin;
    private final Database database;

    // PERFORMANCE: Cache exclusion checks to reduce database queries
    // Configurable TTL (safe with longer durations since exclusions are long-term)
    private final Cache<UUID, ExclusionRecord> exclusionCache;

    // Cache negative results (player NOT excluded) to avoid DB queries on every click
    private final Cache<UUID, Boolean> notExcludedCache;

    public ExclusionManager(ServerArcade plugin) {
        this.plugin = plugin;
        this.database = plugin.getDatabase();

        // Read cache settings from config
        int exclusionTtlMinutes =
                plugin.getConfig().getInt("performance.cache.exclusion_ttl_minutes", 5);
        int maxEntries = plugin.getConfig().getInt("performance.cache.max_entries", 10000);

        // Initialize cache with configured expiration
        this.exclusionCache =
                Caffeine.newBuilder()
                        .expireAfterWrite(exclusionTtlMinutes, TimeUnit.MINUTES)
                        .maximumSize(maxEntries)
                        .build();

        // Cache for non-excluded players to avoid hitting DB on every machine click
        this.notExcludedCache =
                Caffeine.newBuilder()
                        .expireAfterWrite(exclusionTtlMinutes, TimeUnit.MINUTES)
                        .maximumSize(maxEntries)
                        .build();

        plugin.getLogger()
                .info(
                        "Exclusion cache initialized ("
                                + exclusionTtlMinutes
                                + "min TTL, "
                                + maxEntries
                                + " max entries)");
    }

    /** Exclude a player from gambling for a specified duration. */
    public boolean excludePlayer(
            UUID playerId,
            String playerName,
            long durationMillis,
            boolean permanent,
            String reason) {
        if (database == null) return false;

        long now = System.currentTimeMillis();
        long until = permanent ? Long.MAX_VALUE : now + durationMillis;

        // Invalidate caches immediately
        exclusionCache.invalidate(playerId);
        notExcludedCache.invalidate(playerId);

        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            try {
                                // Use upsert to avoid SELECT+INSERT race condition and connection
                                // leak
                                database.executeUpdate(
                                        "INSERT INTO server_arcade_exclusions (player_uuid, player_name, excluded_at, excluded_until, is_permanent, reason) "
                                                + "VALUES (?, ?, ?, ?, ?, ?) "
                                                + "ON DUPLICATE KEY UPDATE excluded_at = VALUES(excluded_at), excluded_until = VALUES(excluded_until), "
                                                + "is_permanent = VALUES(is_permanent), reason = VALUES(reason)",
                                        playerId.toString(),
                                        playerName,
                                        now,
                                        until,
                                        permanent,
                                        reason);

                                plugin.getLogger()
                                        .info(
                                                String.format(
                                                        "Player %s excluded from gambling%s",
                                                        playerName,
                                                        permanent
                                                                ? " permanently"
                                                                : " for "
                                                                        + formatDuration(
                                                                                durationMillis)));

                            } catch (Exception e) {
                                plugin.getLogger()
                                        .warning("Failed to exclude player: " + e.getMessage());
                            }
                        });

        return true;
    }

    /**
     * Check if a player is currently excluded from gambling. PERFORMANCE: Uses cache for both
     * positive and negative results to avoid DB queries on every click. Never blocks the main
     * thread - returns false if DB is unavailable.
     */
    public boolean isExcluded(UUID playerId) {
        if (database == null) return false;

        // Check positive cache (player IS excluded)
        ExclusionRecord cached = exclusionCache.getIfPresent(playerId);
        if (cached != null) {
            if (cached.isPermanent()) {
                return true;
            }
            if (System.currentTimeMillis() < cached.getExcludedUntil()) {
                return true;
            }
            // Exclusion expired, remove from cache
            exclusionCache.invalidate(playerId);
            return false;
        }

        // Check negative cache (player is NOT excluded)
        if (notExcludedCache.getIfPresent(playerId) != null) {
            return false;
        }

        // Not in either cache, fetch from database
        try (ResultSet rs =
                database.executeQuery(
                        "SELECT excluded_until, is_permanent, player_name, excluded_at, reason FROM server_arcade_exclusions WHERE player_uuid = ?",
                        playerId.toString())) {

            if (rs.next()) {
                boolean isPermanent = rs.getBoolean("is_permanent");
                long excludedUntil = rs.getLong("excluded_until");
                long now = System.currentTimeMillis();

                boolean isActive = isPermanent || now < excludedUntil;

                if (isActive) {
                    // Cache the full exclusion record
                    ExclusionRecord record = new ExclusionRecord(rs);
                    exclusionCache.put(playerId, record);
                } else {
                    // Exclusion expired, cache as not excluded
                    notExcludedCache.put(playerId, Boolean.TRUE);
                }

                return isActive;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to check exclusion status: " + e.getMessage());
            // On error, don't block - assume not excluded
            return false;
        }

        // No record found - player is not excluded, cache this
        notExcludedCache.put(playerId, Boolean.TRUE);
        return false;
    }

    /**
     * Get exclusion record for a player. PERFORMANCE: Uses cache to avoid repeated database
     * queries.
     */
    public ExclusionRecord getExclusion(UUID playerId) {
        if (database == null) return null;

        // Check cache first
        ExclusionRecord cached = exclusionCache.getIfPresent(playerId);
        if (cached != null) {
            return cached;
        }

        // Not in cache, fetch from database
        ExclusionRecord record = getExclusionFromDatabase(playerId);
        if (record != null) {
            exclusionCache.put(playerId, record);
        }
        return record;
    }

    /** Internal method to fetch exclusion from database (no caching). */
    private ExclusionRecord getExclusionFromDatabase(UUID playerId) {
        try (ResultSet rs =
                database.executeQuery(
                        "SELECT * FROM server_arcade_exclusions WHERE player_uuid = ?",
                        playerId.toString())) {

            if (rs.next()) {
                return new ExclusionRecord(rs);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get exclusion record: " + e.getMessage());
        }
        return null;
    }

    /** Remove exclusion for a player (admin only). */
    public boolean removeExclusion(UUID playerId) {
        if (database == null) return false;

        // PERFORMANCE: Invalidate caches immediately
        exclusionCache.invalidate(playerId);
        notExcludedCache.invalidate(playerId);

        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            try {
                                database.executeUpdate(
                                        "DELETE FROM server_arcade_exclusions WHERE player_uuid = ?",
                                        playerId.toString());

                                plugin.getLogger()
                                        .info("Removed gambling exclusion for player: " + playerId);

                            } catch (Exception e) {
                                plugin.getLogger()
                                        .warning("Failed to remove exclusion: " + e.getMessage());
                            }
                        });

        return true;
    }

    /** Format duration in human-readable format. */
    private String formatDuration(long millis) {
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        if (days > 0) {
            return days + " day" + (days > 1 ? "s" : "");
        }

        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        if (hours > 0) {
            return hours + " hour" + (hours > 1 ? "s" : "");
        }

        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        return minutes + " minute" + (minutes > 1 ? "s" : "");
    }

    /** Parse duration string (e.g., "1d", "7d", "30d") to milliseconds. */
    public static long parseDuration(String duration) throws IllegalArgumentException {
        if (duration == null || duration.isEmpty()) {
            throw new IllegalArgumentException("Duration cannot be empty");
        }

        duration = duration.toLowerCase().trim();

        if (duration.equals("permanent") || duration.equals("perm")) {
            return Long.MAX_VALUE;
        }

        try {
            char unit = duration.charAt(duration.length() - 1);
            int value = Integer.parseInt(duration.substring(0, duration.length() - 1));

            return switch (unit) {
                case 'm' -> TimeUnit.MINUTES.toMillis(value);
                case 'h' -> TimeUnit.HOURS.toMillis(value);
                case 'd' -> TimeUnit.DAYS.toMillis(value);
                case 'w' -> TimeUnit.DAYS.toMillis(value * 7L);
                default ->
                        throw new IllegalArgumentException(
                                "Invalid duration format. Use: 1m, 1h, 1d, 1w, or 'permanent'");
            };
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid duration format. Use: 1m, 1h, 1d, 1w, or 'permanent'");
        }
    }

    /** Exclusion record data class. */
    public static class ExclusionRecord {
        private final String playerName;
        private final long excludedAt;
        private final long excludedUntil;
        private final boolean isPermanent;
        private final String reason;

        public ExclusionRecord(ResultSet rs) throws Exception {
            this.playerName = rs.getString("player_name");
            this.excludedAt = rs.getLong("excluded_at");
            this.excludedUntil = rs.getLong("excluded_until");
            this.isPermanent = rs.getBoolean("is_permanent");
            this.reason = rs.getString("reason");
        }

        public String getPlayerName() {
            return playerName;
        }

        public long getExcludedAt() {
            return excludedAt;
        }

        public long getExcludedUntil() {
            return excludedUntil;
        }

        public boolean isPermanent() {
            return isPermanent;
        }

        public String getReason() {
            return reason;
        }

        public long getRemainingTime() {
            if (isPermanent) return Long.MAX_VALUE;
            return Math.max(0, excludedUntil - System.currentTimeMillis());
        }

        public String getFormattedRemaining() {
            if (isPermanent) return "Permanent";

            long remaining = getRemainingTime();
            if (remaining <= 0) return "Expired";

            long days = TimeUnit.MILLISECONDS.toDays(remaining);
            if (days > 0) {
                return days + " day" + (days > 1 ? "s" : "");
            }

            long hours = TimeUnit.MILLISECONDS.toHours(remaining);
            if (hours > 0) {
                return hours + " hour" + (hours > 1 ? "s" : "");
            }

            long minutes = TimeUnit.MILLISECONDS.toMinutes(remaining);
            return minutes + " minute" + (minutes > 1 ? "s" : "");
        }
    }
}
