package net.serverplugins.arcade.machines;

import java.util.*;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.games.GameType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Represents a physical arcade machine in the world. */
public class Machine {

    // Namespace keys for persistent data
    public static NamespacedKey MACHINE_ITEM_KEY;
    public static NamespacedKey MACHINE_ENTITY_KEY;
    public static NamespacedKey MACHINE_SEAT_KEY;
    public static NamespacedKey MACHINE_HOLOGRAM_KEY;

    private final String id;
    private final GameType gameType;
    private final Location location;
    private final Direction direction;
    private UUID placedBy;
    private long placedAt;

    private Set<Block> blocks = new HashSet<>();
    private Set<ArmorStand> entities = new HashSet<>();
    private boolean active;

    public Machine(
            String id,
            GameType gameType,
            Location location,
            Direction direction,
            UUID placedBy,
            long placedAt) {
        this.id = id;
        this.gameType = gameType;
        // Preserve Y coordinate while centering X/Z on block
        this.location =
                new Location(
                        location.getWorld(),
                        location.getBlockX() + 0.5,
                        location.getBlockY(),
                        location.getBlockZ() + 0.5);
        this.direction = direction;
        this.placedBy = placedBy;
        this.placedAt = placedAt;
        this.active = true;
    }

    public Machine(GameType gameType, Location location, Direction direction, UUID placedBy) {
        this(generateId(), gameType, location, direction, placedBy, System.currentTimeMillis());
    }

    /** Generate a unique machine ID. */
    public static String generateId() {
        return Long.toString(System.currentTimeMillis(), 36)
                + Integer.toString(new Random().nextInt(1000), 36);
    }

    /** Initialize namespace keys (call once on plugin enable). */
    public static void initKeys(ServerArcade plugin) {
        MACHINE_ITEM_KEY = new NamespacedKey(plugin, "machine_item");
        MACHINE_ENTITY_KEY = new NamespacedKey(plugin, "machine_entity");
        MACHINE_SEAT_KEY = new NamespacedKey(plugin, "machine_seat");
        MACHINE_HOLOGRAM_KEY = new NamespacedKey(plugin, "machine_hologram");
    }

    /** Place the machine structure in the world. */
    public void place() {
        // Clean up any existing entities for this machine first (prevents duplicates on server
        // restart)
        cleanupExistingEntities();

        MachineStructure structure = gameType.getMachineStructure();
        if (structure != null) {
            blocks = structure.place(location, direction, id, MACHINE_ENTITY_KEY);
        } else {
            // Fallback: simple armor stand display
            spawnSimpleDisplay();
        }
        loadEntities();
        // Note: Holograms are part of the machine's 3D model via custom_model_data in the resource
        // pack
        // No separate DecentHolograms creation needed - the model includes fancy text
    }

    /**
     * Remove any existing armor stands with this machine's ID. Prevents duplicates when server
     * restarts and persisted entities still exist.
     */
    private void cleanupExistingEntities() {
        for (var entity : location.getNearbyEntities(10, 10, 10)) {
            if (entity.getType() == EntityType.ARMOR_STAND
                    && entity.getPersistentDataContainer()
                            .has(MACHINE_ENTITY_KEY, PersistentDataType.STRING)) {
                String storedId =
                        entity.getPersistentDataContainer()
                                .get(MACHINE_ENTITY_KEY, PersistentDataType.STRING);
                if (id.equals(storedId)) {
                    entity.remove();
                }
            }
        }
    }

