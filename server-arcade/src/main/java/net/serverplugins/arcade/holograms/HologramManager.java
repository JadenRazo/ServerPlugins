package net.serverplugins.arcade.holograms;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import net.serverplugins.arcade.ServerArcade;

/**
 * Handles cleanup of legacy holograms.
 *
 * <p>Note: Server-arcade no longer uses DecentHolograms for machine labels. The fancy 3D text is
 * baked into the machine models via custom_model_data in the resource pack. This manager only
 * exists to clean up old holograms that may still exist.
 */
public class HologramManager {
    private final ServerArcade plugin;

    // Known legacy DreamArcade hologram name prefixes
    private static final String[] LEGACY_PREFIXES = {
        "Slots-Arcade", "Blackjack-Arcade", "Crash-Arcade", "Jackpot-Arcade", "Lottery-Arcade"
    };
    private static final int MAX_LEGACY_MACHINES = 50;

    public HologramManager(ServerArcade plugin) {
        this.plugin = plugin;
        boolean decentHologramsPresent =
                plugin.getServer().getPluginManager().getPlugin("DecentHolograms") != null;

        if (decentHologramsPresent) {
            plugin.getLogger().info("DecentHolograms detected - cleaning up legacy holograms");
            cleanupAllLegacyHolograms();
        }
    }

    /** Clean up all legacy holograms (both DreamArcade and old server-arcade). */
    private void cleanupAllLegacyHolograms() {
        int cleaned = 0;

        try {
            // Clean up DreamArcade holograms
            for (String prefix : LEGACY_PREFIXES) {
                // Base name (e.g., "Lottery-Arcade")
                cleaned += tryDeleteHologram(prefix);

                // Numbered variants (e.g., "Slots-Arcade-1" through "Slots-Arcade-50")
                for (int i = 1; i <= MAX_LEGACY_MACHINES; i++) {
                    cleaned += tryDeleteHologram(prefix + "-" + i);
                }
            }

            // Clean up old server-arcade holograms (arcade-{machineId} format)
            // Try common patterns since DHAPI doesn't have a list method
            for (int i = 0; i < 1000; i++) {
                // Try various machine ID formats that may have been generated
                String[] possibleIds = {
                    "arcade-" + i, "arcade-" + Long.toString(i, 36),
                };
                for (String id : possibleIds) {
                    cleaned += tryDeleteHologram(id);
                }
            }

            if (cleaned > 0) {
                plugin.getLogger().info("Cleaned up " + cleaned + " legacy holograms");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error during hologram cleanup: " + e.getMessage());
        }
    }

    /** Try to delete a hologram by name. Returns 1 if deleted, 0 if not found. */
    private int tryDeleteHologram(String name) {
        try {
            Hologram hologram = DHAPI.getHologram(name);
            if (hologram != null) {
                hologram.delete();
                plugin.getLogger().fine("Cleaned up hologram: " + name);
                return 1;
            }
        } catch (Exception ignored) {
        }
        return 0;
    }
}
