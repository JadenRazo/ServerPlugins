package net.serverplugins.admin.vanish;

public enum VanishMode {
    OFF("Not vanished"),
    STAFF("Visible to staff only"),
    FULL("Invisible to everyone");

    private final String description;

    VanishMode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isVanished() {
        return this != OFF;
    }

    public static VanishMode fromString(String str) {
        if (str == null) return OFF;
        try {
            return valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            return OFF;
        }
    }
}
