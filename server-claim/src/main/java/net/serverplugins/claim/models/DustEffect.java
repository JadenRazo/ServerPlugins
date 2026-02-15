package net.serverplugins.claim.models;

import org.bukkit.Color;
import org.bukkit.Material;

public enum DustEffect {
    // Solid Colors - unlocked progressively by playtime (density: 0.3)
    WHITE(Color.WHITE, Material.WHITE_STAINED_GLASS_PANE, "White", 0, false, 0.3),
    LIGHT_GRAY(Color.SILVER, Material.LIGHT_GRAY_STAINED_GLASS_PANE, "Light Gray", 60, false, 0.3),
    GRAY(Color.GRAY, Material.GRAY_STAINED_GLASS_PANE, "Gray", 300, false, 0.3),
    BLACK(Color.BLACK, Material.BLACK_STAINED_GLASS_PANE, "Black", 600, false, 0.3),
    RED(Color.RED, Material.RED_STAINED_GLASS_PANE, "Red", 1200, false, 0.3),
    ORANGE(Color.ORANGE, Material.ORANGE_STAINED_GLASS_PANE, "Orange", 1800, false, 0.3),
    YELLOW(Color.YELLOW, Material.YELLOW_STAINED_GLASS_PANE, "Yellow", 2400, false, 0.3),
    LIME(Color.LIME, Material.LIME_STAINED_GLASS_PANE, "Lime", 3000, false, 0.3),
    GREEN(Color.GREEN, Material.GREEN_STAINED_GLASS_PANE, "Green", 3600, false, 0.3),
    CYAN(Color.TEAL, Material.CYAN_STAINED_GLASS_PANE, "Cyan", 4200, false, 0.3),
    LIGHT_BLUE(Color.AQUA, Material.LIGHT_BLUE_STAINED_GLASS_PANE, "Light Blue", 4800, false, 0.3),
    BLUE(Color.BLUE, Material.BLUE_STAINED_GLASS_PANE, "Blue", 5400, false, 0.3),
    PURPLE(Color.PURPLE, Material.PURPLE_STAINED_GLASS_PANE, "Purple", 6000, false, 0.3),
    MAGENTA(Color.FUCHSIA, Material.MAGENTA_STAINED_GLASS_PANE, "Magenta", 7200, false, 0.3),
    PINK(Color.fromRGB(255, 105, 180), Material.PINK_STAINED_GLASS_PANE, "Pink", 8400, false, 0.3),
    BROWN(Color.fromRGB(139, 69, 19), Material.BROWN_STAINED_GLASS_PANE, "Brown", 9600, false, 0.3),

    // Animated Effects - premium unlocks (density: 0.5 for better visual continuity)
    RAINBOW(null, Material.BEACON, "Rainbow", 12000, true, 0.5),
    GRADIENT_FIRE(null, Material.BLAZE_POWDER, "Fire Gradient", 15000, true, 0.5),
    GRADIENT_ICE(null, Material.BLUE_ICE, "Ice Gradient", 18000, true, 0.5),
    GRADIENT_NATURE(null, Material.OAK_LEAVES, "Nature Gradient", 21000, true, 0.5),
    PULSING_RED(Color.RED, Material.REDSTONE, "Pulsing Red", 24000, true, 0.5),
    PULSING_GOLD(Color.YELLOW, Material.GOLD_INGOT, "Pulsing Gold", 27000, true, 0.5),
    BREATHING(null, Material.ENDER_PEARL, "Breathing", 30000, true, 0.5);

    private final Color baseColor;
    private final Material displayMaterial;
    private final String displayName;
    private final long requiredPlaytimeMinutes;
    private final boolean animated;
    private final double recommendedDensity;

    DustEffect(
            Color baseColor,
            Material displayMaterial,
            String displayName,
            long requiredPlaytimeMinutes,
            boolean animated,
            double recommendedDensity) {
        this.baseColor = baseColor;
        this.displayMaterial = displayMaterial;
        this.displayName = displayName;
        this.requiredPlaytimeMinutes = requiredPlaytimeMinutes;
        this.animated = animated;
        this.recommendedDensity = recommendedDensity;
    }

    public Color getBaseColor() {
        return baseColor;
    }

