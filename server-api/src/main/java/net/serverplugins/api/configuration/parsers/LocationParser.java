package net.serverplugins.api.configuration.parsers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/** Parser for Location objects. Format: "world x y z [yaw pitch]" */
public class LocationParser extends Parser<Location> {

    private static final LocationParser INSTANCE = new LocationParser();

    private LocationParser() {}

    public static LocationParser getInstance() {
        return INSTANCE;
    }

    @Override
    public Location loadFromConfig(ConfigurationSection config, String path) {
        if (config == null) return null;

        String value = path != null ? config.getString(path) : null;
        if (value == null && path == null) {
            // Try to load from section format
            return loadFromSection(config);
        }

        if (value == null || value.isEmpty()) {
            return null;
        }

        return parseFromString(value);
    }

    /** Parse location from string format: "world x y z [yaw pitch]" */
    public Location parseFromString(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        String[] parts = value.split(" ");
        if (parts.length < 4) {
            warning("Invalid location format: " + value + " (expected: world x y z [yaw pitch])");
            return null;
        }

        try {
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) {
                warning("World not found: " + parts[0]);
                return null;
            }

            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);

            float yaw = 0;
            float pitch = 0;

            if (parts.length >= 5) {
                yaw = Float.parseFloat(parts[4]);
            }
            if (parts.length >= 6) {
                pitch = Float.parseFloat(parts[5]);
            }

            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            warning("Invalid number in location: " + value);
            return null;
        }
    }

    /** Load location from config section format. */
    private Location loadFromSection(ConfigurationSection section) {
        if (section == null) return null;

        String worldName = section.getString("world");
        if (worldName == null) return null;

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            warning("World not found: " + worldName);
            return null;
        }

        double x = section.getDouble("x", 0);
        double y = section.getDouble("y", 0);
        double z = section.getDouble("z", 0);
        float yaw = (float) section.getDouble("yaw", 0);
        float pitch = (float) section.getDouble("pitch", 0);

        return new Location(world, x, y, z, yaw, pitch);
    }

    @Override
    public void saveToConfig(ConfigurationSection config, String path, Location location) {
        if (config == null || location == null) return;

        String worldName = location.getWorld() != null ? location.getWorld().getName() : "world";
        String value =
                String.format(
                        "%s %.2f %.2f %.2f %.2f %.2f",
                        worldName,
                        location.getX(),
                        location.getY(),
                        location.getZ(),
                        location.getYaw(),
                        location.getPitch());

        config.set(path, value);
    }

    /** Convert location to string format. */
    public String toString(Location location) {
        if (location == null) return "";
        String worldName = location.getWorld() != null ? location.getWorld().getName() : "world";
        return String.format(
                "%s %.2f %.2f %.2f %.2f %.2f",
                worldName,
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch());
    }
}
