package net.serverplugins.api.messages;

/**
 * Enum representing the type of message format. LEGACY uses BungeeCord chat API (Spigot servers).
 * MODERN uses Adventure/MiniMessage API (Paper servers). EMPTY represents a null/empty message.
 */
public enum MessageType {
    LEGACY,
    MODERN,
    EMPTY;

    public boolean isLegacy() {
        return this == LEGACY;
    }

    public boolean isModern() {
        return this == MODERN;
    }

    public boolean isEmpty() {
        return this == EMPTY;
    }
}
