package net.serverplugins.api.messages;

import net.kyori.adventure.text.Component;
import net.serverplugins.api.utils.TextUtil;

/**
 * Standardized color scheme for all ServerPlugins plugins.
 *
 * <p>This class provides consistent color tags and icons across the entire plugin suite. All
 * plugins should use these constants rather than hardcoded color codes.
 *
 * <h3>Color Usage Guidelines:</h3>
 *
 * <ul>
 *   <li>{@link #ERROR} - Errors, denials, failures
 *   <li>{@link #SUCCESS} - Success messages, confirmations, completion
 *   <li>{@link #WARNING} - Warnings, notices, important information
 *   <li>{@link #INFO} - Secondary info, descriptions, hints
 *   <li>{@link #EMPHASIS} - Emphasis, highlights, numerical values
 *   <li>{@link #HIGHLIGHT} - Primary info, player names, data
 *   <li>{@link #SECONDARY} - Tertiary info, subtle hints
 *   <li>{@link #COMMAND} - Commands, clickable text
 * </ul>
 *
 * @since 1.0.0
 */
public final class ColorScheme {

    // Prevent instantiation
    private ColorScheme() {
        throw new UnsupportedOperationException("ColorScheme is a utility class");
    }

    // ========== COLOR TAGS ==========

    /** Error color - Used for errors, denials, and failures */
    public static final String ERROR = "<red>";

    /** Success color - Used for success messages, confirmations */
    public static final String SUCCESS = "<green>";

    /** Warning color - Used for warnings, notices, important info */
    public static final String WARNING = "<yellow>";

    /** Info color - Used for secondary information, descriptions */
    public static final String INFO = "<gray>";

    /** Emphasis color - Used for highlights, numerical values */
    public static final String EMPHASIS = "<gold>";

    /** Highlight color - Used for primary info, player names, data */
    public static final String HIGHLIGHT = "<white>";

    /** Secondary color - Used for tertiary info, subtle hints */
    public static final String SECONDARY = "<dark_gray>";

    /** Command color - Used for commands, clickable text */
    public static final String COMMAND = "<aqua>";

    // ========== ICONS ==========

    /** Checkmark icon - ✓ */
    public static final String CHECKMARK = "✓";

    /** Cross/X icon - ✗ */
    public static final String CROSS = "✗";

    /** Warning icon - ⚠ */
    public static final String WARNING_ICON = "⚠";

    /** Arrow icon - → */
    public static final String ARROW = "→";

    /** Bullet point - • */
    public static final String BULLET = "•";

    /** Star icon - ★ */
    public static final String STAR = "★";

    // ========== UTILITY METHODS ==========

    /**
     * Wraps text in the specified color tag.
     *
     * @param text The text to wrap
     * @param color The color tag (e.g., "&lt;red&gt;")
     * @return Formatted string with color tags
     */
    public static String wrap(String text, String color) {
        if (text == null || text.isEmpty()) return "";
        if (color == null || color.isEmpty()) return text;

        // Build closing tags in reverse order for proper nesting
        // e.g., "<red><bold>" -> closing is "</bold></red>"
        StringBuilder closing = new StringBuilder();
        int i = color.length() - 1;
        while (i >= 0) {
            if (color.charAt(i) == '>') {
                int start = color.lastIndexOf('<', i);
                if (start >= 0) {
                    String tag = color.substring(start + 1, i);
                    closing.append("</").append(tag).append('>');
                    i = start - 1;
                } else {
                    i--;
                }
            } else {
                i--;
            }
        }
        return color + text + closing;
    }

    /**
     * Creates a colored Component from text and color tag.
     *
     * @param text The text to color
     * @param color The color tag (e.g., "&lt;red&gt;")
     * @return Colored Component
     */
    public static Component colored(String text, String color) {
        return TextUtil.parse(wrap(text, color));
    }

    /**
     * Creates an error-colored Component.
     *
     * @param text The text to color
     * @return Red-colored Component
     */
    public static Component error(String text) {
        return colored(text, ERROR);
    }

    /**
     * Creates a success-colored Component.
     *
     * @param text The text to color
     * @return Green-colored Component
     */
    public static Component success(String text) {
        return colored(text, SUCCESS);
    }

    /**
     * Creates a warning-colored Component.
     *
     * @param text The text to color
     * @return Yellow-colored Component
     */
    public static Component warning(String text) {
        return colored(text, WARNING);
    }

    /**
     * Creates an info-colored Component.
     *
     * @param text The text to color
     * @return Gray-colored Component
     */
    public static Component info(String text) {
        return colored(text, INFO);
    }

    /**
     * Creates an emphasis-colored Component.
     *
     * @param text The text to color
     * @return Gold-colored Component
     */
    public static Component emphasis(String text) {
        return colored(text, EMPHASIS);
    }

    /**
     * Creates a highlight-colored Component.
     *
     * @param text The text to color
     * @return White-colored Component
     */
    public static Component highlight(String text) {
        return colored(text, HIGHLIGHT);
    }
}
