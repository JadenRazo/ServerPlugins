package net.serverplugins.events.util;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityStatus;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * A packet-based fake player NPC using PacketEvents library. Creates a true player entity with full
 * skin rendering (body, arms, legs). Supports movement, custom nametags, and interaction detection.
 */
public class PacketEventsNPC {

    private static final Map<Integer, PacketEventsNPC> activeNPCs = new ConcurrentHashMap<>();
    private static PacketListenerAbstract interactionListener;
    private static boolean listenerRegistered = false;

    private final Plugin plugin;
    private final int entityId;
    private final UUID uuid;
    private final String profileName;
    private final UserProfile userProfile;
    private Location location;
    private String displayName;
    private boolean glowing;
    private boolean spawned;
    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
    private Consumer<Player> interactionHandler;

    /**
     * Create a new PacketEvents-based NPC with the skin of an existing player.
     *
     * @param plugin The plugin instance
     * @param skinSource The player whose skin to copy
     * @param location The spawn location
     * @param displayName The display name (supports legacy color codes)
     */
    public PacketEventsNPC(
            Plugin plugin, Player skinSource, Location location, String displayName) {
        this.plugin = plugin;
        this.entityId = ThreadLocalRandom.current().nextInt(10000, Integer.MAX_VALUE / 2);
        this.uuid = UUID.randomUUID();
        this.profileName = generateProfileName();
        this.location = location.clone();
        this.displayName = displayName;
        this.glowing = false;
        this.spawned = false;

        // Create user profile with skin textures
        this.userProfile = createUserProfile(skinSource);

        // Register in active NPCs map
        activeNPCs.put(entityId, this);

        // Register global interaction listener if not already registered
        registerInteractionListener();

        plugin.getLogger()
                .info(
                        "[PacketEventsNPC] Created NPC with entity ID "
                                + entityId
                                + " using skin of "
                                + skinSource.getName());
    }

    /** Generate a short profile name for the NPC. */
    private String generateProfileName() {
        return "NPC_"
                + Integer.toHexString(entityId)
                        .substring(0, Math.min(6, Integer.toHexString(entityId).length()));
    }

    /** Create a UserProfile with the skin textures from the source player. */
    private UserProfile createUserProfile(Player skinSource) {
        UserProfile profile = new UserProfile(uuid, profileName);

        try {
            // Use Paper's PlayerProfile API directly (no reflection needed)
            com.destroystokyo.paper.profile.PlayerProfile paperProfile =
                    skinSource.getPlayerProfile();

            for (com.destroystokyo.paper.profile.ProfileProperty property :
                    paperProfile.getProperties()) {
                if ("textures".equals(property.getName())) {
                    TextureProperty textureProp =
                            new TextureProperty(
                                    "textures", property.getValue(), property.getSignature());
                    profile.setTextureProperties(Collections.singletonList(textureProp));
                    plugin.getLogger()
                            .info(
                                    "[PacketEventsNPC] Successfully copied skin textures from "
                                            + skinSource.getName());
                    break;
                }
            }

            if (profile.getTextureProperties() == null
                    || profile.getTextureProperties().isEmpty()) {
                plugin.getLogger()
                        .warning(
                                "[PacketEventsNPC] No texture data found for "
                                        + skinSource.getName());
            }
        } catch (Exception e) {
            plugin.getLogger()
                    .log(Level.WARNING, "[PacketEventsNPC] Failed to copy skin textures", e);
        }

        return profile;
    }

