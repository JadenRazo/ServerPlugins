package net.serverplugins.keys.models;

public enum KeyType {
    CRATE,
    DUNGEON;

    public static KeyType fromString(String str) {
        if (str == null) return null;
        return switch (str.toLowerCase()) {
            case "crate", "c" -> CRATE;
            case "dungeon", "d" -> DUNGEON;
            default -> null;
        };
    }

    public String getDisplayName() {
        return switch (this) {
            case CRATE -> "Crate";
            case DUNGEON -> "Dungeon";
        };
    }
}
