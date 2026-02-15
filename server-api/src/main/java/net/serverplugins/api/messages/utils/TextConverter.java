package net.serverplugins.api.messages.utils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utility class for converting between legacy (&x) and modern (MiniMessage) color formats. */
public final class TextConverter {

    private static final Map<String, String> LEGACY_TO_MODERN = new HashMap<>();
    private static final String[] END_TAGS;
    private static final Pattern HEX_PATTERN = Pattern.compile("#[a-fA-F0-9]{6}");
    private static final Pattern MODERN_HEX_PATTERN = Pattern.compile("<(color:)?#[a-fA-F0-9]{6}>");

    static {
        LEGACY_TO_MODERN.put("&0", "<black>");
        LEGACY_TO_MODERN.put("&1", "<dark_blue>");
        LEGACY_TO_MODERN.put("&2", "<dark_green>");
        LEGACY_TO_MODERN.put("&3", "<dark_aqua>");
        LEGACY_TO_MODERN.put("&4", "<dark_red>");
        LEGACY_TO_MODERN.put("&5", "<dark_purple>");
        LEGACY_TO_MODERN.put("&6", "<gold>");
        LEGACY_TO_MODERN.put("&7", "<gray>");
        LEGACY_TO_MODERN.put("&8", "<dark_gray>");
        LEGACY_TO_MODERN.put("&9", "<blue>");
        LEGACY_TO_MODERN.put("&a", "<green>");
        LEGACY_TO_MODERN.put("&b", "<aqua>");
        LEGACY_TO_MODERN.put("&c", "<red>");
        LEGACY_TO_MODERN.put("&d", "<light_purple>");
        LEGACY_TO_MODERN.put("&e", "<yellow>");
        LEGACY_TO_MODERN.put("&f", "<white>");
        LEGACY_TO_MODERN.put("&k", "<obfuscated>");
        LEGACY_TO_MODERN.put("&l", "<bold>");
        LEGACY_TO_MODERN.put("&m", "<strikethrough>");
        LEGACY_TO_MODERN.put("&n", "<underline>");
        LEGACY_TO_MODERN.put("&o", "<italic>");
        LEGACY_TO_MODERN.put("&r", "<reset>");

        END_TAGS =
                new String[] {
                    "</black>", "</dark_blue>", "</dark_green>", "</dark_aqua>",
                    "</dark_red>", "</dark_purple>", "</gold>", "</gray>",
                    "</dark_gray>", "</blue>", "</green>", "</aqua>",
                    "</red>", "</light_purple>", "</yellow>", "</white>",
                    "</obfuscated>", "</bold>", "</strikethrough>", "</underline>",
                    "</italic>", "</reset>"
                };
    }

    private TextConverter() {}

    /**
     * Convert legacy format (&x colors) to MiniMessage format.
     *
     * @param message The legacy formatted message
     * @return The MiniMessage formatted message
     */
    public static String legacyToModern(String message) {
        if (message == null) return "";

        // Temporarily protect already-modern hex codes
        Set<String> modernMatches = new HashSet<>();
        Matcher modernMatcher = MODERN_HEX_PATTERN.matcher(message);
        while (modernMatcher.find()) {
            modernMatches.add(modernMatcher.group());
        }
        for (String match : modernMatches) {
            message = message.replace(match, match.replace("#", "SERVER:HEX:"));
        }

        // Convert legacy hex (#RRGGBB) to MiniMessage format (<#RRGGBB>)
        Set<String> hexMatches = new HashSet<>();
        Matcher hexMatcher = HEX_PATTERN.matcher(message);
        while (hexMatcher.find()) {
            hexMatches.add(hexMatcher.group());
        }
        for (String hex : hexMatches) {
            message = message.replace(hex, "<" + hex + ">");
        }

        // Restore protected modern hex codes
        message = message.replace("SERVER:HEX:", "#");

        // Convert legacy color codes to MiniMessage tags
        for (Map.Entry<String, String> entry : LEGACY_TO_MODERN.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue());
        }

        return message;
    }

    /**
     * Convert MiniMessage format to legacy format (&x colors).
     *
     * @param message The MiniMessage formatted message
     * @return The legacy formatted message
     */
    public static String modernToLegacy(String message) {
        if (message == null) return "";

        // Convert MiniMessage hex (<#RRGGBB> or <color:#RRGGBB>) to legacy hex
        Set<String> hexMatches = new HashSet<>();
        Matcher matcher = MODERN_HEX_PATTERN.matcher(message);
        while (matcher.find()) {
            hexMatches.add(matcher.group());
        }
        for (String match : hexMatches) {
            String hex = "#" + match.split("#")[1].substring(0, 6);
            message = message.replace(match, hex);
        }

        // Convert MiniMessage tags to legacy codes
        for (Map.Entry<String, String> entry : LEGACY_TO_MODERN.entrySet()) {
            message = message.replace(entry.getValue(), entry.getKey());
        }

        // Remove closing tags
        for (String endTag : END_TAGS) {
            message = message.replace(endTag, "");
        }

        return message;
    }

    /**
     * Check if a message uses MiniMessage format.
     *
     * @param message The message to check
     * @return true if the message contains MiniMessage tags
     */
    public static boolean isMiniMessage(String message) {
        if (message == null) return false;
        return message.contains("<") && message.contains(">");
    }

    /**
     * Check if a message uses legacy format.
     *
     * @param message The message to check
     * @return true if the message contains legacy color codes
     */
    public static boolean isLegacy(String message) {
        if (message == null) return false;
        return message.contains("&") || message.contains("\u00a7");
    }
}