    /** Register the global interaction listener for all NPCs. */
    private void registerInteractionListener() {
        if (listenerRegistered) return;

        interactionListener =
                new PacketListenerAbstract(PacketListenerPriority.NORMAL) {
                    @Override
                    public void onPacketReceive(PacketReceiveEvent event) {
                        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

                        WrapperPlayClientInteractEntity packet =
                                new WrapperPlayClientInteractEntity(event);
                        int targetId = packet.getEntityId();

                        PacketEventsNPC npc = activeNPCs.get(targetId);
                        if (npc != null && npc.interactionHandler != null) {
                            Player player = (Player) event.getPlayer();

                            // Run on main thread
                            Bukkit.getScheduler()
                                    .runTask(
                                            npc.plugin,
                                            () -> {
                                                if (npc.spawned && npc.interactionHandler != null) {
                                                    npc.interactionHandler.accept(player);
                                                }
                                            });
                        }
                    }
                };

        PacketEvents.getAPI().getEventManager().registerListener(interactionListener);
        listenerRegistered = true;
    }

    /** Spawn the NPC for all online players. */
    public void spawnForAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            spawnFor(player);
        }
        spawned = true;
    }

    /** Spawn the NPC for a specific viewer. */
    public void spawnFor(Player viewer) {
        if (viewers.contains(viewer.getUniqueId())) {
            return;
        }

        try {
            Object channel = PacketEvents.getAPI().getPlayerManager().getChannel(viewer);
            if (channel == null) {
                plugin.getLogger()
                        .warning("[PacketEventsNPC] Could not get channel for " + viewer.getName());
                return;
            }

            // 1. Send PlayerInfo packet to add player data (including skin)
            sendPlayerInfoAdd(viewer, channel);

            // 2. Small delay, then spawn the entity and send metadata
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!viewer.isOnline()) return;

                    try {
                        Object ch = PacketEvents.getAPI().getPlayerManager().getChannel(viewer);
                        if (ch == null) return;

                        // Send spawn packet
                        sendSpawnPacket(viewer, ch);

                        // Send metadata (name, glowing, skin parts)
                        sendMetadataPacket(viewer, ch);

                        // Send head rotation
                        sendHeadRotation(viewer, ch);

                        // Remove from tab list after skin loads (2 seconds)
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (viewer.isOnline()) {
                                    try {
                                        Object c =
                                                PacketEvents.getAPI()
                                                        .getPlayerManager()
                                                        .getChannel(viewer);
                                        if (c != null) {
                                            sendPlayerInfoRemove(viewer, c);
                                        }
                                    } catch (Exception e) {
                                        plugin.getLogger()
                                                .log(
                                                        Level.WARNING,
                                                        "[PacketEventsNPC] Failed to remove from tab",
                                                        e);
                                    }
                                }
                            }
                        }.runTaskLater(plugin, 40L);

                    } catch (Exception e) {
                        plugin.getLogger()
                                .log(
                                        Level.WARNING,
                                        "[PacketEventsNPC] Failed during spawn sequence",
                                        e);
                    }
                }
            }.runTaskLater(plugin, 2L);

            viewers.add(viewer.getUniqueId());
            plugin.getLogger().info("[PacketEventsNPC] Spawned NPC for " + viewer.getName());

        } catch (Exception e) {
            plugin.getLogger()
                    .log(
                            Level.WARNING,
                            "[PacketEventsNPC] Failed to spawn NPC for " + viewer.getName(),
                            e);
        }
    }

    /** Send PlayerInfo ADD packet to register the player with skin data. */
    private void sendPlayerInfoAdd(Player viewer, Object channel) {
        WrapperPlayServerPlayerInfoUpdate.PlayerInfo playerInfo =
                new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                        userProfile,
                        true, // listed - needed temporarily for spawn
                        0, // latency
                        GameMode.SURVIVAL,
                        net.kyori.adventure.text.Component.text(displayName),
                        null // chat session
                        );

        WrapperPlayServerPlayerInfoUpdate packet =
                new WrapperPlayServerPlayerInfoUpdate(
                        EnumSet.of(
                                WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                                WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED,
                                WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE),
                        playerInfo);

        PacketEvents.getAPI().getPlayerManager().sendPacket(channel, packet);
    }

    /** Send PlayerInfo packet to remove from tab list (set listed=false). */
    private void sendPlayerInfoRemove(Player viewer, Object channel) {
        // First update listed to false
        WrapperPlayServerPlayerInfoUpdate.PlayerInfo unlistedInfo =
                new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                        userProfile,
                        false, // listed = false
                        0,
                        GameMode.SURVIVAL,
                        null,
                        null);

        WrapperPlayServerPlayerInfoUpdate updatePacket =
                new WrapperPlayServerPlayerInfoUpdate(
                        EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED),
                        unlistedInfo);

        PacketEvents.getAPI().getPlayerManager().sendPacket(channel, updatePacket);

        // Then send remove packet
        WrapperPlayServerPlayerInfoRemove removePacket =
                new WrapperPlayServerPlayerInfoRemove(uuid);
        PacketEvents.getAPI().getPlayerManager().sendPacket(channel, removePacket);
    }

    /** Send spawn entity packet. */
    private void sendSpawnPacket(Player viewer, Object channel) {
        // Use Vector3d for position
        Vector3d position = new Vector3d(location.getX(), location.getY(), location.getZ());

        WrapperPlayServerSpawnEntity packet =
                new WrapperPlayServerSpawnEntity(
                        entityId,
                        Optional.of(uuid),
                        EntityTypes.PLAYER,
                        position,
                        location.getPitch(),
                        location.getYaw(),
                        location.getYaw(), // head yaw
                        0, // data
                        null // velocity
                        );

        PacketEvents.getAPI().getPlayerManager().sendPacket(channel, packet);
    }

    /** Send entity metadata packet. */
    private void sendMetadataPacket(Player viewer, Object channel) {
        List<EntityData> metadata = new ArrayList<>();

        // Entity flags (index 0) - glowing flag is 0x40
        byte flags = 0;
        if (glowing) {
            flags |= 0x40;
        }
        metadata.add(new EntityData(0, EntityDataTypes.BYTE, flags));

        // Custom name (index 2) - Optional<Component>
        if (displayName != null && !displayName.isEmpty()) {
            // Convert legacy color codes to Component
            Component nameComponent =
                    net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                            .legacySection()
                            .deserialize(displayName);
            metadata.add(
                    new EntityData(
                            2, EntityDataTypes.OPTIONAL_ADV_COMPONENT, Optional.of(nameComponent)));

            // Custom name visible (index 3)
            metadata.add(new EntityData(3, EntityDataTypes.BOOLEAN, true));
        }

        // Skin parts displayed (index 17 for players) - show all parts (0x7F)
        byte skinParts = 0x7F;
        metadata.add(new EntityData(17, EntityDataTypes.BYTE, skinParts));

        WrapperPlayServerEntityMetadata packet =
                new WrapperPlayServerEntityMetadata(entityId, metadata);
        PacketEvents.getAPI().getPlayerManager().sendPacket(channel, packet);
    }

    /** Send head rotation packet. */
    private void sendHeadRotation(Player viewer, Object channel) {
        WrapperPlayServerEntityHeadLook packet =
                new WrapperPlayServerEntityHeadLook(entityId, location.getYaw());
        PacketEvents.getAPI().getPlayerManager().sendPacket(channel, packet);
    }

    /** Update the display name for all viewers. */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;

        for (UUID viewerId : viewers) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                try {
                    Object channel = PacketEvents.getAPI().getPlayerManager().getChannel(viewer);
                    if (channel != null) {
                        sendMetadataPacket(viewer, channel);
                    }
                } catch (Exception e) {
                    plugin.getLogger()
                            .log(
                                    Level.WARNING,
                                    "[PacketEventsNPC] Failed to update display name",
                                    e);
                }
            }
        }
    }

    /** Set whether this NPC should glow. */
    public void setGlowing(boolean glowing) {
        this.glowing = glowing;

        for (UUID viewerId : viewers) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                try {
                    Object channel = PacketEvents.getAPI().getPlayerManager().getChannel(viewer);
                    if (channel != null) {
                        sendMetadataPacket(viewer, channel);
                    }
                } catch (Exception e) {
                    plugin.getLogger()
                            .log(
                                    Level.WARNING,
                                    "[PacketEventsNPC] Failed to update glowing state",
                                    e);
                }
            }
        }
    }

    /** Teleport the NPC to a new location. */
    public void teleport(Location newLocation) {
        this.location = newLocation.clone();

        for (UUID viewerId : viewers) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                try {
                    Object channel = PacketEvents.getAPI().getPlayerManager().getChannel(viewer);
                    if (channel != null) {
                        sendTeleportPacket(viewer, channel);
                        sendHeadRotation(viewer, channel);
                    }
                } catch (Exception e) {
                    plugin.getLogger()
                            .log(Level.WARNING, "[PacketEventsNPC] Failed to teleport", e);
                }
            }
        }
    }

    /** Send teleport packet. */
    private void sendTeleportPacket(Player viewer, Object channel) {
        Vector3d position = new Vector3d(location.getX(), location.getY(), location.getZ());

        WrapperPlayServerEntityTeleport packet =
                new WrapperPlayServerEntityTeleport(
                        entityId,
                        position,
                        location.getYaw(),
                        location.getPitch(),
                        false // on ground
                        );

        PacketEvents.getAPI().getPlayerManager().sendPacket(channel, packet);
    }

    /** Apply velocity to the NPC (for jumping/movement effects). */
    public void setVelocity(Vector velocity) {
        for (UUID viewerId : viewers) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                try {
                    Object channel = PacketEvents.getAPI().getPlayerManager().getChannel(viewer);
                    if (channel != null) {
                        sendVelocityPacket(viewer, channel, velocity);
                    }
                } catch (Exception e) {
                    plugin.getLogger()
                            .log(Level.WARNING, "[PacketEventsNPC] Failed to set velocity", e);
                }
            }
        }
    }

    /** Send velocity packet. */
    private void sendVelocityPacket(Player viewer, Object channel, Vector velocity) {
        // PacketEvents uses velocity as Vector3d (blocks per tick * 8000)
        Vector3d peVelocity = new Vector3d(velocity.getX(), velocity.getY(), velocity.getZ());

        WrapperPlayServerEntityVelocity packet =
                new WrapperPlayServerEntityVelocity(entityId, peVelocity);
        PacketEvents.getAPI().getPlayerManager().sendPacket(channel, packet);
    }

    /**
     * Play an animation on the NPC.
     *
     * @param animation 0=swing main arm, 1=take damage, 2=leave bed, 3=swing offhand, 4=critical
     *     effect, 5=magic critical effect
     */
    public void playAnimation(int animation) {
        for (UUID viewerId : viewers) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                try {
                    Object channel = PacketEvents.getAPI().getPlayerManager().getChannel(viewer);
                    if (channel != null) {
                        WrapperPlayServerEntityAnimation packet =
                                new WrapperPlayServerEntityAnimation(
                                        entityId,
                                        WrapperPlayServerEntityAnimation.EntityAnimationType
                                                .values()[animation]);
                        PacketEvents.getAPI().getPlayerManager().sendPacket(channel, packet);
                    }
                } catch (Exception e) {
                    plugin.getLogger()
                            .log(Level.WARNING, "[PacketEventsNPC] Failed to play animation", e);
                }
            }
        }
    }

    /** Play hurt animation with red tint effect. */
    public void playHurtAnimation() {
        // Send damage animation (type 1)
        playAnimation(1);

        // Send entity status packet for red tint (status byte 2)
        for (UUID viewerId : viewers) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                try {
                    Object channel = PacketEvents.getAPI().getPlayerManager().getChannel(viewer);
                    if (channel != null) {
                        WrapperPlayServerEntityStatus statusPacket =
                                new WrapperPlayServerEntityStatus(entityId, (byte) 2);
                        PacketEvents.getAPI().getPlayerManager().sendPacket(channel, statusPacket);
                    }
                } catch (Exception e) {
                    plugin.getLogger()
                            .log(
                                    Level.WARNING,
                                    "[PacketEventsNPC] Failed to play hurt animation",
                                    e);
                }
            }
        }
    }

    /** Play arm swing (main hand or offhand). */
    public void playArmSwing(boolean offHand) {
        int animationType = offHand ? 3 : 0;
        playAnimation(animationType);
    }

    /** Set crouching/sneaking pose. */
    public void setCrouching(boolean crouch) {
        for (UUID viewerId : viewers) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                try {
                    Object channel = PacketEvents.getAPI().getPlayerManager().getChannel(viewer);
                    if (channel != null) {
                        List<EntityData> metadata = new ArrayList<>();
                        com.github.retrooper.packetevents.protocol.entity.pose.EntityPose pose =
                                crouch
                                        ? com.github.retrooper.packetevents.protocol.entity.pose
                                                .EntityPose.CROUCHING
                                        : com.github.retrooper.packetevents.protocol.entity.pose
                                                .EntityPose.STANDING;
                        metadata.add(new EntityData(6, EntityDataTypes.ENTITY_POSE, pose));

                        WrapperPlayServerEntityMetadata packet =
                                new WrapperPlayServerEntityMetadata(entityId, metadata);
                        PacketEvents.getAPI().getPlayerManager().sendPacket(channel, packet);
                    }
                } catch (Exception e) {
                    plugin.getLogger()
                            .log(Level.WARNING, "[PacketEventsNPC] Failed to set crouching", e);
                }
            }
        }
    }

    /** Set the interaction handler that gets called when players interact with this NPC. */
    public void setInteractionHandler(Consumer<Player> handler) {
        this.interactionHandler = handler;
    }

    /** Send equipment to all viewers. */
    private void sendEquipmentToAll(List<Equipment> equipment) {
        WrapperPlayServerEntityEquipment packet =
                new WrapperPlayServerEntityEquipment(entityId, equipment);
        for (UUID viewerId : viewers) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                try {
                    Object channel = PacketEvents.getAPI().getPlayerManager().getChannel(viewer);
                    if (channel != null) {
                        PacketEvents.getAPI().getPlayerManager().sendPacket(channel, packet);
                    }
                } catch (Exception e) {
                    plugin.getLogger()
                            .log(Level.WARNING, "[PacketEventsNPC] Failed to send equipment", e);
                }
            }
        }
    }

    /** Convert a Bukkit ItemStack to a PacketEvents ItemStack. */
    private com.github.retrooper.packetevents.protocol.item.ItemStack toPacketItem(
            ItemStack bukkitItem) {
        if (bukkitItem == null || bukkitItem.getType().isAir()) {
            return com.github.retrooper.packetevents.protocol.item.ItemStack.EMPTY;
        }
        return SpigotConversionUtil.fromBukkitItemStack(bukkitItem);
    }

    /** Set equipment in main hand. */
    public void setMainHand(ItemStack item) {
        sendEquipmentToAll(List.of(new Equipment(EquipmentSlot.MAIN_HAND, toPacketItem(item))));
    }

    /** Set equipment in off hand. */
    public void setOffHand(ItemStack item) {
        sendEquipmentToAll(List.of(new Equipment(EquipmentSlot.OFF_HAND, toPacketItem(item))));
    }

    /** Set helmet equipment. */
    public void setHelmet(ItemStack item) {
        sendEquipmentToAll(List.of(new Equipment(EquipmentSlot.HELMET, toPacketItem(item))));
    }

    /** Set all equipment at once. */
    public void setEquipment(ItemStack mainHand, ItemStack offHand, ItemStack helmet) {
        List<Equipment> equipment = new ArrayList<>();
        if (mainHand != null) {
            equipment.add(new Equipment(EquipmentSlot.MAIN_HAND, toPacketItem(mainHand)));
        }
        if (offHand != null) {
            equipment.add(new Equipment(EquipmentSlot.OFF_HAND, toPacketItem(offHand)));
        }
        if (helmet != null) {
            equipment.add(new Equipment(EquipmentSlot.HELMET, toPacketItem(helmet)));
        }
        if (!equipment.isEmpty()) {
            sendEquipmentToAll(equipment);
        }
    }

    /** Set full armor and hands at once. */
    public void setFullEquipment(
            ItemStack mainHand,
            ItemStack offHand,
            ItemStack helmet,
            ItemStack chestplate,
            ItemStack leggings,
            ItemStack boots) {
        List<Equipment> equipment = new ArrayList<>();
        if (mainHand != null) {
            equipment.add(new Equipment(EquipmentSlot.MAIN_HAND, toPacketItem(mainHand)));
        }
        if (offHand != null) {
            equipment.add(new Equipment(EquipmentSlot.OFF_HAND, toPacketItem(offHand)));
        }
        if (helmet != null) {
            equipment.add(new Equipment(EquipmentSlot.HELMET, toPacketItem(helmet)));
        }
        if (chestplate != null) {
            equipment.add(new Equipment(EquipmentSlot.CHEST_PLATE, toPacketItem(chestplate)));
        }
        if (leggings != null) {
            equipment.add(new Equipment(EquipmentSlot.LEGGINGS, toPacketItem(leggings)));
        }
        if (boots != null) {
            equipment.add(new Equipment(EquipmentSlot.BOOTS, toPacketItem(boots)));
        }
        if (!equipment.isEmpty()) {
            sendEquipmentToAll(equipment);
        }
    }

    /** Remove the NPC for all viewers. */
    public void remove() {
        spawned = false;

        for (UUID viewerId : new ArrayList<>(viewers)) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                removeFor(viewer);
            }
        }

        viewers.clear();
        activeNPCs.remove(entityId);
        interactionHandler = null;

        plugin.getLogger().info("[PacketEventsNPC] Removed NPC with entity ID " + entityId);
    }

    /** Remove the NPC for a specific viewer. */
    public void removeFor(Player viewer) {
        if (!viewers.contains(viewer.getUniqueId())) return;

        try {
            Object channel = PacketEvents.getAPI().getPlayerManager().getChannel(viewer);
            if (channel != null) {
                // Send entity destroy packet
                WrapperPlayServerDestroyEntities packet =
                        new WrapperPlayServerDestroyEntities(entityId);
                PacketEvents.getAPI().getPlayerManager().sendPacket(channel, packet);

                // Also remove from player info
                sendPlayerInfoRemove(viewer, channel);
            }

            viewers.remove(viewer.getUniqueId());
        } catch (Exception e) {
            plugin.getLogger()
                    .log(
                            Level.WARNING,
                            "[PacketEventsNPC] Failed to remove NPC for " + viewer.getName(),
                            e);
        }
    }

    /** Get the entity ID of this NPC. */
    public int getEntityId() {
        return entityId;
    }

    /** Get the UUID of this NPC. */
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

    /** Get the viewers of this NPC. */
    public Set<UUID> getViewers() {
        return Collections.unmodifiableSet(viewers);
    }

    /** Check if PacketEvents is available on the server. */
    public static boolean isAvailable() {
        try {
            Class.forName("com.github.retrooper.packetevents.PacketEvents");
            return PacketEvents.getAPI() != null && PacketEvents.getAPI().isInitialized();
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** Get an NPC by entity ID. */
    public static PacketEventsNPC getByEntityId(int entityId) {
        return activeNPCs.get(entityId);
    }

    /** Remove all active NPCs (for cleanup). */
    public static void removeAll() {
        for (PacketEventsNPC npc : new ArrayList<>(activeNPCs.values())) {
            npc.remove();
        }
        activeNPCs.clear();
    }

    /** Unregister the global interaction listener (call on plugin disable). */
    public static void unregisterListener() {
        if (listenerRegistered && interactionListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(interactionListener);
            listenerRegistered = false;
            interactionListener = null;
        }
    }
}
