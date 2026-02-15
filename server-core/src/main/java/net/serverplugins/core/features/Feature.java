package net.serverplugins.core.features;

import net.serverplugins.core.ServerCore;

public abstract class Feature {

    protected final ServerCore plugin;
    private boolean enabled = false;

    public Feature(ServerCore plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        if (!enabled) {
            enabled = true;
            onEnable();
        }
    }

    protected void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void disable() {
        if (enabled) {
            onDisable();
            enabled = false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public abstract String getName();

    public abstract String getDescription();

    protected void onEnable() {}

    protected void onDisable() {}
}
