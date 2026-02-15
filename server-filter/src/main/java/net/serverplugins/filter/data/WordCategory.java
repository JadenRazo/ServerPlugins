package net.serverplugins.filter.data;

import org.bukkit.Material;

public enum WordCategory {
    SLURS("Slurs", Material.BARRIER, true),
    EXTREME("Extreme", Material.TNT, false),
    MODERATE("Moderate", Material.FIRE_CHARGE, false),
    MILD("Mild", Material.PAPER, false);

    private final String displayName;
    private final Material guiIcon;
    private final boolean alwaysBlocked;

    WordCategory(String displayName, Material guiIcon, boolean alwaysBlocked) {
        this.displayName = displayName;
        this.guiIcon = guiIcon;
        this.alwaysBlocked = alwaysBlocked;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getGuiIcon() {
        return guiIcon;
    }

    public boolean isAlwaysBlocked() {
        return alwaysBlocked;
    }
}
