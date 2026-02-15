package net.serverplugins.items.models;

import java.util.UUID;
import org.bukkit.Location;

public class FurnitureInstance {

    private final UUID displayEntityUuid;
    private final UUID interactionEntityUuid;
    private final String furnitureId;
    private final Location location;
    private final float yaw;
    private final UUID placedBy;

    public FurnitureInstance(
            UUID displayEntityUuid,
            UUID interactionEntityUuid,
            String furnitureId,
            Location location,
            float yaw,
            UUID placedBy) {
        this.displayEntityUuid = displayEntityUuid;
        this.interactionEntityUuid = interactionEntityUuid;
        this.furnitureId = furnitureId;
        this.location = location;
        this.yaw = yaw;
        this.placedBy = placedBy;
    }

    public UUID getDisplayEntityUuid() {
        return displayEntityUuid;
    }

    public UUID getInteractionEntityUuid() {
        return interactionEntityUuid;
    }

    public String getFurnitureId() {
        return furnitureId;
    }

    public Location getLocation() {
        return location;
    }

    public float getYaw() {
        return yaw;
    }

    public UUID getPlacedBy() {
        return placedBy;
    }
}
