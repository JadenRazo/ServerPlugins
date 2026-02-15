package net.serverplugins.arcade.machines;

import java.util.*;
import net.serverplugins.api.database.Database;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.games.GameType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** Manages all arcade machines in the world. */
public class MachineManager {

    public static final int MAX_DISTANCE_SQUARED = 36; // 6 blocks

    private final ServerArcade plugin;
    private final MachineDatabase database;
    private final Map<String, Machine> machines = new HashMap<>();
    private final Map<String, String> locationIndex = new HashMap<>(); // locationKey -> machineId

    public MachineManager(ServerArcade plugin, Database db) {
        this.plugin = plugin;
        this.database = new MachineDatabase(plugin);
        this.database.setDatabase(db);
    }

    /** Initialize the machine manager and load all machines. */
    public void initialize() {
        database.initialize();
        loadMachines();
        plugin.getLogger().info("Loaded " + machines.size() + " arcade machines");
    }

    /** Shutdown the machine manager. */
    public void shutdown() {
        saveAllMachines();
        database.close();
    }

    /** Load all machines from the database. */
    private void loadMachines() {
        machines.clear();
        locationIndex.clear();

        List<Machine> loaded = database.loadAllMachines();
        for (Machine machine : loaded) {
            machines.put(machine.getId(), machine);
            locationIndex.put(machine.getLocationKey(), machine.getId());

            // Spawn the machine visuals on the main thread
            Bukkit.getScheduler().runTask(plugin, machine::place);
        }
    }

    /** Save all machines to database. */
    public void saveAllMachines() {
        // Create snapshot to avoid ConcurrentModificationException
        Collection<Machine> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(machines.values());
        }
        database.saveAllMachines(snapshot);
    }

    /** Create a new machine at the specified location. */
    public synchronized Machine createMachine(
            GameType gameType, Location location, Direction direction, Player placer) {
        // Double-check inside synchronized block
        String locationKey = getLocationKey(location);
        if (locationIndex.containsKey(locationKey)) {
            return null; // Machine already exists at this location
        }

        // Check if structure can be placed
        MachineStructure structure = gameType.getMachineStructure();
        if (structure != null && !structure.canPlace(location, direction)) {
            return null; // Can't place here
        }

        // Use gameType.createMachine() to allow game types to return specialized machines (e.g.,
        // OnePlayerMachine)
        String machineId = UUID.randomUUID().toString();
        Machine machine = gameType.createMachine(machineId, location, direction);
        machine.setPlacedBy(placer.getUniqueId());

        // Atomic operations within synchronized block
        machines.put(machine.getId(), machine);
        locationIndex.put(machine.getLocationKey(), machine.getId());

        // Place the structure in the world
        machine.place();

        // Save to database asynchronously to avoid blocking main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> database.saveMachine(machine));

        return machine;
    }

    /**
     * Add a pre-constructed machine (for migration/import purposes). Does not place structure or
     * validate location.
     */
    public void addMachine(Machine machine) {
        machines.put(machine.getId(), machine);
        locationIndex.put(machine.getLocationKey(), machine.getId());
    }

    /** Remove a machine by ID. */
    public synchronized boolean removeMachine(String machineId) {
        Machine machine = machines.get(machineId);
        if (machine == null) return false;

        machine.destroy();
        machines.remove(machineId);
        locationIndex.remove(machine.getLocationKey());
        // Delete from database asynchronously
        Bukkit.getScheduler()
                .runTaskAsynchronously(plugin, () -> database.deleteMachine(machineId));

        return true;
    }

    /** Remove a machine at a location. */
    public boolean removeMachineAt(Location location) {
        String locationKey = getLocationKey(location);
        String machineId = locationIndex.get(locationKey);
        if (machineId == null) return false;

        return removeMachine(machineId);
    }

    /** Get a machine at a location. */
    public Machine getMachineAt(Location location) {
        String locationKey = getLocationKey(location);
        String machineId = locationIndex.get(locationKey);
        if (machineId == null) return null;

        return machines.get(machineId);
    }

    /** Get a machine by block (checks if block is part of any machine). */
    public Machine getMachineByBlock(Block block) {
        Location blockLoc = block.getLocation();

        for (Machine machine : machines.values()) {
            // Check distance first for performance
            if (machine.getLocation().getWorld().equals(blockLoc.getWorld())
                    && machine.getLocation().distanceSquared(blockLoc) <= MAX_DISTANCE_SQUARED) {

                if (machine.containsBlock(block)) {
                    return machine;
                }
            }
        }
        return null;
    }

    /** Get a machine by its ID. */
    public Machine getMachine(String id) {
        return machines.get(id);
    }

    /** Get all machines. */
    public Collection<Machine> getAllMachines() {
        return Collections.unmodifiableCollection(machines.values());
    }

    /** Get machines by game type. */
    public List<Machine> getMachinesByType(GameType gameType) {
        List<Machine> result = new ArrayList<>();
        for (Machine machine : machines.values()) {
            if (machine.getGameType() == gameType) {
                result.add(machine);
            }
        }
        return result;
    }

    /** Get machines by game type key. */
    public List<Machine> getMachinesByTypeKey(String typeKey) {
        List<Machine> result = new ArrayList<>();
        for (Machine machine : machines.values()) {
            if (machine.getGameType() != null
                    && machine.getGameType().getConfigKey().equalsIgnoreCase(typeKey)) {
                result.add(machine);
            }
        }
        return result;
    }

    /** Check if a machine exists at a location. */
    public boolean hasMachineAt(Location location) {
        return locationIndex.containsKey(getLocationKey(location));
    }

    private String getLocationKey(Location location) {
        return location.getWorld().getName()
                + ":"
                + location.getBlockX()
                + ":"
                + location.getBlockY()
                + ":"
                + location.getBlockZ();
    }

    public void reload() {
        // Destroy all existing machines
        for (Machine machine : machines.values()) {
            machine.destroy();
        }
        loadMachines();
    }

    /**
     * Clear all machines from manager state without removing from world. Used for cleanup when
     * world entities were already removed.
     */
    public int clearAllFromState() {
        int count = machines.size();
        List<String> idsToDelete = new ArrayList<>();
        for (String machineId : new ArrayList<>(machines.keySet())) {
            Machine machine = machines.remove(machineId);
            if (machine != null) {
                locationIndex.remove(machine.getLocationKey());
                idsToDelete.add(machineId);
            }
        }
        // Delete from database asynchronously
        if (!idsToDelete.isEmpty()) {
            Bukkit.getScheduler()
                    .runTaskAsynchronously(
                            plugin,
                            () -> {
                                for (String id : idsToDelete) {
                                    database.deleteMachine(id);
                                }
                            });
        }
        return count;
    }

    /**
     * Remove machines at given locations from state. Used when cleanup removes entities from world.
     */
    public int clearLocationsFromState(java.util.Set<String> locationKeys) {
        int count = 0;
        List<String> idsToDelete = new ArrayList<>();
        for (String locKey : locationKeys) {
            String machineId = locationIndex.remove(locKey);
            if (machineId != null) {
                Machine machine = machines.remove(machineId);
                if (machine != null) {
                    idsToDelete.add(machineId);
                    count++;
                }
            }
        }
        // Delete from database asynchronously
        if (!idsToDelete.isEmpty()) {
            Bukkit.getScheduler()
                    .runTaskAsynchronously(
                            plugin,
                            () -> {
                                for (String id : idsToDelete) {
                                    database.deleteMachine(id);
                                }
                            });
        }
        return count;
    }

    public MachineDatabase getDatabase() {
        return database;
    }
}
