package net.serverplugins.api.broadcast;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Filters for targeting broadcast audiences. All filters are combined with AND logic - a player
 * must pass all filters.
 */
public class BroadcastFilter {
    private String permission;
    private Set<String> worlds;
    private Location center;
    private double radius;

    public BroadcastFilter() {
        this.worlds = new HashSet<>();
        this.radius = -1; // -1 means no radius filtering
    }

    /** Require players to have a specific permission */
    public BroadcastFilter permission(String permission) {
        this.permission = permission;
        return this;
    }

    /** Only players in specific world(s) */
    public BroadcastFilter world(String... worldNames) {
        for (String worldName : worldNames) {
            this.worlds.add(worldName);
        }
        return this;
    }

    /** Only players in a specific world */
    public BroadcastFilter world(World world) {
        this.worlds.add(world.getName());
        return this;
    }

    /** Only players within radius of a location */
    public BroadcastFilter radius(Location center, double radius) {
        this.center = center;
        this.radius = radius;
        return this;
    }

    /** Test if a player passes all filters */
    public boolean test(Player player) {
        // Permission filter
        if (permission != null && !player.hasPermission(permission)) {
            return false;
        }

        // World filter
        if (!worlds.isEmpty() && !worlds.contains(player.getWorld().getName())) {
            return false;
        }

        // Radius filter
        if (radius > 0 && center != null) {
            if (!player.getWorld().equals(center.getWorld())) {
                return false;
            }
            if (player.getLocation().distance(center) > radius) {
                return false;
            }
        }

        return true;
    }

    // Getters
    public String getPermission() {
        return permission;
    }

    public Set<String> getWorlds() {
        return worlds;
    }

    public Location getCenter() {
        return center;
    }

    public double getRadius() {
        return radius;
    }

    public boolean hasFilters() {
        return permission != null || !worlds.isEmpty() || radius > 0;
    }
}
