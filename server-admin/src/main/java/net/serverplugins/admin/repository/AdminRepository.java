package net.serverplugins.admin.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Logger;
import net.serverplugins.api.database.Database;

public class AdminRepository {

    private final Database database;
    private final Logger logger;

    public AdminRepository(Database database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    public static class PlayerIpRecord {
        public final UUID uuid;
        public final String ipHash;
        public final String username;
        public final long lastSeen;

        public PlayerIpRecord(UUID uuid, String ipHash, String username, long lastSeen) {
            this.uuid = uuid;
            this.ipHash = ipHash;
            this.username = username;
            this.lastSeen = lastSeen;
        }
    }

    public Map<UUID, String> getAllPlayerIps() {
        Map<UUID, String> result = new HashMap<>();
        try {
            ResultSet rs = database.executeQuery("SELECT uuid, ip_hash FROM server_player_ips");
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String ipHash = rs.getString("ip_hash");
                result.put(uuid, ipHash);
            }
            rs.close();
        } catch (SQLException e) {
            logger.warning("Failed to load player IPs: " + e.getMessage());
        }
        return result;
    }

    public List<PlayerIpRecord> getPlayersByIpHash(String ipHash) {
        List<PlayerIpRecord> players = new ArrayList<>();
        try {
            ResultSet rs =
                    database.executeQuery(
                            "SELECT uuid, ip_hash, username, last_seen FROM server_player_ips WHERE ip_hash = ?",
                            ipHash);
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                players.add(
                        new PlayerIpRecord(
                                uuid,
                                rs.getString("ip_hash"),
                                rs.getString("username"),
                                rs.getTimestamp("last_seen").getTime()));
            }
            rs.close();
        } catch (SQLException e) {
            logger.warning("Failed to load players by IP: " + e.getMessage());
        }
        return players;
    }

    public void savePlayerIp(UUID uuid, String ipHash, String username) {
        try {
            database.executeUpdate(
                    "INSERT INTO server_player_ips (uuid, ip_hash, username) "
                            + "VALUES (?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE username = VALUES(username), last_seen = CURRENT_TIMESTAMP",
                    uuid.toString(),
                    ipHash,
                    username);
        } catch (SQLException e) {
            logger.warning("Failed to save player IP: " + e.getMessage());
        }
    }

    public String getUsername(UUID uuid) {
        try {
            ResultSet rs =
                    database.executeQuery(
                            "SELECT username FROM server_player_ips WHERE uuid = ? LIMIT 1",
                            uuid.toString());
            if (rs.next()) {
                String name = rs.getString("username");
                rs.close();
                return name;
            }
            rs.close();
        } catch (SQLException e) {
            logger.warning("Failed to get username: " + e.getMessage());
        }
        return null;
    }
}
