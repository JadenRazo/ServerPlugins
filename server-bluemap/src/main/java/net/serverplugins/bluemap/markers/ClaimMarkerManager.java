package net.serverplugins.bluemap.markers;

import com.flowpowered.math.vector.Vector2d;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.ExtrudeMarker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import java.util.*;
import net.serverplugins.bluemap.BlueMapConfig;
import net.serverplugins.bluemap.ServerBlueMap;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimedChunk;
import net.serverplugins.claim.models.PlayerClaimData;
import org.bukkit.Bukkit;

public class ClaimMarkerManager {

    private final ServerBlueMap plugin;
    private final BlueMapAPI blueMapAPI;
    private final ServerClaim claimPlugin;
    private final BlueMapConfig config;

    private final Map<Integer, String> claimMarkerIds = new HashMap<>();

    public ClaimMarkerManager(
            ServerBlueMap plugin, BlueMapAPI blueMapAPI, ServerClaim claimPlugin) {
        this.plugin = plugin;
        this.blueMapAPI = blueMapAPI;
        this.claimPlugin = claimPlugin;
        this.config = plugin.getBlueMapConfig();
    }

    public void initialize() {
        plugin.getLogger().info("Claim marker layer initialized: " + config.getClaimLayerName());
    }

    public void updateMarkers() {
        try {
            Set<Integer> existingMarkers = new HashSet<>(claimMarkerIds.keySet());
            List<Claim> allClaims = claimPlugin.getRepository().getAllClaims();

            for (Claim claim : allClaims) {
                updateClaimMarker(claim);
                existingMarkers.remove(claim.getId());
            }

            // Remove markers for claims that no longer exist
            for (Integer claimId : existingMarkers) {
                removeClaimMarker(claimId);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error updating claim markers: " + e.getMessage());
        }
    }

    private void updateClaimMarker(Claim claim) {
        if (claim.getChunks().isEmpty()) return;

        String markerId = "claim_" + claim.getId();

        // Calculate polygon from chunks
        List<Vector2d> polygon = calculatePolygon(claim.getChunks());
        if (polygon.isEmpty()) return;

        // Get world from first chunk
        String worldName = claim.getChunks().get(0).getWorld();
        org.bukkit.World bukkitWorld = Bukkit.getWorld(worldName);
        if (bukkitWorld == null) return;

        Optional<BlueMapWorld> blueMapWorld = blueMapAPI.getWorld(bukkitWorld);
        if (!blueMapWorld.isPresent()) return;

        // Create shape from polygon
        Shape shape = new Shape(polygon.toArray(new Vector2d[0]));

        // Create extruded marker - creates a vertical wall from ground to sky
        // This ensures the claim boundary is always visible above all blocks
        ExtrudeMarker marker =
                ExtrudeMarker.builder()
                        .label(buildLabel(claim))
                        .shape(shape, 0f, 320f) // Extrude from bedrock to above max build height
                        .fillColor(convertColor(getClaimColor(claim), config.getClaimFillOpacity()))
                        .lineColor(convertColor(config.getClaimBorderColor(), 0.8))
                        .lineWidth(config.getClaimBorderWidth())
                        .detail(buildDescription(claim))
                        .build();

        // Add marker to all maps in the world
        for (BlueMapMap map : blueMapWorld.get().getMaps()) {
            MarkerSet markerSet = getOrCreateMarkerSet(map);
            markerSet.getMarkers().put(markerId, marker);
        }

        claimMarkerIds.put(claim.getId(), markerId);
    }

    private void removeClaimMarker(Integer claimId) {
        String markerId = claimMarkerIds.remove(claimId);
        if (markerId == null) return;

        // Remove from all worlds and maps
        for (BlueMapWorld world : blueMapAPI.getWorlds()) {
            for (BlueMapMap map : world.getMaps()) {
                try {
                    MarkerSet markerSet = map.getMarkerSets().get("serverclaim.claims");
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
        MarkerSet markerSet = map.getMarkerSets().get("serverclaim.claims");

        if (markerSet == null) {
            markerSet =
                    MarkerSet.builder()
                            .label(config.getClaimLayerName())
                            .defaultHidden(!config.isClaimLayerDefaultVisible())
                            .build();
            map.getMarkerSets().put("serverclaim.claims", markerSet);
        }

        return markerSet;
    }

    private List<Vector2d> calculatePolygon(List<ClaimedChunk> chunks) {
        if (chunks.isEmpty()) return Collections.emptyList();

        // Get bounding box
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        for (ClaimedChunk chunk : chunks) {
            minX = Math.min(minX, chunk.getChunkX());
            maxX = Math.max(maxX, chunk.getChunkX());
            minZ = Math.min(minZ, chunk.getChunkZ());
            maxZ = Math.max(maxZ, chunk.getChunkZ());
        }

        // Convert chunk coords to block coords (each chunk is 16x16)
        // BlueMap uses Vector2d with x and z coordinates
        List<Vector2d> polygon = new ArrayList<>();
        polygon.add(new Vector2d(minX * 16, minZ * 16));
        polygon.add(new Vector2d((maxX + 1) * 16, minZ * 16));
        polygon.add(new Vector2d((maxX + 1) * 16, (maxZ + 1) * 16));
        polygon.add(new Vector2d(minX * 16, (maxZ + 1) * 16));

        return polygon;
    }

    private java.awt.Color getClaimColor(Claim claim) {
        if (claim.getColor() != null) {
            org.bukkit.Color bukkitColor = claim.getColor().getBukkitColor();
            return new java.awt.Color(bukkitColor.asRGB());
        }
        return config.getClaimFillColor();
    }

    private Color convertColor(java.awt.Color awtColor, double opacity) {
        return new Color(
                awtColor.getRed(), awtColor.getGreen(), awtColor.getBlue(), (float) opacity);
    }

    private String buildLabel(Claim claim) {
        // Get owner name
        PlayerClaimData ownerData = claimPlugin.getRepository().getPlayerData(claim.getOwnerUuid());
        String ownerName = ownerData != null ? ownerData.getUsername() : "Unknown";

        if (config.showClaimName()) {
            return claim.getName() + " (" + ownerName + ")";
        }
        return ownerName + "'s Claim #" + claim.getId();
    }

    private String buildDescription(Claim claim) {
        StringBuilder desc = new StringBuilder();
        desc.append("<div class=\"infowindow\">");
        desc.append("<strong>").append(claim.getName()).append("</strong><br>");

        if (config.showClaimOwner()) {
            PlayerClaimData ownerData =
                    claimPlugin.getRepository().getPlayerData(claim.getOwnerUuid());
            String ownerName = ownerData != null ? ownerData.getUsername() : "Unknown";
            desc.append("Owner: ").append(ownerName).append("<br>");
        }

        desc.append("Chunks: ").append(claim.getChunks().size()).append("<br>");

        if (config.showClaimMembers()) {
            int memberCount = claim.getMembers().size();
            desc.append("Members: ").append(memberCount).append("<br>");
        }

        // Check if claim is part of a nation
        var nation = claimPlugin.getNationManager().getNationForClaim(claim.getId());
        if (nation != null) {
            desc.append("Nation: ").append(nation.getName()).append("<br>");
        }

        desc.append("</div>");
        return desc.toString();
    }

    public void cleanup() {
        for (Integer claimId : new HashSet<>(claimMarkerIds.keySet())) {
            removeClaimMarker(claimId);
        }
        claimMarkerIds.clear();
    }
}
