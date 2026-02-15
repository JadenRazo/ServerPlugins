package net.serverplugins.filter.data;

import java.util.EnumSet;
import java.util.Set;

public enum FilterLevel {
    STRICT(
            "Strict (PG)",
            "Maximum filtering for family-friendly chat",
            WordCategory.SLURS,
            WordCategory.EXTREME,
            WordCategory.MODERATE,
            WordCategory.MILD),
    MODERATE(
            "Moderate (13+)",
            "Blocks explicit content and harsh language",
            WordCategory.SLURS,
            WordCategory.EXTREME,
            WordCategory.MODERATE),
    RELAXED(
            "Relaxed (17+)",
            "Blocks severe profanity only",
            WordCategory.SLURS,
            WordCategory.EXTREME),
    MINIMAL("Minimal (Adults)", "Only slurs are blocked", WordCategory.SLURS);

    private final String displayName;
    private final String description;
    private final Set<WordCategory> blockedCategories;

    FilterLevel(String displayName, String description, WordCategory... categories) {
        this.displayName = displayName;
        this.description = description;
        this.blockedCategories =
                categories.length > 0
                        ? EnumSet.of(categories[0], categories)
                        : EnumSet.noneOf(WordCategory.class);
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public Set<WordCategory> getBlockedCategories() {
        return blockedCategories;
    }

    public boolean isBlocked(WordCategory category) {
        return blockedCategories.contains(category);
    }

    public static FilterLevel fromString(String name) {
        if (name == null) return STRICT;
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return STRICT;
        }
    }
}
