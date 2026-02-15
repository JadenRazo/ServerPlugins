package net.serverplugins.claim.models;

import org.bukkit.Color;
import org.bukkit.Material;

public enum ProfileColor {
    WHITE(Color.WHITE, Material.WHITE_STAINED_GLASS_PANE, "White", 0),
    LIGHT_GRAY(Color.SILVER, Material.LIGHT_GRAY_STAINED_GLASS_PANE, "Light Gray", 60),
    GRAY(Color.GRAY, Material.GRAY_STAINED_GLASS_PANE, "Gray", 300),
    BLACK(Color.BLACK, Material.BLACK_STAINED_GLASS_PANE, "Black", 600),
    RED(Color.RED, Material.RED_STAINED_GLASS_PANE, "Red", 1200),
    ORANGE(Color.ORANGE, Material.ORANGE_STAINED_GLASS_PANE, "Orange", 1800),
    YELLOW(Color.YELLOW, Material.YELLOW_STAINED_GLASS_PANE, "Yellow", 2400),
    LIME(Color.LIME, Material.LIME_STAINED_GLASS_PANE, "Lime", 3000),
    GREEN(Color.GREEN, Material.GREEN_STAINED_GLASS_PANE, "Green", 3600),
    CYAN(Color.TEAL, Material.CYAN_STAINED_GLASS_PANE, "Cyan", 4200),
    LIGHT_BLUE(Color.AQUA, Material.LIGHT_BLUE_STAINED_GLASS_PANE, "Light Blue", 4800),
    BLUE(Color.BLUE, Material.BLUE_STAINED_GLASS_PANE, "Blue", 5400),
    PURPLE(Color.PURPLE, Material.PURPLE_STAINED_GLASS_PANE, "Purple", 6000),
    MAGENTA(Color.FUCHSIA, Material.MAGENTA_STAINED_GLASS_PANE, "Magenta", 7200),
    PINK(Color.fromRGB(255, 105, 180), Material.PINK_STAINED_GLASS_PANE, "Pink", 8400),
    BROWN(Color.fromRGB(139, 69, 19), Material.BROWN_STAINED_GLASS_PANE, "Brown", 9600);

    private final Color bukkitColor;
    private final Material glassPaneMaterial;
    private final String displayName;
    private final long requiredPlaytimeMinutes;

    ProfileColor(
            Color bukkitColor,
            Material glassPaneMaterial,
            String displayName,
            long requiredPlaytimeMinutes) {
        this.bukkitColor = bukkitColor;
        this.glassPaneMaterial = glassPaneMaterial;
        this.displayName = displayName;
        this.requiredPlaytimeMinutes = requiredPlaytimeMinutes;
    }

    public Color getBukkitColor() {
        return bukkitColor;
    }

    public Material getGlassPaneMaterial() {
        return glassPaneMaterial;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getRequiredPlaytimeMinutes() {
        return requiredPlaytimeMinutes;
    }

    public boolean isUnlockedFor(long playtimeMinutes) {
        return playtimeMinutes >= requiredPlaytimeMinutes;
    }

    public String formatPlaytimeRequired() {
        long minutes = requiredPlaytimeMinutes;
        if (minutes == 0) return "None";
        long hours = minutes / 60;
        long mins = minutes % 60;
        if (hours == 0) return mins + " min";
        if (mins == 0) return hours + " hr";
        return hours + " hr " + mins + " min";
    }

    public String getColorTag() {
        return switch (this) {
            case WHITE -> "<white>";
            case ORANGE -> "<gold>";
            case MAGENTA -> "<light_purple>";
            case LIGHT_BLUE -> "<aqua>";
            case YELLOW -> "<yellow>";
            case LIME -> "<green>";
            case PINK -> "<#ff69b4>";
            case GRAY -> "<gray>";
            case LIGHT_GRAY -> "<gray>";
            case CYAN -> "<dark_aqua>";
            case PURPLE -> "<dark_purple>";
            case BLUE -> "<blue>";
            case BROWN -> "<#8b4513>";
            case GREEN -> "<dark_green>";
            case RED -> "<red>";
            case BLACK -> "<dark_gray>";
        };
    }

    public static ProfileColor fromString(String name) {
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return WHITE;
        }
    }
}
