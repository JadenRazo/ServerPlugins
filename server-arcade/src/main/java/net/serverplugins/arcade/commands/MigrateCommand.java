package net.serverplugins.arcade.commands;

import java.io.File;
import java.sql.*;
import java.util.Arrays;
import java.util.UUID;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.games.GameType;
import net.serverplugins.arcade.machines.Direction;
import net.serverplugins.arcade.machines.Machine;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Command to migrate machine data from DreamArcade to ServerArcade. Usage: /arcademigrate
 * [path-to-dreamarcade-data]
 */
public class MigrateCommand implements CommandExecutor {

    private final ServerArcade plugin;

    public MigrateCommand(ServerArcade plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("serverarcade.admin")) {
            TextUtil.sendError(sender, "You don't have permission to use this command!");
            return true;
        }

        // Default path to DreamArcade data
        String dataPath;
        File defaultPath = new File(plugin.getDataFolder().getParentFile(), "DreamArcade/data");

        if (args.length > 0) {
            File customPath = new File(args[0]);
            dataPath = customPath.getAbsolutePath().replace(".mv.db", "");
        } else {
            dataPath = defaultPath.getAbsolutePath();
        }

        File dataFile = new File(dataPath + ".mv.db");
        if (!dataFile.exists()) {
            TextUtil.sendError(
                    sender, "DreamArcade database not found at: " + dataFile.getAbsolutePath());
            TextUtil.send(sender, "<yellow>Usage: /arcademigrate [path-to-data-file]");
            TextUtil.send(sender, "<yellow>Example: /arcademigrate plugins/DreamArcade/data");
            TextUtil.send(sender, "<yellow>Default path: " + defaultPath.getAbsolutePath());
            return true;
        }

        TextUtil.sendWarning(sender, "Migrating machines from DreamArcade...");

        // Run async to avoid blocking
        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            int migrated = migrateFromDreamArcade(dataPath);

