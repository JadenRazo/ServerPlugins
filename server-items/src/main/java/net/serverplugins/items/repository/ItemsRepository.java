package net.serverplugins.items.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.serverplugins.api.database.Database;
import net.serverplugins.items.models.BlockStateMapping;
import net.serverplugins.items.models.PlacedBlock;
import org.bukkit.Instrument;

public class ItemsRepository {

    private final Database database;

    public ItemsRepository(Database database) {
        this.database = database;
    }

    // Placed blocks

    public void insertPlacedBlock(PlacedBlock block) {
        database.execute(
                "INSERT INTO server_placed_blocks (world, x, y, z, block_id, placed_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                block.world(),
                block.x(),
                block.y(),
                block.z(),
                block.blockId(),
                block.placedBy() != null ? block.placedBy().toString() : null);
    }

    public void deletePlacedBlock(String world, int x, int y, int z) {
        database.execute(
                "DELETE FROM server_placed_blocks WHERE world = ? AND x = ? AND y = ? AND z = ?",
                world,
                x,
                y,
                z);
    }

    public List<PlacedBlock> loadPlacedBlocksInChunk(String world, int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int maxX = minX + 15;
        int minZ = chunkZ << 4;
        int maxZ = minZ + 15;

        return database.query(
                "SELECT id, world, x, y, z, block_id, placed_by, placed_at "
                        + "FROM server_placed_blocks "
                        + "WHERE world = ? AND x BETWEEN ? AND ? AND z BETWEEN ? AND ?",
                rs -> {
                    List<PlacedBlock> blocks = new ArrayList<>();
                    while (rs.next()) {
                        String placedByStr = rs.getString("placed_by");
                        UUID placedBy = placedByStr != null ? UUID.fromString(placedByStr) : null;
                        blocks.add(
                                new PlacedBlock(
                                        rs.getLong("id"),
                                        rs.getString("world"),
                                        rs.getInt("x"),
                                        rs.getInt("y"),
                                        rs.getInt("z"),
                                        rs.getString("block_id"),
                                        placedBy,
                                        rs.getTimestamp("placed_at").getTime()));
                    }
                    return blocks;
                },
                world,
                minX,
                maxX,
                minZ,
                maxZ);
    }

    // Block state mappings

    public void saveStateMapping(BlockStateMapping mapping) {
        database.execute(
                "INSERT INTO server_block_state_map (item_id, instrument, note, powered) "
                        + "VALUES (?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE instrument = VALUES(instrument), "
                        + "note = VALUES(note), powered = VALUES(powered)",
                mapping.itemId(),
                mapping.instrument().name(),
                mapping.note(),
                mapping.powered());
    }

    public List<BlockStateMapping> loadAllStateMappings() {
        return database.query(
                "SELECT item_id, instrument, note, powered FROM server_block_state_map",
                rs -> {
                    List<BlockStateMapping> mappings = new ArrayList<>();
                    while (rs.next()) {
                        mappings.add(
                                new BlockStateMapping(
                                        rs.getString("item_id"),
                                        Instrument.valueOf(rs.getString("instrument")),
                                        rs.getInt("note"),
                                        rs.getBoolean("powered")));
                    }
                    return mappings;
                });
    }

    // Furniture

    public void insertFurniture(
            String displayUuid,
            String interactionUuid,
            String furnitureId,
            String world,
            double x,
            double y,
            double z,
            float yaw,
            String placedBy) {
        database.execute(
                "INSERT INTO server_furniture "
                        + "(display_uuid, interaction_uuid, furniture_id, world, x, y, z, yaw, placed_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                displayUuid,
                interactionUuid,
                furnitureId,
                world,
                x,
                y,
                z,
                yaw,
                placedBy);
    }

    public void deleteFurniture(String displayUuid) {
        database.execute("DELETE FROM server_furniture WHERE display_uuid = ?", displayUuid);
    }

    public List<FurnitureRow> loadFurnitureInChunk(String world, int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int maxX = minX + 16;
        int minZ = chunkZ << 4;
        int maxZ = minZ + 16;

        return database.query(
                "SELECT display_uuid, interaction_uuid, furniture_id, world, x, y, z, yaw, placed_by "
                        + "FROM server_furniture "
                        + "WHERE world = ? AND x >= ? AND x < ? AND z >= ? AND z < ?",
                rs -> {
                    List<FurnitureRow> rows = new ArrayList<>();
                    while (rs.next()) {
                        rows.add(
                                new FurnitureRow(
                                        rs.getString("display_uuid"),
                                        rs.getString("interaction_uuid"),
                                        rs.getString("furniture_id"),
                                        rs.getString("world"),
                                        rs.getDouble("x"),
                                        rs.getDouble("y"),
                                        rs.getDouble("z"),
                                        rs.getFloat("yaw"),
                                        rs.getString("placed_by")));
                    }
                    return rows;
                },
                world,
                (double) minX,
                (double) maxX,
                (double) minZ,
                (double) maxZ);
    }

    public record FurnitureRow(
            String displayUuid,
            String interactionUuid,
            String furnitureId,
            String world,
            double x,
            double y,
            double z,
            float yaw,
            String placedBy) {}
}
