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
import net.serverplugins.claim.models.Nation;
import net.serverplugins.claim.models.NationMember;
import net.serverplugins.claim.models.PlayerClaimData;
import org.bukkit.Bukkit;

public class NationOverlayManager {

    private final ServerBlueMap plugin;
    private final BlueMapAPI blueMapAPI;
    private final ServerClaim claimPlugin;
    private final BlueMapConfig config;

    private final Map<Integer, String> nationMarkerIds = new HashMap<>();

    public NationOverlayManager(
            ServerBlueMap plugin, BlueMapAPI blueMapAPI, ServerClaim claimPlugin) {
        this.plugin = plugin;
        this.blueMapAPI = blueMapAPI;
        this.claimPlugin = claimPlugin;
        this.config = plugin.getBlueMapConfig();
    }

    public void initialize() {
        plugin.getLogger().info("Nation overlay layer initialized: " + config.getNationLayerName());
    }

    public void updateMarkers() {
        try {
            Set<Integer> existingMarkers = new HashSet<>(nationMarkerIds.keySet());
            List<Nation> allNations = claimPlugin.getNationManager().getAllNations();

            for (Nation nation : allNations) {
                updateNationMarker(nation);
                existingMarkers.remove(nation.getId());
            }

            // Remove markers for nations that no longer exist
            for (Integer nationId : existingMarkers) {
                removeNationMarker(nationId);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error updating nation markers: " + e.getMessage());
        }
    }

    private void updateNationMarker(Nation nation) {
        List<NationMember> members = claimPlugin.getNationManager().getMembers(nation.getId());
        if (members.isEmpty()) return;

        // Collect all chunks from all member claims
        List<ClaimedChunk> allChunks = new ArrayList<>();
        String worldName = null;

        for (NationMember member : members) {
            Claim claim = claimPlugin.getClaimManager().getClaimById(member.getClaimId());
            if (claim != null && !claim.getChunks().isEmpty()) {
                allChunks.addAll(claim.getChunks());
                if (worldName == null) {
                    worldName = claim.getChunks().get(0).getWorld();
                }
            }
        }

        if (allChunks.isEmpty() || worldName == null) return;

        org.bukkit.World bukkitWorld = Bukkit.getWorld(worldName);
        if (bukkitWorld == null) return;

        Optional<BlueMapWorld> blueMapWorld = blueMapAPI.getWorld(bukkitWorld);
        if (!blueMapWorld.isPresent()) return;

        String markerId = "nation_" + nation.getId();

        // Calculate bounding polygon
        List<Vector2d> polygon = calculateNationPolygon(allChunks);
        if (polygon.isEmpty()) return;

        // Create shape from polygon
        Shape shape = new Shape(polygon.toArray(new Vector2d[0]));

        // Create extruded marker for nation territory
        ExtrudeMarker marker =
                ExtrudeMarker.builder()
                        .label(buildLabel(nation))
                        .shape(shape, 0f, 320f) // Extrude from bedrock to above max build height
                        .fillColor(
                                convertColor(getNationColor(nation), config.getNationFillOpacity()))
                        .lineColor(convertColor(getNationBorderColor(nation), 0.8))
                        .lineWidth(config.getNationBorderWidth())
                        .detail(buildDescription(nation))
                        .build();

        // Add marker to all maps in the world
        for (BlueMapMap map : blueMapWorld.get().getMaps()) {
            MarkerSet markerSet = getOrCreateMarkerSet(map);
            markerSet.getMarkers().put(markerId, marker);
        }

        nationMarkerIds.put(nation.getId(), markerId);
    }

    private void removeNationMarker(Integer nationId) {
        String markerId = nationMarkerIds.remove(nationId);
        if (markerId == null) return;

        for (BlueMapWorld world : blueMapAPI.getWorlds()) {
            for (BlueMapMap map : world.getMaps()) {
                try {
                    MarkerSet markerSet = map.getMarkerSets().get("serverclaim.nations");
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
        MarkerSet markerSet = map.getMarkerSets().get("serverclaim.nations");

        if (markerSet == null) {
            markerSet =
                    MarkerSet.builder()
                            .label(config.getNationLayerName())
                            .defaultHidden(!config.isNationLayerDefaultVisible())
                            .build();
            map.getMarkerSets().put("serverclaim.nations", markerSet);
        }

        return markerSet;
    }

    private List<Vector2d> calculateNationPolygon(List<ClaimedChunk> chunks) {
        if (chunks.isEmpty()) return Collections.emptyList();

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        for (ClaimedChunk chunk : chunks) {
            minX = Math.min(minX, chunk.getChunkX());
            maxX = Math.max(maxX, chunk.getChunkX());
            minZ = Math.min(minZ, chunk.getChunkZ());
            maxZ = Math.max(maxZ, chunk.getChunkZ());
        }

        List<Vector2d> polygon = new ArrayList<>();
        polygon.add(new Vector2d(minX * 16, minZ * 16));
        polygon.add(new Vector2d((maxX + 1) * 16, minZ * 16));
        polygon.add(new Vector2d((maxX + 1) * 16, (maxZ + 1) * 16));
        polygon.add(new Vector2d(minX * 16, (maxZ + 1) * 16));

        return polygon;
    }

    private java.awt.Color getNationColor(Nation nation) {
        if (nation.getColor() != null) {
            org.bukkit.Color bukkitColor = nation.getColor().getBukkitColor();
            return new java.awt.Color(bukkitColor.asRGB());
        }
        return config.getNationFillColor();
    }

    private java.awt.Color getNationBorderColor(Nation nation) {
        java.awt.Color fillColor = getNationColor(nation);
        // Darken the fill color for border
        int r = (fillColor.getRed() * 2 / 3);
        int g = (fillColor.getGreen() * 2 / 3);
        int b = (fillColor.getBlue() * 2 / 3);
        return new java.awt.Color(r, g, b);
    }

    private Color convertColor(java.awt.Color awtColor, double opacity) {
        return new Color(
                awtColor.getRed(), awtColor.getGreen(), awtColor.getBlue(), (float) opacity);
    }

    private String buildLabel(Nation nation) {
        StringBuilder label = new StringBuilder();

        if (config.showNationTag()) {
            label.append("[").append(nation.getTag()).append("] ");
        }

        label.append(nation.getName());
        return label.toString();
    }

    private String buildDescription(Nation nation) {
        StringBuilder desc = new StringBuilder();
        desc.append("<div class=\"infowindow\">");
        desc.append("<strong>").append(nation.getName()).append("</strong>");

        if (config.showNationTag()) {
            desc.append(" [").append(nation.getTag()).append("]");
        }
        desc.append("<br>");

        if (config.showNationLeader()) {
            PlayerClaimData leaderData =
                    claimPlugin.getRepository().getPlayerData(nation.getLeaderUuid());
            String leaderName = leaderData != null ? leaderData.getUsername() : "Unknown";
            desc.append("Leader: ").append(leaderName).append("<br>");
        }

        if (config.showNationMembers()) {
            desc.append("Members: ").append(nation.getMemberCount()).append("<br>");
        }

        desc.append("Chunks: ").append(nation.getTotalChunks()).append("<br>");
        desc.append("Level: ").append(nation.getLevel()).append("<br>");

        if (nation.getDescription() != null) {
            desc.append("<br><em>").append(nation.getDescription()).append("</em>");
        }

        desc.append("</div>");
        return desc.toString();
    }

    public void cleanup() {
        for (Integer nationId : new HashSet<>(nationMarkerIds.keySet())) {
            removeNationMarker(nationId);
        }
        nationMarkerIds.clear();
    }
}
