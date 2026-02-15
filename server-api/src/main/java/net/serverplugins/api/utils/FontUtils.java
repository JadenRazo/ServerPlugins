package net.serverplugins.api.utils;

import java.util.HashMap;
import java.util.Map;

/** Utility class for font/text transformations. */
public final class FontUtils {

    private static final Map<Character, Character> NORMAL_TO_SMALL_CAP = new HashMap<>();

    static {
        char[] normalAlphabet = "abcdefghijklmnopqrstuvwxyz".toCharArray();
        char[] smallCapAlphabet = {
            '\u1d00', '\u0299', '\u1d04', '\u1d05', '\u1d07', '\ua730',
            '\u0262', '\u029c', '\u026a', '\u1d0a', '\u1d0b', '\u029f',
            '\u1d0d', '\u0274', '\u1d0f', '\u1d18', '\u01eb', '\u0280',
            '\u0455', '\u1d1b', '\u1d1c', '\u1d20', '\u1d21', '\u0445',
            '\u028f', '\u1d22'
        };

        for (int i = 0; i < normalAlphabet.length; i++) {
            NORMAL_TO_SMALL_CAP.put(normalAlphabet[i], smallCapAlphabet[i]);
        }
    }

    private FontUtils() {}

    /**
     * Convert a string to small caps Unicode characters.
     *
     * @param string The string to convert
     * @return The string in small caps
     */
    public static String toSmallCap(String string) {
        if (string == null) {
            return "";
        }

        char[] chars = string.toLowerCase().toCharArray();
        for (int i = 0; i < chars.length; i++) {
            Character replacement = NORMAL_TO_SMALL_CAP.get(chars[i]);
            if (replacement != null) {
                chars[i] = replacement;
            }
        }
        return new String(chars);
    }

    /**
     * Convert small caps text back to normal text.
     *
     * @param string The small caps string
     * @return The normal string
     */
    public static String fromSmallCap(String string) {
        if (string == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        for (char c : string.toCharArray()) {
            boolean found = false;
            for (Map.Entry<Character, Character> entry : NORMAL_TO_SMALL_CAP.entrySet()) {
                if (entry.getValue() == c) {
                    result.append(entry.getKey());
                    found = true;
                    break;
                }
            }
            if (!found) {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Convert a string to subscript numbers.
     *
     * @param number The number string
     * @return The subscript version
     */
    public static String toSubscript(String number) {
        if (number == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        for (char c : number.toCharArray()) {
            if (c >= '0' && c <= '9') {
                result.append((char) ('\u2080' + (c - '0')));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Convert a string to superscript numbers.
     *
     * @param number The number string
     * @return The superscript version
     */
    public static String toSuperscript(String number) {
        if (number == null) {
            return "";
        }

        char[] superscripts = {
            '\u2070', '\u00b9', '\u00b2', '\u00b3', '\u2074', '\u2075', '\u2076', '\u2077',
            '\u2078', '\u2079'
        };
        StringBuilder result = new StringBuilder();
        for (char c : number.toCharArray()) {
            if (c >= '0' && c <= '9') {
                result.append(superscripts[c - '0']);
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
