package net.serverplugins.core.features;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.serverplugins.core.ServerCore;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.TileState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Enhances spawner functionality to allow mobs to spawn in air without requiring solid blocks
 * beneath them.
 *
 * <p>Uses vanilla spawner timing by reading each spawner's delay settings.
 */
public class SpawnerEnhancementFeature extends Feature implements Listener {

    // Spawner registry to avoid O(n³) block scanning
    private record ChunkKey(World world, int x, int z) {}

    private final Map<ChunkKey, Set<Location>> chunkSpawnerMap = new ConcurrentHashMap<>();

    private BukkitTask spawnerTask;
    private final Map<Location, Long> spawnerLastSpawn = new HashMap<>();
    private final Map<Location, Long> vanillaSpawnTimes = new HashMap<>();
    private final Set<Location> processedThisCycle = new HashSet<>();

    // Spawner constants (vanilla values)
    private static final int SPAWN_RANGE_HORIZONTAL = 4;
    private static final int SPAWN_RANGE_VERTICAL = 1;
    private static final int ACTIVATION_RANGE = 16;
    private static final int MAX_NEARBY_ENTITIES = 6;

    // Key for checking player-placed spawners (set by server-admin)
    private NamespacedKey playerPlacedKey;
    // Key to mark entities we've already processed (prevents double-handling)
    private NamespacedKey spawnedFromSpawnerKey;
    // Local key for player-placed tracking (fallback when server-admin unavailable)
    private NamespacedKey localPlayerPlacedKey;
    // Delay before applying velocity to ensure entity initialization is complete
    private static final long VELOCITY_DELAY_TICKS = 2L;

    public SpawnerEnhancementFeature(ServerCore plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "Spawner Enhancement";
    }

    @Override
    public String getDescription() {
        return "Allows spawner mobs to spawn in air without solid blocks";
    }

    @Override
    protected void onEnable() {
        playerPlacedKey = new NamespacedKey("serveradmin", "player_placed");
        spawnedFromSpawnerKey = new NamespacedKey("servercore", "spawner_spawned");
        localPlayerPlacedKey = new NamespacedKey("servercore", "player_placed_spawner");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startSpawnerTask();
        plugin.getLogger()
                .info("Spawner Enhancement enabled - mobs can now spawn in air from spawners");
    }

    @Override
    protected void onDisable() {
        if (spawnerTask != null) {
            spawnerTask.cancel();
            spawnerTask = null;
        }
        CreatureSpawnEvent.getHandlerList().unregister(this);
        SpawnerSpawnEvent.getHandlerList().unregister(this);
        spawnerLastSpawn.clear();
        vanillaSpawnTimes.clear();
        chunkSpawnerMap.clear();
    }

