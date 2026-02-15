package net.serverplugins.claim.models;

import org.bukkit.Material;

public enum WarpVisibility {
    PUBLIC("Public", Material.LIME_STAINED_GLASS_PANE, "<green>", "Anyone can visit"),
    ALLOWLIST("Allowlist", Material.YELLOW_STAINED_GLASS_PANE, "<yellow>", "Only allowed players"),
    PRIVATE("Private", Material.RED_STAINED_GLASS_PANE, "<red>", "Owner and trusted only");

    private final String displayName;
    private final Material icon;
    private final String colorTag;
    private final String description;

    WarpVisibility(String displayName, Material icon, String colorTag, String description) {
        this.displayName = displayName;
        this.icon = icon;
        this.colorTag = colorTag;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public String getColorTag() {
        return colorTag;
    }

    public String getDescription() {
        return description;
    }

    public WarpVisibility next() {
        WarpVisibility[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
