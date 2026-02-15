package net.serverplugins.adminvelocity.messaging;

import java.text.DecimalFormat;
import java.util.Objects;

/**
 * Represents a key-value pair for placeholder replacement in messages.
 *
 * <p>This class mirrors the server-api Placeholder but is adapted for Velocity.
 */
public class VelocityPlaceholder {

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.00");
    private static final DecimalFormat INTEGER_FORMAT = new DecimalFormat("#,##0");

    private final String key;
    private final String value;

    private VelocityPlaceholder(String key, String value) {
        this.key = Objects.requireNonNull(key, "Placeholder key cannot be null");
        this.value = Objects.requireNonNull(value, "Placeholder value cannot be null");
    }

    public static VelocityPlaceholder of(String key, String value) {
        return new VelocityPlaceholder(key, value == null ? "" : value);
    }

    public static VelocityPlaceholder of(String key, int value) {
        return new VelocityPlaceholder(key, INTEGER_FORMAT.format(value));
    }

    public static VelocityPlaceholder of(String key, long value) {
        return new VelocityPlaceholder(key, INTEGER_FORMAT.format(value));
    }

    public static VelocityPlaceholder of(String key, double value) {
        return new VelocityPlaceholder(key, DECIMAL_FORMAT.format(value));
    }

    public static VelocityPlaceholder of(String key, float value) {
        return new VelocityPlaceholder(key, DECIMAL_FORMAT.format(value));
    }

    public static VelocityPlaceholder of(String key, boolean value) {
        return new VelocityPlaceholder(key, String.valueOf(value));
    }

    public static VelocityPlaceholder of(String key, Object value) {
        return new VelocityPlaceholder(key, value == null ? "" : value.toString());
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    /** Replaces this placeholder in the given text. Supports both {key} and %key% formats. */
    public String replace(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text.replace("{" + key + "}", value).replace("%" + key + "%", value);
    }

    /** Applies multiple placeholders to the given text. */
    public static String replaceAll(String text, VelocityPlaceholder... placeholders) {
        if (text == null || text.isEmpty() || placeholders == null) {
            return text;
        }

        String result = text;
        for (VelocityPlaceholder placeholder : placeholders) {
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
        if (!(obj instanceof VelocityPlaceholder other)) return false;
        return key.equals(other.key) && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }
}
