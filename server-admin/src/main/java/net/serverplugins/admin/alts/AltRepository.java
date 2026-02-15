package net.serverplugins.admin.alts;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import net.serverplugins.api.database.Database;

/**
 * Database access layer for the server_player_ips table. Manages IP hash tracking for alt account
 * detection.
 */
public class AltRepository {

    private final Database database;
    private final Logger logger;

    public AltRepository(Database database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    /**
     * Record a player's IP hash. Uses ON DUPLICATE KEY UPDATE to maintain last_seen timestamp.
     *
     * @param uuid Player UUID
     * @param username Player name
     * @param ipHash SHA-256 hash of IP address
     * @return CompletableFuture that completes when the operation finishes
     */
    public CompletableFuture<Void> recordIp(UUID uuid, String username, String ipHash) {
        String sql =
                "INSERT INTO server_player_ips (uuid, username, ip_hash, first_seen, last_seen) "
                        + "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
                        + "ON DUPLICATE KEY UPDATE username = VALUES(username), last_seen ="
                        + " CURRENT_TIMESTAMP";

        return database.executeUpdateAsync(sql, uuid.toString(), username, ipHash)
                .thenAccept(
                        rows ->
                                logger.fine(
                                        "Recorded IP for "
                                                + username
                                                + " ("
                                                + uuid
                                                + "): "
                                                + ipHash));
    }

    /**
     * Get all alt accounts for a player. Finds all UUIDs that share at least one IP hash with the
     * given player.
     *
     * @param uuid Player UUID
     * @return CompletableFuture containing list of alt accounts
     */
    public CompletableFuture<List<AltAccount>> getAltsByUuid(UUID uuid) {
        String sql =
                "SELECT DISTINCT p2.uuid, p2.username, p2.ip_hash, p2.first_seen, p2.last_seen "
                        + "FROM server_player_ips p1 "
                        + "JOIN server_player_ips p2 ON p1.ip_hash = p2.ip_hash "
                        + "WHERE p1.uuid = ? AND p2.uuid != ?";

        return database.executeQueryAsync(sql, uuid.toString(), uuid.toString())
                .thenApply(
                        rs -> {
                            List<AltAccount> alts = new ArrayList<>();
                            try {
                                while (rs.next()) {
                                    UUID altUuid = UUID.fromString(rs.getString("uuid"));
                                    String username = rs.getString("username");
                                    Timestamp lastSeenTimestamp = rs.getTimestamp("last_seen");
                                    long lastSeen =
                                            lastSeenTimestamp != null
                                                    ? lastSeenTimestamp.getTime()
                                                    : 0;

                                    // Note: online and banned status must be determined by the
                                    // caller
                                    // This repository only provides the database data
                                    alts.add(
                                            new AltAccount(
                                                    altUuid, username, lastSeen, false, false));
                                }
                            } catch (SQLException e) {
                                logger.warning(
                                        "Failed to fetch alts for " + uuid + ": " + e.getMessage());
                            } finally {
                                try {
                                    rs.close();
                                } catch (SQLException e) {
                                    logger.warning("Failed to close result set: " + e.getMessage());
                                }
                            }
                            return alts;
                        });
    }

    /**
     * Get all UUIDs that have used a specific IP hash.
     *
     * @param ipHash SHA-256 hash of IP address
     * @return CompletableFuture containing list of alt accounts
     */
    public CompletableFuture<List<AltAccount>> getAltsByIpHash(String ipHash) {
        String sql =
                "SELECT uuid, username, ip_hash, first_seen, last_seen FROM server_player_ips"
                        + " WHERE ip_hash = ?";

        return database.executeQueryAsync(sql, ipHash)
                .thenApply(
                        rs -> {
                            List<AltAccount> alts = new ArrayList<>();
                            try {
                                while (rs.next()) {
                                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                                    String username = rs.getString("username");
                                    Timestamp lastSeenTimestamp = rs.getTimestamp("last_seen");
                                    long lastSeen =
                                            lastSeenTimestamp != null
                                                    ? lastSeenTimestamp.getTime()
                                                    : 0;

                                    alts.add(
                                            new AltAccount(uuid, username, lastSeen, false, false));
                                }
                            } catch (SQLException e) {
                                logger.warning(
                                        "Failed to fetch alts for IP hash "
                                                + ipHash
                                                + ": "
                                                + e.getMessage());
                            } finally {
                                try {
                                    rs.close();
                                } catch (SQLException e) {
                                    logger.warning("Failed to close result set: " + e.getMessage());
                                }
                            }
                            return alts;
                        });
    }

    /**
     * Get all known IP hashes for a player.
     *
     * @param uuid Player UUID
     * @return CompletableFuture containing set of IP hashes
     */
    public CompletableFuture<Set<String>> getIpHashes(UUID uuid) {
        String sql = "SELECT ip_hash FROM server_player_ips WHERE uuid = ?";

        return database.executeQueryAsync(sql, uuid.toString())
                .thenApply(
                        rs -> {
                            Set<String> hashes = new HashSet<>();
                            try {
                                while (rs.next()) {
                                    hashes.add(rs.getString("ip_hash"));
                                }
                            } catch (SQLException e) {
                                logger.warning(
                                        "Failed to fetch IP hashes for "
                                                + uuid
                                                + ": "
                                                + e.getMessage());
                            } finally {
                                try {
                                    rs.close();
                                } catch (SQLException e) {
                                    logger.warning("Failed to close result set: " + e.getMessage());
                                }
                            }
                            return hashes;
                        });
    }

    /**
     * Migrate data from the flat-file players.yml format to the database. This is a one-time
     * operation.
     *
     * @param flatFileData Map structure matching the players.yml format
     * @return CompletableFuture that completes when migration finishes
     */
    public CompletableFuture<Integer> migrateFromFlatFile(
            Map<String, Map<String, Object>> flatFileData) {
        return CompletableFuture.supplyAsync(
                () -> {
                    int migratedCount = 0;
                    logger.info(
                            "Starting migration of "
                                    + flatFileData.size()
                                    + " players from flat file...");

                    for (Map.Entry<String, Map<String, Object>> entry : flatFileData.entrySet()) {
                        try {
                            String uuidStr = entry.getKey();
                            Map<String, Object> playerData = entry.getValue();

                            UUID uuid = UUID.fromString(uuidStr);
                            String ipHash = (String) playerData.get("ip");
                            String username = (String) playerData.get("name");

                            if (ipHash == null || ipHash.isEmpty()) {
                                logger.warning(
                                        "Skipping player "
                                                + username
                                                + " ("
                                                + uuidStr
                                                + ") - no IP hash");
                                continue;
                            }

                            // Get timestamps if available
                            Object lastSeenObj = playerData.get("lastSeen");
                            long lastSeen =
                                    lastSeenObj instanceof Number
                                            ? ((Number) lastSeenObj).longValue()
                                            : System.currentTimeMillis();

                            // Insert with explicit timestamps
                            String sql =
                                    "INSERT INTO server_player_ips (uuid, username, ip_hash,"
                                            + " first_seen, last_seen) "
                                            + "VALUES (?, ?, ?, FROM_UNIXTIME(?), FROM_UNIXTIME(?)) "
                                            + "ON DUPLICATE KEY UPDATE username = VALUES(username),"
                                            + " last_seen = VALUES(last_seen)";

                            database.executeUpdate(
                                    sql,
                                    uuid.toString(),
                                    username,
                                    ipHash,
                                    lastSeen / 1000,
                                    lastSeen / 1000);
                            migratedCount++;

                        } catch (Exception e) {
                            logger.warning(
                                    "Failed to migrate player "
                                            + entry.getKey()
                                            + ": "
                                            + e.getMessage());
                        }
                    }

                    logger.info(
                            "Migration complete! Migrated " + migratedCount + " player records.");
                    return migratedCount;
                });
    }

    /**
     * Get all player IP records (for migration verification).
     *
     * @return Map of UUID string to player data
     */
    public Map<String, Map<String, Object>> getAllRecords() {
        Map<String, Map<String, Object>> records = new HashMap<>();

        String sql = "SELECT uuid, username, ip_hash, first_seen, last_seen FROM server_player_ips";

        try {
            database.executeQueryWithConsumer(
                    sql,
                    rs -> {
                        try {
                            while (rs.next()) {
                                String uuidStr = rs.getString("uuid");
                                Map<String, Object> data = new HashMap<>();
                                data.put("name", rs.getString("username"));
                                data.put("ip", rs.getString("ip_hash"));

                                Timestamp lastSeenTimestamp = rs.getTimestamp("last_seen");
                                if (lastSeenTimestamp != null) {
                                    data.put("lastSeen", lastSeenTimestamp.getTime());
                                }

                                records.put(uuidStr, data);
                            }
                        } catch (SQLException e) {
                            logger.warning("Failed to read records: " + e.getMessage());
                        }
                    });
        } catch (SQLException e) {
            logger.warning("Failed to get all records: " + e.getMessage());
        }

        return records;
    }
}
