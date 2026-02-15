package net.serverplugins.admin.vanish;

import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;

public class VanishState {

    private final UUID playerId;
    private VanishMode mode;
    private boolean wasFlying;
    private boolean wasAllowFlight;
    private GameMode previousGameMode;
    private Location previousLocation;

    public VanishState(UUID playerId, VanishMode mode) {
        this.playerId = playerId;
        this.mode = mode;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public VanishMode getMode() {
        return mode;
    }

    public void setMode(VanishMode mode) {
        this.mode = mode;
    }

    public boolean wasFlying() {
        return wasFlying;
    }

    public void setWasFlying(boolean wasFlying) {
        this.wasFlying = wasFlying;
    }

    public boolean wasAllowFlight() {
        return wasAllowFlight;
    }

    public void setWasAllowFlight(boolean wasAllowFlight) {
        this.wasAllowFlight = wasAllowFlight;
    }

    public GameMode getPreviousGameMode() {
        return previousGameMode;
    }

    public void setPreviousGameMode(GameMode previousGameMode) {
        this.previousGameMode = previousGameMode;
    }

    public Location getPreviousLocation() {
        return previousLocation;
    }

    public void setPreviousLocation(Location previousLocation) {
        this.previousLocation = previousLocation;
    }
}
