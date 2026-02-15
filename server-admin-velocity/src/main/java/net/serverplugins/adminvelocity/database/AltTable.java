package net.serverplugins.adminvelocity.database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;

/**
 * Database access layer for player IP tracking and alt detection.
 *
 * <p>Provides async methods for recording and querying IP addresses in the server_player_ips table.
 */
public class AltTable {

    private final AdminDatabase database;
    private final Logger logger;

    public AltTable(AdminDatabase database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    /**
     * Records or updates a player's IP hash.
     *
     * @param uuid the player's UUID
     * @param username the player's username
     * @param ipHash the hashed IP address
     * @return a CompletableFuture that completes when the operation is done
     */
    public CompletableFuture<Void> recordIp(UUID uuid, String username, String ipHash) {
        return database.executeAsync(
                conn -> {
                    String sql =
                            "INSERT INTO server_player_ips (uuid, username, ip_hash, first_seen, last_seen) "
                                    + "VALUES (?, ?, ?, NOW(), NOW()) "
                                    + "ON DUPLICATE KEY UPDATE username = ?, last_seen = NOW()";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, uuid.toString());
                        stmt.setString(2, username);
                        stmt.setString(3, ipHash);
                        stmt.setString(4, username);

                        stmt.executeUpdate();
                        logger.debug("Recorded IP for player {} ({})", username, uuid);
                    }
                    return null;
                });
    }

    /**
     * Gets all alt accounts associated with a player (based on shared IP addresses).
     *
     * @param uuid the player's UUID
     * @return a CompletableFuture containing a map of UUID to username for alt accounts
     */
    public CompletableFuture<Map<UUID, String>> getAlts(UUID uuid) {
        return database.executeAsync(
                conn -> {
                    Map<UUID, String> alts = new HashMap<>();

                    // First, get all IP hashes for this player
                    List<String> ipHashes = new ArrayList<>();
                    String ipSql = "SELECT DISTINCT ip_hash FROM server_player_ips WHERE uuid = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(ipSql)) {
                        stmt.setString(1, uuid.toString());
                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                ipHashes.add(rs.getString("ip_hash"));
                            }
                        }
                    }

                    // For each IP hash, find other UUIDs
                    if (!ipHashes.isEmpty()) {
                        String altSql =
                                "SELECT DISTINCT uuid, username FROM server_player_ips WHERE ip_hash = ? AND uuid != ?";
                        for (String ipHash : ipHashes) {
                            try (PreparedStatement stmt = conn.prepareStatement(altSql)) {
                                stmt.setString(1, ipHash);
                                stmt.setString(2, uuid.toString());
                                try (ResultSet rs = stmt.executeQuery()) {
                                    while (rs.next()) {
                                        UUID altUuid = UUID.fromString(rs.getString("uuid"));
                                        String altUsername = rs.getString("username");
                                        alts.put(altUuid, altUsername);
                                    }
                                }
                            }
                        }
                    }

                    return alts;
                });
    }

    /**
     * Gets all accounts associated with a specific IP hash.
     *
     * @param ipHash the hashed IP address
     * @return a CompletableFuture containing a map of UUID to username
     */
    public CompletableFuture<Map<UUID, String>> getAltsByIpHash(String ipHash) {
        return database.executeAsync(
                conn -> {
                    Map<UUID, String> accounts = new HashMap<>();
                    String sql =
                            "SELECT DISTINCT uuid, username FROM server_player_ips WHERE ip_hash = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, ipHash);
                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                UUID uuid = UUID.fromString(rs.getString("uuid"));
                                String username = rs.getString("username");
                                accounts.put(uuid, username);
                            }
                        }
                    }
                    return accounts;
                });
    }

    /**
     * Gets all known IP hashes for a player.
     *
     * @param uuid the player's UUID
     * @return a CompletableFuture containing a list of IP hashes
     */
    public CompletableFuture<List<String>> getIpHashes(UUID uuid) {
        return database.executeAsync(
                conn -> {
                    List<String> ipHashes = new ArrayList<>();
                    String sql = "SELECT DISTINCT ip_hash FROM server_player_ips WHERE uuid = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, uuid.toString());
                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                ipHashes.add(rs.getString("ip_hash"));
                            }
                        }
                    }
                    return ipHashes;
                });
    }

    /**
     * Gets the total number of accounts associated with an IP hash.
     *
     * @param ipHash the hashed IP address
     * @return a CompletableFuture containing the account count
     */
    public CompletableFuture<Integer> getAccountCount(String ipHash) {
        return database.executeAsync(
                conn -> {
                    String sql =
                            "SELECT COUNT(DISTINCT uuid) as count FROM server_player_ips WHERE ip_hash = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, ipHash);
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                return rs.getInt("count");
                            }
                        }
                    }
                    return 0;
                });
    }
}
