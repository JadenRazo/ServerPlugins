package net.serverplugins.bluemap.markers;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import de.bluecolored.bluemap.api.math.Color;
import java.util.*;
import net.serverplugins.bluemap.BlueMapConfig;
import net.serverplugins.bluemap.ServerBlueMap;
import net.serverplugins.bluemap.models.POI;
import net.serverplugins.bluemap.repository.POIRepository;
import org.bukkit.Bukkit;

public class POIMarkerManager {

    private final ServerBlueMap plugin;
    private final BlueMapAPI blueMapAPI;
    private final POIRepository poiRepository;
    private final BlueMapConfig config;

    private final Map<Integer, String> poiMarkerIds = new HashMap<>();

    public POIMarkerManager(
            ServerBlueMap plugin, BlueMapAPI blueMapAPI, POIRepository poiRepository) {
        this.plugin = plugin;
        this.blueMapAPI = blueMapAPI;
        this.poiRepository = poiRepository;
        this.config = plugin.getBlueMapConfig();
    }

    public void initialize() {
        plugin.getLogger().info("POI marker layer initialized: " + config.getPOILayerName());
    }

    public void updateMarkers() {
        try {
            Set<Integer> existingMarkers = new HashSet<>(poiMarkerIds.keySet());
            List<POI> allPOIs = poiRepository.getVisiblePOIs();

            for (POI poi : allPOIs) {
                updatePOIMarker(poi);
                existingMarkers.remove(poi.getId());
            }

            // Remove markers for POIs that no longer exist or are hidden
            for (Integer poiId : existingMarkers) {
                removePOIMarker(poiId);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error updating POI markers: " + e.getMessage());
        }
    }

    private void updatePOIMarker(POI poi) {
        if (!poi.isVisible()) return;

        String markerId = "poi_" + poi.getId();

        // Get Bukkit world
        org.bukkit.World bukkitWorld = Bukkit.getWorld(poi.getWorld());
        if (bukkitWorld == null) return;

        Optional<BlueMapWorld> blueMapWorld = blueMapAPI.getWorld(bukkitWorld);
        if (!blueMapWorld.isPresent()) return;

        // Get category color and icon
        Color color = getCategoryColor(poi.getCategory());
        String icon = getCategoryIcon(poi.getCategory());

        // Build marker label
        String label = poi.getName() + " (" + getCategoryDisplayName(poi.getCategory()) + ")";

        // Build marker description
        String description = buildDescription(poi);

        // Create POI marker
        POIMarker.Builder markerBuilder =
                POIMarker.builder()
                        .label(label)
                        .position(poi.getX(), poi.getY(), poi.getZ())
                        .detail(description);

        // Add icon if available
        if (icon != null && !icon.isEmpty()) {
            markerBuilder.icon(icon, 0, 0);
        }

        POIMarker marker = markerBuilder.build();

        // Add marker to all maps in the world
        for (BlueMapMap map : blueMapWorld.get().getMaps()) {
            MarkerSet markerSet = getOrCreateMarkerSet(map);
            markerSet.getMarkers().put(markerId, marker);
        }

        poiMarkerIds.put(poi.getId(), markerId);
    }

    private void removePOIMarker(Integer poiId) {
        String markerId = poiMarkerIds.remove(poiId);
        if (markerId == null) return;

        // Remove from all worlds and maps
        for (BlueMapWorld world : blueMapAPI.getWorlds()) {
            for (BlueMapMap map : world.getMaps()) {
                try {
                    MarkerSet markerSet = map.getMarkerSets().get("serverbluemap.pois");
                    if (markerSet != null) {
                        markerSet.getMarkers().remove(markerId);
                    }
                } catch (Exception e) {
                    // Ignore if marker set doesn't exist
                }
            }
        }
    }

    private MarkerSet getOrCreateMarkerSet(BlueMapMap map) {
        MarkerSet markerSet = map.getMarkerSets().get("serverbluemap.pois");

        if (markerSet == null) {
            markerSet =
                    MarkerSet.builder()
                            .label(config.getPOILayerName())
                            .defaultHidden(!config.isPOILayerDefaultVisible())
                            .build();
            map.getMarkerSets().put("serverbluemap.pois", markerSet);
        }

        return markerSet;
    }

    private String buildDescription(POI poi) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family: sans-serif;'>");
        sb.append("<h3>").append(poi.getName()).append("</h3>");
        sb.append("<p><strong>Type:</strong> ")
                .append(getCategoryDisplayName(poi.getCategory()))
                .append("</p>");

        if (poi.getDescription() != null && !poi.getDescription().isEmpty()) {
            sb.append("<p><strong>Description:</strong> ")
                    .append(poi.getDescription())
                    .append("</p>");
        }

        sb.append("<p><strong>Location:</strong> ")
                .append(poi.getX())
                .append(", ")
                .append(poi.getY())
                .append(", ")
                .append(poi.getZ())
                .append("</p>");
        sb.append("<p style='color: #888; font-size: 0.9em;'>Created: ")
                .append(poi.getCreatedAt().toString())
                .append("</p>");
        sb.append("</div>");

        return sb.toString();
    }

    private Color getCategoryColor(String category) {
        String colorHex = config.getPOICategoryColor(category);
        if (colorHex == null) {
            colorHex = "#FFFFFF"; // Default white
        }
        return convertColor(colorHex);
    }

    private String getCategoryIcon(String category) {
        return config.getPOICategoryIcon(category);
    }

    private String getCategoryDisplayName(String category) {
        String displayName = config.getPOICategoryDisplayName(category);
        if (displayName == null) {
            // Capitalize first letter
            return category.substring(0, 1).toUpperCase() + category.substring(1);
        }
        return displayName;
    }

    private Color convertColor(String hex) {
        try {
            String cleanHex = hex.replace("#", "");
            int rgb = Integer.parseInt(cleanHex, 16);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            return new Color(r, g, b);
        } catch (Exception e) {
            return new Color(255, 255, 255); // Default white
        }
    }

    public void cleanup() {
        // Remove all POI markers
        for (BlueMapWorld world : blueMapAPI.getWorlds()) {
            for (BlueMapMap map : world.getMaps()) {
                try {
                    map.getMarkerSets().remove("serverbluemap.pois");
                } catch (Exception e) {
                    // Ignore errors during cleanup
                }
            }
        }
        poiMarkerIds.clear();
    }
}
