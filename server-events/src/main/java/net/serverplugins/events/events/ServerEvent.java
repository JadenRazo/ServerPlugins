package net.serverplugins.events.events;

/** Interface for all server events. */
public interface ServerEvent {

    /** Get the event type. */
    EventType getType();

    /** Start the event. */
    void start();

    /** Stop the event (cleanup). */
    void stop();

    /** Check if the event is currently active. */
    boolean isActive();

    /** Get the display name for this event. */
    String getDisplayName();

    /** Get the minimum number of players required for this event. Default is 0 (no minimum). */
    default int getMinimumPlayers() {
        return 0;
    }

    /** Event types. */
    enum EventType {
        PINATA("Pinata", "pinata"),
        PREMIUM_PINATA("Premium Pinata", "premium_pinata"),
        SPELLING("Spelling Bee", "spelling"),
        CRAFTING("Crafting Challenge", "crafting"),
        MATH("Math Race", "math"),
        DROP_PARTY("Drop Party", "drop_party"),
        DRAGON("Dragon Fight", "dragon");

        private final String displayName;
        private final String configKey;

        EventType(String displayName, String configKey) {
            this.displayName = displayName;
            this.configKey = configKey;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getConfigKey() {
            return configKey;
        }

        public static EventType fromString(String name) {
            for (EventType type : values()) {
                if (type.name().equalsIgnoreCase(name) || type.configKey.equalsIgnoreCase(name)) {
                    return type;
                }
            }
            return null;
        }
    }
}
