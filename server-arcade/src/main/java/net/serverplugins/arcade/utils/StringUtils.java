package net.serverplugins.arcade.utils;

import java.util.Arrays;

/** Utility class for string operations, including base-62 encoding for IDs. */
public class StringUtils {

    // Base-62 characters: 0-9, a-z, A-Z (sorted for binary search)
    private static final char[] DIGITS;

    static {
        DIGITS =
                new char[] {
                    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F',
                    'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V',
                    'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l',
                    'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'
                };
        Arrays.sort(DIGITS);
    }

    /**
     * Converts a long to a base-62 encoded string. Useful for generating short, unique identifiers.
     *
     * @param value The long value to encode
     * @return Base-62 encoded string
     */
    public static String longToString(long value) {
        if (value == 0) {
            return String.valueOf(DIGITS[0]);
        }

        byte[] buf = new byte[65];
        int charPos = 64;
        int radix = DIGITS.length;

        // Handle negative by converting to positive work
        long num = value < 0 ? value : -value;
        while (num <= -radix) {
            buf[charPos--] = (byte) DIGITS[(int) (-(num % radix))];
            num /= radix;
        }
        buf[charPos] = (byte) DIGITS[(int) (-num)];

        return new String(buf, charPos, 65 - charPos);
    }

    /**
     * Converts a base-62 encoded string back to a long.
     *
     * @param s The base-62 encoded string
     * @return The decoded long value
     * @throws NumberFormatException if the string contains invalid characters
     */
    public static long stringToLong(String s) {
        if (s == null || s.isEmpty()) {
            throw new NumberFormatException("Input string is null or empty");
        }

        long result = 0;
        int radix = DIGITS.length;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int digit = Arrays.binarySearch(DIGITS, c);
            if (digit < 0) {
                throw new NumberFormatException("Invalid character in input: " + c);
            }
            result = result * radix + digit;
        }

        return result;
    }

    /**
     * Formats a double value ensuring trailing zero if needed.
     *
     * @param d The double to format
     * @return Formatted string with proper decimal places
     */
    public static String formatDouble(double d) {
        String result = String.valueOf(d);
        // Add trailing zero if second decimal place is zero
        if ((d * 100) % 10 == 0) {
            result += "0";
        }
        return result;
    }

    /**
     * Formats a money amount in a compact form.
     *
     * @param amount The amount to format
     * @return Formatted string (e.g., "1.5M", "500K", "100")
     */
    public static String formatMoney(double amount) {
        if (amount >= 1_000_000) {
            return String.format("%.1fM", amount / 1_000_000.0);
        } else if (amount >= 1_000) {
            return String.format("%.1fK", amount / 1_000.0);
        }
        return String.valueOf((int) amount);
    }
}
