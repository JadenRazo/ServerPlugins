package net.serverplugins.commands.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.logging.Logger;
import net.serverplugins.api.database.Database;
import net.serverplugins.commands.models.Home;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

public class CommandsRepository {

    private final Database database;
    private final Logger logger;

    public CommandsRepository(Database database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    // ==================== HOMES ====================

    public Map<String, Location> getHomes(UUID uuid) {
        Map<String, Location> homes = new HashMap<>();
        try {
            ResultSet rs =
                    database.executeQuery(
                            "SELECT name, world, x, y, z, yaw, pitch FROM server_homes WHERE uuid = ?",
                            uuid.toString());
            while (rs.next()) {
                String name = rs.getString("name");
                World world = Bukkit.getWorld(rs.getString("world"));
                if (world != null) {
                    Location loc =
                            new Location(
                                    world,
                                    rs.getDouble("x"),
                                    rs.getDouble("y"),
                                    rs.getDouble("z"),
                                    rs.getFloat("yaw"),
                                    rs.getFloat("pitch"));
                    homes.put(name, loc);
                }
            }
            rs.close();
        } catch (SQLException e) {
            logger.warning("Failed to load homes for " + uuid + ": " + e.getMessage());
        }
        return homes;
    }

    public void saveHome(UUID uuid, String name, Location loc) {
        try {
            database.executeUpdate(
                    "INSERT INTO server_homes (uuid, name, world, x, y, z, yaw, pitch) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE world = VALUES(world), x = VALUES(x), y = VALUES(y), "
                            + "z = VALUES(z), yaw = VALUES(yaw), pitch = VALUES(pitch)",
                    uuid.toString(),
                    name,
                    loc.getWorld().getName(),
                    loc.getX(),
                    loc.getY(),
                    loc.getZ(),
                    loc.getYaw(),
                    loc.getPitch());
        } catch (SQLException e) {
            logger.warning("Failed to save home " + name + " for " + uuid + ": " + e.getMessage());
        }
    }

    public void deleteHome(UUID uuid, String name) {
        try {
            database.executeUpdate(
                    "DELETE FROM server_homes WHERE uuid = ? AND name = ?", uuid.toString(), name);
        } catch (SQLException e) {
            logger.warning(
                    "Failed to delete home " + name + " for " + uuid + ": " + e.getMessage());
        }
    }

    public int getHomeCount(UUID uuid) {
        try {
            ResultSet rs =
                    database.executeQuery(
                            "SELECT COUNT(*) as count FROM server_homes WHERE uuid = ?",
                            uuid.toString());
            if (rs.next()) {
                int count = rs.getInt("count");
                rs.close();
                return count;
            }
            rs.close();
        } catch (SQLException e) {
            logger.warning("Failed to count homes for " + uuid + ": " + e.getMessage());
        }
        return 0;
    }

    /** Get all homes for a player with full details (icon, description, created_at). */
    public Map<String, Home> getHomesWithDetails(UUID uuid) {
        Map<String, Home> homes = new HashMap<>();
        try {
            ResultSet rs =
                    database.executeQuery(
                            "SELECT name, world, x, y, z, yaw, pitch, icon, description, created_at FROM server_homes WHERE uuid = ?",
                            uuid.toString());
            while (rs.next()) {
                String name = rs.getString("name");
                String worldName = rs.getString("world");
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    String iconStr = rs.getString("icon");
                    Material icon = Material.RED_BED;
                    if (iconStr != null) {
                        try {
                            icon = Material.valueOf(iconStr);
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    String description = rs.getString("description");
                    Timestamp createdTs = rs.getTimestamp("created_at");
                    long createdAt =
                            createdTs != null ? createdTs.getTime() : System.currentTimeMillis();

                    Home home =
                            new Home(
                                    name,
                                    worldName,
                                    rs.getDouble("x"),
                                    rs.getDouble("y"),
                                    rs.getDouble("z"),
                                    rs.getFloat("yaw"),
                                    rs.getFloat("pitch"),
                                    icon,
                                    description,
                                    createdAt);
                    homes.put(name, home);
                }
            }
            rs.close();
        } catch (SQLException e) {
            logger.warning("Failed to load homes with details for " + uuid + ": " + e.getMessage());
        }
        return homes;
    }

    /** Save a home with icon and description. */
    public void saveHomeWithDetails(
            UUID uuid, String name, Location loc, Material icon, String description) {
        try {
            database.executeUpdate(
                    "INSERT INTO server_homes (uuid, name, world, x, y, z, yaw, pitch, icon, description) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE world = VALUES(world), x = VALUES(x), y = VALUES(y), "
                            + "z = VALUES(z), yaw = VALUES(yaw), pitch = VALUES(pitch), icon = VALUES(icon), description = VALUES(description)",
                    uuid.toString(),
                    name,
                    loc.getWorld().getName(),
                    loc.getX(),
                    loc.getY(),
                    loc.getZ(),
                    loc.getYaw(),
                    loc.getPitch(),
                    icon != null ? icon.name() : "RED_BED",
                    description);
        } catch (SQLException e) {
            logger.warning(
                    "Failed to save home with details "
                            + name
                            + " for "
                            + uuid
                            + ": "
                            + e.getMessage());
        }
    }

    /** Rename a home. */
    public void renameHome(UUID uuid, String oldName, String newName) {
        try {
            database.executeUpdate(
                    "UPDATE server_homes SET name = ? WHERE uuid = ? AND name = ?",
                    newName,
                    uuid.toString(),
                    oldName);
        } catch (SQLException e) {
            logger.warning(
                    "Failed to rename home from "
                            + oldName
                            + " to "
                            + newName
                            + " for "
                            + uuid
                            + ": "
                            + e.getMessage());
        }
    }

    /** Update only the icon of a home. */
    public void updateHomeIcon(UUID uuid, String name, Material icon) {
        try {
            database.executeUpdate(
                    "UPDATE server_homes SET icon = ? WHERE uuid = ? AND name = ?",
                    icon != null ? icon.name() : "RED_BED",
                    uuid.toString(),
                    name);
        } catch (SQLException e) {
            logger.warning(
                    "Failed to update home icon for "
                            + name
                            + " for "
                            + uuid
                            + ": "
                            + e.getMessage());
        }
    }

    /** Update only the description of a home. */
    public void updateHomeDescription(UUID uuid, String name, String description) {
        try {
            database.executeUpdate(
                    "UPDATE server_homes SET description = ? WHERE uuid = ? AND name = ?",
                    description,
                    uuid.toString(),
                    name);
        } catch (SQLException e) {
            logger.warning(
                    "Failed to update home description for "
                            + name
                            + " for "
                            + uuid
                            + ": "
                            + e.getMessage());
        }
    }

    // ==================== WARPS ====================

    public Map<String, Location> getAllWarps() {
        Map<String, Location> warps = new HashMap<>();
        try {
            ResultSet rs =
                    database.executeQuery(
                            "SELECT name, world, x, y, z, yaw, pitch FROM server_warps");
            while (rs.next()) {
                String name = rs.getString("name");
                World world = Bukkit.getWorld(rs.getString("world"));
                if (world != null) {
                    Location loc =
                            new Location(
                                    world,
                                    rs.getDouble("x"),
                                    rs.getDouble("y"),
                                    rs.getDouble("z"),
                                    rs.getFloat("yaw"),
                                    rs.getFloat("pitch"));
                    warps.put(name, loc);
                }
            }
            rs.close();
        } catch (SQLException e) {
            logger.warning("Failed to load warps: " + e.getMessage());
        }
        return warps;
    }

    public void saveWarp(String name, Location loc, UUID createdBy) {
        try {
            database.executeUpdate(
                    "INSERT INTO server_warps (name, world, x, y, z, yaw, pitch, created_by) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE world = VALUES(world), x = VALUES(x), y = VALUES(y), "
                            + "z = VALUES(z), yaw = VALUES(yaw), pitch = VALUES(pitch)",
                    name,
                    loc.getWorld().getName(),
                    loc.getX(),
                    loc.getY(),
                    loc.getZ(),
                    loc.getYaw(),
                    loc.getPitch(),
                    createdBy != null ? createdBy.toString() : null);
        } catch (SQLException e) {
            logger.warning("Failed to save warp " + name + ": " + e.getMessage());
        }
    }

    public void deleteWarp(String name) {
        try {
            database.executeUpdate("DELETE FROM server_warps WHERE name = ?", name);
        } catch (SQLException e) {
            logger.warning("Failed to delete warp " + name + ": " + e.getMessage());
        }
    }

    // ==================== MUTES ====================

    public static class MuteData {
        public final UUID uuid;
        public final UUID mutedBy;
        public final String reason;
        public final Long expiresAt;
        public final long mutedAt;

        public MuteData(UUID uuid, UUID mutedBy, String reason, Long expiresAt, long mutedAt) {
            this.uuid = uuid;
            this.mutedBy = mutedBy;
            this.reason = reason;
            this.expiresAt = expiresAt;
            this.mutedAt = mutedAt;
        }

        public boolean isExpired() {
            return expiresAt != null && System.currentTimeMillis() > expiresAt;
        }
    }

    public Map<UUID, MuteData> getAllMutes() {
        Map<UUID, MuteData> mutes = new HashMap<>();
        try {
            ResultSet rs =
                    database.executeQuery(
                            "SELECT uuid, muted_by, reason, expires_at, muted_at FROM server_mutes");
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String mutedByStr = rs.getString("muted_by");
                UUID mutedBy = mutedByStr != null ? UUID.fromString(mutedByStr) : null;
                String reason = rs.getString("reason");
                Timestamp expiresTs = rs.getTimestamp("expires_at");
                Long expiresAt = expiresTs != null ? expiresTs.getTime() : null;
                Timestamp mutedTs = rs.getTimestamp("muted_at");
                long mutedAt = mutedTs != null ? mutedTs.getTime() : System.currentTimeMillis();

                mutes.put(uuid, new MuteData(uuid, mutedBy, reason, expiresAt, mutedAt));
            }
            rs.close();
        } catch (SQLException e) {
            logger.warning("Failed to load mutes: " + e.getMessage());
        }
        return mutes;
    }

    public void saveMute(UUID uuid, UUID mutedBy, String reason, Long expiresAt) {
        try {
            database.executeUpdate(
                    "INSERT INTO server_mutes (uuid, muted_by, reason, expires_at) "
                            + "VALUES (?, ?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE muted_by = VALUES(muted_by), reason = VALUES(reason), "
                            + "expires_at = VALUES(expires_at), muted_at = CURRENT_TIMESTAMP",
                    uuid.toString(),
                    mutedBy != null ? mutedBy.toString() : null,
                    reason,
                    expiresAt != null ? new Timestamp(expiresAt) : null);
        } catch (SQLException e) {
            logger.warning("Failed to save mute for " + uuid + ": " + e.getMessage());
        }
    }

    public void deleteMute(UUID uuid) {
        try {
            database.executeUpdate("DELETE FROM server_mutes WHERE uuid = ?", uuid.toString());
        } catch (SQLException e) {
            logger.warning("Failed to delete mute for " + uuid + ": " + e.getMessage());
        }
    }

    // ==================== PLAYTIME ====================

    public static class PlaytimeData {
        public long totalSeconds;
        public Long lastJoin;
        public Long lastQuit;

        public PlaytimeData(long totalSeconds, Long lastJoin, Long lastQuit) {
            this.totalSeconds = totalSeconds;
            this.lastJoin = lastJoin;
            this.lastQuit = lastQuit;
        }
    }

    public PlaytimeData getPlaytime(UUID uuid) {
        try {
            ResultSet rs =
                    database.executeQuery(
                            "SELECT total_seconds, last_join, last_quit FROM server_playtime WHERE uuid = ?",
                            uuid.toString());
            if (rs.next()) {
                long totalSeconds = rs.getLong("total_seconds");
                Timestamp joinTs = rs.getTimestamp("last_join");
                Timestamp quitTs = rs.getTimestamp("last_quit");
                rs.close();
                return new PlaytimeData(
                        totalSeconds,
                        joinTs != null ? joinTs.getTime() : null,
                        quitTs != null ? quitTs.getTime() : null);
            }
            rs.close();
        } catch (SQLException e) {
            logger.warning("Failed to load playtime for " + uuid + ": " + e.getMessage());
        }
        return new PlaytimeData(0, null, null);
    }

    public void savePlaytime(
            UUID uuid, String username, long totalSeconds, Long lastJoin, Long lastQuit) {
        try {
            database.executeUpdate(
                    "INSERT INTO server_playtime (uuid, username, total_seconds, last_join, last_quit) "
                            + "VALUES (?, ?, ?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE username = VALUES(username), total_seconds = VALUES(total_seconds), "
                            + "last_join = VALUES(last_join), last_quit = VALUES(last_quit)",
                    uuid.toString(),
                    username,
                    totalSeconds,
                    lastJoin != null ? new Timestamp(lastJoin) : null,
                    lastQuit != null ? new Timestamp(lastQuit) : null);
        } catch (SQLException e) {
            logger.warning("Failed to save playtime for " + uuid + ": " + e.getMessage());
        }
    }

    // ==================== PLAYER DATA ====================

    public static class PlayerDataRecord {
        public int warnings;
        public boolean flyEnabled;
        public boolean godMode;
        public Location lastLocation;

        public PlayerDataRecord(
                int warnings, boolean flyEnabled, boolean godMode, Location lastLocation) {
            this.warnings = warnings;
            this.flyEnabled = flyEnabled;
            this.godMode = godMode;
            this.lastLocation = lastLocation;
        }
    }

    public PlayerDataRecord getPlayerData(UUID uuid) {
        try {
            ResultSet rs =
                    database.executeQuery(
                            "SELECT warnings, fly_enabled, god_mode, last_world, last_x, last_y, last_z, last_yaw, last_pitch "
                                    + "FROM server_player_data WHERE uuid = ?",
                            uuid.toString());
            if (rs.next()) {
                int warnings = rs.getInt("warnings");
                boolean flyEnabled = rs.getBoolean("fly_enabled");
                boolean godMode = rs.getBoolean("god_mode");
                String worldName = rs.getString("last_world");
                Location lastLocation = null;
                if (worldName != null) {
                    World world = Bukkit.getWorld(worldName);
                    if (world != null) {
                        lastLocation =
                                new Location(
                                        world,
                                        rs.getDouble("last_x"),
                                        rs.getDouble("last_y"),
                                        rs.getDouble("last_z"),
                                        rs.getFloat("last_yaw"),
                                        rs.getFloat("last_pitch"));
                    }
                }
                rs.close();
                return new PlayerDataRecord(warnings, flyEnabled, godMode, lastLocation);
            }
            rs.close();
        } catch (SQLException e) {
            logger.warning("Failed to load player data for " + uuid + ": " + e.getMessage());
        }
        return new PlayerDataRecord(0, false, false, null);
    }

    public void savePlayerData(
            UUID uuid,
            String username,
            int warnings,
            boolean flyEnabled,
            boolean godMode,
            Location lastLocation) {
        try {
            String worldName = lastLocation != null ? lastLocation.getWorld().getName() : null;
            Double x = lastLocation != null ? lastLocation.getX() : null;
            Double y = lastLocation != null ? lastLocation.getY() : null;
            Double z = lastLocation != null ? lastLocation.getZ() : null;
            Float yaw = lastLocation != null ? lastLocation.getYaw() : null;
            Float pitch = lastLocation != null ? lastLocation.getPitch() : null;

            database.executeUpdate(
                    "INSERT INTO server_player_data (uuid, username, warnings, fly_enabled, god_mode, "
                            + "last_world, last_x, last_y, last_z, last_yaw, last_pitch) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE username = VALUES(username), warnings = VALUES(warnings), "
                            + "fly_enabled = VALUES(fly_enabled), god_mode = VALUES(god_mode), "
                            + "last_world = VALUES(last_world), last_x = VALUES(last_x), last_y = VALUES(last_y), "
                            + "last_z = VALUES(last_z), last_yaw = VALUES(last_yaw), last_pitch = VALUES(last_pitch)",
                    uuid.toString(),
                    username,
                    warnings,
                    flyEnabled,
                    godMode,
                    worldName,
                    x,
                    y,
                    z,
                    yaw,
                    pitch);
        } catch (SQLException e) {
            logger.warning("Failed to save player data for " + uuid + ": " + e.getMessage());
        }
    }

    public void updateWarnings(UUID uuid, int warnings) {
        try {
            database.executeUpdate(
                    "UPDATE server_player_data SET warnings = ? WHERE uuid = ?",
                    warnings,
                    uuid.toString());
        } catch (SQLException e) {
            logger.warning("Failed to update warnings for " + uuid + ": " + e.getMessage());
        }
    }

    public void updateFlyEnabled(UUID uuid, boolean enabled) {
        try {
            database.executeUpdate(
                    "UPDATE server_player_data SET fly_enabled = ? WHERE uuid = ?",
                    enabled,
                    uuid.toString());
        } catch (SQLException e) {
            logger.warning("Failed to update fly enabled for " + uuid + ": " + e.getMessage());
        }
    }

    public void updateGodMode(UUID uuid, boolean enabled) {
        try {
            database.executeUpdate(
                    "UPDATE server_player_data SET god_mode = ? WHERE uuid = ?",
                    enabled,
                    uuid.toString());
        } catch (SQLException e) {
            logger.warning("Failed to update god mode for " + uuid + ": " + e.getMessage());
        }
    }

    // ==================== SURVIVAL GUIDE ====================

    /**
     * Get whether a player has the survival guide enabled. Defaults to true if no record exists.
     */
    public boolean getSurvivalGuideEnabled(UUID uuid) {
        try {
            ResultSet rs =
                    database.executeQuery(
                            "SELECT survival_guide_enabled FROM server_player_data WHERE uuid = ?",
                            uuid.toString());
            if (rs.next()) {
                boolean enabled = rs.getBoolean("survival_guide_enabled");
                rs.close();
                return enabled;
            }
            rs.close();
        } catch (SQLException e) {
            logger.warning(
                    "Failed to get survival guide preference for " + uuid + ": " + e.getMessage());
        }
        return true; // Default to enabled
    }

    /** Set whether a player has the survival guide enabled. */
    public void setSurvivalGuideEnabled(UUID uuid, String username, boolean enabled) {
        try {
            database.executeUpdate(
                    "INSERT INTO server_player_data (uuid, username, survival_guide_enabled) "
                            + "VALUES (?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE username = VALUES(username), survival_guide_enabled = VALUES(survival_guide_enabled)",
                    uuid.toString(),
                    username,
                    enabled);
        } catch (SQLException e) {
            logger.warning(
                    "Failed to set survival guide preference for " + uuid + ": " + e.getMessage());
        }
    }
}
