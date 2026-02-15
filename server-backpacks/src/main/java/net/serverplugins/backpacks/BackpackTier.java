package net.serverplugins.backpacks;

/**
 * Represents the six tiers of backpacks in the progression system. This enum provides tier
 * information for backpack upgrades and crafting.
 */
public enum BackpackTier {
    TIER_1("tier1", "I", 9, 100),
    TIER_2("tier2", "II", 18, 101),
    TIER_3("tier3", "III", 27, 102),
    TIER_4("tier4", "IV", 36, 105),
    TIER_5("tier5", "V", 45, 104),
    TIER_6("tier6", "VI", 54, 103);

    private final String id;
    private final String romanNumeral;
    private final int defaultSize;
    private final int customModelData;

    BackpackTier(String id, String romanNumeral, int defaultSize, int customModelData) {
        this.id = id;
        this.romanNumeral = romanNumeral;
        this.defaultSize = defaultSize;
        this.customModelData = customModelData;
    }

    /** Gets the configuration ID for this tier (e.g., "tier1", "tier2") */
    public String getId() {
        return id;
    }

    /** Gets the Roman numeral representation (e.g., "I", "II", "III") */
    public String getRomanNumeral() {
        return romanNumeral;
    }

    /** Gets the default inventory size for this tier */
    public int getDefaultSize() {
        return defaultSize;
    }

    /** Gets the custom model data value for this tier */
    public int getCustomModelData() {
        return customModelData;
    }

    /** Gets the tier number (1-6) */
    public int getTierNumber() {
        return ordinal() + 1;
    }

    /** Gets the next tier, or null if this is the max tier */
    public BackpackTier getNextTier() {
        if (this == TIER_6) {
            return null;
        }
        return values()[ordinal() + 1];
    }

    /** Gets the previous tier, or null if this is the first tier */
    public BackpackTier getPreviousTier() {
        if (this == TIER_1) {
            return null;
        }
        return values()[ordinal() - 1];
    }

    /** Checks if this tier can be upgraded */
    public boolean canUpgrade() {
        return this != TIER_6;
    }

    /** Looks up a tier by its ID */
    public static BackpackTier fromId(String id) {
        if (id == null) {
            return null;
        }
        for (BackpackTier tier : values()) {
            if (tier.id.equalsIgnoreCase(id)) {
                return tier;
            }
        }
        return null;
    }

    /** Looks up a tier by its tier number (1-6) */
    public static BackpackTier fromNumber(int number) {
        if (number < 1 || number > 6) {
            return null;
        }
        return values()[number - 1];
    }

    /** Looks up a tier by its custom model data value */
    public static BackpackTier fromCustomModelData(int cmd) {
        for (BackpackTier tier : values()) {
            if (tier.customModelData == cmd) {
                return tier;
            }
        }
        return null;
    }
}
