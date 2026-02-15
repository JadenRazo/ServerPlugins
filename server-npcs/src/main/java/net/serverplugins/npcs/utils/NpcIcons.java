package net.serverplugins.npcs.utils;

public class NpcIcons {

    // NPC Portrait Icons (90px height, ascent 25, mapped in resource pack)
    public static final String SYLVIA = "\uE394"; //  - item/ui/npcs/sylvia.png
    public static final String AUGUST = "\uE639"; //  - item/ui/npcs/august.png
    public static final String CASSIAN = "\uE63A"; //  - item/ui/npcs/cassian.png
    public static final String GANDALF = "\uE63B"; //  - item/ui/npcs/gandalf.png
    public static final String MARCUS = "\uE63C"; //  - item/ui/npcs/marcus.png
    public static final String MARK = "\uE63D"; //  - item/ui/npcs/mark.png
    public static final String NOAH = "\uE63E"; //  - item/ui/npcs/noah.png
    public static final String PETRA = "\uE63F"; //  - item/ui/npcs/petra.png
    public static final String THYRA = "\uE640"; //  - item/ui/npcs/thyra.png

    // Core UI Elements
    public static final String SPACE_CUSTOM = "\u2ED4"; // ⻔ - item/ui/space.png
    public static final String WELCOME_1 = "\uC111"; // 섑 - item/ui/welcome_title.png
    public static final String WELCOME_2 = "\uC112"; // 섒 - item/ui/welcome_title.png
    public static final String WELCOME_3 = "\uC001"; // 쀁 - item/ui/welcome_title.png
    public static final String COIN = "\uE001"; // - item/ui/coin.png
    public static final String BOSSBAR = "お"; // - item/ui/bossbar/bossbar.png

    // Negative Spaces for Icon Positioning
    public static final String SPACE_MINUS_1 = "\uF801";
    public static final String SPACE_MINUS_2 = "\uF802";
    public static final String SPACE_MINUS_4 = "\uF803";
    public static final String SPACE_MINUS_8 = "\uF804";
    public static final String SPACE_MINUS_16 = "\uF805";
    public static final String SPACE_MINUS_32 = "\uF806";
    public static final String SPACE_MINUS_64 = "\uF807";
    public static final String SPACE_MINUS_128 = "\uF808";

    // Positive Spaces
    public static final String SPACE_PLUS_1 = "\uF821";
    public static final String SPACE_PLUS_2 = "\uF822";
    public static final String SPACE_PLUS_4 = "\uF823";
    public static final String SPACE_PLUS_8 = "\uF824";

    // Common Decorative Characters
    public static final String SEPARATOR = "━";
    public static final String DOT = "•";
    public static final String ARROW_RIGHT = "→";
    public static final String ARROW_LEFT = "←";
    public static final String STAR = "★";

    /**
     * Get NPC icon by name
     *
     * @param npcName The NPC name (case-insensitive)
     * @return The Unicode icon character, or empty string if not found
     */
    public static String getIcon(String npcName) {
        if (npcName == null) return "";

        switch (npcName.toLowerCase()) {
            case "sylvia":
                return SYLVIA;
            case "august":
                return AUGUST;
            case "cassian":
                return CASSIAN;
            case "gandalf":
                return GANDALF;
            case "marcus":
                return MARCUS;
            case "mark":
                return MARK;
            case "noah":
                return NOAH;
            case "petra":
                return PETRA;
            case "thyra":
                return THYRA;
            default:
                return "";
        }
    }

    /**
     * Create an icon with adjusted spacing
     *
     * @param icon The icon character
     * @param spacing Negative space adjustment
     * @return Icon with spacing applied
     */
    public static String withSpacing(String icon, String spacing) {
        return icon + spacing;
    }

    /**
     * Create a formatted header with icon
     *
     * @param icon The NPC icon
     * @param name The NPC display name
     * @param color The color code (e.g., "&d")
     * @return Formatted header string
     */
    public static String createHeader(String icon, String name, String color) {
        return icon + SPACE_MINUS_8 + color + "&l" + name.toUpperCase();
    }

    /**
     * Create a dialog line with icon prefix
     *
     * @param icon The NPC icon
     * @param message The message to display
     * @return Formatted line with icon
     */
    public static String createDialogLine(String icon, String message) {
        return "    " + icon + " " + message;
    }

    /**
     * Create a separator line with icons
     *
     * @param icon The NPC icon
     * @param color The color code
     * @param length Number of separator characters
     * @return Formatted separator
     */
    public static String createSeparator(String icon, String color, int length) {
        StringBuilder separator = new StringBuilder("    ");
        separator.append(icon).append(" ");
        separator.append(color).append("&l");
        for (int i = 0; i < length; i++) {
            separator.append(SEPARATOR);
        }
        return separator.toString();
    }
}
