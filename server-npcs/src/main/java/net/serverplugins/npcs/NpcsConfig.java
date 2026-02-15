package net.serverplugins.npcs;

import net.serverplugins.api.messages.PluginMessenger;
import org.bukkit.configuration.file.FileConfiguration;

public class NpcsConfig {

    private final ServerNpcs plugin;
    private final FileConfiguration config;
    private PluginMessenger messenger;

    public NpcsConfig(ServerNpcs plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        reload();
    }

    /** Reloads the configuration and reinitializes the messenger. */
    public final void reload() {
        plugin.reloadConfig();

        // Initialize PluginMessenger with NPC-themed prefix
        this.messenger =
                new PluginMessenger(
                        plugin.getConfig(),
                        "messages",
                        "<gradient:#9b59b6:#e91e63>[ServerNpcs]</gradient> ");
    }

    /**
     * Gets the PluginMessenger instance for sending messages.
     *
     * @return The PluginMessenger instance
     */
    public PluginMessenger getMessenger() {
        return messenger;
    }

    public boolean isDebugEnabled() {
        return config.getBoolean("debug", false);
    }

    public boolean isDialogGuiEnabled() {
        return config.getBoolean("dialog.gui-enabled", true);
    }

    public boolean isDialogFormEnabled() {
        return config.getBoolean("dialog.form-enabled", true);
    }

    public int getDialogTimeout() {
        return config.getInt("dialog.timeout-seconds", 60);
    }

    public String getDialogsFolder() {
        return config.getString("dialog.dialogs-folder", "dialogs");
    }

    public boolean isFancyNpcsIntegrationEnabled() {
        return config.getBoolean("integrations.fancynpcs", true);
    }

    public boolean isCitizensIntegrationEnabled() {
        return config.getBoolean("integrations.citizens", false);
    }
}
