package net.serverplugins.api.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.kyori.adventure.text.format.TextColor;

public final class LegacyText {

    private LegacyText() {}

    private static final char COLOR_CHAR = '\u00A7';
    private static final char ALT_COLOR_CHAR = '&';
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern SECTION_HEX_PATTERN = Pattern.compile("§x(§[A-Fa-f0-9]){6}");
    private static final Pattern GRADIENT_PATTERN =
            Pattern.compile("<gradient:([#A-Fa-f0-9]+):([#A-Fa-f0-9]+)>(.*?)</gradient>");

    /**
     * Translates alternate color codes (using &) to section symbol (§) format.
     *
     * @param text The text to translate
     * @return The translated text with § color codes
     */
    @Nonnull
    public static String translateAlternateColorCodes(@Nonnull String text) {
        return translateAlternateColorCodes(ALT_COLOR_CHAR, text);
    }

    /**
     * Translates alternate color codes to section symbol (§) format.
     *
     * @param altChar The alternate color code character
     * @param text The text to translate
     * @return The translated text with § color codes
     */
    @Nonnull
    public static String translateAlternateColorCodes(char altChar, @Nonnull String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == altChar
                    && "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(chars[i + 1]) > -1) {
                chars[i] = COLOR_CHAR;
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    /**
     * Translates hex color codes (&#RRGGBB) to Minecraft format (§x§R§R§G§G§B§B).
     *
     * @param text The text to translate
     * @return The translated text with Minecraft hex color codes
     */
    @Nonnull
    public static String translateHexColorCodes(@Nonnull String text) {
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append("§").append(Character.toLowerCase(c));
            }
            matcher.appendReplacement(result, replacement.toString());
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Fully colorizes text, translating both & codes and hex codes.
     *
     * @param text The text to colorize
     * @return The fully colorized text
     */
    @Nonnull
    public static String colorize(@Nonnull String text) {
        String result = translateHexColorCodes(text);
        result = translateGradients(result);
        return translateAlternateColorCodes(result);
    }

    /**
     * Translates gradient tags to colored text. Format: <gradient:#RRGGBB:#RRGGBB>text</gradient>
     *
     * @param text The text containing gradient tags
     * @return The text with gradients applied
     */
    @Nonnull
    public static String translateGradients(@Nonnull String text) {
        Matcher matcher = GRADIENT_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String startHex = matcher.group(1).replace("#", "");
            String endHex = matcher.group(2).replace("#", "");
            String content = matcher.group(3);

            String gradient = applyGradient(content, startHex, endHex);
            matcher.appendReplacement(result, Matcher.quoteReplacement(gradient));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Applies a gradient between two colors to text.
     *
     * @param text The text to apply gradient to
     * @param startHex The starting color hex (without #)
     * @param endHex The ending color hex (without #)
     * @return The text with gradient applied
     */
    @Nonnull
    public static String applyGradient(
            @Nonnull String text, @Nonnull String startHex, @Nonnull String endHex) {
        if (text.isEmpty()) return text;

        int startR = Integer.parseInt(startHex.substring(0, 2), 16);
        int startG = Integer.parseInt(startHex.substring(2, 4), 16);
        int startB = Integer.parseInt(startHex.substring(4, 6), 16);

        int endR = Integer.parseInt(endHex.substring(0, 2), 16);
        int endG = Integer.parseInt(endHex.substring(2, 4), 16);
        int endB = Integer.parseInt(endHex.substring(4, 6), 16);

        StringBuilder result = new StringBuilder();
        int length = text.length();

        for (int i = 0; i < length; i++) {
            float ratio = length == 1 ? 0 : (float) i / (length - 1);

            int r = (int) (startR + (endR - startR) * ratio);
            int g = (int) (startG + (endG - startG) * ratio);
            int b = (int) (startB + (endB - startB) * ratio);

            String hex = String.format("%02x%02x%02x", r, g, b);
            result.append("§x");
            for (char c : hex.toCharArray()) {
                result.append("§").append(c);
            }
            result.append(text.charAt(i));
        }

        return result.toString();
    }

    /**
     * Strips all color codes from text.
     *
     * @param text The text to strip
     * @return The text without color codes
     */
    @Nonnull
    public static String stripColor(@Nonnull String text) {
        if (text.isEmpty()) return text;

        // First strip hex colors
        String result = SECTION_HEX_PATTERN.matcher(text).replaceAll("");

        // Then strip regular color codes
        char[] chars = result.toCharArray();
        StringBuilder stripped = new StringBuilder();

        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == COLOR_CHAR && i + 1 < chars.length) {
                char next = chars[i + 1];
                if ("0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(next) > -1) {
                    i++;
                    continue;
                }
            }
            stripped.append(chars[i]);
        }

        return stripped.toString();
    }

    /**
     * Gets the last color code from text.
     *
     * @param text The text to check
     * @return The last color code including § symbol, or null if none found
     */
    @Nullable
    public static String getLastColor(@Nonnull String text) {
        String lastColor = null;
        char[] chars = text.toCharArray();

        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == COLOR_CHAR) {
                char code = chars[i + 1];
                if ("0123456789AaBbCcDdEeFf".indexOf(code) > -1) {
                    lastColor = String.valueOf(COLOR_CHAR) + code;
                } else if ("RrXx".indexOf(code) > -1) {
                    lastColor = null;
                }
            }
        }

        return lastColor;
    }

    /**
     * Parses a hex string to a TextColor.
     *
     * @param hex The hex string (with or without #)
     * @return The TextColor, or null if invalid
     */
    @Nullable
    public static TextColor parseHex(@Nonnull String hex) {
        try {
            if (hex.startsWith("#")) {
                hex = hex.substring(1);
            }
            return TextColor.color(Integer.parseInt(hex, 16));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Creates a rainbow gradient text.
     *
     * @param text The text to apply rainbow to
     * @return The rainbow-colored text
     */
    @Nonnull
    public static String rainbow(@Nonnull String text) {
        if (text.isEmpty()) return text;

        String[] colors = {"FF0000", "FF7F00", "FFFF00", "00FF00", "0000FF", "4B0082", "8B00FF"};
        StringBuilder result = new StringBuilder();
        int length = text.length();

        for (int i = 0; i < length; i++) {
            float position = (float) i / length * (colors.length - 1);
            int colorIndex = (int) position;
            float ratio = position - colorIndex;

            String startHex = colors[colorIndex];
            String endHex = colors[Math.min(colorIndex + 1, colors.length - 1)];

            int startR = Integer.parseInt(startHex.substring(0, 2), 16);
            int startG = Integer.parseInt(startHex.substring(2, 4), 16);
            int startB = Integer.parseInt(startHex.substring(4, 6), 16);

            int endR = Integer.parseInt(endHex.substring(0, 2), 16);
            int endG = Integer.parseInt(endHex.substring(2, 4), 16);
            int endB = Integer.parseInt(endHex.substring(4, 6), 16);

            int r = (int) (startR + (endR - startR) * ratio);
            int g = (int) (startG + (endG - startG) * ratio);
            int b = (int) (startB + (endB - startB) * ratio);

            String hex = String.format("%02x%02x%02x", r, g, b);
            result.append("§x");
            for (char c : hex.toCharArray()) {
                result.append("§").append(c);
            }
            result.append(text.charAt(i));
        }

        return result.toString();
    }
}
