package net.serverplugins.commands.data;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serverplugins.commands.ServerCommands;
import net.serverplugins.commands.repository.CommandsRepository;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

public class WarpManager {

    private final ServerCommands plugin;
    private final CommandsRepository repository;
    private final boolean useDatabase;
    private final File warpsFile;
    private YamlConfiguration warpsConfig;
    private final Map<String, Location> warps = new HashMap<>();

    public WarpManager(ServerCommands plugin, CommandsRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        this.useDatabase = repository != null;
        this.warpsFile = new File(plugin.getDataFolder(), "warps.yml");

        if (!useDatabase) {
            if (!warpsFile.exists()) {
                try {
                    warpsFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            this.warpsConfig = YamlConfiguration.loadConfiguration(warpsFile);
        }

        loadWarps();
    }

    public void reload() {
        if (useDatabase) {
            loadWarps();
        } else {
            this.warpsConfig = YamlConfiguration.loadConfiguration(warpsFile);
            loadWarps();
        }
    }

    private void loadWarps() {
        warps.clear();

        if (useDatabase) {
            warps.putAll(repository.getAllWarps());
        } else {
            for (String key : warpsConfig.getKeys(false)) {
                String world = warpsConfig.getString(key + ".world");
                if (world == null || Bukkit.getWorld(world) == null) continue;

                double x = warpsConfig.getDouble(key + ".x");
                double y = warpsConfig.getDouble(key + ".y");
                double z = warpsConfig.getDouble(key + ".z");
                float yaw = (float) warpsConfig.getDouble(key + ".yaw");
                float pitch = (float) warpsConfig.getDouble(key + ".pitch");

                Location location = new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
                warps.put(key.toLowerCase(), location);
            }
        }
    }

    public void setWarp(String name, Location location) {
        setWarp(name, location, null);
    }

    public void setWarp(String name, Location location, UUID createdBy) {
        String key = name.toLowerCase();
        warps.put(key, location);

        if (location.getWorld() != null) {
            if (useDatabase) {
                // Save async to avoid blocking the main thread
                CompletableFuture.runAsync(() -> repository.saveWarp(key, location, createdBy));
            } else {
                warpsConfig.set(key + ".world", location.getWorld().getName());
                warpsConfig.set(key + ".x", location.getX());
                warpsConfig.set(key + ".y", location.getY());
                warpsConfig.set(key + ".z", location.getZ());
                warpsConfig.set(key + ".yaw", location.getYaw());
                warpsConfig.set(key + ".pitch", location.getPitch());
                save();
            }
        }
    }

    public void deleteWarp(String name) {
        String key = name.toLowerCase();
        warps.remove(key);

        if (useDatabase) {
            // Delete async to avoid blocking the main thread
            CompletableFuture.runAsync(() -> repository.deleteWarp(key));
        } else {
            warpsConfig.set(key, null);
            save();
        }
    }

    public Location getWarp(String name) {
        return warps.get(name.toLowerCase());
    }

    public Set<String> getWarpNames() {
        return warps.keySet();
    }

    public boolean warpExists(String name) {
        return warps.containsKey(name.toLowerCase());
    }

    private void save() {
        if (!useDatabase) {
            try {
                warpsConfig.save(warpsFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
