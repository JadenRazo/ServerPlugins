package net.serverplugins.parkour.npc;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import java.util.ArrayList;
import java.util.List;
import net.serverplugins.parkour.ParkourConfig;
import net.serverplugins.parkour.ServerParkour;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class ParkourHologramManager {

    private final ServerParkour plugin;
    private final ParkourConfig config;
    private Hologram hologram;

    private static final String HOLOGRAM_NAME = "parkour_hologram";

    public ParkourHologramManager(ServerParkour plugin) {
        this.plugin = plugin;
        this.config = plugin.getParkourConfig();
    }

    public void createHologram() {
        if (!config.isHologramEnabled()) {
            return;
        }

        // Remove existing hologram
        removeHologram();

        try {
            // Get location above NPC
            Location npcLoc = config.getNpcLocation();
            Location holoLoc = npcLoc.clone().add(0, config.getHologramOffsetY(), 0);

            // Process lines with placeholders
            List<String> lines = new ArrayList<>();
            for (String line : config.getHologramLines()) {
                lines.add(ChatColor.translateAlternateColorCodes('&', line));
            }

            // Create hologram with placeholders enabled
            hologram = DHAPI.createHologram(HOLOGRAM_NAME, holoLoc, true, lines);

            if (hologram != null) {
                hologram.showAll();
                plugin.getLogger().info("Parkour hologram created");
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to create hologram: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void updateHologram() {
        if (!config.isHologramEnabled()) {
            removeHologram();
            return;
        }

        if (hologram == null) {
            createHologram();
            return;
        }

        try {
            // Update lines
            List<String> lines = new ArrayList<>();
            for (String line : config.getHologramLines()) {
                lines.add(ChatColor.translateAlternateColorCodes('&', line));
            }

            // Update hologram content
            for (int i = 0; i < lines.size(); i++) {
                DHAPI.setHologramLine(hologram, i, lines.get(i));
            }

            // Update location
            Location npcLoc = config.getNpcLocation();
            Location holoLoc = npcLoc.clone().add(0, config.getHologramOffsetY(), 0);
            DHAPI.moveHologram(hologram, holoLoc);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to update hologram: " + e.getMessage());
            // Try recreating
            createHologram();
        }
    }

    public void updateForPlayer(Player player) {
        // This is called to update player-specific placeholders
        // DecentHolograms handles PlaceholderAPI placeholders automatically
        // So we don't need to do anything special here
    }

    public void removeHologram() {
        try {
            // Remove by name
            Hologram existing = DHAPI.getHologram(HOLOGRAM_NAME);
            if (existing != null) {
                DHAPI.removeHologram(HOLOGRAM_NAME);
            }

            if (hologram != null) {
                hologram.delete();
                hologram = null;
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Error removing hologram: " + e.getMessage());
        }
    }

    public Hologram getHologram() {
        return hologram;
    }
}
