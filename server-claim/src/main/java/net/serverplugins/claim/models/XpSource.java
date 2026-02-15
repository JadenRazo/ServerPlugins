package net.serverplugins.claim.models;

public enum XpSource {
    PLAYTIME("Playtime", 1, "XP earned for time spent in the claim"),
    BLOCKS_PLACED("Blocks Placed", 2, "XP earned for placing blocks"),
    BLOCKS_BROKEN("Blocks Broken", 2, "XP earned for breaking blocks"),
    MEMBER_ADDED("Member Added", 50, "XP earned for adding a new member"),
    UPKEEP_PAID("Upkeep Paid", 100, "XP earned for paying upkeep on time"),
    CHUNK_CLAIMED("Chunk Claimed", 25, "XP earned for claiming new chunks"),
    ACHIEVEMENT("Achievement", 0, "XP earned from achievements"),
    ADMIN_GRANT("Admin Grant", 0, "XP granted by an admin");

    private final String displayName;
    private final int defaultAmount;
    private final String description;

    XpSource(String displayName, int defaultAmount, String description) {
        this.displayName = displayName;
        this.defaultAmount = defaultAmount;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDefaultAmount() {
        return defaultAmount;
    }

    public String getDescription() {
        return description;
    }

    public static XpSource fromString(String name) {
        try {
            return XpSource.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
