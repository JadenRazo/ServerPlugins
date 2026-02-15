package net.serverplugins.admin.freecam;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.Pair;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.serverplugins.admin.ServerAdmin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Handles ProtocolLib packet operations for freecam functionality. This class is only loaded when
 * ProtocolLib is available.
 */
public class FreecamPacketHandler {

    private final ServerAdmin plugin;
    private final ProtocolManager protocolManager;
    private final AtomicInteger entityIdCounter;

    public FreecamPacketHandler(ServerAdmin plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        this.entityIdCounter = new AtomicInteger(Integer.MAX_VALUE - 200000);
    }

    /**
     * Spawns a ghost armor stand entity at the specified location for all viewers. The ghost
     * displays the player's armor and held item.
     *
     * @return The entity ID of the spawned ghost, or -1 if failed
     */
    public int spawnGhostEntity(
            Player originalPlayer,
            Location location,
            UUID ghostUuid,
            ItemStack[] armorContents,
            ItemStack mainHandItem) {
        int entityId = entityIdCounter.getAndDecrement();

        // Get all online players except the freecam player
        Collection<Player> viewers = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(originalPlayer)) {
                viewers.add(online);
            }
        }

        for (Player viewer : viewers) {
            try {
                spawnGhostForViewer(
                        viewer,
                        entityId,
                        ghostUuid,
                        location,
                        originalPlayer.getName(),
                        armorContents,
                        mainHandItem);
            } catch (Exception e) {
                plugin.getLogger()
                        .warning(
                                "Failed to spawn ghost for "
                                        + viewer.getName()
                                        + ": "
                                        + e.getMessage());
            }
        }

        return entityId;
    }

    /** Spawns the ghost entity for a specific viewer. */
    public void spawnGhostForViewer(
            Player viewer,
            int entityId,
            UUID ghostUuid,
            Location location,
            String playerName,
            ItemStack[] armorContents,
            ItemStack mainHandItem) {
        try {
            // Spawn armor stand entity
            PacketContainer spawnPacket =
                    protocolManager.createPacket(PacketType.Play.Server.SPAWN_ENTITY);

            spawnPacket.getIntegers().write(0, entityId);
            spawnPacket.getUUIDs().write(0, ghostUuid);
            spawnPacket.getEntityTypeModifier().write(0, EntityType.ARMOR_STAND);

            spawnPacket
                    .getDoubles()
                    .write(0, location.getX())
                    .write(1, location.getY())
                    .write(2, location.getZ());

            // Set rotation
            spawnPacket
                    .getBytes()
                    .write(0, (byte) (location.getYaw() * 256.0F / 360.0F))
                    .write(1, (byte) (location.getPitch() * 256.0F / 360.0F));

            protocolManager.sendServerPacket(viewer, spawnPacket);

            // Send metadata packet
            sendGhostMetadata(viewer, entityId, playerName);

            // Send equipment packet
            sendGhostEquipment(viewer, entityId, armorContents, mainHandItem);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn ghost entity: " + e.getMessage());
        }
    }

    /** Sends metadata packet for the ghost entity. */
    private void sendGhostMetadata(Player viewer, int entityId, String playerName) {
        try {
            PacketContainer metaPacket =
                    protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
            metaPacket.getIntegers().write(0, entityId);

            List<WrappedDataValue> dataValues = new ArrayList<>();

            // Entity flags at index 0 - visible (not invisible), no glowing
            byte flags = 0;
            dataValues.add(
                    new WrappedDataValue(0, WrappedDataWatcher.Registry.get(Byte.class), flags));

            // Custom name at index 2 (Optional<Component>)
            if (playerName != null && plugin.getAdminConfig().freecamShowNameTag()) {
                Optional<Object> nameComponent =
                        Optional.of(
                                com.comphenix.protocol.wrappers.WrappedChatComponent.fromText(
                                                playerName)
                                        .getHandle());
                dataValues.add(
                        new WrappedDataValue(
                                2,
                                WrappedDataWatcher.Registry.getChatComponentSerializer(true),
                                nameComponent));

                // Custom name visible at index 3
                dataValues.add(
                        new WrappedDataValue(
                                3, WrappedDataWatcher.Registry.get(Boolean.class), true));
            }

            // Armor stand flags at index 15
            // 0x01 = Small, 0x04 = Has arms, 0x08 = No base plate
            byte armorStandFlags = (byte) (0x04 | 0x08); // Has arms, no base plate
            dataValues.add(
                    new WrappedDataValue(
                            15, WrappedDataWatcher.Registry.get(Byte.class), armorStandFlags));

            // Use immutable copy to prevent ConcurrentModificationException during packet
            // serialization
            metaPacket.getDataValueCollectionModifier().write(0, List.copyOf(dataValues));
            protocolManager.sendServerPacket(viewer, metaPacket);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send ghost metadata: " + e.getMessage());
        }
    }

    /** Sends equipment packet for the ghost entity. */
    private void sendGhostEquipment(
            Player viewer, int entityId, ItemStack[] armorContents, ItemStack mainHandItem) {
        try {
            PacketContainer equipPacket =
                    protocolManager.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);
            equipPacket.getIntegers().write(0, entityId);

            List<Pair<EnumWrappers.ItemSlot, ItemStack>> equipment = new ArrayList<>();

            // Add main hand item
            if (mainHandItem != null && plugin.getAdminConfig().freecamShowHeldItem()) {
                equipment.add(new Pair<>(EnumWrappers.ItemSlot.MAINHAND, mainHandItem));
            }

            // Add armor if configured
            if (armorContents != null && plugin.getAdminConfig().freecamShowArmor()) {
                if (armorContents.length > 3 && armorContents[3] != null) {
                    equipment.add(new Pair<>(EnumWrappers.ItemSlot.HEAD, armorContents[3]));
                }
                if (armorContents.length > 2 && armorContents[2] != null) {
                    equipment.add(new Pair<>(EnumWrappers.ItemSlot.CHEST, armorContents[2]));
                }
                if (armorContents.length > 1 && armorContents[1] != null) {
                    equipment.add(new Pair<>(EnumWrappers.ItemSlot.LEGS, armorContents[1]));
                }
                if (armorContents.length > 0 && armorContents[0] != null) {
                    equipment.add(new Pair<>(EnumWrappers.ItemSlot.FEET, armorContents[0]));
                }
            }

            if (!equipment.isEmpty()) {
                equipPacket.getSlotStackPairLists().write(0, equipment);
                protocolManager.sendServerPacket(viewer, equipPacket);
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send ghost equipment: " + e.getMessage());
        }
    }

    /** Removes the ghost entity for all online players. */
    public void removeGhostEntity(int entityId, Player excludePlayer) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(excludePlayer)) {
                removeGhostForViewer(viewer, entityId);
            }
        }
    }

    /** Removes the ghost entity for a specific viewer. */
    public void removeGhostForViewer(Player viewer, int entityId) {
        try {
            PacketContainer destroyPacket =
                    protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
            destroyPacket.getIntLists().write(0, List.of(entityId));
            protocolManager.sendServerPacket(viewer, destroyPacket);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to remove ghost entity: " + e.getMessage());
        }
    }

    /** Updates the ghost entity's head rotation for a more natural look. */
    public void updateGhostRotation(int entityId, float yaw, float pitch, Player excludePlayer) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(excludePlayer)) continue;

            try {
                // Head rotation packet
                PacketContainer headRotation =
                        protocolManager.createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
                headRotation.getIntegers().write(0, entityId);
                headRotation.getBytes().write(0, (byte) (yaw * 256.0F / 360.0F));
                protocolManager.sendServerPacket(viewer, headRotation);

            } catch (Exception e) {
                // Silent fail for rotation updates
            }
        }
    }
}
