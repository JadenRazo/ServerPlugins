package net.serverplugins.admin.freeze;

import java.util.UUID;
import org.bukkit.Location;

public class FreezeData {

    private final Location location;
    private final UUID frozenBy;
    private final String reason;
    private final long timestamp;

    public FreezeData(Location location, UUID frozenBy, String reason, long timestamp) {
        this.location = location;
        this.frozenBy = frozenBy;
        this.reason = reason;
        this.timestamp = timestamp;
    }

    public Location getLocation() {
        return location;
    }

    public UUID getFrozenBy() {
        return frozenBy;
    }

    public String getReason() {
        return reason;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
