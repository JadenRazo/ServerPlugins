package net.serverplugins.claim.models;

import java.util.EnumSet;
import java.util.Set;
import org.bukkit.Material;

public enum ClaimGroup {
    ENEMY("Enemy", Material.RED_STAINED_GLASS_PANE, "<red>", EnumSet.noneOf(ClaimPermission.class)),

    VISITOR(
            "Visitor",
            Material.YELLOW_STAINED_GLASS_PANE,
            "<yellow>",
            EnumSet.of(ClaimPermission.ENTER_CLAIM)),

    ACQUAINTANCE(
            "Acquaintance",
            Material.LIME_STAINED_GLASS_PANE,
            "<green>",
            EnumSet.of(
                    ClaimPermission.ENTER_CLAIM,
                    ClaimPermission.USE_DOORS,
                    ClaimPermission.USE_FENCE_GATES,
                    ClaimPermission.DAMAGE_HOSTILE,
                    ClaimPermission.RIDE_VEHICLES)),

    FRIEND(
            "Friend",
            Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            "<aqua>",
            EnumSet.of(
                    ClaimPermission.ENTER_CLAIM,
                    ClaimPermission.USE_DOORS,
                    ClaimPermission.USE_FENCE_GATES,
                    ClaimPermission.DAMAGE_HOSTILE,
                    ClaimPermission.OPEN_CONTAINERS,
                    ClaimPermission.INTERACT_ENTITIES,
                    ClaimPermission.DAMAGE_PASSIVE,
                    ClaimPermission.USE_REDSTONE,
                    ClaimPermission.PICKUP_ITEMS,
                    ClaimPermission.DROP_ITEMS,
                    ClaimPermission.USE_BREWING_STANDS,
                    ClaimPermission.USE_ANVILS,
                    ClaimPermission.RIDE_VEHICLES)),

    ADMIN(
            "Admin",
            Material.PURPLE_STAINED_GLASS_PANE,
            "<light_purple>",
            EnumSet.allOf(ClaimPermission.class));

    private final String displayName;
    private final Material icon;
    private final String colorTag;
    private final Set<ClaimPermission> defaultPermissions;

    ClaimGroup(
            String displayName,
            Material icon,
            String colorTag,
            Set<ClaimPermission> defaultPermissions) {
        this.displayName = displayName;
        this.icon = icon;
        this.colorTag = colorTag;
        this.defaultPermissions = defaultPermissions;
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

    public Set<ClaimPermission> getDefaultPermissions() {
        return EnumSet.copyOf(defaultPermissions);
    }
}
