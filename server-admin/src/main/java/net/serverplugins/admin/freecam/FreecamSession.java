package net.serverplugins.admin.freecam;

import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

/** Stores session data for a player in freecam mode. */
public class FreecamSession {

    private final UUID playerId;
    private final Location originalLocation;
    private final GameMode previousGameMode;
    private final boolean wasFlying;
    private final boolean wasAllowFlight;
    private final boolean wasVanished;
    private final ItemStack[] armorContents;
    private final ItemStack mainHandItem;

    // Ghost entity tracking
    private int ghostEntityId = -1;
    private UUID ghostEntityUuid;

    // Task IDs
    private int actionBarTaskId = -1;

    public FreecamSession(
            UUID playerId,
            Location originalLocation,
            GameMode previousGameMode,
            boolean wasFlying,
            boolean wasAllowFlight,
            boolean wasVanished,
            ItemStack[] armorContents,
            ItemStack mainHandItem) {
        this.playerId = playerId;
        this.originalLocation = originalLocation;
        this.previousGameMode = previousGameMode;
        this.wasFlying = wasFlying;
        this.wasAllowFlight = wasAllowFlight;
        this.wasVanished = wasVanished;
        this.armorContents = armorContents != null ? armorContents.clone() : new ItemStack[4];
        this.mainHandItem = mainHandItem;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Location getOriginalLocation() {
        return originalLocation;
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

    public ItemStack[] getArmorContents() {
        return armorContents;
    }

    public ItemStack getMainHandItem() {
        return mainHandItem;
    }

    public int getGhostEntityId() {
        return ghostEntityId;
    }

    public void setGhostEntityId(int ghostEntityId) {
        this.ghostEntityId = ghostEntityId;
    }

    public UUID getGhostEntityUuid() {
        return ghostEntityUuid;
    }

    public void setGhostEntityUuid(UUID ghostEntityUuid) {
        this.ghostEntityUuid = ghostEntityUuid;
    }

    public int getActionBarTaskId() {
        return actionBarTaskId;
    }

    public void setActionBarTaskId(int actionBarTaskId) {
        this.actionBarTaskId = actionBarTaskId;
    }

    public boolean hasActionBarTask() {
        return actionBarTaskId != -1;
    }

    public boolean hasGhostEntity() {
        return ghostEntityId != -1;
    }
}
