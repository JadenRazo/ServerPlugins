package net.serverplugins.api.messages.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.md_5.bungee.api.ChatColor;

/** Utility class for processing legacy color codes and hex colors. */
public final class LegacyText {

    private static final Pattern HEX_PATTERN = Pattern.compile("#([A-Fa-f0-9]{6})");

    private LegacyText() {}

    /**
     * Replace all color codes in a string, including: - Ampersand codes (&a, &b, etc.) - Hex codes
     * (#RRGGBB)
     *
     * @param string The string to process
     * @return The string with color codes translated
     */
    @Nonnull
    public static String replaceAllColorCodes(@Nullable String string) {
        if (string == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', translateHexColorCodes("#", "", string));
    }

    /**
     * Translate hex color codes to Minecraft format. Converts #RRGGBB to §x§R§R§G§G§B§B
     *
     * @param startTag Start tag before hex (usually "#")
     * @param endTag End tag after hex (usually "")
     * @param message The message to process
     * @return The message with hex colors translated
     */
    @Nonnull
    public static String translateHexColorCodes(
            @Nonnull String startTag, @Nonnull String endTag, @Nonnull String message) {
        Pattern hexPattern =
                Pattern.compile(
                        Pattern.quote(startTag) + "([A-Fa-f0-9]{6})" + Pattern.quote(endTag));
        Matcher matcher = hexPattern.matcher(message);
        StringBuilder buffer = new StringBuilder(message.length() + 32);

        while (matcher.find()) {
            String group = matcher.group(1);
            StringBuilder replacement = new StringBuilder("\u00a7x");
            for (char c : group.toCharArray()) {
                replacement.append("\u00a7").append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }

        return matcher.appendTail(buffer).toString();
    }

    /**
     * Strip all color codes from a string.
     *
     * @param string The string to strip
     * @return The string without color codes
     */
    @Nonnull
    public static String stripColor(@Nullable String string) {
        if (string == null) {
            return "";
        }
        return ChatColor.stripColor(replaceAllColorCodes(string));
    }

    /**
     * Check if a string contains hex color codes.
     *
     * @param string The string to check
     * @return true if the string contains hex colors
     */
    public static boolean containsHexColors(@Nullable String string) {
        if (string == null) {
            return false;
        }
        return HEX_PATTERN.matcher(string).find();
    }
}
