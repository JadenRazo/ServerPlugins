package net.serverplugins.items.models;

import java.util.UUID;
import org.bukkit.Location;

public record PlacedBlock(
        long id, String world, int x, int y, int z, String blockId, UUID placedBy, long placedAt) {

    public PlacedBlock(String world, int x, int y, int z, String blockId, UUID placedBy) {
        this(0, world, x, y, z, blockId, placedBy, System.currentTimeMillis());
    }

    public boolean matchesLocation(Location loc) {
        return loc.getWorld().getName().equals(world)
                && loc.getBlockX() == x
                && loc.getBlockY() == y
                && loc.getBlockZ() == z;
    }

    public long chunkKey() {
        return ((long) (x >> 4) << 32) | ((z >> 4) & 0xFFFFFFFFL);
    }
}
