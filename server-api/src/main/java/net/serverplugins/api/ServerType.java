package net.serverplugins.api;

/**
 * Enum representing the type of server (Spigot vs Paper). Used for determining message format and
 * feature availability.
 */
public enum ServerType {
    SPIGOT("Spigot"),
    PAPER("Paper");

    private final String name;
    private static ServerType detected = null;

    ServerType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /** Check if this server type supports modern Adventure API. */
    public boolean isModern() {
        return this == PAPER;
    }

    /** Check if this server type is legacy (BungeeCord chat). */
    public boolean isLegacy() {
        return this == SPIGOT;
    }

    /** Detect and cache the current server type. */
    public static ServerType detect() {
        if (detected != null) {
            return detected;
        }

        try {
            // Check for Paper-specific class
            Class.forName("io.papermc.paper.adventure.PaperAdventure");
            detected = PAPER;
        } catch (ClassNotFoundException e) {
            try {
                // Alternative Paper check
                Class.forName("com.destroystokyo.paper.PaperConfig");
                detected = PAPER;
            } catch (ClassNotFoundException e2) {
                detected = SPIGOT;
            }
        }

        return detected;
    }

    /** Get the detected server type. */
    public static ServerType get() {
        return detect();
    }

    /** Check if the server is running Paper. */
    public static boolean isPaper() {
        return detect() == PAPER;
    }

    /** Check if the server is running Spigot (non-Paper). */
    public static boolean isSpigot() {
        return detect() == SPIGOT;
    }
}
