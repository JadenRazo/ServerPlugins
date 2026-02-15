package net.serverplugins.api.configuration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Level;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public class ConfigManager {

    private final Plugin plugin;

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public FileConfiguration loadConfig(String fileName) {
        File configFile = new File(plugin.getDataFolder(), fileName);
        if (!configFile.exists()) {
            plugin.saveResource(fileName, false);
        }
        return YamlConfiguration.loadConfiguration(configFile);
    }

    public FileConfiguration loadConfigWithDefaults(String fileName) {
        File configFile = new File(plugin.getDataFolder(), fileName);
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        InputStream defaultStream = plugin.getResource(fileName);
        if (defaultStream != null) {
            FileConfiguration defaultConfig =
                    YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream));
            config.setDefaults(defaultConfig);
        }
        return config;
    }

    public boolean saveConfig(FileConfiguration config, String fileName) {
        File configFile = new File(plugin.getDataFolder(), fileName);
        try {
            config.save(configFile);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save config to " + fileName, e);
            return false;
        }
    }

    public FileConfiguration reloadConfig(String fileName) {
        File configFile = new File(plugin.getDataFolder(), fileName);
        return YamlConfiguration.loadConfiguration(configFile);
    }

    public boolean createConfig(String fileName, boolean saveFromResources) {
        File configFile = new File(plugin.getDataFolder(), fileName);
        if (configFile.exists()) {
            return false;
        }
        if (saveFromResources) {
            plugin.saveResource(fileName, false);
        } else {
            try {
                configFile.getParentFile().mkdirs();
                configFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create config file " + fileName, e);
                return false;
            }
        }
        return true;
    }

    public boolean configExists(String fileName) {
        return new File(plugin.getDataFolder(), fileName).exists();
    }

    public File getDataFolder() {
        return plugin.getDataFolder();
    }
}
