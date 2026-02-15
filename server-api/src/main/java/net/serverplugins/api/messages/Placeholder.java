package net.serverplugins.api.messages;

import java.text.DecimalFormat;
import java.util.Objects;

/**
 * Represents a key-value pair for placeholder replacement in messages.
 *
 * <p>Placeholders are used to insert dynamic values into message templates. For example: "You have
 * {balance} coins" with Placeholder.of("balance", 100) becomes "You have 100 coins".
 *
 * <h3>Usage Examples:</h3>
 *
 * <pre>{@code
 * // String placeholder
 * Placeholder.of("player", "Steve")
 *
 * // Integer placeholder
 * Placeholder.of("amount", 50)
 *
 * // Double placeholder (formatted)
 * Placeholder.of("balance", 1250.75)
 *
 * // Boolean placeholder
 * Placeholder.of("enabled", true)
 * }</pre>
 *
 * @since 1.0.0
 */
public class Placeholder {

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.00");
    private static final DecimalFormat INTEGER_FORMAT = new DecimalFormat("#,##0");

    private final String key;
    private final String value;

    /**
     * Private constructor. Use static factory methods instead.
     *
     * @param key The placeholder key (without braces)
     * @param value The replacement value
     */
    private Placeholder(String key, String value) {
        this.key = Objects.requireNonNull(key, "Placeholder key cannot be null");
        this.value = Objects.requireNonNull(value, "Placeholder value cannot be null");
    }

    /**
     * Creates a placeholder from a string value.
     *
     * @param key The placeholder key (e.g., "player")
     * @param value The string value
     * @return A new Placeholder instance
     */
    public static Placeholder of(String key, String value) {
        return new Placeholder(key, value == null ? "" : value);
    }

    /**
     * Creates a placeholder from an integer value.
     *
     * <p>The integer will be formatted with thousand separators.
     *
     * @param key The placeholder key (e.g., "amount")
     * @param value The integer value
     * @return A new Placeholder instance
     */
    public static Placeholder of(String key, int value) {
        return new Placeholder(key, INTEGER_FORMAT.format(value));
    }

    /**
     * Creates a placeholder from a long value.
     *
     * <p>The long will be formatted with thousand separators.
     *
     * @param key The placeholder key (e.g., "amount")
     * @param value The long value
     * @return A new Placeholder instance
     */
    public static Placeholder of(String key, long value) {
        return new Placeholder(key, INTEGER_FORMAT.format(value));
    }

    /**
     * Creates a placeholder from a double value.
     *
     * <p>The double will be formatted with 2 decimal places and thousand separators.
     *
     * @param key The placeholder key (e.g., "balance")
     * @param value The double value
     * @return A new Placeholder instance
     */
    public static Placeholder of(String key, double value) {
        return new Placeholder(key, DECIMAL_FORMAT.format(value));
    }

    /**
     * Creates a placeholder from a float value.
     *
     * <p>The float will be formatted with 2 decimal places and thousand separators.
     *
     * @param key The placeholder key (e.g., "percentage")
     * @param value The float value
     * @return A new Placeholder instance
     */
    public static Placeholder of(String key, float value) {
        return new Placeholder(key, DECIMAL_FORMAT.format(value));
    }

    /**
     * Creates a placeholder from a boolean value.
     *
     * @param key The placeholder key (e.g., "enabled")
     * @param value The boolean value
     * @return A new Placeholder instance
     */
    public static Placeholder of(String key, boolean value) {
        return new Placeholder(key, String.valueOf(value));
    }

    /**
     * Creates a placeholder from any object.
     *
     * <p>Uses the object's toString() method for conversion.
     *
     * @param key The placeholder key
     * @param value The object value
     * @return A new Placeholder instance
     */
    public static Placeholder of(String key, Object value) {
        return new Placeholder(key, value == null ? "" : value.toString());
    }

    /**
     * Gets the placeholder key (without braces).
     *
     * @return The placeholder key
     */
    public String getKey() {
        return key;
    }

    /**
     * Gets the placeholder value as a string.
     *
     * @return The placeholder value
     */
    public String getValue() {
        return value;
    }

    /**
     * Replaces this placeholder in the given text.
     *
     * <p>Supports both {key} and %key% formats.
     *
     * @param text The text containing placeholders
     * @return Text with this placeholder replaced
     */
    public String replace(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text.replace("{" + key + "}", value).replace("%" + key + "%", value);
    }

    /**
     * Applies multiple placeholders to the given text.
     *
     * @param text The text containing placeholders
     * @param placeholders The placeholders to apply
     * @return Text with all placeholders replaced
     */
    public static String replaceAll(String text, Placeholder... placeholders) {
        if (text == null || text.isEmpty() || placeholders == null) {
            return text;
        }

        String result = text;
        for (Placeholder placeholder : placeholders) {
            if (placeholder != null) {
                result = placeholder.replace(result);
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return "{" + key + "=" + value + "}";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Placeholder other)) return false;
        return key.equals(other.key) && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }
}
