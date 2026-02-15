package net.serverplugins.claim.managers;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import net.serverplugins.claim.ServerClaim;

/**
 * ProtocolLib packet listener (currently disabled - count=0 in API is sufficient). Previously used
 * to intercept particle packets and ensure exact positioning.
 */
public class ParticlePacketListener {

    private final ServerClaim plugin;
    private PacketAdapter packetAdapter;

    public ParticlePacketListener(ServerClaim plugin) {
        this.plugin = plugin;
    }

    public void register() {
        packetAdapter =
                new PacketAdapter(
                        plugin, ListenerPriority.NORMAL, PacketType.Play.Server.WORLD_PARTICLES) {
                    @Override
                    public void onPacketSending(PacketEvent event) {
                        PacketContainer packet = event.getPacket();

                        try {
                            // Check if this is an EGG_CRACK particle (our claim boundary particles)
                            Object particleData = packet.getNewParticles().read(0);

                            // Only modify EGG_CRACK particles for completely static behavior
                            if (particleData != null
                                    && particleData.toString().contains("EGG_CRACK")) {
                                // CRITICAL: Ensure count is 0 to prevent reproduction/doubling
                                // When count=0, offsetX/Y/Z become velocity vectors instead of
                                // random spread
                                // This bypasses client-side "burst" physics and creates perfectly
                                // static particles
                                packet.getIntegers().write(0, 0);

                                // With count=0, these are velocity components (all 0 = completely
                                // stationary)
                                packet.getFloat().write(0, 0.0f); // velocityX
                                packet.getFloat().write(1, 0.0f); // velocityY
                                packet.getFloat().write(2, 0.0f); // velocityZ

                                // Speed/extra data (not used when count=0)
                                packet.getFloat().write(3, 0.0f);
                            }
                        } catch (Exception e) {
                            // Silently catch any errors - don't break particles if packet structure
                            // changes
                            plugin.getLogger()
                                    .warning("Failed to modify particle packet: " + e.getMessage());
                        }
                    }
                };

        ProtocolLibrary.getProtocolManager().addPacketListener(packetAdapter);
        plugin.getLogger()
                .info(
                        "ProtocolLib particle packet listener registered - exact particle positioning enabled");
    }

    public void unregister() {
        if (packetAdapter != null) {
            ProtocolLibrary.getProtocolManager().removePacketListener(packetAdapter);
            packetAdapter = null;
            plugin.getLogger().info("ProtocolLib particle packet listener unregistered");
        }
    }
}
