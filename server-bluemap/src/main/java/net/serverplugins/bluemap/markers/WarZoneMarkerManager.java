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
import net.serverplugins.claim.models.War;
import org.bukkit.Bukkit;

public class WarZoneMarkerManager {

    private final ServerBlueMap plugin;
    private final BlueMapAPI blueMapAPI;
    private final ServerClaim claimPlugin;
    private final BlueMapConfig config;

    private final Map<String, String> warZoneMarkerIds = new HashMap<>();

    public WarZoneMarkerManager(
            ServerBlueMap plugin, BlueMapAPI blueMapAPI, ServerClaim claimPlugin) {
        this.plugin = plugin;
        this.blueMapAPI = blueMapAPI;
        this.claimPlugin = claimPlugin;
        this.config = plugin.getBlueMapConfig();
    }

    public void initialize() {
        plugin.getLogger().info("War zone layer initialized: " + config.getWarZoneLayerName());
    }

    public void updateMarkers() {
        try {
            Set<String> existingMarkers = new HashSet<>(warZoneMarkerIds.keySet());

            // Get all nations involved in active wars
            List<Nation> allNations = claimPlugin.getNationManager().getAllNations();
            Set<Integer> atWarNations = new HashSet<>();

            for (Nation nation : allNations) {
                List<War> wars = claimPlugin.getWarManager().getActiveWarsForNation(nation.getId());
                for (War war : wars) {
                    if (war.isActive()) {
                        atWarNations.add(nation.getId());
                    }
                }
            }

            // Create markers for war zone territories
            for (Integer nationId : atWarNations) {
                Nation nation = claimPlugin.getNationManager().getNation(nationId);
                if (nation != null) {
                    String markerId = updateWarZoneMarker(nation);
                    if (markerId != null) {
                        existingMarkers.remove(markerId);
                    }
                }
            }

            // Remove markers for territories no longer at war
            for (String markerId : existingMarkers) {
                removeWarZoneMarker(markerId);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error updating war zone markers: " + e.getMessage());
        }
    }

    private String updateWarZoneMarker(Nation nation) {
        List<NationMember> members = claimPlugin.getNationManager().getMembers(nation.getId());
        if (members.isEmpty()) return null;

        // Collect all chunks from member claims
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

        if (allChunks.isEmpty() || worldName == null) return null;

        org.bukkit.World bukkitWorld = Bukkit.getWorld(worldName);
        if (bukkitWorld == null) return null;

        Optional<BlueMapWorld> blueMapWorld = blueMapAPI.getWorld(bukkitWorld);
        if (!blueMapWorld.isPresent()) return null;

        String markerId = "warzone_" + nation.getId();

        List<Vector2d> polygon = calculatePolygon(allChunks);
        if (polygon.isEmpty()) return null;

        // Create shape from polygon
        Shape shape = new Shape(polygon.toArray(new Vector2d[0]));

        // Create extruded marker for war zones
        ExtrudeMarker marker =
                ExtrudeMarker.builder()
                        .label(buildLabel(nation))
                        .shape(shape, 0f, 320f) // Extrude from bedrock to above max build height
                        .fillColor(
                                convertColor(
                                        config.getWarZoneFillColor(),
                                        config.getWarZoneFillOpacity()))
                        .lineColor(convertColor(config.getWarZoneBorderColor(), 1.0))
                        .lineWidth(config.getWarZoneBorderWidth())
                        .detail(buildDescription(nation))
                        .build();

        // Add marker to all maps in the world
        for (BlueMapMap map : blueMapWorld.get().getMaps()) {
            MarkerSet markerSet = getOrCreateMarkerSet(map);
            markerSet.getMarkers().put(markerId, marker);
        }

        warZoneMarkerIds.put(markerId, markerId);
        return markerId;
    }

    private void removeWarZoneMarker(String markerId) {
        warZoneMarkerIds.remove(markerId);

        for (BlueMapWorld world : blueMapAPI.getWorlds()) {
            for (BlueMapMap map : world.getMaps()) {
                try {
                    MarkerSet markerSet = map.getMarkerSets().get("serverclaim.warzones");
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
        MarkerSet markerSet = map.getMarkerSets().get("serverclaim.warzones");

        if (markerSet == null) {
            markerSet =
                    MarkerSet.builder()
                            .label(config.getWarZoneLayerName())
                            .defaultHidden(!config.isWarZoneLayerDefaultVisible())
                            .build();
            map.getMarkerSets().put("serverclaim.warzones", markerSet);
        }

        return markerSet;
    }

    private List<Vector2d> calculatePolygon(List<ClaimedChunk> chunks) {
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

    private Color convertColor(java.awt.Color awtColor, double opacity) {
        return new Color(
                awtColor.getRed(), awtColor.getGreen(), awtColor.getBlue(), (float) opacity);
    }

    private String buildLabel(Nation nation) {
        return "⚔ " + nation.getName() + " (WAR ZONE)";
    }

    private String buildDescription(Nation nation) {
        List<War> wars = claimPlugin.getWarManager().getActiveWarsForNation(nation.getId());

        StringBuilder desc = new StringBuilder();
        desc.append(
                "<div class=\"infowindow\" style=\"background-color: #330000; color: #ff6666;\">");
        desc.append("<strong>⚔ WAR ZONE ⚔</strong><br><br>");
        desc.append("<strong>").append(nation.getName()).append("</strong><br>");
        desc.append("Status: <span style=\"color: #ff0000;\">AT WAR</span><br><br>");

        desc.append("<strong>Active Conflicts:</strong><br>");
        for (War war : wars) {
            if (war.isActive()) {
                boolean isAttacker = war.isAttacker(nation.getId());
                Integer opponentId =
                        isAttacker ? war.getDefenderNationId() : war.getAttackerNationId();
                Nation opponent =
                        opponentId != null
                                ? claimPlugin.getNationManager().getNation(opponentId)
                                : null;
                String opponentName = opponent != null ? opponent.getName() : "Unknown";
                String role = isAttacker ? "Attacking" : "Defending";

                desc.append("• ").append(role).append(" vs ").append(opponentName).append("<br>");
            }
        }

        desc.append(
                "<br><span style=\"color: #ffff00;\">⚠ PvP is ENABLED in this territory!</span>");
        desc.append("</div>");
        return desc.toString();
    }

    public void cleanup() {
        for (String markerId : new HashSet<>(warZoneMarkerIds.keySet())) {
            removeWarZoneMarker(markerId);
        }
        warZoneMarkerIds.clear();
    }
}
