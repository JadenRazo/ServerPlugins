package net.serverplugins.core.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.serverplugins.core.ServerCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Manages per-player data persistence using JSON files. Each player has their own file in
 * plugins/ServerCore/playerdata/{uuid}.json
 */
public class PlayerDataManager {

    private final ServerCore plugin;
    private final File dataFolder;
    private final Gson gson;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final ExecutorService ioExecutor =
            Executors.newFixedThreadPool(
                    2,
                    r -> {
                        Thread t = new Thread(r, "ServerCore-PlayerData-IO");
                        t.setDaemon(true);
                        return t;
                    });

    public PlayerDataManager(ServerCore plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        // Create data folder if it doesn't exist
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    /**
     * Asynchronously preload player data from disk. Should be called during
     * AsyncPlayerPreLoginEvent to avoid blocking the main thread.
     *
     * @param playerId The UUID of the player
     * @return CompletableFuture that completes with the loaded PlayerData
     */
    public CompletableFuture<PlayerData> preloadPlayerDataAsync(UUID playerId) {
        // If already cached, return immediately
        PlayerData cached = cache.get(playerId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return CompletableFuture.supplyAsync(
                () -> {
                    PlayerData data = loadPlayerDataFromDisk(playerId);
                    cache.put(playerId, data);
                    return data;
                },
                ioExecutor);
    }

    /**
     * Load player data from disk or create default. Uses cache-first approach - data MUST be
     * preloaded during AsyncPlayerPreLoginEvent. This method should NEVER be called directly -
     * always use preloadPlayerDataAsync() first.
     *
     * @param playerId The UUID of the player
     * @return The PlayerData for this player, or null if not in cache
     * @throws IllegalStateException if called before data is preloaded
     */
    public PlayerData loadPlayerData(UUID playerId) {
        // Check cache first
        PlayerData data = cache.get(playerId);

        if (data == null) {
            // Data not preloaded (e.g. after plugin reload) — synchronous fallback
            plugin.getLogger()
                    .warning(
                            "Player data not preloaded for "
                                    + playerId
                                    + " — loading synchronously as fallback.");
            data = loadPlayerDataFromDisk(playerId);
            cache.put(playerId, data);
        }

        return data;
    }

    /**
     * Internal method to perform the actual disk load. Only called from async preload - never from
     * main thread.
     */
    private PlayerData loadPlayerDataFromDisk(UUID playerId) {
        File playerFile = getPlayerFile(playerId);
        PlayerData data;

        if (playerFile.exists()) {
            try (FileReader reader = new FileReader(playerFile)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                data = new PlayerData(playerId);

                // Load feature toggles
                if (json.has("features")) {
                    JsonObject features = json.getAsJsonObject("features");
                    for (String key : features.keySet()) {
                        data.setFeatureEnabled(key, features.get(key).getAsBoolean());
                    }
                }

                // Load first join status
                if (json.has("firstJoin")) {
                    data.setFirstJoin(json.get("firstJoin").getAsBoolean());
                } else {
                    data.setFirstJoin(true); // Default to true if not set
                }

            } catch (Exception e) {
                plugin.getLogger()
                        .warning(
                                "Failed to load player data for "
                                        + playerId
                                        + ": "
                                        + e.getMessage());
                data = new PlayerData(playerId);
            }
        } else {
            data = new PlayerData(playerId);
        }

        return data;
    }

    /** Save player data to disk asynchronously */
    public void savePlayerData(UUID playerId) {
        PlayerData data = cache.get(playerId);
        if (data == null) return;

        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            File playerFile = getPlayerFile(playerId);
                            try (FileWriter writer = new FileWriter(playerFile)) {
                                JsonObject json = new JsonObject();

                                // Save feature toggles
                                JsonObject features = new JsonObject();
                                data.getFeatureToggles().forEach(features::addProperty);
                                json.add("features", features);

                                // Save first join status
                                json.addProperty("firstJoin", data.isFirstJoin());

                                gson.toJson(json, writer);
                            } catch (IOException e) {
                                plugin.getLogger()
                                        .severe(
                                                "Failed to save player data for "
                                                        + playerId
                                                        + ": "
                                                        + e.getMessage());
                            }
                        });
    }

    /** Unload player data from cache (call on player quit) */
    public void unloadPlayerData(UUID playerId) {
        savePlayerData(playerId);
        cache.remove(playerId);
    }

    /** Save all cached player data and shut down IO executor (call on plugin disable) */
    public void saveAll() {
        cache.keySet().forEach(this::savePlayerData);
        ioExecutor.shutdown();
    }

    /** Check if a player has a specific feature enabled */
    public boolean isFeatureEnabled(Player player, String featureName) {
        PlayerData data = loadPlayerData(player.getUniqueId());
        return data.isFeatureEnabled(featureName);
    }

    /** Set feature state for a player */
    public void setFeatureEnabled(Player player, String featureName, boolean enabled) {
        PlayerData data = loadPlayerData(player.getUniqueId());
        data.setFeatureEnabled(featureName, enabled);
        savePlayerData(player.getUniqueId());
    }

    /** Toggle feature state for a player */
    public boolean toggleFeature(Player player, String featureName) {
        PlayerData data = loadPlayerData(player.getUniqueId());
        boolean newState = !data.isFeatureEnabled(featureName);
        data.setFeatureEnabled(featureName, newState);
        savePlayerData(player.getUniqueId());
        return newState;
    }

    /** Check if this is a player's first join */
    public boolean isFirstJoin(UUID playerId) {
        PlayerData data = loadPlayerData(playerId);
        return data.isFirstJoin();
    }

    /** Mark player as having joined before */
    public void markJoined(UUID playerId) {
        PlayerData data = loadPlayerData(playerId);
        data.setFirstJoin(false);
        savePlayerData(playerId);
    }

    private File getPlayerFile(UUID playerId) {
        return new File(dataFolder, playerId.toString() + ".json");
    }

    /** Represents player-specific data */
    public static class PlayerData {
        private final UUID playerId;
        private final Map<String, Boolean> featureToggles;
        private boolean firstJoin;

        public PlayerData(UUID playerId) {
            this.playerId = playerId;
            this.featureToggles = new HashMap<>();
            this.firstJoin = true;
        }

        public UUID getPlayerId() {
            return playerId;
        }

        public boolean isFeatureEnabled(String featureName) {
            // Default to null if not set (will use global state)
            return featureToggles.getOrDefault(featureName, null);
        }

        public void setFeatureEnabled(String featureName, boolean enabled) {
            featureToggles.put(featureName, enabled);
        }

        public Map<String, Boolean> getFeatureToggles() {
            return new HashMap<>(featureToggles);
        }

        public boolean isFirstJoin() {
            return firstJoin;
        }

        public void setFirstJoin(boolean firstJoin) {
            this.firstJoin = firstJoin;
        }

        public boolean hasFeaturePreference(String featureName) {
            return featureToggles.containsKey(featureName);
        }
    }
}
