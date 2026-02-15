package net.serverplugins.admin.spawner;

import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;

public class SpawnerTracker {

    private final UUID playerId;
    private final Queue<SpawnerEvent> recentEvents;

    public SpawnerTracker(UUID playerId) {
        this.playerId = playerId;
        this.recentEvents = new LinkedList<>();
    }

    public void recordSpawnerBreak(EntityType mobType, Location location, long timestamp) {
        recentEvents.add(new SpawnerEvent(mobType, location, timestamp));
    }

    public void pruneOldEvents(long cutoffTime) {
        while (!recentEvents.isEmpty() && recentEvents.peek().timestamp() < cutoffTime) {
            recentEvents.poll();
        }
    }

    public int getRecentCount() {
        return recentEvents.size();
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Queue<SpawnerEvent> getRecentEvents() {
        return recentEvents;
    }

    public record SpawnerEvent(EntityType mobType, Location location, long timestamp) {}
}
