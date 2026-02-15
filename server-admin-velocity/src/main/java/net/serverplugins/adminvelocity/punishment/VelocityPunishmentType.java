package net.serverplugins.adminvelocity.punishment;

/**
 * Punishment types supported by ServerAdmin Velocity.
 *
 * <p>This enum does NOT include Material references (unlike the Bukkit version) since those are not
 * available on Velocity.
 */
public enum VelocityPunishmentType {
    BAN("Ban", "banned", true),
    MUTE("Mute", "muted", true),
    KICK("Kick", "kicked", false),
    WARN("Warning", "warned", false),
    FREEZE("Freeze", "frozen", true);

    private final String displayName;
    private final String pastTense;
    private final boolean hasDuration;

    VelocityPunishmentType(String displayName, String pastTense, boolean hasDuration) {
        this.displayName = displayName;
        this.pastTense = pastTense;
        this.hasDuration = hasDuration;
    }

    /**
     * Gets the display name of this punishment type.
     *
     * @return the display name (e.g., "Ban")
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the past tense form for messages.
     *
     * @return the past tense (e.g., "banned")
     */
    public String getPastTense() {
        return pastTense;
    }

    /**
     * Checks if this punishment type supports duration.
     *
     * @return true if this punishment can have a duration
     */
    public boolean hasDuration() {
        return hasDuration;
    }

    /**
     * Parses a punishment type from a string.
     *
     * @param name the punishment type name
     * @return the punishment type, or null if not found
     */
    public static VelocityPunishmentType fromString(String name) {
        if (name == null) return null;
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
