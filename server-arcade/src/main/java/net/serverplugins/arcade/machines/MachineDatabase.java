package net.serverplugins.arcade.machines;

import java.sql.*;
import java.util.*;
import net.serverplugins.api.database.Database;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.games.GameType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/** Database layer for persisting arcade machines using ServerAPI database. */
public class MachineDatabase {

    private final ServerArcade plugin;
    private Database database;

    public MachineDatabase(ServerArcade plugin) {
        this.plugin = plugin;
    }

    public void setDatabase(Database database) {
        this.database = database;
    }

    public void initialize() {
        // Database is now initialized from ServerArcade main class
        if (database == null) {
            plugin.getLogger().warning("No database configured - machine persistence disabled");
        }
    }

    public boolean isConnected() {
        return database != null;
    }

    public void saveMachine(Machine machine) {
        if (!isConnected()) return;

        try {
            Location loc = machine.getLocation();
            database.executeUpdate(
                    "INSERT INTO server_arcade_machines (id, type, world, x, y, z, direction, placed_by, placed_at, active, blocks_data) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE type = VALUES(type), world = VALUES(world), x = VALUES(x), y = VALUES(y), "
                            + "z = VALUES(z), direction = VALUES(direction), active = VALUES(active), blocks_data = VALUES(blocks_data)",
                    machine.getId(),
                    machine.getGameType() != null
                            ? machine.getGameType().getConfigKey()
                            : "unknown",
                    loc.getWorld().getName(),
                    loc.getBlockX(),
                    loc.getBlockY(),
                    loc.getBlockZ(),
                    machine.getDirection().name(),
                    machine.getPlacedBy() != null
                            ? machine.getPlacedBy().toString()
                            : "00000000-0000-0000-0000-000000000000",
                    machine.getPlacedAt(),
                    machine.isActive(),
                    encodeBlocks(machine.getBlocks()));
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save machine: " + e.getMessage());
        }
    }

    public void saveAllMachines(Collection<Machine> machines) {
        if (!isConnected()) return;

        for (Machine machine : machines) {
            saveMachine(machine);
        }
    }

    public void deleteMachine(String id) {
        if (!isConnected()) return;

        try {
            database.executeUpdate("DELETE FROM server_arcade_machines WHERE id = ?", id);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to delete machine: " + e.getMessage());
        }
    }

    public List<Machine> loadAllMachines() {
        List<Machine> machines = new ArrayList<>();
        if (!isConnected()) return machines;

        try (ResultSet rs = database.executeQuery("SELECT * FROM server_arcade_machines")) {
            while (rs.next()) {
                try {
                    Machine machine = parseMachine(rs);
                    if (machine != null) {
                        machines.add(machine);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load machine: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load machines: " + e.getMessage());
        }

        return machines;
    }

    private Machine parseMachine(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String typeKey = rs.getString("type");
        String worldName = rs.getString("world");
        int x = rs.getInt("x");
        int y = rs.getInt("y");
        int z = rs.getInt("z");

        Direction direction = Direction.SOUTH;
        try {
            String dirStr = rs.getString("direction");
            if (dirStr != null && !dirStr.isEmpty()) {
                direction = Direction.valueOf(dirStr);
            }
        } catch (Exception ignored) {
        }

        UUID placedBy;
        try {
            placedBy = UUID.fromString(rs.getString("placed_by"));
        } catch (Exception e) {
            placedBy = null;
        }
        long placedAt = rs.getLong("placed_at");
        boolean active = rs.getBoolean("active");
        String blocksData = rs.getString("blocks_data");

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("World '" + worldName + "' not found for machine " + id);
            return null;
        }

        // Get the game type from the plugin
        GameType gameType = plugin.getGameType(typeKey);
        if (gameType == null) {
            plugin.getLogger().warning("Unknown game type '" + typeKey + "' for machine " + id);
            return null;
        }

        Location location = new Location(world, x, y, z);
        // Use gameType.createMachine() to get the correct machine type (e.g., OnePlayerMachine for
        // Slots)
        Machine machine = gameType.createMachine(id, location, direction);
        machine.setPlacedBy(placedBy);
        machine.setPlacedAt(placedAt);
        machine.setActive(active);

        // Parse and set blocks
        if (blocksData != null && !blocksData.isEmpty()) {
            Set<Block> blocks = decodeBlocks(blocksData, world);
            machine.setBlocks(blocks);
        }

        return machine;
    }

    /** Encode blocks as a string: "x1,y1,z1;x2,y2,z2;..." */
    private String encodeBlocks(Set<Block> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (Block block : blocks) {
            if (sb.length() > 0) sb.append(";");
            sb.append(block.getX())
                    .append(",")
                    .append(block.getY())
                    .append(",")
                    .append(block.getZ());
        }
        return sb.toString();
    }

    /** Decode blocks from string format. */
    private Set<Block> decodeBlocks(String data, World world) {
        Set<Block> blocks = new HashSet<>();
        if (data == null || data.isEmpty()) {
            return blocks;
        }

        for (String part : data.split(";")) {
            String[] coords = part.split(",");
            if (coords.length == 3) {
                try {
                    int bx = Integer.parseInt(coords[0]);
                    int by = Integer.parseInt(coords[1]);
                    int bz = Integer.parseInt(coords[2]);
                    blocks.add(world.getBlockAt(bx, by, bz));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return blocks;
    }

    public void close() {
        // Database connection is managed by ServerArcade main class
    }
}
