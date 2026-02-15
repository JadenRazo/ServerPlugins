package net.serverplugins.admin.punishment;

import org.bukkit.Material;

public enum PunishmentType {
    WARN("Warning", Material.PAPER, false),
    MUTE("Mute", Material.BARRIER, true),
    KICK("Kick", Material.LEATHER_BOOTS, false),
    BAN("Ban", Material.IRON_DOOR, true),
    FREEZE("Freeze", Material.ICE, true);

    private final String displayName;
    private final Material icon;
    private final boolean hasDuration;

    PunishmentType(String displayName, Material icon, boolean hasDuration) {
        this.displayName = displayName;
        this.icon = icon;
        this.hasDuration = hasDuration;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public boolean hasDuration() {
        return hasDuration;
    }

    public static PunishmentType fromString(String name) {
        if (name == null) return null;
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
