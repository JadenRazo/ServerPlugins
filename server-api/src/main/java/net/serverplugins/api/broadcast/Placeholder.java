package net.serverplugins.api.broadcast;

/** Represents a custom placeholder for broadcast messages. Use the curly brace format: {key} */
public record Placeholder(String key, String value) {

    /**
     * Create a placeholder.
     *
     * @param key The placeholder key (without braces)
     * @param value The replacement value
     * @return A new Placeholder
     */
    public static Placeholder of(String key, String value) {
        return new Placeholder(key, value);
    }

    /** Apply this placeholder to text. Replaces {key} with value. */
    public String apply(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.replace("{" + key + "}", value);
    }
}
