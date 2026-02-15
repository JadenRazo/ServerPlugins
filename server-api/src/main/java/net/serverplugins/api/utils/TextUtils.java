package net.serverplugins.api.utils;

import org.jetbrains.annotations.NotNull;

/** Utility class for text formatting operations. */
public class TextUtils {

    private static final String[] ROMAN_NUMERALS = {
        "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"
    };
    private static final int[] ROMAN_VALUES = {
        1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1
    };

    /**
     * Formats an enum name to a readable string. Example: SOME_ENUM_NAME -> SomeEnumName or
     * someEnumName
     *
     * @param enumName The enum name to format
     * @param capitalize Whether to capitalize the first letter
     * @return The formatted string
     */
    @NotNull
    public static String formatEnumName(@NotNull String enumName, boolean capitalize) {
        return formatEnumName(enumName, "", capitalize);
    }

    /**
     * Formats an enum name to a readable string with a custom word divider. Example: SOME_ENUM_NAME
     * with " " divider -> Some Enum Name
     *
     * @param enumName The enum name to format
     * @param wordDivider String to insert between words (e.g., " " or "-")
     * @param capitalize Whether to capitalize the first letter
     * @return The formatted string
     */
    @NotNull
    public static String formatEnumName(
            @NotNull String enumName, String wordDivider, boolean capitalize) {
        char[] name = enumName.toLowerCase().toCharArray();
        StringBuilder result = new StringBuilder();
        result.append(capitalize ? Character.toUpperCase(name[0]) : name[0]);

        for (int i = 1; i < name.length; i++) {
            if (name[i] == '_') {
                result.append(wordDivider);
                if (i + 1 < name.length) {
                    result.append(Character.toUpperCase(name[++i]));
                }
            } else {
                result.append(name[i]);
            }
        }
        return result.toString();
    }

    /**
     * Formats seconds into MM:SS format.
     *
     * @param seconds The number of seconds
     * @return Formatted time string (e.g., "05:30")
     */
    @NotNull
    public static String formatTime(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return (minutes < 10 ? "0" : "") + minutes + ":" + (secs < 10 ? "0" : "") + secs;
    }

    /**
     * Converts a number to its Roman numeral representation.
     *
     * @param number The number to convert (must be > 0)
     * @return The Roman numeral string
     * @throws IllegalArgumentException if number is <= 0
     */
    @NotNull
    public static String romanNumeral(int number) {
        if (number <= 0) {
            throw new IllegalArgumentException("Number must be greater than 0");
        }

        StringBuilder roman = new StringBuilder();
        for (int i = 0; i < ROMAN_VALUES.length; i++) {
            while (number >= ROMAN_VALUES[i]) {
                roman.append(ROMAN_NUMERALS[i]);
                number -= ROMAN_VALUES[i];
            }
        }
        return roman.toString();
    }

    /**
     * Formats a money amount in a compact form.
     *
     * @param amount The amount to format
     * @return Formatted string (e.g., "1.5M", "500K", "100")
     */
    @NotNull
    public static String formatMoney(double amount) {
        if (amount >= 1_000_000) {
            return String.format("%.1fM", amount / 1_000_000.0);
        } else if (amount >= 1_000) {
            return String.format("%.1fK", amount / 1_000.0);
        }
        return String.valueOf((int) amount);
    }
}
