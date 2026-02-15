package net.serverplugins.events.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.logging.Logger;
import net.serverplugins.api.database.Database;

public class EventsRepository {

    private final Database database;
    private final Logger logger;

    public EventsRepository(Database database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    // ==================== PLAYER STATS ====================

    public static class PlayerStats {
        public final UUID uuid;
        public final String name;
        public final int wins;
        public final int participations;
        public final long coinsEarned;
        public final int keysEarned;

        public PlayerStats(
                UUID uuid,
                String name,
                int wins,
                int participations,
                long coinsEarned,
                int keysEarned) {
            this.uuid = uuid;
            this.name = name;
            this.wins = wins;
            this.participations = participations;
            this.coinsEarned = coinsEarned;
            this.keysEarned = keysEarned;
        }
    }

    public Map<UUID, PlayerStats> getAllPlayerStats() {
        Map<UUID, PlayerStats> stats = new HashMap<>();
        try {
            ResultSet rs =
                    database.executeQuery(
                            "SELECT uuid, username, wins, participations, coins_earned, keys_earned FROM server_event_stats");
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                stats.put(
                        uuid,
                        new PlayerStats(
                                uuid,
                                rs.getString("username"),
                                rs.getInt("wins"),
                                rs.getInt("participations"),
                                rs.getLong("coins_earned"),
                                rs.getInt("keys_earned")));
            }
            rs.close();
        } catch (SQLException e) {
            logger.warning("Failed to load player stats: " + e.getMessage());
        }
        return stats;
    }

    public PlayerStats getPlayerStats(UUID uuid) {
        try {
            ResultSet rs =
                    database.executeQuery(
                            "SELECT uuid, username, wins, participations, coins_earned, keys_earned FROM server_event_stats WHERE uuid = ?",
                            uuid.toString());
            if (rs.next()) {
                PlayerStats stats =
                        new PlayerStats(
                                uuid,
                                rs.getString("username"),
                                rs.getInt("wins"),
                                rs.getInt("participations"),
                                rs.getLong("coins_earned"),
                                rs.getInt("keys_earned"));
                rs.close();
                return stats;
            }
            rs.close();
        } catch (SQLException e) {
            logger.warning("Failed to load stats for " + uuid + ": " + e.getMessage());
        }
        return null;
    }

    public void savePlayerStats(
            UUID uuid,
            String name,
            int wins,
            int participations,
            long coinsEarned,
            int keysEarned) {
        try {
            database.executeUpdate(
                    "INSERT INTO server_event_stats (uuid, username, wins, participations, coins_earned, keys_earned) "
                            + "VALUES (?, ?, ?, ?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE username = VALUES(username), wins = VALUES(wins), "
                            + "participations = VALUES(participations), coins_earned = VALUES(coins_earned), keys_earned = VALUES(keys_earned)",
                    uuid.toString(),
                    name,
                    wins,
                    participations,
                    coinsEarned,
                    keysEarned);
        } catch (SQLException e) {
            logger.warning("Failed to save stats for " + uuid + ": " + e.getMessage());
        }
    }

    public List<PlayerStats> getTopWinners(int limit) {
        List<PlayerStats> top = new ArrayList<>();
        try {
            ResultSet rs =
                    database.executeQuery(
                            "SELECT uuid, username, wins, participations, coins_earned, keys_earned "
                                    + "FROM server_event_stats ORDER BY wins DESC LIMIT ?",
                            limit);
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                top.add(
                        new PlayerStats(
                                uuid,
                                rs.getString("username"),
                                rs.getInt("wins"),
                                rs.getInt("participations"),
                                rs.getLong("coins_earned"),
                                rs.getInt("keys_earned")));
            }
            rs.close();
        } catch (SQLException e) {
            logger.warning("Failed to load top winners: " + e.getMessage());
        }
        return top;
    }

    // ==================== KEYALL LOG ====================

    /** Record a keyall distribution to the database. */
    public void recordKeyall(String keyType, String keyName, int amount, int playerCount) {
        try {
            database.executeUpdate(
                    "INSERT INTO server_keyall_log (key_type, key_name, amount, player_count) "
                            + "VALUES (?, ?, ?, ?)",
                    keyType,
                    keyName,
                    amount,
                    playerCount);
        } catch (SQLException e) {
            logger.warning("Failed to record keyall: " + e.getMessage());
        }
    }

    /**
     * Get the timestamp (epoch millis) of the last keyall distribution. Returns 0 if no keyall has
     * ever been recorded.
     */
    public long getLastKeyallTime() {
        try {
            ResultSet rs =
                    database.executeQuery(
                            "SELECT distributed_at FROM server_keyall_log "
                                    + "ORDER BY distributed_at DESC LIMIT 1");
            if (rs.next()) {
                Timestamp ts = rs.getTimestamp("distributed_at");
                rs.close();
                return ts != null ? ts.getTime() : 0;
            }
            rs.close();
        } catch (SQLException e) {
            logger.warning("Failed to query last keyall time: " + e.getMessage());
        }
        return 0;
    }

    // ==================== EVENT HISTORY ====================

    public static class EventRecord {
        public final String eventType;
        public final UUID winnerUuid;
        public final String winnerName;
        public final int participantCount;
        public final long timestamp;

        public EventRecord(
                String eventType,
                UUID winnerUuid,
                String winnerName,
                int participantCount,
                long timestamp) {
            this.eventType = eventType;
            this.winnerUuid = winnerUuid;
            this.winnerName = winnerName;
            this.participantCount = participantCount;
            this.timestamp = timestamp;
        }
    }

    public List<EventRecord> getRecentEvents(int limit) {
        List<EventRecord> events = new ArrayList<>();
        try {
            ResultSet rs =
                    database.executeQuery(
                            "SELECT event_type, winner_uuid, winner_name, participant_count, ended_at "
                                    + "FROM server_event_history ORDER BY ended_at DESC LIMIT ?",
                            limit);
            while (rs.next()) {
                String winnerUuidStr = rs.getString("winner_uuid");
                UUID winnerUuid = winnerUuidStr != null ? UUID.fromString(winnerUuidStr) : null;
                Timestamp endedAt = rs.getTimestamp("ended_at");
                events.add(
                        new EventRecord(
                                rs.getString("event_type"),
                                winnerUuid,
                                rs.getString("winner_name"),
                                rs.getInt("participant_count"),
                                endedAt != null ? endedAt.getTime() / 1000 : 0));
            }
            rs.close();
        } catch (SQLException e) {
            logger.warning("Failed to load event history: " + e.getMessage());
        }
        return events;
    }

    public void recordEvent(
            String eventType, UUID winnerUuid, String winnerName, int participantCount) {
        try {
            database.executeUpdate(
                    "INSERT INTO server_event_history (event_type, winner_uuid, winner_name, participant_count) "
                            + "VALUES (?, ?, ?, ?)",
                    eventType,
                    winnerUuid != null ? winnerUuid.toString() : null,
                    winnerName,
                    participantCount);
        } catch (SQLException e) {
            logger.warning("Failed to record event: " + e.getMessage());
        }
    }
}
