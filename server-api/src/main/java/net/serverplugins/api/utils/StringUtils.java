package net.serverplugins.api.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

public final class StringUtils {

    private StringUtils() {}

    private static final String[] ROMAN_THOUSANDS = {"", "M", "MM", "MMM"};
    private static final String[] ROMAN_HUNDREDS = {
        "", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM"
    };
    private static final String[] ROMAN_TENS = {
        "", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"
    };
    private static final String[] ROMAN_ONES = {
        "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"
    };

    /**
     * Converts an InputStream to a String.
     *
     * @param inputStream The input stream to convert
     * @return The string content of the stream
     */
    @Nonnull
    public static String streamToString(@Nonnull InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Formats an enum-style name (ENUM_NAME) to a readable format (Enum Name).
     *
     * @param name The enum name to format
     * @param capitalizeAll If true, capitalizes all words; if false, only first word
     * @return The formatted name
     */
    @Nonnull
    public static String formatEnumName(@Nonnull String name, boolean capitalizeAll) {
        return formatEnumName(name, "_", capitalizeAll);
    }

    /**
     * Formats an enum-style name with a custom separator to a readable format.
     *
     * @param name The name to format
     * @param separator The separator to split on (e.g., "_", "-")
     * @param capitalizeAll If true, capitalizes all words; if false, only first word
     * @return The formatted name
     */
    @Nonnull
    public static String formatEnumName(
            @Nonnull String name, @Nonnull String separator, boolean capitalizeAll) {
        if (name.isEmpty()) return name;

        String[] parts = name.toLowerCase().split(separator);
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;

            if (i > 0) result.append(" ");

            if (capitalizeAll || i == 0) {
                result.append(Character.toUpperCase(parts[i].charAt(0)));
                if (parts[i].length() > 1) {
                    result.append(parts[i].substring(1));
                }
            } else {
                result.append(parts[i]);
            }
        }

        return result.toString();
    }

    /**
     * Formats seconds into a human-readable time string. Examples: 90 -> "1m 30s", 3661 -> "1h 1m
     * 1s", 86400 -> "1d"
     *
     * @param seconds The number of seconds
     * @return A formatted time string
     */
    @Nonnull
    public static String formatTime(int seconds) {
        if (seconds < 0) seconds = 0;

        int days = seconds / 86400;
        int hours = (seconds % 86400) / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;

        StringBuilder result = new StringBuilder();

        if (days > 0) {
            result.append(days).append("d ");
        }
        if (hours > 0) {
            result.append(hours).append("h ");
        }
        if (minutes > 0) {
            result.append(minutes).append("m ");
        }
        if (secs > 0 || result.length() == 0) {
            result.append(secs).append("s");
        }

        return result.toString().trim();
    }

    /**
     * Formats milliseconds into a human-readable time string.
     *
     * @param millis The number of milliseconds
     * @return A formatted time string
     */
    @Nonnull
    public static String formatTimeMillis(long millis) {
        return formatTime((int) (millis / 1000));
    }

    /**
     * Converts an integer to a Roman numeral string. Supports values from 1 to 3999.
     *
     * @param number The number to convert (1-3999)
     * @return The Roman numeral representation, or the number as a string if out of range
     */
    @Nonnull
    public static String romanNumeral(int number) {
        if (number < 1 || number > 3999) {
            return String.valueOf(number);
        }

        return ROMAN_THOUSANDS[number / 1000]
                + ROMAN_HUNDREDS[(number % 1000) / 100]
                + ROMAN_TENS[(number % 100) / 10]
                + ROMAN_ONES[number % 10];
    }

    /**
     * Pads a string to the left with a specified character.
     *
     * @param str The string to pad
     * @param length The desired length
     * @param padChar The character to pad with
     * @return The padded string
     */
    @Nonnull
    public static String padLeft(@Nonnull String str, int length, char padChar) {
        if (str.length() >= length) return str;
        StringBuilder sb = new StringBuilder();
        for (int i = str.length(); i < length; i++) {
            sb.append(padChar);
        }
        sb.append(str);
        return sb.toString();
    }

    /**
     * Pads a string to the right with a specified character.
     *
     * @param str The string to pad
     * @param length The desired length
     * @param padChar The character to pad with
     * @return The padded string
     */
    @Nonnull
    public static String padRight(@Nonnull String str, int length, char padChar) {
        if (str.length() >= length) return str;
        StringBuilder sb = new StringBuilder(str);
        for (int i = str.length(); i < length; i++) {
            sb.append(padChar);
        }
        return sb.toString();
    }

    /**
     * Truncates a string to the specified length, adding ellipsis if truncated.
     *
     * @param str The string to truncate
     * @param maxLength The maximum length (including ellipsis)
     * @return The truncated string
     */
    @Nonnull
    public static String truncate(@Nonnull String str, int maxLength) {
        if (str.length() <= maxLength) return str;
        if (maxLength <= 3) return str.substring(0, maxLength);
        return str.substring(0, maxLength - 3) + "...";
    }
}
