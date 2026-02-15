package net.serverplugins.commands.data.punishment;

import org.bukkit.Material;

/** Represents different types of punishments that can be issued. */
public enum PunishmentType {
    BAN("Ban", Material.IRON_DOOR, "c"),
    TEMPBAN("Temp Ban", Material.CLOCK, "6"),
    KICK("Kick", Material.LEATHER_BOOTS, "e"),
    MUTE("Mute", Material.BARRIER, "9"),
    FREEZE("Freeze", Material.ICE, "b"),
    WARN("Warning", Material.PAPER, "d"),
    UNBAN("Unban", Material.OAK_DOOR, "a"),
    UNMUTE("Unmute", Material.LIME_WOOL, "a"),
    UNFREEZE("Unfreeze", Material.PACKED_ICE, "a");

    private final String displayName;
    private final Material icon;
    private final String colorCode;

    PunishmentType(String displayName, Material icon, String colorCode) {
        this.displayName = displayName;
        this.icon = icon;
        this.colorCode = colorCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public String getColorCode() {
        return colorCode;
    }

    public String getColoredName() {
        return "§" + colorCode + displayName;
    }

    /** Returns true if this punishment type removes/lifts a previous punishment */
    public boolean isRemoval() {
        return this == UNBAN || this == UNMUTE || this == UNFREEZE;
    }

    /** Returns true if this punishment has a duration component */
    public boolean hasDuration() {
        return this == TEMPBAN || this == MUTE;
    }

    /** Returns the corresponding removal type for this punishment */
    public PunishmentType getRemovalType() {
        return switch (this) {
            case BAN, TEMPBAN -> UNBAN;
            case MUTE -> UNMUTE;
            case FREEZE -> UNFREEZE;
            default -> null;
        };
    }
}
