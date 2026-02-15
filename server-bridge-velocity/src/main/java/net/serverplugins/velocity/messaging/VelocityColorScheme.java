package net.serverplugins.velocity.messaging;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Standardized color scheme for ServerBridge Velocity plugin.
 *
 * <p>This class mirrors the server-api ColorScheme but is adapted for Velocity. It provides
 * consistent color tags and icons matching the Paper plugins.
 */
public final class VelocityColorScheme {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private VelocityColorScheme() {
        throw new UnsupportedOperationException("VelocityColorScheme is a utility class");
    }

    // ========== COLOR TAGS ==========

    public static final String ERROR = "<red>";
    public static final String SUCCESS = "<green>";
    public static final String WARNING = "<yellow>";
    public static final String INFO = "<gray>";
    public static final String EMPHASIS = "<gold>";
    public static final String HIGHLIGHT = "<white>";
    public static final String SECONDARY = "<dark_gray>";
    public static final String COMMAND = "<aqua>";

    // ========== ICONS ==========

    public static final String CHECKMARK = "✓";
    public static final String CROSS = "✗";
    public static final String WARNING_ICON = "⚠";
    public static final String ARROW = "→";
    public static final String BULLET = "•";
    public static final String STAR = "★";

    // ========== UTILITY METHODS ==========

    /** Wraps text in the specified color tag. */
    public static String wrap(String text, String color) {
        if (text == null || text.isEmpty()) return "";
        if (color == null || color.isEmpty()) return text;

        String colorName = color.replace("<", "").replace(">", "");
        return color + text + "</" + colorName + ">";
    }

    /** Creates a colored Component from text and color tag. */
    public static Component colored(String text, String color) {
        return MINI_MESSAGE.deserialize(wrap(text, color));
    }

    /** Creates an error-colored Component. */
    public static Component error(String text) {
        return colored(text, ERROR);
    }

    /** Creates a success-colored Component. */
    public static Component success(String text) {
        return colored(text, SUCCESS);
    }

    /** Creates a warning-colored Component. */
    public static Component warning(String text) {
        return colored(text, WARNING);
    }

    /** Creates an info-colored Component. */
    public static Component info(String text) {
        return colored(text, INFO);
    }

    /** Creates an emphasis-colored Component. */
    public static Component emphasis(String text) {
        return colored(text, EMPHASIS);
    }

    /** Creates a highlight-colored Component. */
    public static Component highlight(String text) {
        return colored(text, HIGHLIGHT);
    }
}
