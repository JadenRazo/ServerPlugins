package net.serverplugins.admin.alts;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.api.database.Database;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class AltManager {

    private final ServerAdmin plugin;
    private final AltRepository altRepository;
    private final File dataFile;
    private final File migrationMarker;
    private boolean databaseAvailable;

    /**
     * Constructor that automatically gets the database from ServerAPI. This maintains backward
     * compatibility with the existing ServerAdmin initialization.
     *
     * @param plugin ServerAdmin plugin instance
     */
    public AltManager(ServerAdmin plugin) {
        this(plugin, getDatabaseFromAPI(plugin));
    }

    /**
     * Constructor with explicit database parameter. Used for testing or when database is already
     * available.
     *
     * @param plugin ServerAdmin plugin instance
     * @param database Database instance
     */
    public AltManager(ServerAdmin plugin, Database database) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "players.yml");
        this.migrationMarker = new File(plugin.getDataFolder(), ".alt-migrated");

        // Initialize repository if database is available
        if (database != null && database.isConnected()) {
            this.altRepository = new AltRepository(database, plugin.getLogger());
            this.databaseAvailable = true;
            plugin.getLogger().info("Alt detection using database backend");

            // Run migration if needed
            if (!migrationMarker.exists() && dataFile.exists()) {
                migrateFromFlatFile();
            }
        } else {
            this.altRepository = null;
            this.databaseAvailable = false;
            plugin.getLogger()
                    .warning(
                            "Database not available - alt detection disabled. Install ServerAPI"
                                    + " with database support.");
        }
    }

    /**
     * Get the database instance from ServerAPI if available.
     *
     * @param plugin ServerAdmin plugin instance
     * @return Database instance or null if not available
     */
    private static Database getDatabaseFromAPI(ServerAdmin plugin) {
        try {
            net.serverplugins.api.ServerAPI api = net.serverplugins.api.ServerAPI.getInstance();
            if (api != null && api.getDatabase() != null) {
                return api.getDatabase();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get database from ServerAPI: " + e.getMessage());
        }
        return null;
    }

    /**
     * Record a player's IP hash in the database.
     *
     * @param player Player to record
     */
    public void recordPlayer(Player player) {
        if (!databaseAvailable) {
            return;
        }

        String ip = getPlayerIp(player);
        if (ip == null) return;

        String ipHash = hashIp(ip);
        UUID uuid = player.getUniqueId();

        // Record async
        altRepository
                .recordIp(uuid, player.getName(), ipHash)
                .exceptionally(
                        ex -> {
                            plugin.getLogger()
                                    .warning(
                                            "Failed to record IP for "
                                                    + player.getName()
                                                    + ": "
                                                    + ex.getMessage());
                            return null;
                        });
    }

    /**
     * Get all alt accounts for a player UUID.
     *
     * @param playerId Player UUID
     * @return CompletableFuture containing list of alt accounts
     */
    public CompletableFuture<List<AltAccount>> getAlts(UUID playerId) {
        if (!databaseAvailable) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        return altRepository
                .getAltsByUuid(playerId)
                .thenApply(
                        alts -> {
                            // Enhance with online/banned status from Bukkit
                            for (int i = 0; i < alts.size(); i++) {
                                AltAccount alt = alts.get(i);
                                boolean online = Bukkit.getPlayer(alt.getUuid()) != null;
                                boolean banned =
                                        Bukkit.getBanList(org.bukkit.BanList.Type.NAME)
                                                .isBanned(alt.getName());

                                // Replace with enhanced version
                                alts.set(
                                        i,
                                        new AltAccount(
                                                alt.getUuid(),
                                                alt.getName(),
                                                alt.getIpHash(),
                                                alt.getFirstSeen(),
                                                alt.getLastSeen(),
                                                online,
                                                banned));
                            }
                            return alts;
                        })
                .exceptionally(
                        ex -> {
                            plugin.getLogger().warning("Failed to fetch alts: " + ex.getMessage());
                            return new ArrayList<>();
                        });
    }

    /**
     * Get alt accounts by player name.
     *
     * @param playerName Player name
     * @return CompletableFuture containing list of alt accounts
     */
    public CompletableFuture<List<AltAccount>> getAltsByName(String playerName) {
        // Try to find UUID by name
        @SuppressWarnings("deprecation")
        org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);

        if (offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline()) {
            return getAlts(offlinePlayer.getUniqueId());
        }

        // If not found, return empty list
        return CompletableFuture.completedFuture(new ArrayList<>());
    }

    /**
     * Get count of alt accounts for a player.
     *
     * @param playerId Player UUID
     * @return CompletableFuture containing alt count
     */
    public CompletableFuture<Integer> getAltCount(UUID playerId) {
        return getAlts(playerId).thenApply(List::size);
    }

    /**
     * Check if any of a player's alt accounts have active bans. This method queries the punishment
     * system to determine if any alts are banned.
     *
     * @param playerId Player UUID to check
     * @return CompletableFuture containing true if any alt is banned
     */
    public CompletableFuture<Boolean> checkForBannedAlts(UUID playerId) {
        if (!databaseAvailable || plugin.getPunishmentManager() == null) {
            return CompletableFuture.completedFuture(false);
        }

        return getAlts(playerId)
                .thenApply(
                        alts -> {
                            for (AltAccount alt : alts) {
                                if (alt.isBanned()) {
                                    return true;
                                }
                            }
                            return false;
                        });
    }

    /**
     * Hash an IP address using SHA-256. This is the same algorithm used by the Velocity plugin for
     * cross-platform consistency.
     *
     * @param ip IP address string
     * @return SHA-256 hash as hex string
     */
    public String hashIp(String ip) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(ip.getBytes(StandardCharsets.UTF_8));

            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // Fallback to simple hash if SHA-256 not available
            return String.valueOf(ip.hashCode());
        }
    }

    /**
     * Get the last known name for a UUID.
     *
     * @param uuid Player UUID
     * @return Player name or "Unknown"
     */
    public String getLastKnownName(UUID uuid) {
        // Try online players first
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            return player.getName();
        }

        // Try offline player cache
        org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        String name = offlinePlayer.getName();
        return name != null ? name : "Unknown";
    }

    /**
     * Migrate data from players.yml to the database. This is a one-time operation that runs on
     * first startup after the database is enabled.
     */
    private void migrateFromFlatFile() {
        plugin.getLogger().info("Starting alt detection migration from players.yml to database...");

        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            try {
                                if (!dataFile.exists()) {
                                    plugin.getLogger()
                                            .info(
                                                    "No players.yml found - skipping migration"
                                                            + " (fresh install)");
                                    createMigrationMarker();
                                    return;
                                }

                                FileConfiguration dataConfig =
                                        YamlConfiguration.loadConfiguration(dataFile);

                                if (!dataConfig.contains("players")) {
                                    plugin.getLogger()
                                            .info(
                                                    "players.yml is empty - skipping migration"
                                                            + " (fresh install)");
                                    createMigrationMarker();
                                    return;
                                }

                                // Convert YAML structure to map
                                Map<String, Map<String, Object>> flatFileData = new HashMap<>();
                                for (String uuidStr :
                                        dataConfig
                                                .getConfigurationSection("players")
                                                .getKeys(false)) {
                                    Map<String, Object> playerData = new HashMap<>();
                                    playerData.put(
                                            "ip",
                                            dataConfig.getString("players." + uuidStr + ".ip"));
                                    playerData.put(
                                            "name",
                                            dataConfig.getString("players." + uuidStr + ".name"));
                                    playerData.put(
                                            "lastSeen",
                                            dataConfig.getLong("players." + uuidStr + ".lastSeen"));
                                    flatFileData.put(uuidStr, playerData);
                                }

                                // Migrate to database
                                altRepository
                                        .migrateFromFlatFile(flatFileData)
                                        .thenAccept(
                                                count -> {
                                                    plugin.getLogger()
                                                            .info(
                                                                    "Successfully migrated "
                                                                            + count
                                                                            + " player records to"
                                                                            + " database");

                                                    // Backup the old file
                                                    File backup =
                                                            new File(
                                                                    plugin.getDataFolder(),
                                                                    "players.yml.backup");
                                                    if (dataFile.renameTo(backup)) {
                                                        plugin.getLogger()
                                                                .info(
                                                                        "Original players.yml backed"
                                                                                + " up to"
                                                                                + " players.yml.backup");
                                                    }

                                                    createMigrationMarker();
                                                })
                                        .exceptionally(
                                                ex -> {
                                                    plugin.getLogger()
                                                            .severe(
                                                                    "Migration failed: "
                                                                            + ex.getMessage());
                                                    ex.printStackTrace();
                                                    return null;
                                                });

                            } catch (Exception e) {
                                plugin.getLogger()
                                        .severe(
                                                "Failed to read players.yml for migration: "
                                                        + e.getMessage());
                                e.printStackTrace();
                            }
                        });
    }

    private void createMigrationMarker() {
        try {
            if (migrationMarker.createNewFile()) {
                plugin.getLogger().info("Migration marker created - migration complete");
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to create migration marker: " + e.getMessage());
        }
    }

    private String getPlayerIp(Player player) {
        if (player.getAddress() == null) {
            return null;
        }
        return player.getAddress().getAddress().getHostAddress();
    }

    public boolean isDatabaseAvailable() {
        return databaseAvailable;
    }
}
