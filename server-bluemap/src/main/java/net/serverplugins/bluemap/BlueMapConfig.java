package net.serverplugins.bluemap;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import net.serverplugins.api.messages.PluginMessenger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class BlueMapConfig {

    private final ServerBlueMap plugin;
    private final FileConfiguration config;
    private PluginMessenger messenger;

    public BlueMapConfig(ServerBlueMap plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        initializeMessenger();
    }

    private void initializeMessenger() {
        // Save default messages.yml if it doesn't exist
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            try (InputStream is = plugin.getResource("messages.yml")) {
                if (is != null) {
                    Files.copy(is, messagesFile.toPath());
                }
            } catch (IOException e) {
                plugin.getLogger()
                        .warning("Failed to save default messages.yml: " + e.getMessage());
            }
        }

        // Load messages configuration
        FileConfiguration messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        this.messenger =
                new PluginMessenger(
                        messagesConfig,
                        "messages",
                        "<gradient:#4169e1:#1e90ff>[BlueMap]</gradient> ");
    }

    public boolean isEnabled() {
        return config.getBoolean("enabled", true);
    }

    public int getUpdateIntervalSeconds() {
        return config.getInt("update-interval-seconds", 300);
    }

    // Claim settings
    public String getClaimLayerName() {
        return config.getString("claims.layer-name", "Claims");
    }

    public boolean isClaimLayerDefaultVisible() {
        return config.getBoolean("claims.default-visible", true);
    }

    public Color getClaimFillColor() {
        return parseColor(config.getString("claims.fill-color", "#3399FF"));
    }

    public double getClaimFillOpacity() {
        return config.getDouble("claims.fill-opacity", 0.35);
    }

    public Color getClaimBorderColor() {
        return parseColor(config.getString("claims.border-color", "#1166CC"));
    }

    public int getClaimBorderWidth() {
        return config.getInt("claims.border-width", 3);
    }

    public int getClaimMarkerHeight() {
        return config.getInt("claims.marker-height", 64);
    }

    public boolean showClaimOwner() {
        return config.getBoolean("claims.show-owner", true);
    }

    public boolean showClaimName() {
        return config.getBoolean("claims.show-name", true);
    }

    public boolean showClaimMembers() {
        return config.getBoolean("claims.show-members", true);
    }

    // Nation settings
    public String getNationLayerName() {
        return config.getString("nations.layer-name", "Nations");
    }

    public boolean isNationLayerDefaultVisible() {
        return config.getBoolean("nations.default-visible", false);
    }

    public Color getNationFillColor() {
        return parseColor(config.getString("nations.fill-color", "#FFD700"));
    }

    public double getNationFillOpacity() {
        return config.getDouble("nations.fill-opacity", 0.15);
    }

    public Color getNationBorderColor() {
        return parseColor(config.getString("nations.border-color", "#CC9900"));
    }

    public int getNationBorderWidth() {
        return config.getInt("nations.border-width", 2);
    }

    public int getNationMarkerHeight() {
        return config.getInt("nations.marker-height", 64);
    }

    public boolean showNationTag() {
        return config.getBoolean("nations.show-tag", true);
    }

    public boolean showNationLeader() {
        return config.getBoolean("nations.show-leader", true);
    }

    public boolean showNationMembers() {
        return config.getBoolean("nations.show-members", true);
    }

    // War zone settings
    public String getWarZoneLayerName() {
        return config.getString("war-zones.layer-name", "War Zones");
    }

    public boolean isWarZoneLayerDefaultVisible() {
        return config.getBoolean("war-zones.default-visible", true);
    }

    public Color getWarZoneFillColor() {
        return parseColor(config.getString("war-zones.fill-color", "#FF0000"));
    }

    public double getWarZoneFillOpacity() {
        return config.getDouble("war-zones.fill-opacity", 0.4);
    }

    public Color getWarZoneBorderColor() {
        return parseColor(config.getString("war-zones.border-color", "#990000"));
    }

    public int getWarZoneBorderWidth() {
        return config.getInt("war-zones.border-width", 4);
    }

    public int getWarZoneMarkerHeight() {
        return config.getInt("war-zones.marker-height", 64);
    }

    public boolean isWarZonePulseEnabled() {
        return config.getBoolean("war-zones.pulse-animation", true);
    }

    // POI settings
    public String getPOILayerName() {
        return config.getString("pois.layer-name", "Points of Interest");
    }

    public boolean isPOILayerDefaultVisible() {
        return config.getBoolean("pois.default-visible", true);
    }

    public int getPOIMarkerHeight() {
        return config.getInt("pois.marker-height", 64);
    }

    public String getPOICategoryColor(String category) {
        return config.getString("pois.categories." + category + ".color", "#FFFFFF");
    }

    public String getPOICategoryIcon(String category) {
        return config.getString("pois.categories." + category + ".icon", null);
    }

    public String getPOICategoryDisplayName(String category) {
        return config.getString("pois.categories." + category + ".name", null);
    }

    public void reload() {
        plugin.reloadConfig();
        initializeMessenger();
    }

    public PluginMessenger getMessenger() {
        return messenger;
    }

    private Color parseColor(String colorStr) {
        try {
            String hex = colorStr.replace("#", "");
            int rgb = Integer.parseInt(hex, 16);
            return new Color(rgb);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Invalid color format: " + colorStr + ", using default");
            return new Color(0x3399FF);
        }
    }
}
