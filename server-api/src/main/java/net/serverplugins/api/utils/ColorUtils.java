package net.serverplugins.api.utils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.Color;

/** Utility class for color operations. */
public final class ColorUtils {

    private ColorUtils() {}

    /**
     * Parse a Bukkit Color from a hex string.
     *
     * @param hex The hex color string (with or without # prefix)
     * @return The parsed Color, or WHITE if parsing fails
     */
    @Nonnull
    public static Color colorFromHex(@Nullable String hex) {
        if (hex == null || hex.isEmpty()) {
            return Color.WHITE;
        }

        // Remove # prefix if present
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }

        if (hex.length() != 6) {
            return Color.WHITE;
        }

        try {
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            return Color.fromRGB(r, g, b);
        } catch (NumberFormatException e) {
            return Color.WHITE;
        }
    }

    /**
     * Convert a Bukkit Color to hex string.
     *
     * @param color The color to convert
     * @return The hex string with # prefix
     */
    @Nonnull
    public static String colorToHex(@Nonnull Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * Parse a java.awt.Color from hex string.
     *
     * @param hex The hex color string
     * @return The parsed Color
     */
    @Nonnull
    public static java.awt.Color awtColorFromHex(@Nullable String hex) {
        if (hex == null || hex.isEmpty()) {
            return java.awt.Color.WHITE;
        }

        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }

        if (hex.length() != 6) {
            return java.awt.Color.WHITE;
        }

        try {
            return java.awt.Color.decode("#" + hex);
        } catch (NumberFormatException e) {
            return java.awt.Color.WHITE;
        }
    }

    /**
     * Convert a Bukkit Color to java.awt.Color.
     *
     * @param color The Bukkit color
     * @return The AWT color
     */
    @Nonnull
    public static java.awt.Color toAwtColor(@Nonnull Color color) {
        return new java.awt.Color(color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * Convert a java.awt.Color to Bukkit Color.
     *
     * @param color The AWT color
     * @return The Bukkit color
     */
    @Nonnull
    public static Color toBukkitColor(@Nonnull java.awt.Color color) {
        return Color.fromRGB(color.getRed(), color.getGreen(), color.getBlue());
    }
}
