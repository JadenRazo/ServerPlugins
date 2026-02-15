package net.serverplugins.parkour.npc;

import net.serverplugins.parkour.ParkourConfig;
import net.serverplugins.parkour.ServerParkour;
import org.bukkit.Location;

/**
 * Manages the parkour NPC. The NPC should be created manually using FancyNpcs commands:
 *
 * <p>/npc create parkour_npc /npc skin parkour_npc <skin> /npc displayName parkour_npc &d&lParkour
 * /npc turnToPlayer parkour_npc true /npc message parkour_npc player_command:parkour /npc moveHere
 * parkour_npc
 *
 * <p>This manager just tracks the expected location for the hologram positioning.
 */
public class ParkourNpcManager {

    private final ServerParkour plugin;
    private final ParkourConfig config;
    private Location currentLocation;

    private static final String NPC_NAME = "parkour_npc";

    public ParkourNpcManager(ServerParkour plugin) {
        this.plugin = plugin;
        this.config = plugin.getParkourConfig();
        this.currentLocation = config.getNpcLocation();
    }

    public void createNpc() {
        if (!config.isNpcEnabled()) {
            return;
        }

        // Log instructions for manual NPC creation
        plugin.getLogger().info("=== Parkour NPC Setup ===");
        plugin.getLogger().info("Create the parkour NPC manually using FancyNpcs:");
        plugin.getLogger().info("/npc create " + NPC_NAME);
        plugin.getLogger().info("/npc displayName " + NPC_NAME + " &d&lParkour");
        plugin.getLogger().info("/npc turnToPlayer " + NPC_NAME + " true");
        plugin.getLogger().info("/npc message " + NPC_NAME + " player_command:parkour");
        plugin.getLogger()
                .info("Then teleport to " + locationToString(currentLocation) + " and run:");
        plugin.getLogger().info("/npc moveHere " + NPC_NAME);
        plugin.getLogger().info("========================");
    }

    public void removeNpc() {
        plugin.getLogger().info("To remove the parkour NPC: /npc remove " + NPC_NAME);
    }

    public void setLocation(Location location) {
        this.currentLocation = location.clone();
        plugin.getLogger()
                .info("NPC location updated. Move the NPC with: /npc moveHere " + NPC_NAME);
    }

    public Location getLocation() {
        return currentLocation != null ? currentLocation.clone() : null;
    }

    public String getNpcName() {
        return NPC_NAME;
    }

    private String locationToString(Location loc) {
        return String.format(
                "%.1f, %.1f, %.1f in %s",
                loc.getX(),
                loc.getY(),
                loc.getZ(),
                loc.getWorld() != null ? loc.getWorld().getName() : "unknown");
    }
}
