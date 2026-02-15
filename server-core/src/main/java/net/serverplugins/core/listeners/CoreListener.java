package net.serverplugins.core.listeners;

import java.util.Map;
import net.serverplugins.core.ServerCore;
import net.serverplugins.core.features.Feature;
import org.bukkit.event.Listener;

public class CoreListener implements Listener {

    private final ServerCore plugin;
    private final Map<String, Feature> features;

    public CoreListener(ServerCore plugin, Map<String, Feature> features) {
        this.plugin = plugin;
        this.features = features;
        registerFeatureListeners();
    }

    private void registerFeatureListeners() {
        features.values()
                .forEach(
                        feature -> {
                            if (feature instanceof Listener listener) {
                                plugin.getServer()
                                        .getPluginManager()
                                        .registerEvents(listener, plugin);
                            }
                        });
    }
}
