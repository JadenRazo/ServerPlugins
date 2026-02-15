package net.serverplugins.claim.models;

/** Priority levels for notifications. */
public enum NotificationPriority {
    LOW("Low", "<gray>"),
    NORMAL("Normal", "<white>"),
    HIGH("High", "<yellow>"),
    URGENT("Urgent", "<red>");

    private final String displayName;
    private final String colorTag;

    NotificationPriority(String displayName, String colorTag) {
        this.displayName = displayName;
        this.colorTag = colorTag;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColor() {
        return colorTag;
    }

    /** Get the color name (e.g. "red", "yellow") for use in MiniMessage tags. */
    public String getColorName() {
        return colorTag.replace("<", "").replace(">", "");
    }

    /** Get the priority level from database string. */
    public static NotificationPriority fromString(String value) {
        if (value == null) return NORMAL;
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }
}
