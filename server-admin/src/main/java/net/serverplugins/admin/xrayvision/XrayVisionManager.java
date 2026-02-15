package net.serverplugins.admin.xrayvision;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class XrayVisionManager {

    private final ServerAdmin plugin;
    private final Map<UUID, XrayVisionSession> sessions;
    private final ProtocolManager protocolManager;
    private final AtomicInteger entityIdCounter;

    private static final int SCAN_RADIUS = 32;
    private static final int UPDATE_INTERVAL_TICKS = 40; // 2 seconds
    private static final int MAX_ORES_PER_SCAN = 500; // Limit marker count per scan

    // Target ores to scan for
    private final Set<Material> targetOres;

    public XrayVisionManager(ServerAdmin plugin) {
        this.plugin = plugin;
        this.sessions = new ConcurrentHashMap<>();
        this.protocolManager =
                plugin.isProtocolLibEnabled() ? ProtocolLibrary.getProtocolManager() : null;
        this.entityIdCounter = new AtomicInteger(Integer.MAX_VALUE - 100000);

        // Initialize target ores from config or defaults
        this.targetOres = plugin.getAdminConfig().getXrayVisionOres();
        if (this.targetOres.isEmpty()) {
            initDefaultOres();
        }
    }

    private void initDefaultOres() {
        // Default ores if config is empty
        targetOres.add(Material.DIAMOND_ORE);
        targetOres.add(Material.DEEPSLATE_DIAMOND_ORE);
        targetOres.add(Material.GOLD_ORE);
        targetOres.add(Material.DEEPSLATE_GOLD_ORE);
        targetOres.add(Material.NETHER_GOLD_ORE);
        targetOres.add(Material.EMERALD_ORE);
        targetOres.add(Material.DEEPSLATE_EMERALD_ORE);
        targetOres.add(Material.ANCIENT_DEBRIS);
        targetOres.add(Material.SPAWNER);
    }

    public boolean toggle(Player player) {
        if (isEnabled(player)) {
            disable(player);
            return false;
        } else {
            return enable(player);
        }
    }

    public boolean enable(Player player) {
        if (protocolManager == null) {
            TextUtil.sendError(player, "Xray vision requires ProtocolLib to be installed!");
            return false;
        }

        XrayVisionSession session = new XrayVisionSession(player.getUniqueId());
        sessions.put(player.getUniqueId(), session);

        // Setup scoreboard teams for ore colors
        setupTeams(player);

        // Start the scanning task
        startScanTask(player, session);

        // Send confirmation
        TextUtil.send(
                player,
                plugin.getAdminConfig().getPrefix()
                        + "<green>Xray vision enabled - ores will glow");

        return true;
    }

    public void disable(Player player) {
        XrayVisionSession session = sessions.remove(player.getUniqueId());
        if (session == null) return;

        session.cancelTask();

        // Remove all marker entities
        removeAllMarkers(player, session);

        // Cleanup teams
        cleanupTeams(player);

        // Send confirmation
        TextUtil.send(player, plugin.getAdminConfig().getPrefix() + "<red>Xray vision disabled");
    }

    private void setupTeams(Player player) {
        // Use the player's current scoreboard to avoid overwriting TAB/other plugin scoreboards
        Scoreboard sb = player.getScoreboard();

        for (OreType type : OreType.values()) {
            String teamName =
                    "xray_"
                            + type.name()
                                    .substring(0, Math.min(type.name().length(), 10))
                                    .toLowerCase();
            Team team = sb.getTeam(teamName);
            if (team == null) {
                team = sb.registerNewTeam(teamName);
            }
            team.setColor(chatColorToNamedTextColor(type.getColor()));
        }
    }

    private void startScanTask(Player player, XrayVisionSession session) {
        BukkitTask task =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                plugin,
                                () -> {
                                    if (!player.isOnline()
                                            || !sessions.containsKey(player.getUniqueId())) {
                                        session.cancelTask();
                                        return;
                                    }

                                    // Run block scanning async
                                    Bukkit.getScheduler()
                                            .runTaskAsynchronously(
                                                    plugin,
                                                    () -> {
                                                        scanAndUpdate(player, session);
                                                    });
                                },
                                0L,
                                UPDATE_INTERVAL_TICKS);

        session.setTaskId(task.getTaskId());
    }

    private void scanAndUpdate(Player player, XrayVisionSession session) {
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) return;

        Set<Location> newOreLocations = new HashSet<>();
        Map<Location, OreType> oreTypes = new HashMap<>();

        // Calculate chunk boundaries (4 chunk radius around player)
        int centerChunkX = center.getBlockX() >> 4;
        int centerChunkZ = center.getBlockZ() >> 4;
        int chunkRadius = 4; // ~64 block radius

        int minY = Math.max(world.getMinHeight(), center.getBlockY() - SCAN_RADIUS);
        int maxY = Math.min(world.getMaxHeight(), center.getBlockY() + SCAN_RADIUS);

        // Collect ChunkSnapshots for nearby loaded chunks
        Map<String, ChunkSnapshot> snapshots = new HashMap<>();
        for (int chunkX = centerChunkX - chunkRadius;
                chunkX <= centerChunkX + chunkRadius;
                chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius;
                    chunkZ <= centerChunkZ + chunkRadius;
                    chunkZ++) {
                final int finalChunkX = chunkX;
                final int finalChunkZ = chunkZ;

                // Get chunk snapshot on main thread synchronously
                try {
                    ChunkSnapshot snapshot =
                            Bukkit.getScheduler()
                                    .callSyncMethod(
                                            plugin,
                                            () -> {
                                                if (world.isChunkLoaded(finalChunkX, finalChunkZ)) {
                                                    return world.getChunkAt(
                                                                    finalChunkX, finalChunkZ)
                                                            .getChunkSnapshot(false, false, false);
                                                }
                                                return null;
                                            })
                                    .get();

                    if (snapshot != null) {
                        snapshots.put(finalChunkX + "," + finalChunkZ, snapshot);
                    }
                } catch (Exception e) {
                    // Skip this chunk if we can't get snapshot
                    continue;
                }
            }
        }

        // Scan using ChunkSnapshots (thread-safe, no main thread access)
        int oresFound = 0;
        outerLoop:
        for (Map.Entry<String, ChunkSnapshot> entry : snapshots.entrySet()) {
            String[] coords = entry.getKey().split(",");
            int chunkX = Integer.parseInt(coords[0]);
            int chunkZ = Integer.parseInt(coords[1]);
            ChunkSnapshot snapshot = entry.getValue();

            int chunkWorldX = chunkX << 4;
            int chunkWorldZ = chunkZ << 4;

            // Scan each block in the chunk within Y range
            for (int x = 0; x < 16; x++) {
                int worldX = chunkWorldX + x;

                // Check if this X coordinate is within player radius
                if (Math.abs(worldX - center.getBlockX()) > SCAN_RADIUS) continue;

                for (int z = 0; z < 16; z++) {
                    int worldZ = chunkWorldZ + z;

                    // Check if this Z coordinate is within player radius
                    if (Math.abs(worldZ - center.getBlockZ()) > SCAN_RADIUS) continue;

                    for (int y = minY; y <= maxY; y++) {
                        // Check if this Y coordinate is within player radius
                        if (Math.abs(y - center.getBlockY()) > SCAN_RADIUS) continue;

                        // Use ChunkSnapshot to get block type (thread-safe)
                        Material type = snapshot.getBlockType(x, y, z);

                        if (targetOres.contains(type)) {
                            Location loc = new Location(world, worldX, y, worldZ);
                            newOreLocations.add(loc);
                            OreType oreType = OreType.fromMaterial(type);
                            if (oreType != null) {
                                oreTypes.put(loc, oreType);
                            }

                            // Break out early if we hit max markers
                            oresFound++;
                            if (oresFound >= MAX_ORES_PER_SCAN) {
                                break outerLoop;
                            }
                        }
                    }
                }
            }
        }

        // Calculate diff - snapshot the keys to avoid ConcurrentModificationException
        // since this runs async while main thread may modify the map
        Set<Location> currentMarkerKeys = Set.copyOf(session.getActiveMarkers().keySet());

        Set<Location> toRemove = new HashSet<>(currentMarkerKeys);
        toRemove.removeAll(newOreLocations);

        Set<Location> toAdd = new HashSet<>(newOreLocations);
        toAdd.removeAll(currentMarkerKeys);

        // Apply changes on main thread
        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            if (!player.isOnline() || !sessions.containsKey(player.getUniqueId())) {
                                return;
                            }

                            // Remove old markers
                            for (Location loc : toRemove) {
                                OreMarker marker = session.getActiveMarkers().remove(loc);
                                if (marker != null) {
                                    sendDestroyPacket(player, marker.getEntityId());
                                }
                            }

                            // Add new markers
                            for (Location loc : toAdd) {
                                OreType oreType = oreTypes.get(loc);
                                if (oreType != null) {
                                    int entityId = entityIdCounter.getAndDecrement();
                                    UUID entityUuid = UUID.randomUUID();
                                    OreMarker marker =
                                            new OreMarker(entityId, entityUuid, loc, oreType);
                                    session.getActiveMarkers().put(loc, marker);

                                    sendSpawnPacket(player, marker);
                                    addToTeam(player, marker);
                                }
                            }
                        });
    }

    private void sendSpawnPacket(Player player, OreMarker marker) {
        try {
            // Spawn entity packet (armor stand)
            PacketContainer spawnPacket =
                    protocolManager.createPacket(PacketType.Play.Server.SPAWN_ENTITY);

            spawnPacket.getIntegers().write(0, marker.getEntityId());
            spawnPacket.getUUIDs().write(0, marker.getEntityUuid());
            spawnPacket.getEntityTypeModifier().write(0, EntityType.ARMOR_STAND);

            Location loc = marker.getLocation();
            spawnPacket
                    .getDoubles()
                    .write(0, loc.getX() + 0.5)
                    .write(1, loc.getY())
                    .write(2, loc.getZ() + 0.5);

            protocolManager.sendServerPacket(player, spawnPacket);

            // Metadata packet (invisible + glowing + marker flag)
            PacketContainer metaPacket =
                    protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);

            metaPacket.getIntegers().write(0, marker.getEntityId());

            // Build metadata values
            List<WrappedDataValue> dataValues = new ArrayList<>();

            // Byte index 0: Entity flags (invisible=0x20, glowing=0x40)
            byte flags = (byte) (0x20 | 0x40); // Invisible + Glowing
            dataValues.add(
                    new WrappedDataValue(0, WrappedDataWatcher.Registry.get(Byte.class), flags));

            // Armor stand flags at index 15: Marker flag (0x10)
            byte armorStandFlags = (byte) 0x10; // Marker flag - no hitbox
            dataValues.add(
                    new WrappedDataValue(
                            15, WrappedDataWatcher.Registry.get(Byte.class), armorStandFlags));

            // Use immutable copy to prevent ConcurrentModificationException during packet
            // serialization
            metaPacket.getDataValueCollectionModifier().write(0, List.copyOf(dataValues));

            protocolManager.sendServerPacket(player, metaPacket);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn xray marker: " + e.getMessage());
        }
    }

    private void sendDestroyPacket(Player player, int entityId) {
        try {
            PacketContainer destroyPacket =
                    protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
            destroyPacket.getIntLists().write(0, List.of(entityId));
            protocolManager.sendServerPacket(player, destroyPacket);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to destroy xray marker: " + e.getMessage());
        }
    }

    private void addToTeam(Player player, OreMarker marker) {
        String teamName =
                "xray_"
                        + marker.getOreType()
                                .name()
                                .substring(0, Math.min(marker.getOreType().name().length(), 10))
                                .toLowerCase();
        Scoreboard sb = player.getScoreboard();
        Team team = sb.getTeam(teamName);
        if (team != null) {
            // Add entity UUID string to team for glow color
            team.addEntry(marker.getEntityUuid().toString());
        }
    }

    private void removeAllMarkers(Player player, XrayVisionSession session) {
        // Snapshot values to avoid ConcurrentModificationException
        List<Integer> entityIds = new ArrayList<>();
        for (OreMarker marker : List.copyOf(session.getActiveMarkers().values())) {
            entityIds.add(marker.getEntityId());
        }

        if (!entityIds.isEmpty() && protocolManager != null) {
            try {
                PacketContainer destroyPacket =
                        protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
                destroyPacket.getIntLists().write(0, entityIds);
                protocolManager.sendServerPacket(player, destroyPacket);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to destroy markers: " + e.getMessage());
            }
        }

        session.getActiveMarkers().clear();
    }

    private void cleanupTeams(Player player) {
        // Only unregister xray teams, don't touch the player's scoreboard assignment
        // This preserves TAB/other plugin scoreboards
        Scoreboard sb = player.getScoreboard();
        for (OreType type : OreType.values()) {
            String teamName =
                    "xray_"
                            + type.name()
                                    .substring(0, Math.min(type.name().length(), 10))
                                    .toLowerCase();
            Team team = sb.getTeam(teamName);
            if (team != null) {
                team.unregister();
            }
        }
    }

    private org.bukkit.ChatColor chatColorToNamedTextColor(ChatColor color) {
        // Return the same ChatColor for team color setting
        return color;
    }

    public boolean isEnabled(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public void handleQuit(Player player) {
        XrayVisionSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            session.cancelTask();
        }
    }

    public void shutdown() {
        for (UUID uuid : new HashSet<>(sessions.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                disable(player);
            } else {
                XrayVisionSession session = sessions.remove(uuid);
                if (session != null) {
                    session.cancelTask();
                }
            }
        }
    }
}
