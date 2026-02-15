package net.serverplugins.parkour.game;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.serverplugins.parkour.ServerParkour;
import org.bukkit.entity.Player;

public class ParkourManager {

    private final ServerParkour plugin;
    private final Map<UUID, ParkourSession> activeSessions = new HashMap<>();

    // Lane system - each player gets their own isolated parkour lane
    private final Set<Integer> usedLanes = new HashSet<>();
    private final Map<UUID, Integer> playerLanes = new HashMap<>();
    private static final int LANE_SEPARATION = 100; // blocks between lanes

    public ParkourManager(ServerParkour plugin) {
        this.plugin = plugin;
    }

    public boolean startGame(Player player) {
        if (isPlaying(player)) {
            return false;
        }

        // Allocate a lane for this player
        int lane = allocateLane();
        int laneOffset = lane * LANE_SEPARATION;

        ParkourSession session = new ParkourSession(plugin, player, laneOffset);
        activeSessions.put(player.getUniqueId(), session);
        playerLanes.put(player.getUniqueId(), lane);
        session.start();
        return true;
    }

    private int allocateLane() {
        int lane = 0;
        while (usedLanes.contains(lane)) {
            lane++;
        }
        usedLanes.add(lane);
        return lane;
    }

    private void freeLane(UUID playerId) {
        Integer lane = playerLanes.remove(playerId);
        if (lane != null) {
            usedLanes.remove(lane);
        }
    }

    public boolean endGame(Player player) {
        ParkourSession session = activeSessions.remove(player.getUniqueId());
        if (session != null) {
            session.end(false);
            return true;
        }
        return false;
    }

    public void endAllGames() {
        for (ParkourSession session : activeSessions.values()) {
            session.end(false);
        }
        activeSessions.clear();
        playerLanes.clear();
        usedLanes.clear();
    }

    public boolean isPlaying(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    public ParkourSession getSession(Player player) {
        return activeSessions.get(player.getUniqueId());
    }

    public void removeSession(Player player) {
        activeSessions.remove(player.getUniqueId());
        freeLane(player.getUniqueId());
    }

    public int getActivePlayerCount() {
        return activeSessions.size();
    }

    public Map<UUID, ParkourSession> getActiveSessions() {
        return activeSessions;
    }
}
