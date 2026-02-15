package net.serverplugins.filter.data;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.api.ServerAPI;
import net.serverplugins.api.database.Database;
import net.serverplugins.filter.ServerFilter;

public class FilterPreferenceManager {

    private static final String TABLE_NAME = "filter_preferences";
    private static final String CREATE_TABLE_SQL =
            """
        CREATE TABLE IF NOT EXISTS %s (
            uuid VARCHAR(36) PRIMARY KEY,
            player_name VARCHAR(16) NOT NULL,
            filter_level VARCHAR(20) NOT NULL DEFAULT 'STRICT',
            updated_at BIGINT NOT NULL,
            created_at BIGINT NOT NULL
        )
        """
                    .formatted(TABLE_NAME);

    private final ServerFilter plugin;
    private final Map<UUID, PlayerFilterData> cache = new ConcurrentHashMap<>();
    private final FilterLevel defaultLevel;
    private Database database;

    public FilterPreferenceManager(ServerFilter plugin) {
        this.plugin = plugin;
        this.defaultLevel =
                FilterLevel.fromString(
                        plugin.getConfig().getString("default-filter-level", "STRICT"));
        // Delay database initialization to ensure ServerAPI is fully loaded
        plugin.getServer().getScheduler().runTaskLater(plugin, this::initializeDatabase, 1L);
    }

    private void initializeDatabase() {
        ServerAPI api = ServerAPI.getInstance();
        if (api == null || api.getDatabase() == null) {
            plugin.getLogger().warning("ServerAPI database not available. Retrying in 20 ticks...");
            // Retry after 1 second
            plugin.getServer().getScheduler().runTaskLater(plugin, this::initializeDatabase, 20L);
            return;
        }

        this.database = api.getDatabase();

        // Create table if not exists
        try {
            database.executeUpdate(CREATE_TABLE_SQL);
            plugin.getLogger()
                    .info("Filter preferences database initialized using " + database.getType());
        } catch (SQLException e) {
            plugin.getLogger()
                    .severe("Failed to create filter_preferences table: " + e.getMessage());
        }
    }

    public FilterLevel getFilterLevel(UUID uuid) {
        PlayerFilterData data = cache.get(uuid);
        if (data != null) {
            return data.getFilterLevel();
        }
        return defaultLevel;
    }

    public CompletableFuture<Void> setFilterLevel(UUID uuid, String playerName, FilterLevel level) {
        long now = System.currentTimeMillis();
        cache.put(uuid, new PlayerFilterData(uuid, playerName, level));

        if (database == null) {
            return CompletableFuture.completedFuture(null);
        }

        return database.executeUpdateAsync(
                        "INSERT INTO "
                                + TABLE_NAME
                                + " (uuid, player_name, filter_level, updated_at, created_at) "
                                + "VALUES (?, ?, ?, ?, ?) "
                                + "ON DUPLICATE KEY UPDATE player_name = ?, filter_level = ?, updated_at = ?",
                        uuid.toString(),
                        playerName,
                        level.name(),
                        now,
                        now,
                        playerName,
                        level.name(),
                        now)
                .thenAccept(
                        rows -> {
                            plugin.getLogger()
                                    .fine(
                                            "Saved filter preference for "
                                                    + playerName
                                                    + ": "
                                                    + level);
                        })
                .exceptionally(
                        e -> {
                            plugin.getLogger()
                                    .severe("Failed to save filter preference: " + e.getMessage());
                            return null;
                        });
    }

    public void loadPlayerData(UUID uuid, String playerName) {
        if (cache.containsKey(uuid)) {
            return;
        }

        if (database == null) {
            cache.put(uuid, new PlayerFilterData(uuid, playerName, defaultLevel));
            return;
        }

        // Load from database async
        database.executeQueryAsyncWithConsumer(
                        "SELECT filter_level FROM " + TABLE_NAME + " WHERE uuid = ?",
                        rs -> {
                            try {
                                if (rs.next()) {
                                    String levelStr = rs.getString("filter_level");
                                    FilterLevel level = FilterLevel.fromString(levelStr);
                                    cache.put(uuid, new PlayerFilterData(uuid, playerName, level));
                                } else {
                                    cache.put(
                                            uuid,
                                            new PlayerFilterData(uuid, playerName, defaultLevel));
                                }
                            } catch (SQLException e) {
                                plugin.getLogger()
                                        .warning(
                                                "Failed to load filter preference for "
                                                        + playerName
                                                        + ": "
                                                        + e.getMessage());
                                cache.put(
                                        uuid, new PlayerFilterData(uuid, playerName, defaultLevel));
                            }
                        },
                        uuid.toString())
                .exceptionally(
                        e -> {
                            plugin.getLogger()
                                    .warning(
                                            "Database error loading filter preference: "
                                                    + e.getMessage());
                            cache.put(uuid, new PlayerFilterData(uuid, playerName, defaultLevel));
                            return null;
                        });
    }

    public void unloadPlayerData(UUID uuid) {
        cache.remove(uuid);
    }

    public FilterLevel getDefaultLevel() {
        return defaultLevel;
    }

    public void saveAll() {
        if (database == null) {
            return;
        }

        for (Map.Entry<UUID, PlayerFilterData> entry : cache.entrySet()) {
            PlayerFilterData data = entry.getValue();
            try {
                database.executeUpdate(
                        "INSERT INTO "
                                + TABLE_NAME
                                + " (uuid, player_name, filter_level, updated_at, created_at) "
                                + "VALUES (?, ?, ?, ?, ?) "
                                + "ON DUPLICATE KEY UPDATE player_name = ?, filter_level = ?, updated_at = ?",
                        data.getUuid().toString(),
                        data.getPlayerName(),
                        data.getFilterLevel().name(),
                        data.getLastUpdated(),
                        data.getLastUpdated(),
                        data.getPlayerName(),
                        data.getFilterLevel().name(),
                        data.getLastUpdated());
            } catch (SQLException e) {
                plugin.getLogger()
                        .warning(
                                "Failed to save filter preference for "
                                        + data.getPlayerName()
                                        + ": "
                                        + e.getMessage());
            }
        }
    }
}