    /**
     * Intercept vanilla spawner spawns to apply velocity and manage AI. Uses HIGH priority to
     * modify entities before other plugins process them.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        if (!isEnabled()) return;

        Entity entity = event.getEntity();
        CreatureSpawner spawner = event.getSpawner();

        if (spawner == null) return;

        Location spawnerLoc = spawner.getLocation();

        // Mark that vanilla handled this spawn
        vanillaSpawnTimes.put(spawnerLoc, System.currentTimeMillis());

        // Set gravity and initial velocity immediately
        entity.setGravity(true);
        entity.setVelocity(new Vector(0, -0.2, 0));

        // Mark entity as processed to prevent double-handling
        if (entity instanceof Mob mob) {
            mob.getPersistentDataContainer()
                    .set(spawnedFromSpawnerKey, PersistentDataType.BYTE, (byte) 1);

            // Disable AI for player-placed spawners
            if (plugin.getCoreConfig().shouldDisableAIOnPlacedSpawners()) {
                if (isPlayerPlacedSpawner(spawner.getBlock())) {
                    mob.setAware(false);
                }
            }
        }

        // Apply velocity with delay as backup
        applyDelayedVelocity(entity);
    }

    /**
     * Catch any spawner spawns that didn't fire SpawnerSpawnEvent (fallback). Uses HIGH priority to
     * modify entities before other plugins process them.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!isEnabled()) return;
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER) return;

        Entity entity = event.getEntity();

        // Skip if already processed via SpawnerSpawnEvent
        if (entity instanceof Mob mob) {
            if (mob.getPersistentDataContainer()
                    .has(spawnedFromSpawnerKey, PersistentDataType.BYTE)) {
                return;
            }

            // Set gravity and initial velocity immediately
            entity.setGravity(true);
            entity.setVelocity(new Vector(0, -0.2, 0));

            // Mark as processed
            mob.getPersistentDataContainer()
                    .set(spawnedFromSpawnerKey, PersistentDataType.BYTE, (byte) 1);

            // Find the spawner that caused this spawn
            Location spawnLoc = event.getLocation();
            Location nearestSpawner = findNearestSpawner(spawnLoc, 5.0);

            if (nearestSpawner != null) {
                vanillaSpawnTimes.put(nearestSpawner, System.currentTimeMillis());

                // Disable AI for player-placed spawners
                if (plugin.getCoreConfig().shouldDisableAIOnPlacedSpawners()) {
                    Block block = nearestSpawner.getBlock();
                    if (isPlayerPlacedSpawner(block)) {
                        mob.setAware(false);
                    }
                }
            }
        }

        // Apply velocity with delay as backup
        applyDelayedVelocity(entity);
    }

    /** Ensure entity falls to the ground. If velocity doesn't work, teleport to ground. */
    private void applyDelayedVelocity(Entity entity) {
        // First: try velocity
        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            if (entity.isValid() && !entity.isDead()) {
                                entity.setGravity(true);
                                entity.setVelocity(new Vector(0, -0.5, 0));
                            }
                        },
                        1L);

        // Second check: if still floating after 10 ticks, teleport to ground
        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            if (entity.isValid() && !entity.isDead()) {
                                Location loc = entity.getLocation();
                                Block below = loc.getBlock();

                                // Check if entity is floating (not on solid ground)
                                if (!below.getRelative(0, -1, 0).getType().isSolid()) {
                                    // Find solid ground below
                                    Location groundLoc = findGroundBelow(loc);
                                    if (groundLoc != null) {
                                        float fallDistance =
                                                (float) (loc.getY() - groundLoc.getY());

                                        // Set fall distance before teleport so damage is applied on
                                        // landing
                                        entity.setFallDistance(fallDistance);
                                        entity.teleport(groundLoc);
                                    }
                                }
                            }
                        },
                        10L);
    }

    /**
     * Find ground or liquid surface below a location (searches up to 50 blocks down). Returns
     * position on top of solid ground, or on surface of water/lava so mobs interact with it.
     */
    private Location findGroundBelow(Location loc) {
        World world = loc.getWorld();
        if (world == null) return null;

        int startY = loc.getBlockY();
        int minY = world.getMinHeight();

        for (int y = startY - 1; y >= minY && y >= startY - 50; y--) {
            Block block = world.getBlockAt(loc.getBlockX(), y, loc.getBlockZ());
            Material type = block.getType();

            // If we hit liquid, find the TOP of the liquid and teleport there
            if (type == Material.WATER || type == Material.LAVA) {
                // Find the surface of this liquid
                for (int surfaceY = y; surfaceY <= startY; surfaceY++) {
                    Block above = world.getBlockAt(loc.getBlockX(), surfaceY + 1, loc.getBlockZ());
                    if (above.getType() != Material.WATER && above.getType() != Material.LAVA) {
                        // Found the surface, teleport on top of liquid
                        return new Location(
                                world,
                                loc.getX(),
                                surfaceY + 1,
                                loc.getZ(),
                                loc.getYaw(),
                                loc.getPitch());
                    }
                }
                // Liquid goes all the way up, just teleport to top of where we started
                return new Location(
                        world, loc.getX(), y + 1, loc.getZ(), loc.getYaw(), loc.getPitch());
            }

            if (type.isSolid()) {
                // Found solid ground, return position on top of it
                return new Location(
                        world, loc.getX(), y + 1, loc.getZ(), loc.getYaw(), loc.getPitch());
            }
        }
        return null;
    }

    /** Discover spawners in loaded chunks and add them to the registry */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!isEnabled()) return;

        Chunk chunk = event.getChunk();
        ChunkKey key = new ChunkKey(chunk.getWorld(), chunk.getX(), chunk.getZ());

        Set<Location> spawners = new HashSet<>();

        // Scan chunk tile entities for spawners
        for (BlockState tileEntity : chunk.getTileEntities()) {
            if (tileEntity instanceof CreatureSpawner) {
                spawners.add(tileEntity.getLocation());
            }
        }

        if (!spawners.isEmpty()) {
            chunkSpawnerMap.put(key, spawners);
        }
    }

    /** Remove spawners from registry when chunks unload */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (!isEnabled()) return;

        Chunk chunk = event.getChunk();
        ChunkKey key = new ChunkKey(chunk.getWorld(), chunk.getX(), chunk.getZ());
        chunkSpawnerMap.remove(key);
    }

    /** Add newly placed spawners to the registry and mark as player-placed. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isEnabled()) return;
        if (event.getBlock().getType() != Material.SPAWNER) return;

        Location loc = event.getBlock().getLocation();
        ChunkKey key = new ChunkKey(loc.getWorld(), loc.getChunk().getX(), loc.getChunk().getZ());

        chunkSpawnerMap.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(loc);

        // Mark spawner as player-placed using our own key
        // This ensures AI disabling works even without server-admin
        Block block = event.getBlock();
        if (block.getState() instanceof TileState tileState) {
            tileState
                    .getPersistentDataContainer()
                    .set(localPlayerPlacedKey, PersistentDataType.BYTE, (byte) 1);
            tileState.update();
        }
    }

    /** Remove broken spawners from the registry */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isEnabled()) return;
        if (event.getBlock().getType() != Material.SPAWNER) return;

        Location loc = event.getBlock().getLocation();
        ChunkKey key = new ChunkKey(loc.getWorld(), loc.getChunk().getX(), loc.getChunk().getZ());

        Set<Location> spawners = chunkSpawnerMap.get(key);
        if (spawners != null) {
            spawners.remove(loc);
            if (spawners.isEmpty()) {
                chunkSpawnerMap.remove(key);
            }
        }
    }

    private void startSpawnerTask() {
        // Check every 2 seconds (40 ticks) - spawners typically have 10-40 second delays
        spawnerTask =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                plugin,
                                () -> {
                                    if (!isEnabled()) return;

                                    long now = System.currentTimeMillis();

                                    // Clear the set of spawners processed this cycle
                                    processedThisCycle.clear();

                                    for (World world : Bukkit.getWorlds()) {
                                        for (Player player : world.getPlayers()) {
                                            checkSpawnersNearPlayer(player, now);
                                        }
                                    }

                                    // Cleanup old entries (older than 60 seconds)
                                    spawnerLastSpawn
                                            .entrySet()
                                            .removeIf(e -> now - e.getValue() > 60000);
                                    vanillaSpawnTimes
                                            .entrySet()
                                            .removeIf(e -> now - e.getValue() > 60000);
                                },
                                40L,
                                40L); // Every 2 seconds
    }

    private void checkSpawnersNearPlayer(Player player, long now) {
        Location playerLoc = player.getLocation();
        World world = player.getWorld();

        // Get the player's chunk coordinates
        int playerChunkX = playerLoc.getBlockX() >> 4;
        int playerChunkZ = playerLoc.getBlockZ() >> 4;

        // Check spawners in nearby chunks (3x3 chunk area = max 9 chunks)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChunkKey key = new ChunkKey(world, playerChunkX + dx, playerChunkZ + dz);
                Set<Location> spawners = chunkSpawnerMap.get(key);

                if (spawners != null) {
                    for (Location spawnerLoc : spawners) {
                        double distance = spawnerLoc.add(0.5, 0.5, 0.5).distance(playerLoc);
                        if (distance <= ACTIVATION_RANGE) {
                            Block block = world.getBlockAt(spawnerLoc);
                            if (block.getType() == Material.SPAWNER) {
                                processSpawner(block, now);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Find the nearest spawner to a given location within a certain radius. Used to identify which
     * spawner caused a spawn event.
     */
    private Location findNearestSpawner(Location loc, double maxDistance) {
        if (loc.getWorld() == null) return null;

        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;

        Location nearest = null;
        double nearestDistSq = maxDistance * maxDistance;

        // Check spawners in the spawn location's chunk and adjacent chunks
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChunkKey key = new ChunkKey(loc.getWorld(), chunkX + dx, chunkZ + dz);
                Set<Location> spawners = chunkSpawnerMap.get(key);

                if (spawners != null) {
                    for (Location spawnerLoc : spawners) {
                        double distSq = spawnerLoc.distanceSquared(loc);
                        if (distSq < nearestDistSq) {
                            nearestDistSq = distSq;
                            nearest = spawnerLoc;
                        }
                    }
                }
            }
        }

        return nearest;
    }

    private void processSpawner(Block block, long now) {
        if (!(block.getState() instanceof CreatureSpawner spawner)) return;

        Location spawnerLoc = block.getLocation();

        // Skip if already processed this cycle (prevents double-spawning with multiple players)
        if (processedThisCycle.contains(spawnerLoc)) return;
        processedThisCycle.add(spawnerLoc);

        EntityType entityType = spawner.getSpawnedType();
        if (entityType == null || !entityType.isAlive()) return;

        // Get spawner's delay in milliseconds
        int delayTicks = spawner.getDelay();
        int minDelay = spawner.getMinSpawnDelay();
        int maxDelay = spawner.getMaxSpawnDelay();

        // Use average delay if current delay is 0 (just spawned)
        long delayMs = (delayTicks > 0 ? delayTicks : (minDelay + maxDelay) / 2) * 50L;

        // Check if vanilla recently spawned from this spawner
        Long lastVanillaSpawn = vanillaSpawnTimes.get(spawnerLoc);
        if (lastVanillaSpawn != null && now - lastVanillaSpawn < delayMs) {
            // Vanilla is working, don't interfere
            return;
        }

        // Check if we recently spawned from this spawner
        Long lastManualSpawn = spawnerLastSpawn.get(spawnerLoc);
        if (lastManualSpawn != null && now - lastManualSpawn < delayMs) {
            return;
        }

        // Check nearby entity count
        int nearbyCount = countNearbyEntities(spawnerLoc, entityType);
        if (nearbyCount >= MAX_NEARBY_ENTITIES) return;

        // Get spawn count - limit large mobs to 1 per cycle
        int spawnCount = spawner.getSpawnCount();
        if (isLargeMob(entityType)) {
            spawnCount = 1; // Iron golems, ravagers, etc. spawn 1 at a time
        }

        int spawnRange = spawner.getSpawnRange();
        int spawned = 0;

        // Vanilla behavior: try to spawn up to spawnCount mobs, but each attempt
        // only succeeds ~25-50% of the time due to position checks
        for (int i = 0; i < spawnCount; i++) {
            // Each mob has limited attempts to find a valid spot
            int attemptsPerMob = 4;

            for (int attempt = 0; attempt < attemptsPerMob; attempt++) {
                Location spawnLoc = getRandomSpawnLocation(spawnerLoc, spawnRange);
                if (spawnLoc != null && isValidSpawnLocation(spawnLoc, entityType)) {
                    try {
                        // Cache player-placed check before spawn
                        boolean isPlayerPlaced = isPlayerPlacedSpawner(block);
                        boolean shouldDisableAI =
                                plugin.getCoreConfig().shouldDisableAIOnPlacedSpawners()
                                        && isPlayerPlaced;

                        // Use spawn consumer to set properties DURING spawn (before entity added to
                        // world)
                        Entity entity =
                                spawnerLoc
                                        .getWorld()
                                        .spawnEntity(
                                                spawnLoc,
                                                entityType,
                                                CreatureSpawnEvent.SpawnReason.SPAWNER,
                                                e -> {
                                                    // Set gravity and initial velocity during spawn
                                                    e.setGravity(true);
                                                    e.setVelocity(new Vector(0, -0.2, 0));

                                                    if (e instanceof Mob mob) {
                                                        // Mark entity as processed
                                                        mob.getPersistentDataContainer()
                                                                .set(
                                                                        spawnedFromSpawnerKey,
                                                                        PersistentDataType.BYTE,
                                                                        (byte) 1);

                                                        // Disable AI for player-placed spawners
                                                        if (shouldDisableAI) {
                                                            mob.setAware(false);
                                                        }
                                                    }
                                                });

                        if (entity != null) {
                            // Also apply delayed velocity as backup
                            applyDelayedVelocity(entity);

                            spawned++;
                            break; // Successfully spawned this mob, move to next
                        }
                    } catch (Exception e) {
                        // Spawn failed, try next attempt
                    }
                }
            }
            // If all attempts failed for this mob, it just doesn't spawn (vanilla behavior)
        }

        if (spawned > 0) {
            spawnerLastSpawn.put(spawnerLoc, now);
        }
    }

    private Location getRandomSpawnLocation(Location spawnerLoc, int spawnRange) {
        World world = spawnerLoc.getWorld();
        if (world == null) return null;

        // Use the spawner's configured range (default is 4, making 9x9 area)
        double x =
                spawnerLoc.getX()
                        + 0.5
                        + (ThreadLocalRandom.current().nextDouble() - 0.5) * (spawnRange * 2);
        double y =
                spawnerLoc.getY()
                        + ThreadLocalRandom.current()
                                .nextInt(-SPAWN_RANGE_VERTICAL, SPAWN_RANGE_VERTICAL + 1);
        double z =
                spawnerLoc.getZ()
                        + 0.5
                        + (ThreadLocalRandom.current().nextDouble() - 0.5) * (spawnRange * 2);

        return new Location(world, x, y, z);
    }

    private boolean isValidSpawnLocation(Location loc, EntityType entityType) {
        if (loc == null || loc.getWorld() == null) return false;

        Block feetBlock = loc.getBlock();
        Block headBlock = feetBlock.getRelative(0, 1, 0);
        Block aboveHeadBlock = feetBlock.getRelative(0, 2, 0);

        // Check feet position - must be air or passable non-solid
        if (!isSpawnableBlock(feetBlock)) {
            return false;
        }

        // For 2-tall mobs, check head position
        if (is2TallEntity(entityType)) {
            if (!isSpawnableBlock(headBlock)) {
                return false;
            }
        }

        // For 3-tall mobs (Iron Golem, Enderman, etc.), check above head too
        if (is3TallEntity(entityType)) {
            if (!isSpawnableBlock(aboveHeadBlock)) {
                return false;
            }
        }

        // For wide mobs, check adjacent blocks
        if (isWideMob(entityType)) {
            // Check a small area around the spawn point
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    Block adjacent = feetBlock.getRelative(dx, 0, dz);
                    Block adjacentHead = feetBlock.getRelative(dx, 1, dz);
                    if (!isSpawnableBlock(adjacent) || !isSpawnableBlock(adjacentHead)) {
                        return false;
                    }
                }
            }
        }

        // Check light level for hostile mobs
        if (isHostileMob(entityType)) {
            if (feetBlock.getLightLevel() > 7) {
                return false;
            }
        }

        return true;
    }

    private boolean isSpawnableBlock(Block block) {
        Material type = block.getType();

        // Explicitly allow air blocks
        if (type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR) {
            return true;
        }

        // Allow water for aquatic spawns
        if (type == Material.WATER) {
            return true;
        }

        // Reject solid blocks
        if (type.isSolid()) {
            return false;
        }

        // For other blocks, must be passable and non-solid
        return block.isPassable();
    }

    private int countNearbyEntities(Location loc, EntityType type) {
        if (loc.getWorld() == null) return 0;

        return (int)
                loc.getWorld().getNearbyEntities(loc, 8, 4, 8).stream()
                        .filter(e -> e.getType() == type)
                        .count();
    }

    private boolean is2TallEntity(EntityType type) {
        return switch (type) {
            case ZOMBIE,
                            SKELETON,
                            CREEPER,
                            WITCH,
                            PILLAGER,
                            VINDICATOR,
                            ZOMBIE_VILLAGER,
                            HUSK,
                            STRAY,
                            DROWNED,
                            WITHER_SKELETON,
                            PIGLIN,
                            PIGLIN_BRUTE,
                            ZOMBIFIED_PIGLIN,
                            BLAZE,
                            BOGGED,
                            BREEZE,
                            VILLAGER,
                            WANDERING_TRADER,
                            EVOKER,
                            ILLUSIONER ->
                    true;
            case SPIDER, CAVE_SPIDER, SILVERFISH, SLIME, MAGMA_CUBE, CHICKEN, RABBIT, BAT, VEX ->
                    false;
                // Iron Golem and Enderman are 3-tall, handled separately
            case IRON_GOLEM, ENDERMAN -> false;
            default -> true;
        };
    }

    private boolean is3TallEntity(EntityType type) {
        return switch (type) {
            case IRON_GOLEM, ENDERMAN, WITHER, RAVAGER -> true;
            default -> false;
        };
    }

    private boolean isWideMob(EntityType type) {
        return switch (type) {
            case IRON_GOLEM, RAVAGER, HOGLIN, ZOGLIN, SPIDER, CAVE_SPIDER, GHAST, PHANTOM -> true;
            default -> false;
        };
    }

    private boolean isHostileMob(EntityType type) {
        return switch (type) {
            case ZOMBIE,
                            SKELETON,
                            CREEPER,
                            SPIDER,
                            CAVE_SPIDER,
                            ENDERMAN,
                            WITCH,
                            PILLAGER,
                            VINDICATOR,
                            ZOMBIE_VILLAGER,
                            HUSK,
                            STRAY,
                            DROWNED,
                            WITHER_SKELETON,
                            PIGLIN_BRUTE,
                            BLAZE,
                            GHAST,
                            MAGMA_CUBE,
                            SLIME,
                            SILVERFISH,
                            PHANTOM,
                            BOGGED,
                            BREEZE ->
                    true;
            default -> false;
        };
    }

    private boolean isLargeMob(EntityType type) {
        return switch (type) {
            case IRON_GOLEM, RAVAGER, WITHER, ELDER_GUARDIAN, ENDER_DRAGON, WARDEN, GHAST -> true;
            default -> false;
        };
    }

    /**
     * Check if a spawner block was placed by a player. First checks server-admin's PDC key, falls
     * back to local tracking.
     */
    private boolean isPlayerPlacedSpawner(Block block) {
        if (block == null || block.getType() != Material.SPAWNER) return false;

        BlockState state = block.getState();
        if (!(state instanceof TileState tileState)) return false;

        var pdc = tileState.getPersistentDataContainer();

        // Check server-admin's key first (for compatibility)
        if (pdc.has(playerPlacedKey, PersistentDataType.BYTE)) {
            return true;
        }

        // Fallback to our own local key
        return pdc.has(localPlayerPlacedKey, PersistentDataType.BYTE);
    }
}
