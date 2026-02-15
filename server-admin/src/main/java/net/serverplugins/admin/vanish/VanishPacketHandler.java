package net.serverplugins.admin.vanish;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;
import net.serverplugins.admin.ServerAdmin;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Handles ProtocolLib packet interception for vanish functionality. This class is only loaded when
 * ProtocolLib is available to avoid ClassNotFoundException.
 */
public class VanishPacketHandler {

    private final ServerAdmin plugin;
    private final ProtocolManager protocolManager;
    private final Map<Location, UUID> silentContainerOpens;
    private final BiPredicate<Player, Player> canSeePredicate;

    public VanishPacketHandler(
            ServerAdmin plugin,
            Map<Location, UUID> silentContainerOpens,
            BiPredicate<Player, Player> canSeePredicate) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        this.silentContainerOpens = silentContainerOpens;
        this.canSeePredicate = canSeePredicate;

        registerPacketListeners();
    }

    private void registerPacketListeners() {
        // Block entity spawn packets for vanished players
        // NAMED_ENTITY_SPAWN was removed in 1.20.2+, use SPAWN_ENTITY instead
        PacketType spawnPacket = PacketType.Play.Server.SPAWN_ENTITY;
        protocolManager.addPacketListener(
                new PacketAdapter(plugin, ListenerPriority.HIGH, spawnPacket) {
                    @Override
                    public void onPacketSending(PacketEvent event) {
                        handleEntityPacket(event);
                    }
                });

        // Block entity metadata packets
        protocolManager.addPacketListener(
                new PacketAdapter(
                        plugin, ListenerPriority.HIGH, PacketType.Play.Server.ENTITY_METADATA) {
                    @Override
                    public void onPacketSending(PacketEvent event) {
                        handleEntityPacket(event);
                    }
                });

        // Block relative move packets
        protocolManager.addPacketListener(
                new PacketAdapter(
                        plugin, ListenerPriority.HIGH, PacketType.Play.Server.REL_ENTITY_MOVE) {
                    @Override
                    public void onPacketSending(PacketEvent event) {
                        handleEntityPacket(event);
                    }
                });

        // Block entity teleport packets
        protocolManager.addPacketListener(
                new PacketAdapter(
                        plugin, ListenerPriority.HIGH, PacketType.Play.Server.ENTITY_TELEPORT) {
                    @Override
                    public void onPacketSending(PacketEvent event) {
                        handleEntityPacket(event);
                    }
                });

        // Block move and look packets
        protocolManager.addPacketListener(
                new PacketAdapter(
                        plugin,
                        ListenerPriority.HIGH,
                        PacketType.Play.Server.REL_ENTITY_MOVE_LOOK) {
                    @Override
                    public void onPacketSending(PacketEvent event) {
                        handleEntityPacket(event);
                    }
                });

        // Block entity look packets
        protocolManager.addPacketListener(
                new PacketAdapter(
                        plugin, ListenerPriority.HIGH, PacketType.Play.Server.ENTITY_LOOK) {
                    @Override
                    public void onPacketSending(PacketEvent event) {
                        handleEntityPacket(event);
                    }
                });

        // Block head rotation packets
        protocolManager.addPacketListener(
                new PacketAdapter(
                        plugin,
                        ListenerPriority.HIGH,
                        PacketType.Play.Server.ENTITY_HEAD_ROTATION) {
                    @Override
                    public void onPacketSending(PacketEvent event) {
                        handleEntityPacket(event);
                    }
                });

        // Block entity equipment packets
        protocolManager.addPacketListener(
                new PacketAdapter(
                        plugin, ListenerPriority.HIGH, PacketType.Play.Server.ENTITY_EQUIPMENT) {
                    @Override
                    public void onPacketSending(PacketEvent event) {
                        handleEntityPacket(event);
                    }
                });

        // Block animation packets
        protocolManager.addPacketListener(
                new PacketAdapter(plugin, ListenerPriority.HIGH, PacketType.Play.Server.ANIMATION) {
                    @Override
                    public void onPacketSending(PacketEvent event) {
                        handleEntityPacket(event);
                    }
                });

        // Block container open/close animations (chest lid, etc.)
        protocolManager.addPacketListener(
                new PacketAdapter(
                        plugin, ListenerPriority.HIGH, PacketType.Play.Server.BLOCK_ACTION) {
                    @Override
                    public void onPacketSending(PacketEvent event) {
                        handleBlockActionPacket(event);
                    }
                });

        // Block entity sound effects from vanished players
        protocolManager.addPacketListener(
                new PacketAdapter(
                        plugin, ListenerPriority.HIGH, PacketType.Play.Server.ENTITY_SOUND) {
                    @Override
                    public void onPacketSending(PacketEvent event) {
                        handleEntitySoundPacket(event);
                    }
                });

        plugin.getLogger().info("ProtocolLib vanish packet listeners registered");
    }

    private void handleBlockActionPacket(PacketEvent event) {
        if (!plugin.getAdminConfig().vanishSilentChest()) {
            return;
        }

        Player receiver = event.getPlayer();
        if (receiver == null) return;

        try {
            PacketContainer packet = event.getPacket();
            com.comphenix.protocol.wrappers.BlockPosition blockPos =
                    packet.getBlockPositionModifier().read(0);

            if (blockPos == null) return;

            Location blockLoc =
                    new Location(
                            receiver.getWorld(), blockPos.getX(), blockPos.getY(), blockPos.getZ());

            UUID openerUuid = silentContainerOpens.get(blockLoc);
            if (openerUuid != null) {
                if (!receiver.getUniqueId().equals(openerUuid)) {
                    event.setCancelled(true);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void handleEntitySoundPacket(PacketEvent event) {
        Player receiver = event.getPlayer();
        if (receiver == null) return;

        try {
            int entityId = event.getPacket().getIntegers().read(0);

            // Only iterate online players - much faster than world.getEntities()
            for (Player target : plugin.getServer().getOnlinePlayers()) {
                if (target.getEntityId() == entityId) {
                    if (!canSeePredicate.test(receiver, target)) {
                        event.setCancelled(true);
                    }
                    return;
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void handleEntityPacket(PacketEvent event) {
        Player receiver = event.getPlayer();
        if (receiver == null) return;

        int entityId = event.getPacket().getIntegers().read(0);

        // Only iterate online players - much faster than world.getEntities()
        // We only care about Player entities anyway for vanish
        for (Player target : plugin.getServer().getOnlinePlayers()) {
            if (target.getEntityId() == entityId) {
                if (!canSeePredicate.test(receiver, target)) {
                    event.setCancelled(true);
                }
                return;
            }
        }
    }
}