    /** Spawn a simple display armor stand (fallback). */
    private void spawnSimpleDisplay() {
        ArmorStand stand =
                (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setBasePlate(false);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setCollidable(false);
        stand.setVisible(false);
        stand.setPersistent(true);
        stand.setCustomNameVisible(true);
        stand.setCustomName(gameType.getName());
        stand.getPersistentDataContainer().set(MACHINE_ENTITY_KEY, PersistentDataType.STRING, id);
        entities.add(stand);
    }

    /** Load all entities associated with this machine. */
    private void loadEntities() {
        entities.clear();
        for (var entity : location.getNearbyEntities(10, 10, 10)) {
            if (entity.getType() == EntityType.ARMOR_STAND
                    && entity.getPersistentDataContainer()
                            .has(MACHINE_ENTITY_KEY, PersistentDataType.STRING)) {
                String storedId =
                        entity.getPersistentDataContainer()
                                .get(MACHINE_ENTITY_KEY, PersistentDataType.STRING);
                if (id.equals(storedId)) {
                    entities.add((ArmorStand) entity);
                }
            }
        }
    }

    /** Remove the machine from the world. */
    public void destroy() {
        MachineStructure structure = gameType.getMachineStructure();
        if (structure != null) {
            structure.remove(location, direction, id, MACHINE_ENTITY_KEY);
        }

        // Remove any remaining entities
        for (ArmorStand stand : entities) {
            if (stand != null && !stand.isDead()) {
                stand.remove();
            }
        }
        entities.clear();

        // Clear blocks
        for (Block block : blocks) {
            block.setType(Material.AIR);
        }
        blocks.clear();
    }

    /**
     * Called when a player interacts with this machine. Override in subclasses for specific
     * behavior.
     */
    public void interact(Player player) {
        if (!active) {
            TextUtil.sendError(player, "This machine is currently inactive.");
            return;
        }

        if (!player.hasPermission("serverarcade.play")) {
            CommonMessages.NO_PERMISSION.send(player);
            return;
        }

        // Open the game
        gameType.open(player, this);
    }

    /** Seat a player on a specific seat. */
    public synchronized void seatPlayer(Player player, int seatNumber) {
        ServerArcade plugin = ServerArcade.getInstance();
        boolean debugEnabled = plugin.getConfig().getBoolean("debug.machines", false);

        if (debugEnabled) {
            plugin.getLogger()
                    .info(
                            "[Machines] seatPlayer called for "
                                    + player.getName()
                                    + " at seat "
                                    + seatNumber
                                    + ", total entities: "
                                    + entities.size());
        }

        for (ArmorStand stand : entities) {
            // Check persistent data instead of scoreboard tag for compatibility with old machines
            if (stand.getPersistentDataContainer()
                    .has(MACHINE_SEAT_KEY, PersistentDataType.INTEGER)) {
                int seatNum =
                        stand.getPersistentDataContainer()
                                .get(MACHINE_SEAT_KEY, PersistentDataType.INTEGER);

                if (debugEnabled) {
                    plugin.getLogger()
                            .info("[Machines] Found seat entity with seat number: " + seatNum);
                }

                if (seatNum == seatNumber) {
                    if (debugEnabled) {
                        plugin.getLogger()
                                .info(
                                        "[Machines] Seat number matches! Stand location: "
                                                + stand.getLocation());
                        plugin.getLogger()
                                .info(
                                        "[Machines] Stand valid: "
                                                + stand.isValid()
                                                + ", dead: "
                                                + stand.isDead());
                        plugin.getLogger()
                                .info(
                                        "[Machines] Existing passengers: "
                                                + stand.getPassengers().size());
                    }

                    // Remove existing passengers
                    stand.getPassengers().forEach(stand::removePassenger);

                    // Set player rotation
                    player.setRotation(stand.getLocation().getYaw(), 0);

                    // CRITICAL: Register player BEFORE adding as passenger to prevent race
                    // condition
                    // The enforcement task runs every tick - if we delay registration, the player
                    // might be ejected and re-seated repeatedly during the window
                    if (plugin != null && plugin.getMachineListener() != null) {
                        plugin.getMachineListener().registerSeatedPlayer(player, stand);
                    } else {
                        plugin.getLogger()
                                .warning(
                                        "Could not register seated player - MachineListener is null!");
                        break;
                    }

                    // Now add as passenger
                    if (debugEnabled) {
                        plugin.getLogger().info("[Machines] Calling addPassenger...");
                    }
                    boolean result = stand.addPassenger(player);

                    if (debugEnabled) {
                        plugin.getLogger().info("[Machines] addPassenger returned: " + result);
                        plugin.getLogger()
                                .info(
                                        "[Machines] Player.isInsideVehicle: "
                                                + player.isInsideVehicle());
                    }

                    plugin.getLogger()
                            .info(
                                    "Seated player "
                                            + player.getName()
                                            + " at machine "
                                            + id
                                            + " (seat "
                                            + seatNumber
                                            + ")");
                    break;
                }
            }
        }
    }

    /** Check if a block is part of this machine. */
    public boolean containsBlock(Block block) {
        return blocks.contains(block);
    }

    /** Check if an entity is part of this machine. */
    public boolean containsEntity(ArmorStand entity) {
        return entities.contains(entity);
    }

    /** Get machine type name for serialization. */
    public String getMachineTypeName() {
        return "Default";
    }

    // Getters
    public String getId() {
        return id;
    }

    public GameType getGameType() {
        return gameType;
    }

    public Location getLocation() {
        return location.clone();
    }

    public Direction getDirection() {
        return direction;
    }

    public UUID getPlacedBy() {
        return placedBy;
    }

    public long getPlacedAt() {
        return placedAt;
    }

    public Set<Block> getBlocks() {
        return blocks;
    }

    public Set<ArmorStand> getEntities() {
        return entities;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setPlacedBy(UUID placedBy) {
        this.placedBy = placedBy;
    }

    public void setPlacedAt(long placedAt) {
        this.placedAt = placedAt;
    }

    public void setBlocks(Set<Block> blocks) {
        this.blocks = blocks;
    }

    public String getLocationKey() {
        return location.getWorld().getName()
                + ":"
                + location.getBlockX()
                + ":"
                + location.getBlockY()
                + ":"
                + location.getBlockZ();
    }

    /**
     * Get placeholder value for this machine. Override in subclasses for specific behavior.
     *
     * @param property The property to retrieve (status, player, game, active)
     * @return The placeholder value
     */
    public String getPlaceholder(String property) {
        switch (property.toLowerCase()) {
            case "status":
                return active ? "Available" : "Inactive";
            case "player":
                return "None";
            case "game":
                return gameType != null ? gameType.getName() : "Unknown";
            case "active":
                return String.valueOf(active);
            default:
                return "Unknown property";
        }
    }

    /** Static helper to create ItemStack with custom model data. */
    public static ItemStack createCustomItem(Material material, int customModelData, String name) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(customModelData);
            if (name != null) {
                meta.setDisplayName(name);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
