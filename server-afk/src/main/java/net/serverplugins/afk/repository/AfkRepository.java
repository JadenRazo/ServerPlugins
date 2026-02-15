package net.serverplugins.afk.repository;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.serverplugins.afk.models.AfkSessionRecord;
import net.serverplugins.afk.models.AfkZone;
import net.serverplugins.afk.models.PatternAnalysis;
import net.serverplugins.afk.models.PlayerStats;
import net.serverplugins.afk.models.ZoneReward;
import net.serverplugins.api.database.Database;

public class AfkRepository {

    private final Database database;

    public AfkRepository(Database database) {
        this.database = database;
    }

    // Zone Operations

    public List<AfkZone> getAllZones() {
        List<AfkZone> zones = new ArrayList<>();
        String sql = "SELECT * FROM server_afk_zones";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                AfkZone zone = mapZone(rs);
                zone.setRewards(getRewardsForZone(zone.getId()));
                zones.add(zone);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return zones;
    }

    public Optional<AfkZone> getZoneById(int id) {
        String sql = "SELECT * FROM server_afk_zones WHERE id = ?";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    AfkZone zone = mapZone(rs);
                    zone.setRewards(getRewardsForZone(zone.getId()));
                    return Optional.of(zone);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return Optional.empty();
    }

    public Optional<AfkZone> getZoneByName(String name) {
        String sql = "SELECT * FROM server_afk_zones WHERE name = ?";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, name);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    AfkZone zone = mapZone(rs);
                    zone.setRewards(getRewardsForZone(zone.getId()));
                    return Optional.of(zone);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return Optional.empty();
    }

    public AfkZone createZone(AfkZone zone) {
        String sql =
                "INSERT INTO server_afk_zones (name, world, min_x, min_y, min_z, max_x, max_y, max_z, time_interval_seconds, enabled, use_rank_multipliers, holo_world, holo_x, holo_y, holo_z) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, zone.getName());
            stmt.setString(2, zone.getWorldName());
            stmt.setInt(3, zone.getMinX());
            stmt.setInt(4, zone.getMinY());
            stmt.setInt(5, zone.getMinZ());
            stmt.setInt(6, zone.getMaxX());
            stmt.setInt(7, zone.getMaxY());
            stmt.setInt(8, zone.getMaxZ());
            stmt.setInt(9, zone.getTimeIntervalSeconds());
            stmt.setBoolean(10, zone.isEnabled());
            stmt.setBoolean(11, zone.usesRankMultipliers());
            stmt.setString(12, zone.getHoloWorld());
            if (zone.getHoloX() != null) {
                stmt.setDouble(13, zone.getHoloX());
            } else {
                stmt.setNull(13, java.sql.Types.DOUBLE);
            }
            if (zone.getHoloY() != null) {
                stmt.setDouble(14, zone.getHoloY());
            } else {
                stmt.setNull(14, java.sql.Types.DOUBLE);
            }
            if (zone.getHoloZ() != null) {
                stmt.setDouble(15, zone.getHoloZ());
            } else {
                stmt.setNull(15, java.sql.Types.DOUBLE);
            }

            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    zone.setId(keys.getInt(1));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return zone;
    }

    public void updateZone(AfkZone zone) {
        String sql =
                "UPDATE server_afk_zones SET name = ?, world = ?, min_x = ?, min_y = ?, min_z = ?, max_x = ?, max_y = ?, max_z = ?, time_interval_seconds = ?, enabled = ?, use_rank_multipliers = ?, holo_world = ?, holo_x = ?, holo_y = ?, holo_z = ? WHERE id = ?";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, zone.getName());
            stmt.setString(2, zone.getWorldName());
            stmt.setInt(3, zone.getMinX());
            stmt.setInt(4, zone.getMinY());
            stmt.setInt(5, zone.getMinZ());
            stmt.setInt(6, zone.getMaxX());
            stmt.setInt(7, zone.getMaxY());
            stmt.setInt(8, zone.getMaxZ());
            stmt.setInt(9, zone.getTimeIntervalSeconds());
            stmt.setBoolean(10, zone.isEnabled());
            stmt.setBoolean(11, zone.usesRankMultipliers());
            stmt.setString(12, zone.getHoloWorld());
            if (zone.getHoloX() != null) {
                stmt.setDouble(13, zone.getHoloX());
            } else {
                stmt.setNull(13, java.sql.Types.DOUBLE);
            }
            if (zone.getHoloY() != null) {
                stmt.setDouble(14, zone.getHoloY());
            } else {
                stmt.setNull(14, java.sql.Types.DOUBLE);
            }
            if (zone.getHoloZ() != null) {
                stmt.setDouble(15, zone.getHoloZ());
            } else {
                stmt.setNull(15, java.sql.Types.DOUBLE);
            }
            stmt.setInt(16, zone.getId());

            stmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteZone(int id) {
        // Delete rewards first (in case CASCADE isn't set up)
        String deleteRewardsSql = "DELETE FROM server_afk_rewards WHERE zone_id = ?";
        String deleteZoneSql = "DELETE FROM server_afk_zones WHERE id = ?";

        try (Connection conn = database.getConnection()) {
            // Delete rewards
            try (PreparedStatement stmt = conn.prepareStatement(deleteRewardsSql)) {
                stmt.setInt(1, id);
                stmt.executeUpdate();
            }

            // Delete zone
            try (PreparedStatement stmt = conn.prepareStatement(deleteZoneSql)) {
                stmt.setInt(1, id);
                stmt.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Reward Operations

    public List<ZoneReward> getRewardsForZone(int zoneId) {
        List<ZoneReward> rewards = new ArrayList<>();
        String sql = "SELECT * FROM server_afk_rewards WHERE zone_id = ?";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, zoneId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rewards.add(mapReward(rs));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return rewards;
    }

    public ZoneReward createReward(ZoneReward reward) {
        String sql =
                "INSERT INTO server_afk_rewards (zone_id, reward_type, currency_amount, item_data, gems_amount) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, reward.getZoneId());
            stmt.setString(2, reward.getType().name());
            stmt.setDouble(3, reward.getCurrencyAmount());
            stmt.setString(4, reward.serializeItem());
            stmt.setInt(5, reward.getGemsAmount());

            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    reward.setId(keys.getInt(1));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return reward;
    }

    public void updateReward(ZoneReward reward) {
        String sql =
                "UPDATE server_afk_rewards SET reward_type = ?, currency_amount = ?, item_data = ?, gems_amount = ? WHERE id = ?";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, reward.getType().name());
            stmt.setDouble(2, reward.getCurrencyAmount());
            stmt.setString(3, reward.serializeItem());
            stmt.setInt(4, reward.getGemsAmount());
            stmt.setInt(5, reward.getId());

            stmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteReward(int id) {
        String sql = "DELETE FROM server_afk_rewards WHERE id = ?";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Mapping helpers

    private AfkZone mapZone(ResultSet rs) throws Exception {
        AfkZone zone = new AfkZone();
        zone.setId(rs.getInt("id"));
        zone.setName(rs.getString("name"));
        zone.setWorldName(rs.getString("world"));
        zone.setMinX(rs.getInt("min_x"));
        zone.setMinY(rs.getInt("min_y"));
        zone.setMinZ(rs.getInt("min_z"));
        zone.setMaxX(rs.getInt("max_x"));
        zone.setMaxY(rs.getInt("max_y"));
        zone.setMaxZ(rs.getInt("max_z"));
        zone.setTimeIntervalSeconds(rs.getInt("time_interval_seconds"));
        zone.setEnabled(rs.getBoolean("enabled"));
        zone.setUseRankMultipliers(rs.getBoolean("use_rank_multipliers"));
        zone.setHoloWorld(rs.getString("holo_world"));
        double holoX = rs.getDouble("holo_x");
        if (!rs.wasNull()) zone.setHoloX(holoX);
        double holoY = rs.getDouble("holo_y");
        if (!rs.wasNull()) zone.setHoloY(holoY);
        double holoZ = rs.getDouble("holo_z");
        if (!rs.wasNull()) zone.setHoloZ(holoZ);
        return zone;
    }

    private ZoneReward mapReward(ResultSet rs) throws Exception {
        ZoneReward reward = new ZoneReward();
        reward.setId(rs.getInt("id"));
        reward.setZoneId(rs.getInt("zone_id"));
        reward.setType(ZoneReward.RewardType.valueOf(rs.getString("reward_type")));
        reward.setCurrencyAmount(rs.getDouble("currency_amount"));
        reward.deserializeItem(rs.getString("item_data"));

        // Add new fields
        try {
            reward.setCommandData(rs.getString("command_data"));
            reward.setXpAmount(rs.getInt("xp_amount"));
            reward.setGemsAmount(rs.getInt("gems_amount"));
            reward.setChancePercent(rs.getDouble("chance_percent"));
        } catch (SQLException e) {
            // Columns might not exist yet in older schemas
        }

        return reward;
    }

    // ==================== Player Statistics Operations ====================

    public PlayerStats getPlayerStats(UUID playerId) {
        String sql = "SELECT * FROM server_afk_player_stats WHERE player_uuid = ?";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playerId.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapPlayerStats(rs);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public void savePlayerStats(PlayerStats stats) {
        String checkSql = "SELECT player_uuid FROM server_afk_player_stats WHERE player_uuid = ?";
        String insertSql =
                "INSERT INTO server_afk_player_stats (player_uuid, total_afk_time_seconds, total_rewards_received, total_currency_earned, total_xp_earned, sessions_completed, first_afk_time, last_afk_time, favorite_zone_id, longest_session_seconds, current_streak_days, best_streak_days, last_daily_reward_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String updateSql =
                "UPDATE server_afk_player_stats SET total_afk_time_seconds = ?, total_rewards_received = ?, total_currency_earned = ?, total_xp_earned = ?, sessions_completed = ?, last_afk_time = ?, favorite_zone_id = ?, longest_session_seconds = ?, current_streak_days = ?, best_streak_days = ?, last_daily_reward_date = ? WHERE player_uuid = ?";

        try (Connection conn = database.getConnection()) {
            // Check if exists
            boolean exists = false;
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, stats.getPlayerUuid().toString());
                try (ResultSet rs = checkStmt.executeQuery()) {
                    exists = rs.next();
                }
            }

            if (exists) {
                // Update
                try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                    stmt.setLong(1, stats.getTotalAfkTimeSeconds());
                    stmt.setInt(2, stats.getTotalRewardsReceived());
                    stmt.setDouble(3, stats.getTotalCurrencyEarned());
                    stmt.setInt(4, stats.getTotalXpEarned());
                    stmt.setInt(5, stats.getSessionsCompleted());
                    stmt.setTimestamp(6, Timestamp.valueOf(stats.getLastAfkTime()));
                    if (stats.getFavoriteZoneId() != null) {
                        stmt.setInt(7, stats.getFavoriteZoneId());
                    } else {
                        stmt.setNull(7, Types.INTEGER);
                    }
                    stmt.setLong(8, stats.getLongestSessionSeconds());
                    stmt.setInt(9, stats.getCurrentStreakDays());
                    stmt.setInt(10, stats.getBestStreakDays());
                    if (stats.getLastDailyRewardDate() != null) {
                        stmt.setDate(11, Date.valueOf(stats.getLastDailyRewardDate()));
                    } else {
                        stmt.setNull(11, Types.DATE);
                    }
                    stmt.setString(12, stats.getPlayerUuid().toString());
                    stmt.executeUpdate();
                }
            } else {
                // Insert
                try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                    stmt.setString(1, stats.getPlayerUuid().toString());
                    stmt.setLong(2, stats.getTotalAfkTimeSeconds());
                    stmt.setInt(3, stats.getTotalRewardsReceived());
                    stmt.setDouble(4, stats.getTotalCurrencyEarned());
                    stmt.setInt(5, stats.getTotalXpEarned());
                    stmt.setInt(6, stats.getSessionsCompleted());
                    stmt.setTimestamp(7, Timestamp.valueOf(stats.getFirstAfkTime()));
                    stmt.setTimestamp(8, Timestamp.valueOf(stats.getLastAfkTime()));
                    if (stats.getFavoriteZoneId() != null) {
                        stmt.setInt(9, stats.getFavoriteZoneId());
                    } else {
                        stmt.setNull(9, Types.INTEGER);
                    }
                    stmt.setLong(10, stats.getLongestSessionSeconds());
                    stmt.setInt(11, stats.getCurrentStreakDays());
                    stmt.setInt(12, stats.getBestStreakDays());
                    if (stats.getLastDailyRewardDate() != null) {
                        stmt.setDate(13, Date.valueOf(stats.getLastDailyRewardDate()));
                    } else {
                        stmt.setNull(13, Types.DATE);
                    }
                    stmt.executeUpdate();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<PlayerStats> getTopPlayersByAfkTime(int limit) {
        List<PlayerStats> players = new ArrayList<>();
        String sql =
                "SELECT * FROM server_afk_player_stats ORDER BY total_afk_time_seconds DESC LIMIT ?";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    players.add(mapPlayerStats(rs));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return players;
    }

    public List<PlayerStats> getTopPlayersByCurrency(int limit) {
        List<PlayerStats> players = new ArrayList<>();
        String sql =
                "SELECT * FROM server_afk_player_stats ORDER BY total_currency_earned DESC LIMIT ?";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    players.add(mapPlayerStats(rs));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return players;
    }

    public List<PlayerStats> getTopPlayersBySessions(int limit) {
        List<PlayerStats> players = new ArrayList<>();
        String sql =
                "SELECT * FROM server_afk_player_stats ORDER BY sessions_completed DESC LIMIT ?";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    players.add(mapPlayerStats(rs));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return players;
    }

    public List<PlayerStats> getTopPlayersByStreak(int limit) {
        List<PlayerStats> players = new ArrayList<>();
        String sql =
                "SELECT * FROM server_afk_player_stats ORDER BY current_streak_days DESC LIMIT ?";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    players.add(mapPlayerStats(rs));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return players;
    }

    public long getTotalAfkTimeAllPlayers() {
        String sql = "SELECT SUM(total_afk_time_seconds) as total FROM server_afk_player_stats";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return rs.getLong("total");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return 0;
    }

    public int getTotalPlayersWithStats() {
        String sql = "SELECT COUNT(*) as count FROM server_afk_player_stats";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return rs.getInt("count");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return 0;
    }

    public int getTotalSessionsAllPlayers() {
        String sql = "SELECT SUM(sessions_completed) as total FROM server_afk_player_stats";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return rs.getInt("total");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return 0;
    }

    public double getTotalCurrencyEarned() {
        String sql = "SELECT SUM(total_currency_earned) as total FROM server_afk_player_stats";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return rs.getDouble("total");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return 0.0;
    }

    // ==================== Session History Operations ====================

    public int createSessionRecord(AfkSessionRecord session) {
        String sql =
                "INSERT INTO server_afk_session_history (player_uuid, zone_id, start_time, was_verified) VALUES (?, ?, ?, ?)";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, session.getPlayerUuid().toString());
            stmt.setInt(2, session.getZoneId());
            stmt.setTimestamp(3, Timestamp.valueOf(session.getStartTime()));
            stmt.setBoolean(4, session.isWasVerified());

            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return -1;
    }

    public void endSessionRecord(
            int sessionId,
            int rewardsEarned,
            double currencyEarned,
            int xpEarned,
            boolean endedByCombat) {
        String sql =
                "UPDATE server_afk_session_history SET end_time = ?, duration_seconds = ?, rewards_earned = ?, currency_earned = ?, xp_earned = ?, ended_by_combat = ? WHERE id = ?";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            LocalDateTime endTime = LocalDateTime.now();
            stmt.setTimestamp(1, Timestamp.valueOf(endTime));

            // Calculate duration (will be recalculated from start_time and end_time)
            stmt.setLong(2, 0); // Placeholder, could calculate here
            stmt.setInt(3, rewardsEarned);
            stmt.setDouble(4, currencyEarned);
            stmt.setInt(5, xpEarned);
            stmt.setBoolean(6, endedByCombat);
            stmt.setInt(7, sessionId);

            stmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== Pattern Analysis Operations ====================

    public void savePatternAnalysis(PatternAnalysis analysis) {
        String sql =
                "INSERT INTO server_afk_pattern_analysis (player_uuid, analysis_time, pattern_type, confidence_score, flagged_for_review, auto_action_taken) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, analysis.getPlayerUuid().toString());
            stmt.setTimestamp(2, Timestamp.valueOf(analysis.getAnalysisTime()));
            stmt.setString(3, analysis.getPatternType().name());
            stmt.setDouble(4, analysis.getConfidenceScore());
            stmt.setBoolean(5, analysis.isFlaggedForReview());
            stmt.setString(6, analysis.getAutoActionTaken().name());

            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    analysis.setId(keys.getInt(1));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== Mapping helpers ====================

    private PlayerStats mapPlayerStats(ResultSet rs) throws Exception {
        UUID playerId = UUID.fromString(rs.getString("player_uuid"));
        PlayerStats stats = new PlayerStats(playerId);

        stats.setTotalAfkTimeSeconds(rs.getLong("total_afk_time_seconds"));
        stats.setTotalRewardsReceived(rs.getInt("total_rewards_received"));
        stats.setTotalCurrencyEarned(rs.getDouble("total_currency_earned"));
        stats.setTotalXpEarned(rs.getInt("total_xp_earned"));
        stats.setSessionsCompleted(rs.getInt("sessions_completed"));

        Timestamp firstAfkTime = rs.getTimestamp("first_afk_time");
        if (firstAfkTime != null) {
            stats.setFirstAfkTime(firstAfkTime.toLocalDateTime());
        }

        Timestamp lastAfkTime = rs.getTimestamp("last_afk_time");
        if (lastAfkTime != null) {
            stats.setLastAfkTime(lastAfkTime.toLocalDateTime());
        }

        int favoriteZoneId = rs.getInt("favorite_zone_id");
        if (!rs.wasNull()) {
            stats.setFavoriteZoneId(favoriteZoneId);
        }

        stats.setLongestSessionSeconds(rs.getLong("longest_session_seconds"));
        stats.setCurrentStreakDays(rs.getInt("current_streak_days"));
        stats.setBestStreakDays(rs.getInt("best_streak_days"));

        Date lastDailyRewardDate = rs.getDate("last_daily_reward_date");
        if (lastDailyRewardDate != null) {
            stats.setLastDailyRewardDate(lastDailyRewardDate.toLocalDate());
        }

        return stats;
    }
}
