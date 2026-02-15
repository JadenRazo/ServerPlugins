package net.serverplugins.events.util;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers.NativeGameMode;
import com.comphenix.protocol.wrappers.EnumWrappers.PlayerInfoAction;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedRemoteChatSessionData;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * A fake player entity that can be spawned using ProtocolLib packets. This creates a true player
 * model with complete skin textures.
 */
public class FakePlayer {

    private static final Map<Integer, FakePlayer> activeFakePlayers = new ConcurrentHashMap<>();

    private final Plugin plugin;
    private final ProtocolManager protocolManager;
    private final int entityId;
    private final UUID uuid;
    private final String name;
    private final WrappedGameProfile gameProfile;
    private Location location;
    private String displayName;
    private boolean glowing;
    private boolean spawned;
    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();

    /**
     * Create a new fake player with the skin of an existing player.
     *
     * @param plugin The plugin instance
     * @param skinSource The player whose skin to copy
     * @param location The spawn location
     * @param displayName The display name (supports color codes)
     */
    public FakePlayer(Plugin plugin, Player skinSource, Location location, String displayName) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        this.entityId =
                ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE / 2, Integer.MAX_VALUE);
        this.uuid = UUID.randomUUID();
        this.name = generateShortName(); // Short name to avoid tab list clutter
        this.location = location.clone();
        this.displayName = displayName;
        this.glowing = false;
        this.spawned = false;

        // Create game profile with skin textures
        this.gameProfile = createGameProfile(skinSource);

        activeFakePlayers.put(entityId, this);
    }

    /** Generate a short random name for the fake player. */
    private String generateShortName() {
        return "P"
                + Integer.toHexString(entityId)
                        .substring(0, Math.min(8, Integer.toHexString(entityId).length()));
    }

    /** Create a WrappedGameProfile with the skin of the source player. */
    private WrappedGameProfile createGameProfile(Player skinSource) {
        WrappedGameProfile profile = new WrappedGameProfile(uuid, name);

        try {
            // Get the source player's profile
            PlayerProfile sourceProfile = skinSource.getPlayerProfile();
            PlayerTextures textures = sourceProfile.getTextures();

            if (textures.getSkin() != null) {
                // Get texture properties from the source player
                // We need to extract the skin data from Bukkit's profile
                WrappedGameProfile sourceWrapped = WrappedGameProfile.fromPlayer(skinSource);

                // Copy texture properties
                for (WrappedSignedProperty property :
                        sourceWrapped.getProperties().get("textures")) {
                    profile.getProperties().put("textures", property);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[FakePlayer] Failed to copy skin textures", e);
        }

        return profile;
    }

    /** Spawn the fake player for all online players. */
    public void spawnForAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            spawnFor(player);
        }
        spawned = true;
    }

    /** Spawn the fake player for a specific viewer. */
    public void spawnFor(Player viewer) {
        if (viewers.contains(viewer.getUniqueId())) {
            return; // Already spawned for this player
        }

        try {
            plugin.getLogger().info("[FakePlayer] Spawning fake player for " + viewer.getName());

            // 1. Send PlayerInfo ADD packet to add to tab list temporarily
            // This must come BEFORE the spawn packet so client knows the player data
            sendPlayerInfoAdd(viewer);

            // 2. Small delay to let client process player info before spawn
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!viewer.isOnline()) return;

                    try {
                        // 3. Send spawn packet
                        sendSpawnPacket(viewer);

                        // 4. Send metadata packet (custom name, glowing, etc.)
                        sendMetadataPacket(viewer);

                        // 5. Remove from tab list after client has loaded skin (40 ticks = 2
                        // seconds)
                        // Needs longer delay to ensure skin textures are cached
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (viewer.isOnline()) {
                                    sendPlayerInfoRemove(viewer);
                                    plugin.getLogger()
                                            .info(
                                                    "[FakePlayer] Removed "
                                                            + name
                                                            + " from tab list for "
                                                            + viewer.getName());
                                }
                            }
                        }.runTaskLater(plugin, 40L); // 2 seconds delay for skin to load

                    } catch (Exception e) {
                        plugin.getLogger()
                                .log(
                                        Level.WARNING,
                                        "[FakePlayer] Failed during spawn sequence for "
                                                + viewer.getName(),
                                        e);
                    }
                }
            }.runTaskLater(plugin, 2L); // Small delay after player info

            viewers.add(viewer.getUniqueId());
        } catch (Exception e) {
            plugin.getLogger()
                    .log(
                            Level.WARNING,
                            "[FakePlayer] Failed to spawn fake player for " + viewer.getName(),
                            e);
        }
    }

    /**
     * Send PlayerInfo ADD packet. In MC 1.19.3+, this uses the new PLAYER_INFO packet format with
     * actions set.
     */
    private void sendPlayerInfoAdd(Player viewer) throws InvocationTargetException {
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO);

        // Create player info data with profile that has skin textures
        PlayerInfoData playerInfoData =
                new PlayerInfoData(
                        uuid,
                        0, // latency
                        true, // listed - must be true initially for spawn to work
                        NativeGameMode.SURVIVAL,
                        gameProfile,
                        WrappedChatComponent.fromText(displayName),
                        (WrappedRemoteChatSessionData) null);

        // In MC 1.19.3+, we need ADD_PLAYER to register the player
        // UPDATE_LISTED controls tab list visibility
        // We set listed=true initially, then remove from tab list after spawn
        packet.getPlayerInfoActions()
                .write(
                        0,
                        EnumSet.of(
                                PlayerInfoAction.ADD_PLAYER,
                                PlayerInfoAction.UPDATE_GAME_MODE,
                                PlayerInfoAction.UPDATE_LISTED,
                                PlayerInfoAction.UPDATE_LATENCY,
                                PlayerInfoAction.UPDATE_DISPLAY_NAME));
        packet.getPlayerInfoDataLists().write(1, Collections.singletonList(playerInfoData));

        protocolManager.sendServerPacket(viewer, packet);
    }

    /**
     * Send PlayerInfo packet to hide from tab list. In MC 1.19.3+, we use UPDATE_LISTED with
     * listed=false to hide from tab. We also send PLAYER_INFO_REMOVE to fully clean up.
     */
    private void sendPlayerInfoRemove(Player viewer) {
        try {
            // First, set listed=false to hide from tab list
            PacketContainer updatePacket =
                    protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO);

            PlayerInfoData unlistedData =
                    new PlayerInfoData(
                            uuid,
                            0,
                            false, // listed = false to hide from tab list
                            NativeGameMode.SURVIVAL,
                            gameProfile,
                            null,
                            (WrappedRemoteChatSessionData) null);

            updatePacket
                    .getPlayerInfoActions()
                    .write(0, EnumSet.of(PlayerInfoAction.UPDATE_LISTED));
            updatePacket.getPlayerInfoDataLists().write(1, Collections.singletonList(unlistedData));
            protocolManager.sendServerPacket(viewer, updatePacket);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[FakePlayer] Failed to remove from tab list", e);
        }
    }

    /**
     * Send spawn player packet. In MC 1.20.2+, players use SPAWN_ENTITY packet with entity type
     * PLAYER. The client will look up the player data from the previously sent PLAYER_INFO packet.
     */
    private void sendSpawnPacket(Player viewer) throws InvocationTargetException {
        // Use SPAWN_ENTITY for MC 1.20.2+ (entity type player)
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.SPAWN_ENTITY);

        packet.getIntegers().write(0, entityId); // Entity ID
        packet.getUUIDs().write(0, uuid); // Entity UUID

        // Entity type - use the Bukkit EntityType wrapper
        packet.getEntityTypeModifier().write(0, org.bukkit.entity.EntityType.PLAYER);

        // Position (doubles)
        packet.getDoubles()
                .write(0, location.getX())
                .write(1, location.getY())
                .write(2, location.getZ());

        // Pitch and Yaw (bytes) - note: order is pitch, yaw, head yaw
        byte pitch = (byte) (location.getPitch() * 256.0F / 360.0F);
        byte yaw = (byte) (location.getYaw() * 256.0F / 360.0F);

        packet.getBytes()
                .write(0, pitch) // Pitch
                .write(1, yaw); // Yaw

        // Head yaw (byte index 2)
        packet.getBytes().write(2, yaw);

        // Data value (int) - for players, this should be 0
        // Check if there's a data field
        try {
            packet.getIntegers().write(1, 0); // data
        } catch (Exception ignored) {
            // Field might not exist in this version
        }

        // Velocity (short values, not ints in newer versions)
        try {
            packet.getShorts()
                    .write(0, (short) 0) // velocityX
                    .write(1, (short) 0) // velocityY
                    .write(2, (short) 0); // velocityZ
        } catch (Exception e) {
            // Try integers if shorts don't work
            try {
                packet.getIntegers().write(1, 0).write(2, 0).write(3, 0);
            } catch (Exception ignored) {
                // Velocity might be handled differently
            }
        }

        protocolManager.sendServerPacket(viewer, packet);

        plugin.getLogger()
                .info(
                        "[FakePlayer] Sent spawn packet for entity "
                                + entityId
                                + " to "
                                + viewer.getName());
    }

    /**
     * Send entity metadata packet. Uses the MC 1.20+ compatible WrappedDataValue API instead of the
     * deprecated WrappedDataWatcher.
     */
    private void sendMetadataPacket(Player viewer) throws InvocationTargetException {
        PacketContainer packet =
                protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);

        packet.getIntegers().write(0, entityId);

        // Build list of WrappedDataValue objects (MC 1.20+ API)
        List<WrappedDataValue> dataValues = new ArrayList<>();

        // Entity flags (index 0): glowing = 0x40
        byte flags = 0;
        if (glowing) {
            flags |= 0x40; // Glowing flag
        }
        dataValues.add(new WrappedDataValue(0, WrappedDataWatcher.Registry.get(Byte.class), flags));

        // Custom name (index 2): Optional<IChatBaseComponent>
        if (displayName != null && !displayName.isEmpty()) {
            Optional<Object> optionalName =
                    Optional.of(WrappedChatComponent.fromText(displayName).getHandle());
            dataValues.add(
                    new WrappedDataValue(
                            2,
                            WrappedDataWatcher.Registry.getChatComponentSerializer(true),
                            optionalName));

            // Custom name visible (index 3): Boolean
            dataValues.add(
                    new WrappedDataValue(3, WrappedDataWatcher.Registry.get(Boolean.class), true));
        }

        // Skin parts (index 17 for players): show all parts
        byte skinParts = 0x7F; // All parts visible
        dataValues.add(
                new WrappedDataValue(17, WrappedDataWatcher.Registry.get(Byte.class), skinParts));

        // Use getDataValueCollectionModifier() for MC 1.20+ (not getWatchableCollectionModifier())
        packet.getDataValueCollectionModifier().write(0, dataValues);

        protocolManager.sendServerPacket(viewer, packet);
    }

    /** Update the display name for all viewers. */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        for (UUID viewerId : viewers) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                try {
                    sendMetadataPacket(viewer);
                } catch (Exception e) {
                    plugin.getLogger()
                            .log(Level.WARNING, "[FakePlayer] Failed to update display name", e);
                }
            }
        }
    }

    /** Set whether this fake player should glow. */
    public void setGlowing(boolean glowing) {
        this.glowing = glowing;
        for (UUID viewerId : viewers) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                try {
                    sendMetadataPacket(viewer);
                } catch (Exception e) {
                    plugin.getLogger()
                            .log(Level.WARNING, "[FakePlayer] Failed to update glowing state", e);
                }
            }
        }
    }

    /** Teleport the fake player to a new location. */
    public void teleport(Location newLocation) {
        this.location = newLocation.clone();

        for (UUID viewerId : viewers) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                try {
                    sendTeleportPacket(viewer);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "[FakePlayer] Failed to teleport", e);
                }
            }
        }
    }

    /** Send teleport packet. */
    private void sendTeleportPacket(Player viewer) throws InvocationTargetException {
        PacketContainer packet =
                protocolManager.createPacket(PacketType.Play.Server.ENTITY_TELEPORT);

        packet.getIntegers().write(0, entityId);
        packet.getDoubles()
                .write(0, location.getX())
                .write(1, location.getY())
                .write(2, location.getZ());
        packet.getBytes()
                .write(0, (byte) (location.getYaw() * 256.0F / 360.0F))
                .write(1, (byte) (location.getPitch() * 256.0F / 360.0F));
        packet.getBooleans().write(0, false); // on ground

        protocolManager.sendServerPacket(viewer, packet);
    }

    /** Remove the fake player for all viewers. */
    public void remove() {
        for (UUID viewerId : new ArrayList<>(viewers)) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                removeFor(viewer);
            }
        }
        viewers.clear();
        spawned = false;
        activeFakePlayers.remove(entityId);
    }

    /** Remove the fake player for a specific viewer. */
    public void removeFor(Player viewer) {
        if (!viewers.contains(viewer.getUniqueId())) {
            return;
        }

        try {
            // Send entity destroy packet
            PacketContainer packet =
                    protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
            packet.getIntLists().write(0, Collections.singletonList(entityId));
            protocolManager.sendServerPacket(viewer, packet);

            // Also ensure removed from tab list
            sendPlayerInfoRemove(viewer);

            viewers.remove(viewer.getUniqueId());
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[FakePlayer] Failed to remove fake player", e);
        }
    }

    /** Get the entity ID of this fake player. */
    public int getEntityId() {
        return entityId;
    }

    /** Get the UUID of this fake player. */
    public UUID getUniqueId() {
        return uuid;
    }

    /** Get the current location. */
    public Location getLocation() {
        return location.clone();
    }

    /** Check if spawned. */
    public boolean isSpawned() {
        return spawned;
    }

    /** Get a fake player by entity ID. */
    public static FakePlayer getByEntityId(int entityId) {
        return activeFakePlayers.get(entityId);
    }

    /** Remove all active fake players (for cleanup). */
    public static void removeAll() {
        for (FakePlayer fakePlayer : new ArrayList<>(activeFakePlayers.values())) {
            fakePlayer.remove();
        }
        activeFakePlayers.clear();
    }
}
