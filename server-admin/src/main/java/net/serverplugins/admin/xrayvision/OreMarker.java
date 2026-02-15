package net.serverplugins.admin.xrayvision;

import java.util.UUID;
import org.bukkit.Location;

public class OreMarker {

    private final int entityId;
    private final UUID entityUuid;
    private final Location location;
    private final OreType oreType;

    public OreMarker(int entityId, UUID entityUuid, Location location, OreType oreType) {
        this.entityId = entityId;
        this.entityUuid = entityUuid;
        this.location = location;
        this.oreType = oreType;
    }

    public int getEntityId() {
        return entityId;
    }

    public UUID getEntityUuid() {
        return entityUuid;
    }

    public Location getLocation() {
        return location;
    }

    public OreType getOreType() {
        return oreType;
    }
}
