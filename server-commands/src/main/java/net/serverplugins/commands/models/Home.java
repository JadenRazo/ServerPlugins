package net.serverplugins.commands.models;

import java.text.SimpleDateFormat;
import java.util.Date;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

/** Represents a player's home location with extended metadata. */
public class Home {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM d, yyyy");

    private String name;
    private String worldName;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private Material icon;
    private String description;
    private long createdAt;

    public Home(String name, Location location) {
        this(name, location, Material.RED_BED, null, System.currentTimeMillis());
    }

    public Home(String name, Location location, Material icon) {
        this(name, location, icon, null, System.currentTimeMillis());
    }

    public Home(String name, Location location, Material icon, String description, long createdAt) {
        this.name = name;
        this.worldName = location.getWorld() != null ? location.getWorld().getName() : "playworld";
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();
        this.icon = icon != null ? icon : Material.RED_BED;
        this.description = description;
        this.createdAt = createdAt;
    }

    public Home(
            String name,
            String worldName,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            Material icon,
            String description,
            long createdAt) {
        this.name = name;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.icon = icon != null ? icon : Material.RED_BED;
        this.description = description;
        this.createdAt = createdAt;
    }

    /** Get the location of this home. Returns null if the world no longer exists. */
    public Location getLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x, y, z, yaw, pitch);
    }

    /** Update the location of this home. */
    public void setLocation(Location location) {
        this.worldName =
                location.getWorld() != null ? location.getWorld().getName() : this.worldName;
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();
    }

    /** Get a formatted string of the creation date. */
    public String getFormattedCreatedDate() {
        return DATE_FORMAT.format(new Date(createdAt));
    }

    /** Get coordinates as a formatted string. */
    public String getFormattedCoordinates() {
        return String.format("%.0f, %.0f, %.0f", x, y, z);
    }

    /** Check if the world for this home still exists. */
    public boolean isWorldLoaded() {
        return Bukkit.getWorld(worldName) != null;
    }

    // Getters and setters

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public Material getIcon() {
        return icon;
    }

    public void setIcon(Material icon) {
        this.icon = icon != null ? icon : Material.RED_BED;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Home{"
                + "name='"
                + name
                + '\''
                + ", world='"
                + worldName
                + '\''
                + ", location=["
                + getFormattedCoordinates()
                + "]"
                + ", icon="
                + icon
                + '}';
    }
}
