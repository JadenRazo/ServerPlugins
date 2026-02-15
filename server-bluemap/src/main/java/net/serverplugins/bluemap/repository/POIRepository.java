package net.serverplugins.bluemap.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.serverplugins.api.database.Database;
import net.serverplugins.bluemap.models.POI;

public class POIRepository {

    private final Database database;

    public POIRepository(Database database) {
        this.database = database;
    }

    public POI createPOI(POI poi) {
        try {
            database.executeUpdate(
                    "INSERT INTO server_bluemap_poi (name, world, x, y, z, category, description, creator_uuid, created_at, visible) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    poi.getName(),
                    poi.getWorld(),
                    poi.getX(),
                    poi.getY(),
                    poi.getZ(),
                    poi.getCategory(),
                    poi.getDescription(),
                    poi.getCreatorUuid().toString(),
                    poi.getCreatedAt(),
                    poi.isVisible());

            // Get the auto-generated ID
            return database.query(
                    "SELECT * FROM server_bluemap_poi WHERE name = ? AND world = ? AND x = ? AND y = ? AND z = ? ORDER BY id DESC LIMIT 1",
                    this::mapPOI,
                    poi.getName(),
                    poi.getWorld(),
                    poi.getX(),
                    poi.getY(),
                    poi.getZ());
        } catch (SQLException e) {
            return null;
        }
    }

    public boolean updatePOI(POI poi) {
        try {
            int affected =
                    database.executeUpdate(
                            "UPDATE server_bluemap_poi SET name = ?, description = ?, category = ?, visible = ? WHERE id = ?",
                            poi.getName(),
                            poi.getDescription(),
                            poi.getCategory(),
                            poi.isVisible(),
                            poi.getId());
            return affected > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean deletePOI(int id) {
        try {
            int affected =
                    database.executeUpdate("DELETE FROM server_bluemap_poi WHERE id = ?", id);
            return affected > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public POI getPOIById(int id) {
        return database.query("SELECT * FROM server_bluemap_poi WHERE id = ?", this::mapPOI, id);
    }

    public POI getPOIByName(String name) {
        return database.query(
                "SELECT * FROM server_bluemap_poi WHERE name = ? LIMIT 1", this::mapPOI, name);
    }

    public List<POI> getAllPOIs() {
        return database.query(
                "SELECT * FROM server_bluemap_poi ORDER BY created_at DESC", this::mapPOIList);
    }

    public List<POI> getVisiblePOIs() {
        return database.query(
                "SELECT * FROM server_bluemap_poi WHERE visible = TRUE ORDER BY created_at DESC",
                this::mapPOIList);
    }

    public List<POI> getPOIsByWorld(String world) {
        return database.query(
                "SELECT * FROM server_bluemap_poi WHERE world = ? AND visible = TRUE ORDER BY created_at DESC",
                this::mapPOIList,
                world);
    }

    public List<POI> getPOIsByCategory(String category) {
        return database.query(
                "SELECT * FROM server_bluemap_poi WHERE category = ? AND visible = TRUE ORDER BY created_at DESC",
                this::mapPOIList,
                category);
    }

    public List<POI> getPOIsByCreator(UUID creatorUuid) {
        return database.query(
                "SELECT * FROM server_bluemap_poi WHERE creator_uuid = ? ORDER BY created_at DESC",
                this::mapPOIList,
                creatorUuid.toString());
    }

    private POI mapPOI(ResultSet rs) throws SQLException {
        if (rs.next()) {
            return new POI(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getString("world"),
                    rs.getInt("x"),
                    rs.getInt("y"),
                    rs.getInt("z"),
                    rs.getString("category"),
                    rs.getString("description"),
                    UUID.fromString(rs.getString("creator_uuid")),
                    rs.getTimestamp("created_at"),
                    rs.getBoolean("visible"));
        }
        return null;
    }

    private List<POI> mapPOIList(ResultSet rs) throws SQLException {
        List<POI> pois = new ArrayList<>();
        while (rs.next()) {
            pois.add(
                    new POI(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("world"),
                            rs.getInt("x"),
                            rs.getInt("y"),
                            rs.getInt("z"),
                            rs.getString("category"),
                            rs.getString("description"),
                            UUID.fromString(rs.getString("creator_uuid")),
                            rs.getTimestamp("created_at"),
                            rs.getBoolean("visible")));
        }
        return pois;
    }
}
