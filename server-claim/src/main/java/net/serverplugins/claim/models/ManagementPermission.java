package net.serverplugins.claim.models;

import org.bukkit.Material;

/**
 * Permissions that control management-level actions on a claim. These are separate from
 * ClaimPermission which controls gameplay actions.
 */
public enum ManagementPermission {
    MANAGE_PROFILE_INFO(
            "Manage Profile Info", Material.NAME_TAG, "Change claim name, icon, and color"),
    MANAGE_MEMBERS(
            "Manage Members", Material.PLAYER_HEAD, "Add, remove, promote, and demote members"),
    MANAGE_GROUPS(
            "Manage Groups", Material.WRITABLE_BOOK, "Edit group permissions and rename groups"),
    MANAGE_FLAGS("Manage Flags", Material.COMPARATOR, "Toggle PvP, explosions, mob spawning, etc."),
    MANAGE_CHUNKS(
            "Manage Chunks",
            Material.GRASS_BLOCK,
            "Claim and unclaim chunks (uses your chunk pool)");

    private final String displayName;
    private final Material icon;
    private final String description;

    ManagementPermission(String displayName, Material icon, String description) {
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public String getDescription() {
        return description;
    }
}
