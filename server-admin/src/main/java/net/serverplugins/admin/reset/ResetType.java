package net.serverplugins.admin.reset;

import org.bukkit.Material;

public enum ResetType {
    CLAIMS("Claims", Material.GRASS_BLOCK, "serveradmin.reset.claims", "Reset all claimed chunks"),
    ECONOMY(
            "Economy",
            Material.GOLD_INGOT,
            "serveradmin.reset.economy",
            "Reset balance to starting amount"),
    PLAYTIME("Playtime", Material.CLOCK, "serveradmin.reset.playtime", "Reset playtime statistics"),
    RANK("Rank", Material.NAME_TAG, "serveradmin.reset.rank", "Reset to default rank"),
    PUNISHMENTS(
            "Punishments",
            Material.BARRIER,
            "serveradmin.reset.punishments",
            "Clear punishment history"),
    ALL("All Data", Material.TNT, "serveradmin.reset.all", "Reset everything above");

    private final String displayName;
    private final Material icon;
    private final String permission;
    private final String description;

    ResetType(String displayName, Material icon, String permission, String description) {
        this.displayName = displayName;
        this.icon = icon;
        this.permission = permission;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public String getPermission() {
        return permission;
    }

    public String getDescription() {
        return description;
    }

    public static ResetType fromString(String name) {
        if (name == null) return null;
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
