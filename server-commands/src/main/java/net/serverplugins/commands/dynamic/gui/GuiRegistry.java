package net.serverplugins.commands.dynamic.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Registry for managing GUI definitions loaded from configuration. Provides a singleton instance
 * for global access.
 */
public class GuiRegistry {

    private static GuiRegistry instance;
    private final Map<String, ConfigurationSection> guiConfigs = new HashMap<>();

    private GuiRegistry() {}

    /** Get the singleton instance of the GUI registry. */
    public static GuiRegistry getInstance() {
        if (instance == null) {
            instance = new GuiRegistry();
        }
        return instance;
    }

    /**
     * Register a GUI configuration.
     *
     * @param id The unique identifier for this GUI
     * @param config The configuration section containing the GUI definition
     */
    public void registerGui(String id, ConfigurationSection config) {
        if (id == null || id.isEmpty() || config == null) {
            return;
        }
        guiConfigs.put(id, config);
    }

    /**
     * Create a GUI instance from a registered configuration.
     *
     * @param id The GUI identifier
     * @param player The player who will view the GUI
     * @return A new ConfigurableGui instance, or null if the GUI is not registered
     */
    public ConfigurableGui createGui(String id, Player player) {
        ConfigurationSection config = guiConfigs.get(id);
        if (config == null) {
            return null;
        }

        return GuiConfigParser.parseGui(config, player);
    }

    /**
     * Check if a GUI is registered.
     *
     * @param id The GUI identifier
     * @return true if the GUI is registered
     */
    public boolean isRegistered(String id) {
        return guiConfigs.containsKey(id);
    }

    /** Get all registered GUI identifiers. */
    public Set<String> getRegisteredGuis() {
        return guiConfigs.keySet();
    }

    /**
     * Unregister a GUI.
     *
     * @param id The GUI identifier to remove
     * @return true if the GUI was registered and removed
     */
    public boolean unregisterGui(String id) {
        return guiConfigs.remove(id) != null;
    }

    /** Clear all registered GUIs. */
    public void clear() {
        guiConfigs.clear();
    }

    /** Get the number of registered GUIs. */
    public int getGuiCount() {
        return guiConfigs.size();
    }
}
