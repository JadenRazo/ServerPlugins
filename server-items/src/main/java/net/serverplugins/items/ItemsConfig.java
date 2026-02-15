package net.serverplugins.items;

import net.serverplugins.api.messages.PluginMessenger;
import org.bukkit.configuration.file.FileConfiguration;

public class ItemsConfig {

    private final ServerItems plugin;
    private PluginMessenger messenger;

    public ItemsConfig(ServerItems plugin) {
        this.plugin = plugin;
        this.messenger =
                new PluginMessenger(
                        plugin.getConfig(),
                        "messages",
                        "<gradient:#FF6B6B:#FFD93D>[Items]</gradient> ");
    }

    private FileConfiguration config() {
        return plugin.getConfig();
    }

    public PluginMessenger getMessenger() {
        return messenger;
    }

    // Pack settings
    public boolean isAutoGenerate() {
        return config().getBoolean("pack.auto-generate", true);
    }

    public int getPackFormat() {
        return config().getInt("pack.format", 46);
    }

    public String getPackDescription() {
        return config().getString("pack.description", "ServerPlugins Custom Content");
    }

    public String getPackOutput() {
        return config().getString("pack.output", "pack/generated/ServerPlugins-Items.zip");
    }

    public boolean isMultiVersion() {
        return config().getBoolean("pack.multi-version", true);
    }

    public String getPackOutputDir() {
        return config().getString("pack.output-dir", "pack/generated");
    }

    // Block settings
    public boolean isBlocksEnabled() {
        return config().getBoolean("blocks.enabled", true);
    }

    // Furniture settings
    public boolean isFurnitureEnabled() {
        return config().getBoolean("furniture.enabled", true);
    }

    public int getFurnitureCleanupInterval() {
        return config().getInt("furniture.cleanup-interval", 300);
    }

    public void reload() {
        plugin.reloadConfig();
        this.messenger =
                new PluginMessenger(
                        plugin.getConfig(),
                        "messages",
                        "<gradient:#FF6B6B:#FFD93D>[Items]</gradient> ");
    }
}