    public Material getDisplayMaterial() {
        return displayMaterial;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getRequiredPlaytimeMinutes() {
        return requiredPlaytimeMinutes;
    }

    public boolean isAnimated() {
        return animated;
    }

    public double getRecommendedDensity() {
        return recommendedDensity;
    }

    /**
     * Returns a static color for this effect. Used when static particle mode is enabled. For solid
     * colors, returns the base color. For animated effects without a base color, returns a
     * representative color.
     */
    public Color getStaticColor() {
        if (baseColor != null) {
            return baseColor;
        }
        // Provide static colors for animated effects without a base color
        return switch (this) {
            case RAINBOW -> Color.fromRGB(0, 255, 127); // Vibrant spring green (middle of rainbow)
            case GRADIENT_FIRE -> Color.fromRGB(255, 140, 0); // DarkOrange
            case GRADIENT_ICE -> Color.fromRGB(135, 206, 235); // SkyBlue
            case GRADIENT_NATURE -> Color.fromRGB(34, 139, 34); // ForestGreen
            case BREATHING -> Color.fromRGB(192, 192, 240); // Soft blue-white
            default -> Color.WHITE;
        };
    }

    public boolean isUnlockedFor(long playtimeMinutes) {
        return playtimeMinutes >= requiredPlaytimeMinutes;
    }

    public String getColorTag() {
        return switch (this) {
            case WHITE -> "<white>";
            case LIGHT_GRAY -> "<gray>";
            case GRAY -> "<dark_gray>";
            case BLACK -> "<black>";
            case RED, PULSING_RED -> "<red>";
            case ORANGE -> "<gold>";
            case YELLOW, PULSING_GOLD -> "<yellow>";
            case LIME -> "<green>";
            case GREEN -> "<dark_green>";
            case CYAN -> "<dark_aqua>";
            case LIGHT_BLUE -> "<aqua>";
            case BLUE -> "<blue>";
            case PURPLE -> "<dark_purple>";
            case MAGENTA -> "<light_purple>";
            case PINK -> "<#ff69b4>";
            case BROWN -> "<#8b4513>";
            case RAINBOW -> "<rainbow>";
            case GRADIENT_FIRE -> "<gradient:#ff4500:#ff8c00:#ffd700>";
            case GRADIENT_ICE -> "<gradient:#ffffff:#87ceeb:#4169e1>";
            case GRADIENT_NATURE -> "<gradient:#32cd32:#228b22:#006400>";
            case BREATHING -> "<gradient:#e0e0e0:#a0a0ff>";
        };
    }

    public Color getColorAtTick(long tick) {
        if (!animated) {
            return baseColor;
        }
        return calculateAnimatedColor(tick);
    }

    /**
     * Returns color based on horizontal position (0.0 = start of border, 1.0 = end of border). Used
     * for creating horizontal rainbow gradients that cycle around claim perimeters.
     *
     * @param horizontalPosition Position from 0.0 (start) to 1.0 (end) along border
     * @param tick Current tick for optional animation
     */
    public Color getColorAtPosition(double horizontalPosition, long tick) {
        if (this == RAINBOW) {
            return calculateRainbowGradient(horizontalPosition, tick);
        }
        // For non-rainbow effects, fall back to tick-based color
        return getColorAtTick(tick);
    }

    private Color calculateAnimatedColor(long tick) {
        return switch (this) {
            case RAINBOW -> calculateRainbow(tick);
            case GRADIENT_FIRE ->
                    interpolateGradient(
                            Color.fromRGB(255, 69, 0), // OrangeRed
                            Color.fromRGB(255, 140, 0), // DarkOrange
                            Color.fromRGB(255, 215, 0), // Gold
                            tick);
            case GRADIENT_ICE ->
                    interpolateGradient(
                            Color.WHITE,
                            Color.fromRGB(135, 206, 235), // SkyBlue
                            Color.fromRGB(65, 105, 225), // RoyalBlue
                            tick);
            case GRADIENT_NATURE ->
                    interpolateGradient(
                            Color.fromRGB(50, 205, 50), // LimeGreen
                            Color.fromRGB(34, 139, 34), // ForestGreen
                            Color.fromRGB(0, 100, 0), // DarkGreen
                            tick);
            case PULSING_RED -> calculatePulse(Color.RED, tick);
            case PULSING_GOLD -> calculatePulse(Color.fromRGB(255, 215, 0), tick);
            case BREATHING -> calculateBreathing(tick);
            default -> baseColor != null ? baseColor : Color.WHITE;
        };
    }

    /**
     * Horizontal rainbow gradient that cycles through all ROYGBIV colors. Position 0.0 = red
     * (start), progresses through rainbow, 1.0 wraps back to red Creates seamless repeating rainbow
     * pattern around claim perimeters. Tick adds optional slow horizontal scrolling animation.
     */
    private Color calculateRainbowGradient(double horizontalPosition, long tick) {
        // Define rainbow colors in order: Red -> Violet (ROYGBIV)
        Color[] rainbowColors = {
            Color.fromRGB(255, 0, 0), // Red
            Color.fromRGB(255, 127, 0), // Orange
            Color.fromRGB(255, 255, 0), // Yellow
            Color.fromRGB(0, 255, 0), // Green
            Color.fromRGB(0, 127, 255), // Blue
            Color.fromRGB(75, 0, 130), // Indigo
            Color.fromRGB(148, 0, 211) // Violet
        };

        // Optional: Add slow horizontal scrolling animation (60 second cycle)
        double timeOffset = (tick % 1200) / 1200.0; // Very slow scroll
        double position = (horizontalPosition + timeOffset) % 1.0;

        // Map position to color index (multiply by length to cycle through full spectrum)
        double colorIndex = position * rainbowColors.length;
        int index1 = ((int) colorIndex) % rainbowColors.length;
        int index2 = (index1 + 1) % rainbowColors.length;
        double blend = colorIndex - (int) colorIndex;

        // Smooth interpolation between adjacent rainbow colors
        return lerpColor(rainbowColors[index1], rainbowColors[index2], blend);
    }

    private Color calculateRainbow(long tick) {
        // Fallback for old time-based rainbow (not used for horizontal gradient)
        // This is kept for compatibility but rainbow now uses horizontal gradient
        return calculateRainbowGradient(0.5, tick);
    }

    private Color interpolateGradient(Color c1, Color c2, Color c3, long tick) {
        // Smooth transition between 3 colors over ~3 seconds (60 ticks)
        double position = (tick % 60) / 60.0;
        double angle = position * Math.PI * 2;
        double t = (Math.cos(angle) + 1) / 2; // 0 to 1 smoothly

        if (t < 0.5) {
            // Interpolate c1 to c2
            double localT = t * 2;
            return lerpColor(c1, c2, localT);
        } else {
            // Interpolate c2 to c3
            double localT = (t - 0.5) * 2;
            return lerpColor(c2, c3, localT);
        }
    }

    private Color calculatePulse(Color base, long tick) {
        // Brightness oscillates between 50% and 100% over ~2 seconds (40 ticks)
        double brightness = 0.75 + 0.25 * Math.sin(tick * Math.PI / 20.0);
        int r = (int) (base.getRed() * brightness);
        int g = (int) (base.getGreen() * brightness);
        int b = (int) (base.getBlue() * brightness);
        return Color.fromRGB(
                Math.min(255, Math.max(0, r)),
                Math.min(255, Math.max(0, g)),
                Math.min(255, Math.max(0, b)));
    }

    private Color calculateBreathing(long tick) {
        // Realistic breathing pattern: 2s inhale → 0.5s hold → 1.5s exhale → 0.5s hold (4.5s cycle
        // = 90 ticks)
        // Breathing rhythm simulates fade in/out by adjusting brightness (alpha simulation)
        double cyclePosition = (tick % 90) / 90.0;
        double breathingPhase;

        if (cyclePosition < 0.444) {
            // Inhale phase: 2s (40 ticks) - slow fade in
            double inhaleProgress = cyclePosition / 0.444;
            breathingPhase = easeInOut(inhaleProgress);
        } else if (cyclePosition < 0.555) {
            // Hold (inhaled): 0.5s (10 ticks) - stay at peak brightness
            breathingPhase = 1.0;
        } else if (cyclePosition < 0.888) {
            // Exhale phase: 1.5s (30 ticks) - faster fade out
            double exhaleProgress = (cyclePosition - 0.555) / 0.333;
            breathingPhase = 1.0 - easeInOut(exhaleProgress);
        } else {
            // Hold (exhaled): 0.5s (10 ticks) - stay at lowest brightness
            breathingPhase = 0.0;
        }

        // Map breathing phase to brightness (30% to 100%)
        double brightness = 0.3 + (breathingPhase * 0.7);

        // Apply brightness to a soft blue-white color
        Color baseColor = Color.fromRGB(200, 200, 240);
        int r = (int) (baseColor.getRed() * brightness);
        int g = (int) (baseColor.getGreen() * brightness);
        int b = (int) (baseColor.getBlue() * brightness);

        return Color.fromRGB(
                Math.min(255, Math.max(0, r)),
                Math.min(255, Math.max(0, g)),
                Math.min(255, Math.max(0, b)));
    }

    /** Easing function for smooth acceleration/deceleration (ease-in-out cubic) */
    private double easeInOut(double t) {
        return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    }

    private Color lerpColor(Color c1, Color c2, double t) {
        int r = (int) (c1.getRed() + (c2.getRed() - c1.getRed()) * t);
        int g = (int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * t);
        int b = (int) (c1.getBlue() + (c2.getBlue() - c1.getBlue()) * t);
        return Color.fromRGB(
                Math.min(255, Math.max(0, r)),
                Math.min(255, Math.max(0, g)),
                Math.min(255, Math.max(0, b)));
    }

    private Color hsbToColor(float hue, float saturation, float brightness) {
        int rgb = java.awt.Color.HSBtoRGB(hue, saturation, brightness);
        return Color.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    public static DustEffect fromString(String name) {
        if (name == null || name.isEmpty()) {
            return WHITE;
        }
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return WHITE;
        }
    }

    public static DustEffect[] getSolidColors() {
        return new DustEffect[] {
            WHITE, LIGHT_GRAY, GRAY, BLACK, RED, ORANGE, YELLOW, LIME,
            GREEN, CYAN, LIGHT_BLUE, BLUE, PURPLE, MAGENTA, PINK, BROWN
        };
    }

    public static DustEffect[] getAnimatedEffects() {
        return new DustEffect[] {
            RAINBOW,
            GRADIENT_FIRE,
            GRADIENT_ICE,
            GRADIENT_NATURE,
            PULSING_RED,
            PULSING_GOLD,
            BREATHING
        };
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
}
