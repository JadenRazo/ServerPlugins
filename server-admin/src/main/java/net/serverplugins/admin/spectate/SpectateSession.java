package net.serverplugins.admin.spectate;

import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;

public class SpectateSession {

    private final UUID spectatorId;
    private final UUID targetId;
    private final Location previousLocation;
    private final GameMode previousGameMode;
    private final boolean wasFlying;
    private final boolean wasAllowFlight;
    private final boolean wasVanished;
    private final SpectateType type;
    private int povTaskId = -1;

    // Cached location data for movement delta checks
    private Location lastSyncedLocation;
    private float lastSyncedYaw;
    private float lastSyncedPitch;

    public SpectateSession(
            UUID spectatorId,
            UUID targetId,
            Location previousLocation,
            GameMode previousGameMode,
            boolean wasFlying,
            boolean wasAllowFlight,
            boolean wasVanished,
            SpectateType type) {
        this.spectatorId = spectatorId;
        this.targetId = targetId;
        this.previousLocation = previousLocation;
        this.previousGameMode = previousGameMode;
        this.wasFlying = wasFlying;
        this.wasAllowFlight = wasAllowFlight;
        this.wasVanished = wasVanished;
        this.type = type;
    }

    public UUID getSpectatorId() {
        return spectatorId;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public Location getPreviousLocation() {
        return previousLocation;
    }

    public GameMode getPreviousGameMode() {
        return previousGameMode;
    }

    public boolean wasFlying() {
        return wasFlying;
    }

    public boolean wasAllowFlight() {
        return wasAllowFlight;
    }

    public boolean wasVanished() {
        return wasVanished;
    }

    public SpectateType getType() {
        return type;
    }

    public int getPovTaskId() {
        return povTaskId;
    }

    public void setPovTaskId(int taskId) {
        this.povTaskId = taskId;
    }

    public boolean hasPovTask() {
        return povTaskId != -1;
    }

    public Location getLastSyncedLocation() {
        return lastSyncedLocation;
    }

    public void setLastSyncedLocation(Location lastSyncedLocation) {
        this.lastSyncedLocation = lastSyncedLocation;
    }

    public float getLastSyncedYaw() {
        return lastSyncedYaw;
    }

    public void setLastSyncedYaw(float lastSyncedYaw) {
        this.lastSyncedYaw = lastSyncedYaw;
    }

    public float getLastSyncedPitch() {
        return lastSyncedPitch;
    }

    public void setLastSyncedPitch(float lastSyncedPitch) {
        this.lastSyncedPitch = lastSyncedPitch;
    }

    public enum SpectateType {
        SPECTATE, // Standard spectator mode
        POV, // View from player's perspective
        FREECAM // Free camera mode
    }
}
