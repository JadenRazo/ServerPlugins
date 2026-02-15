package net.serverplugins.commands.data;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.commands.ServerCommands;
import net.serverplugins.commands.models.Home;
import net.serverplugins.commands.repository.CommandsRepository;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class PlayerDataManager {

    private final ServerCommands plugin;
    private final CommandsRepository repository;
    private final File dataFolder;
    private final Map<UUID, PlayerData> dataCache = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<PlayerData>> loadingFutures =
            new ConcurrentHashMap<>();
    private final Map<UUID, Long> activeSessions = new ConcurrentHashMap<>();
    private final boolean useDatabase;

    public PlayerDataManager(ServerCommands plugin, CommandsRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        this.useDatabase = repository != null;
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!useDatabase && !dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    /**
     * Preload player data asynchronously. Call this on player join. Uses CompletableFuture to avoid
     * busy-wait polling - if multiple threads request the same player's data, they all receive the
     * same Future.
     */
    public CompletableFuture<PlayerData> preloadPlayerDataAsync(UUID uuid) {
        // Already cached - return immediately
        PlayerData cached = dataCache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        // Check if already loading - return the existing future
        CompletableFuture<PlayerData> existingFuture = loadingFutures.get(uuid);
        if (existingFuture != null) {
            return existingFuture;
        }

        // Create new future for this load operation
        CompletableFuture<PlayerData> future =
                CompletableFuture.supplyAsync(
                                () -> {
                                    PlayerData data = loadPlayerData(uuid);
                                    dataCache.put(uuid, data);
                                    return data;
                                })
                        .whenComplete(
                                (data, throwable) -> {
                                    // Clean up the loading future when complete
                                    loadingFutures.remove(uuid);

                                    if (throwable != null) {
                                        plugin.getLogger()
                                                .severe(
                                                        "Failed to load player data for "
                                                                + uuid
                                                                + ": "
                                                                + throwable.getMessage());
                                    }
                                });

        // Store the future so other threads can wait on it
        loadingFutures.put(uuid, future);

        return future;
    }

    public PlayerData getPlayerData(UUID uuid) {
        PlayerData cached = dataCache.get(uuid);
        if (cached != null) {
            return cached;
        }

        // Not cached - check if we should load synchronously (not recommended) or return empty
        // If called on main thread, use placeholder and trigger async load
        if (Bukkit.isPrimaryThread()) {
            // Return a placeholder and trigger async load
            PlayerData placeholder = new PlaceholderPlayerData(uuid);
            preloadPlayerDataAsync(uuid)
                    .thenAccept(
                            data -> {
                                // Data is now loaded in cache, placeholder will be replaced on next
                                // access
                            });
            return placeholder;
        }

        // Not on main thread, safe to load synchronously
        return dataCache.computeIfAbsent(uuid, this::loadPlayerData);
    }

    private PlayerData loadPlayerData(UUID uuid) {
        if (useDatabase) {
            return new DatabasePlayerData(uuid, repository);
        } else {
            File file = new File(dataFolder, uuid.toString() + ".yml");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            return new FilePlayerData(uuid, config, file);
        }
    }

    public void savePlayerData(UUID uuid) {
        PlayerData data = dataCache.get(uuid);
        if (data != null) {
            data.save();
        }
    }

    public void saveAll() {
        dataCache.values().forEach(PlayerData::save);
    }

    public void unloadPlayerData(UUID uuid) {
        PlayerData data = dataCache.remove(uuid);
        if (data != null) {
            data.save();
        }
        activeSessions.remove(uuid);
    }

    /**
     * Start tracking a player's session. Should be called on join before async loading. This
     * prevents race conditions where a player quits before data loads.
     */
    public void startSession(UUID uuid, long sessionStart) {
        activeSessions.put(uuid, sessionStart);
    }

    /** Get the session start time for a player, or null if no active session. */
    public Long getActiveSessionStart(UUID uuid) {
        return activeSessions.get(uuid);
    }

    /**
     * Calculate playtime for an active session and clear it. Returns the calculated playtime in
     * milliseconds. Does NOT add to PlayerData - caller must do that.
     */
    public long finalizeSession(UUID uuid) {
        Long sessionStart = activeSessions.remove(uuid);
        if (sessionStart == null || sessionStart <= 0) {
            return 0;
        }

        return System.currentTimeMillis() - sessionStart;
    }

    public interface PlayerData {
        UUID getUuid();

        Map<String, Location> getHomes();

        void setHome(String name, Location location);

        void removeHome(String name);

        Location getHome(String name);

        boolean hasHome(String name);

        int getHomeCount();

        // Extended home methods with full Home model support
        Map<String, Home> getHomesWithDetails();

        Home getHomeDetails(String name);

        void setHome(String name, Location location, Material icon, String description);

        void renameHome(String oldName, String newName);

        void updateHomeIcon(String name, Material icon);

        void updateHomeDescription(String name, String description);

        Location getLastLocation();

        void setLastLocation(Location location);

        long getPlaytime();

        void addPlaytime(long time);

        long getLastSeen();

        void setLastSeen(long time);

        long getSessionStart();

        void setSessionStart(long time);

        int getWarnings();

        void addWarning();

        void setWarnings(int warnings);

        void clearWarnings();

        boolean isFlyEnabled();

        void setFlyEnabled(boolean enabled);

        boolean isGodMode();

        void setGodMode(boolean enabled);

        void save();
    }

    public static class DatabasePlayerData implements PlayerData {
        private final UUID uuid;
        private final CommandsRepository repository;
        private final Map<String, Home> homesDetailed = new HashMap<>();
        private Location lastLocation;
        private long playtime;
        private long lastSeen;
        private long sessionStart;
        private int warnings;
        private boolean flyEnabled;
        private boolean godMode;
        private boolean dirty = false;

        public DatabasePlayerData(UUID uuid, CommandsRepository repository) {
            this.uuid = uuid;
            this.repository = repository;
            loadData();
        }

        private void loadData() {
            // Load homes with full details from database
            homesDetailed.putAll(repository.getHomesWithDetails(uuid));

            // Load player data
            CommandsRepository.PlayerDataRecord record = repository.getPlayerData(uuid);
            this.warnings = record.warnings;
            this.flyEnabled = record.flyEnabled;
            this.godMode = record.godMode;
            this.lastLocation = record.lastLocation;

            // Load playtime
            CommandsRepository.PlaytimeData pt = repository.getPlaytime(uuid);
            this.playtime = pt.totalSeconds;
            this.lastSeen = pt.lastQuit != null ? pt.lastQuit : System.currentTimeMillis();
            this.sessionStart = pt.lastJoin != null ? pt.lastJoin : 0;
        }

        @Override
        public void save() {
            if (!dirty) return;
            dirty = false; // Mark as clean immediately to prevent duplicate saves

            Player player = Bukkit.getPlayer(uuid);
            String username = player != null ? player.getName() : null;

            // Capture values for async save
            final int w = warnings;
            final boolean fly = flyEnabled;
            final boolean god = godMode;
            final Location loc = lastLocation;
            final long pt = playtime;
            final Long ss = sessionStart > 0 ? sessionStart : null;
            final long ls = lastSeen;

            // Save async to avoid blocking the main thread
            CompletableFuture.runAsync(
                    () -> {
                        repository.savePlayerData(uuid, username, w, fly, god, loc);
                        repository.savePlaytime(uuid, username, pt, ss, ls);
                    });
        }

        private void markDirty() {
            this.dirty = true;
        }

        @Override
        public UUID getUuid() {
            return uuid;
        }

        @Override
        public Map<String, Location> getHomes() {
            Map<String, Location> locations = new HashMap<>();
            for (Map.Entry<String, Home> entry : homesDetailed.entrySet()) {
                Location loc = entry.getValue().getLocation();
                if (loc != null) {
                    locations.put(entry.getKey(), loc);
                }
            }
            return locations;
        }

        @Override
        public void setHome(String name, Location location) {
            setHome(name, location, Material.RED_BED, null);
        }

        @Override
        public void setHome(String name, Location location, Material icon, String description) {
            String key = name.toLowerCase();
            Home home = new Home(key, location, icon, description, System.currentTimeMillis());
            homesDetailed.put(key, home);
            // Save async to avoid blocking the main thread
            CompletableFuture.runAsync(
                    () -> repository.saveHomeWithDetails(uuid, key, location, icon, description));
        }

        @Override
        public void removeHome(String name) {
            homesDetailed.remove(name.toLowerCase());
            // Delete async to avoid blocking the main thread
            CompletableFuture.runAsync(() -> repository.deleteHome(uuid, name.toLowerCase()));
        }

        @Override
        public Location getHome(String name) {
            Home home = homesDetailed.get(name.toLowerCase());
            return home != null ? home.getLocation() : null;
        }

        @Override
        public boolean hasHome(String name) {
            return homesDetailed.containsKey(name.toLowerCase());
        }

        @Override
        public int getHomeCount() {
            return homesDetailed.size();
        }

        @Override
        public Map<String, Home> getHomesWithDetails() {
            return new HashMap<>(homesDetailed);
        }

        @Override
        public Home getHomeDetails(String name) {
            return homesDetailed.get(name.toLowerCase());
        }

        @Override
        public void renameHome(String oldName, String newName) {
            String oldKey = oldName.toLowerCase();
            String newKey = newName.toLowerCase();
            Home home = homesDetailed.remove(oldKey);
            if (home != null) {
                home.setName(newKey);
                homesDetailed.put(newKey, home);
                CompletableFuture.runAsync(() -> repository.renameHome(uuid, oldKey, newKey));
            }
        }

        @Override
        public void updateHomeIcon(String name, Material icon) {
            String key = name.toLowerCase();
            Home home = homesDetailed.get(key);
            if (home != null) {
                home.setIcon(icon);
                CompletableFuture.runAsync(() -> repository.updateHomeIcon(uuid, key, icon));
            }
        }

        @Override
        public void updateHomeDescription(String name, String description) {
            String key = name.toLowerCase();
            Home home = homesDetailed.get(key);
            if (home != null) {
                home.setDescription(description);
                CompletableFuture.runAsync(
                        () -> repository.updateHomeDescription(uuid, key, description));
            }
        }

        @Override
        public Location getLastLocation() {
            return lastLocation;
        }

        @Override
        public void setLastLocation(Location location) {
            this.lastLocation = location;
            markDirty();
        }

        @Override
        public long getPlaytime() {
            return playtime;
        }

        @Override
        public void addPlaytime(long time) {
            this.playtime += time;
            markDirty();
        }

        @Override
        public long getLastSeen() {
            return lastSeen;
        }

        @Override
        public void setLastSeen(long time) {
            this.lastSeen = time;
            markDirty();
        }

        @Override
        public long getSessionStart() {
            return sessionStart;
        }

        @Override
        public void setSessionStart(long time) {
            this.sessionStart = time;
            markDirty();
        }

        @Override
        public int getWarnings() {
            return warnings;
        }

        @Override
        public void addWarning() {
            this.warnings++;
            markDirty();
        }

        @Override
        public void setWarnings(int warnings) {
            this.warnings = warnings;
            markDirty();
        }

        @Override
        public void clearWarnings() {
            this.warnings = 0;
            markDirty();
        }

        @Override
        public boolean isFlyEnabled() {
            return flyEnabled;
        }

        @Override
        public void setFlyEnabled(boolean enabled) {
            this.flyEnabled = enabled;
            markDirty();
        }

        @Override
        public boolean isGodMode() {
            return godMode;
        }

        @Override
        public void setGodMode(boolean enabled) {
            this.godMode = enabled;
            markDirty();
        }
    }

    public static class FilePlayerData implements PlayerData {
        private final UUID uuid;
        private final YamlConfiguration config;
        private final File file;
        private final Map<String, Home> homesDetailed = new HashMap<>();
        private Location lastLocation;
        private long playtime;
        private long lastSeen;
        private long sessionStart;
        private int warnings;
        private boolean flyEnabled;
        private boolean godMode;

        public FilePlayerData(UUID uuid, YamlConfiguration config, File file) {
            this.uuid = uuid;
            this.config = config;
            this.file = file;
            loadData();
        }

        private void loadData() {
            if (config.contains("homes")) {
                var homesSection = config.getConfigurationSection("homes");
                if (homesSection != null) {
                    for (String key : homesSection.getKeys(false)) {
                        String path = "homes." + key;
                        String worldName = config.getString(path + ".world");
                        if (worldName != null && Bukkit.getWorld(worldName) != null) {
                            String iconStr = config.getString(path + ".icon", "RED_BED");
                            Material icon = Material.RED_BED;
                            try {
                                icon = Material.valueOf(iconStr);
                            } catch (IllegalArgumentException ignored) {
                            }

                            String description = config.getString(path + ".description");
                            long createdAt =
                                    config.getLong(
                                            path + ".created_at", System.currentTimeMillis());

                            Home home =
                                    new Home(
                                            key,
                                            worldName,
                                            config.getDouble(path + ".x"),
                                            config.getDouble(path + ".y"),
                                            config.getDouble(path + ".z"),
                                            (float) config.getDouble(path + ".yaw"),
                                            (float) config.getDouble(path + ".pitch"),
                                            icon,
                                            description,
                                            createdAt);
                            homesDetailed.put(key, home);
                        }
                    }
                }
            }

            if (config.contains("lastLocation")) {
                String worldName = config.getString("lastLocation.world");
                if (worldName != null && Bukkit.getWorld(worldName) != null) {
                    lastLocation =
                            deserializeLocation(
                                    worldName,
                                    config.getDouble("lastLocation.x"),
                                    config.getDouble("lastLocation.y"),
                                    config.getDouble("lastLocation.z"),
                                    (float) config.getDouble("lastLocation.yaw"),
                                    (float) config.getDouble("lastLocation.pitch"));
                }
            }

            playtime = config.getLong("playtime", 0);
            lastSeen = config.getLong("lastSeen", System.currentTimeMillis());
            sessionStart = config.getLong("sessionStart", 0);
            warnings = config.getInt("warnings", 0);
            flyEnabled = config.getBoolean("flyEnabled", false);
            godMode = config.getBoolean("godMode", false);
        }

        @Override
        public void save() {
            config.set("homes", null);
            for (Map.Entry<String, Home> entry : homesDetailed.entrySet()) {
                String path = "homes." + entry.getKey();
                Home home = entry.getValue();
                config.set(path + ".world", home.getWorldName());
                config.set(path + ".x", home.getX());
                config.set(path + ".y", home.getY());
                config.set(path + ".z", home.getZ());
                config.set(path + ".yaw", home.getYaw());
                config.set(path + ".pitch", home.getPitch());
                config.set(path + ".icon", home.getIcon().name());
                config.set(path + ".description", home.getDescription());
                config.set(path + ".created_at", home.getCreatedAt());
            }

            if (lastLocation != null && lastLocation.getWorld() != null) {
                config.set("lastLocation.world", lastLocation.getWorld().getName());
                config.set("lastLocation.x", lastLocation.getX());
                config.set("lastLocation.y", lastLocation.getY());
                config.set("lastLocation.z", lastLocation.getZ());
                config.set("lastLocation.yaw", lastLocation.getYaw());
                config.set("lastLocation.pitch", lastLocation.getPitch());
            }

            config.set("playtime", playtime);
            config.set("lastSeen", lastSeen);
            config.set("sessionStart", sessionStart);
            config.set("warnings", warnings);
            config.set("flyEnabled", flyEnabled);
            config.set("godMode", godMode);

            try {
                config.save(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private Location deserializeLocation(
                String world, double x, double y, double z, float yaw, float pitch) {
            return new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
        }

        @Override
        public UUID getUuid() {
            return uuid;
        }

        @Override
        public Map<String, Location> getHomes() {
            Map<String, Location> locations = new HashMap<>();
            for (Map.Entry<String, Home> entry : homesDetailed.entrySet()) {
                Location loc = entry.getValue().getLocation();
                if (loc != null) {
                    locations.put(entry.getKey(), loc);
                }
            }
            return locations;
        }

        @Override
        public void setHome(String name, Location location) {
            setHome(name, location, Material.RED_BED, null);
        }

        @Override
        public void setHome(String name, Location location, Material icon, String description) {
            String key = name.toLowerCase();
            homesDetailed.put(
                    key, new Home(key, location, icon, description, System.currentTimeMillis()));
        }

        @Override
        public void removeHome(String name) {
            homesDetailed.remove(name.toLowerCase());
        }

        @Override
        public Location getHome(String name) {
            Home home = homesDetailed.get(name.toLowerCase());
            return home != null ? home.getLocation() : null;
        }

        @Override
        public boolean hasHome(String name) {
            return homesDetailed.containsKey(name.toLowerCase());
        }

        @Override
        public int getHomeCount() {
            return homesDetailed.size();
        }

        @Override
        public Map<String, Home> getHomesWithDetails() {
            return new HashMap<>(homesDetailed);
        }

        @Override
        public Home getHomeDetails(String name) {
            return homesDetailed.get(name.toLowerCase());
        }

        @Override
        public void renameHome(String oldName, String newName) {
            String oldKey = oldName.toLowerCase();
            String newKey = newName.toLowerCase();
            Home home = homesDetailed.remove(oldKey);
            if (home != null) {
                home.setName(newKey);
                homesDetailed.put(newKey, home);
            }
        }

        @Override
        public void updateHomeIcon(String name, Material icon) {
            Home home = homesDetailed.get(name.toLowerCase());
            if (home != null) {
                home.setIcon(icon);
            }
        }

        @Override
        public void updateHomeDescription(String name, String description) {
            Home home = homesDetailed.get(name.toLowerCase());
            if (home != null) {
                home.setDescription(description);
            }
        }

        @Override
        public Location getLastLocation() {
            return lastLocation;
        }

        @Override
        public void setLastLocation(Location location) {
            this.lastLocation = location;
        }

        @Override
        public long getPlaytime() {
            return playtime;
        }

        @Override
        public void addPlaytime(long time) {
            this.playtime += time;
        }

        @Override
        public long getLastSeen() {
            return lastSeen;
        }

        @Override
        public void setLastSeen(long time) {
            this.lastSeen = time;
        }

        @Override
        public long getSessionStart() {
            return sessionStart;
        }

        @Override
        public void setSessionStart(long time) {
            this.sessionStart = time;
        }

        @Override
        public int getWarnings() {
            return warnings;
        }

        @Override
        public void addWarning() {
            this.warnings++;
        }

        @Override
        public void setWarnings(int warnings) {
            this.warnings = warnings;
        }

        @Override
        public void clearWarnings() {
            this.warnings = 0;
        }

        @Override
        public boolean isFlyEnabled() {
            return flyEnabled;
        }

        @Override
        public void setFlyEnabled(boolean enabled) {
            this.flyEnabled = enabled;
        }

        @Override
        public boolean isGodMode() {
            return godMode;
        }

        @Override
        public void setGodMode(boolean enabled) {
            this.godMode = enabled;
        }
    }

    /**
     * Placeholder data returned when real data is loading asynchronously. Returns safe defaults and
     * no-ops for setters.
     */
    public static class PlaceholderPlayerData implements PlayerData {
        private final UUID uuid;
        private long sessionStart;

        public PlaceholderPlayerData(UUID uuid) {
            this.uuid = uuid;
        }

        @Override
        public UUID getUuid() {
            return uuid;
        }

        @Override
        public Map<String, Location> getHomes() {
            return Collections.emptyMap();
        }

        @Override
        public void setHome(String name, Location location) {}

        @Override
        public void setHome(String name, Location location, Material icon, String description) {}

        @Override
        public void removeHome(String name) {}

        @Override
        public Location getHome(String name) {
            return null;
        }

        @Override
        public boolean hasHome(String name) {
            return false;
        }

        @Override
        public int getHomeCount() {
            return 0;
        }

        @Override
        public Map<String, Home> getHomesWithDetails() {
            return Collections.emptyMap();
        }

        @Override
        public Home getHomeDetails(String name) {
            return null;
        }

        @Override
        public void renameHome(String oldName, String newName) {}

        @Override
        public void updateHomeIcon(String name, Material icon) {}

        @Override
        public void updateHomeDescription(String name, String description) {}

        @Override
        public Location getLastLocation() {
            return null;
        }

        @Override
        public void setLastLocation(Location location) {}

        @Override
        public long getPlaytime() {
            return 0;
        }

        @Override
        public void addPlaytime(long time) {}

        @Override
        public long getLastSeen() {
            return System.currentTimeMillis();
        }

        @Override
        public void setLastSeen(long time) {}

        @Override
        public long getSessionStart() {
            return sessionStart;
        }

        @Override
        public void setSessionStart(long time) {
            this.sessionStart = time;
        }

        @Override
        public int getWarnings() {
            return 0;
        }

        @Override
        public void addWarning() {}

        @Override
        public void setWarnings(int warnings) {}

        @Override
        public void clearWarnings() {}

        @Override
        public boolean isFlyEnabled() {
            return false;
        }

        @Override
        public void setFlyEnabled(boolean enabled) {}

        @Override
        public boolean isGodMode() {
            return false;
        }

        @Override
        public void setGodMode(boolean enabled) {}

        @Override
        public void save() {}
    }
}
