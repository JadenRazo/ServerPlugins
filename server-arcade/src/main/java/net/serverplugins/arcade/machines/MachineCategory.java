package net.serverplugins.arcade.machines;

/**
 * Defines the category of arcade machines. Personal machines can be placed by players in their
 * claims. Casino machines are larger and typically admin-placed in casino buildings.
 */
public enum MachineCategory {

    /**
     * Personal machines are smaller, portable machines that players can place. These can be placed
     * in player claims with appropriate permissions.
     */
    PERSONAL("personal", "serverarcade.place.personal"),

    /**
     * Casino machines are larger, stationary machines for casino buildings. These require admin
     * permissions to place.
     */
    CASINO("casino", "serverarcade.place.casino");

    private final String configName;
    private final String placePermission;

    MachineCategory(String configName, String placePermission) {
        this.configName = configName;
        this.placePermission = placePermission;
    }

    public String getConfigName() {
        return configName;
    }

    public String getPlacePermission() {
        return placePermission;
    }

    /** Parse category from config string. Defaults to CASINO if not recognized. */
    public static MachineCategory fromString(String name) {
        if (name == null || name.isEmpty()) {
            return CASINO;
        }

        for (MachineCategory category : values()) {
            if (category.configName.equalsIgnoreCase(name)) {
                return category;
            }
        }

        // Try direct enum name match
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CASINO;
        }
    }

    /** Check if the category is personal (smaller, player-placeable). */
    public boolean isPersonal() {
        return this == PERSONAL;
    }

    /** Check if the category is casino (larger, admin-placed). */
    public boolean isCasino() {
        return this == CASINO;
    }
}
