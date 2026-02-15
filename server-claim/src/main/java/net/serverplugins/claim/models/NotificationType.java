package net.serverplugins.claim.models;

import org.bukkit.Material;

/** Types of notifications that can be sent to players. */
public enum NotificationType {
    NATION_INVITE("Nation Invite", Material.PAPER, "You've been invited to join a nation"),
    UPKEEP_WARNING("Upkeep Warning", Material.GOLD_INGOT, "Upkeep payment is due soon"),
    WAR_DECLARED("War Declared", Material.DIAMOND_SWORD, "War has been declared"),
    TRANSFER_RECEIVED("Transfer Received", Material.ENDER_CHEST, "You received claim ownership"),
    MEMBER_REMOVED("Member Removed", Material.BARRIER, "You were removed from a claim"),
    PERMISSION_CHANGED(
            "Permission Changed", Material.WRITABLE_BOOK, "Your claim permissions changed");

    private final String displayName;
    private final Material icon;
    private final String defaultMessage;

    NotificationType(String displayName, Material icon, String defaultMessage) {
        this.displayName = displayName;
        this.icon = icon;
        this.defaultMessage = defaultMessage;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