                            Bukkit.getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                if (migrated >= 0) {
                                                    TextUtil.sendSuccess(
                                                            sender,
                                                            "Successfully migrated <yellow>"
                                                                    + migrated
                                                                    + "<green> machines from DreamArcade!");
                                                    TextUtil.send(
                                                            sender,
                                                            "<gray>Use <white>/am list<gray> to see all machines.");
                                                } else {
                                                    TextUtil.sendError(
                                                            sender,
                                                            "Failed to migrate machines. Check console for errors.");
                                                }
                                            });
                        });

        return true;
    }

    private int migrateFromDreamArcade(String dataPath) {
        String dbUrl = "jdbc:h2:file:" + dataPath + ";MODE=MySQL;AUTO_SERVER=TRUE";
        int count = 0;

        try (Connection conn = DriverManager.getConnection(dbUrl, "sa", "")) {
            conn.setAutoCommit(false);
            plugin.getLogger().info("Connected to DreamArcade database");

            try {
                String sql = "SELECT * FROM Machines";
                try (Statement stmt = conn.createStatement();
                        ResultSet rs = stmt.executeQuery(sql)) {

                    while (rs.next()) {
                        try {
                            long id = rs.getLong("id");
                            String gameType = rs.getString("gameType");
                            String machineType = rs.getString("machineType");
                            String worldName = rs.getString("world");
                            String directionStr = rs.getString("direction");
                            Array blocksArray = rs.getArray("blocks");

                            // Parse blocks array - first 3 values are location
                            Object[] blockPositionsRaw = (Object[]) blocksArray.getArray();
                            int[] blockPositions = parseBlockPositions(blockPositionsRaw);

                            if (blockPositions.length < 3) {
                                plugin.getLogger().warning("Invalid block data for machine " + id);
                                continue;
                            }

                            World world = Bukkit.getWorld(worldName);
                            if (world == null) {
                                plugin.getLogger()
                                        .warning(
                                                "World '"
                                                        + worldName
                                                        + "' not found for machine "
                                                        + id);
                                continue;
                            }

                            // Map DreamArcade game types to ServerArcade
                            GameType serverGameType = mapGameType(gameType);
                            if (serverGameType == null) {
                                plugin.getLogger()
                                        .warning(
                                                "Unknown game type '"
                                                        + gameType
                                                        + "' for machine "
                                                        + id);
                                continue;
                            }

                            Location location =
                                    new Location(
                                            world,
                                            blockPositions[0],
                                            blockPositions[1],
                                            blockPositions[2]);
                            Direction direction = parseDirection(directionStr);

                            // Generate new ID for ServerArcade
                            String newId = longToString(id);

                            // Create machine in ServerArcade
                            Machine machine =
                                    new Machine(
                                            newId,
                                            serverGameType,
                                            location,
                                            direction,
                                            UUID.fromString("00000000-0000-0000-0000-000000000000"),
                                            System.currentTimeMillis());

                            // Validate machine can be placed
                            if (serverGameType.getMachineStructure() != null
                                    && !serverGameType
                                            .getMachineStructure()
                                            .canPlace(location, direction)) {
                                plugin.getLogger()
                                        .warning(
                                                "Skipping machine "
                                                        + id
                                                        + " at "
                                                        + location.getBlockX()
                                                        + ","
                                                        + location.getBlockY()
                                                        + ","
                                                        + location.getBlockZ()
                                                        + " - structure cannot fit");
                                continue;
                            }

                            plugin.getMachineManager().addMachine(machine);
                            count++;

                            plugin.getLogger()
                                    .info(
                                            "Migrated "
                                                    + gameType
                                                    + " machine at "
                                                    + location.getBlockX()
                                                    + ","
                                                    + location.getBlockY()
                                                    + ","
                                                    + location.getBlockZ());

                        } catch (Exception e) {
                            plugin.getLogger()
                                    .warning(
                                            "Failed to migrate machine "
                                                    + rs.getLong("id")
                                                    + ": "
                                                    + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }

                // Save all migrated machines
                if (count > 0) {
                    plugin.getMachineManager().saveAllMachines();
                }

                conn.commit();
                plugin.getLogger().info("Migration completed successfully");

            } catch (Exception e) {
                conn.rollback();
                plugin.getLogger().severe("Migration failed, rolled back: " + e.getMessage());
                e.printStackTrace();
                throw e;
            }

        } catch (SQLException e) {
            plugin.getLogger()
                    .severe("Failed to connect to DreamArcade database: " + e.getMessage());
            e.printStackTrace();
            return -1;
        } catch (Exception e) {
            plugin.getLogger().severe("Migration error: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }

        return count;
    }

    private int[] parseBlockPositions(Object[] raw) {
        if (raw[0] instanceof String) {
            return Arrays.stream(raw)
                    .map(o -> Integer.parseInt((String) o))
                    .mapToInt(Integer::intValue)
                    .toArray();
        } else {
            return Arrays.stream(raw).mapToInt(o -> (Integer) o).toArray();
        }
    }

    private GameType mapGameType(String dreamType) {
        // Map DreamArcade game type names to ServerArcade
        return switch (dreamType.toLowerCase()) {
            case "slots" -> plugin.getSlotsType();
            case "blackjack" -> plugin.getBlackjackType();
            case "jackpot" -> plugin.getJackpotType();
                // Add more mappings as needed
            default -> plugin.getGameType(dreamType);
        };
    }

    private Direction parseDirection(String dir) {
        try {
            return Direction.valueOf(dir.toUpperCase());
        } catch (Exception e) {
            return Direction.SOUTH;
        }
    }

    /** Convert long ID to string (matching DreamArcade's StringUtils) */
    private String longToString(long value) {
        StringBuilder sb = new StringBuilder();
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        int base = chars.length();

        if (value == 0) return "0";

        while (value > 0) {
            sb.insert(0, chars.charAt((int) (value % base)));
            value /= base;
        }

        return sb.toString();
    }
}
