package net.serverplugins.admin.xrayvision;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;

public class XrayVisionSession {

    private final UUID playerId;
    private final Map<Location, OreMarker> activeMarkers;
    private int taskId = -1;

    public XrayVisionSession(UUID playerId) {
        this.playerId = playerId;
        this.activeMarkers = new ConcurrentHashMap<>();
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Map<Location, OreMarker> getActiveMarkers() {
        return activeMarkers;
    }

    public void setTaskId(int taskId) {
        this.taskId = taskId;
    }

    public int getTaskId() {
        return taskId;
    }

    public boolean hasTask() {
        return taskId != -1;
    }

    public void cancelTask() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }
}
