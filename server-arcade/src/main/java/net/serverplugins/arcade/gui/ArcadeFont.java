package net.serverplugins.arcade.gui;

import net.serverplugins.api.ui.ResourcePackIcons;

/**
 * Custom font characters from the resource pack for arcade GUIs Unicode mappings from
 * assets/minecraft/font/default.json
 *
 * <p>Note: Arcade menus use 11 negative spaces vs ResourcePackIcons' 8 for proper alignment.
 * Adjusted from 10 to 11 to fix left-alignment issue with UI elements.
 */
public class ArcadeFont {

    // Full screen GUIs (256px backgrounds) - Aliased to ResourcePackIcons for consistency
    public static final String BLACKJACK_MENU = ResourcePackIcons.MenuTitles.BLACKJACK_MENU;
    public static final String BLACKJACK_BET = ResourcePackIcons.MenuTitles.BLACKJACK_BET;
    public static final String BETS_SCREEN = ResourcePackIcons.MenuTitles.BETS;
    public static final String SLOTS_SCREEN = ResourcePackIcons.MenuTitles.SLOTS;
    public static final String CRASH_WAITING = ResourcePackIcons.MenuTitles.CRASH_WAITING;
    public static final String CRASH_CRASHED = ResourcePackIcons.MenuTitles.CRASH_CRASHED;
    public static final String CRASH_STARTED = ResourcePackIcons.MenuTitles.CRASH_STARTED;
    public static final String CRASH_REMOVE = ResourcePackIcons.MenuTitles.CRASH_REMOVE;
    public static final String ROULETTE_SCREEN = ResourcePackIcons.MenuTitles.ROULETTE;
    public static final String ROULETTE_SCREEN_2 = ResourcePackIcons.MenuTitles.ROULETTE_2;
    public static final String LOTTERY_BETS = ResourcePackIcons.MenuTitles.LOTTERY_BETS;
    public static final String LOTTERY_START = ResourcePackIcons.MenuTitles.LOTTERY_START;
    public static final String LOTTERY_REMOVE = ResourcePackIcons.MenuTitles.LOTTERY_REMOVE;

    // Game titles (large 60px) - chars array format ["main","alt"]
    public static final String TITLE_BLACKJACK = ""; // item/casino/titles/blackjack.png
    public static final String TITLE_BLACKJACK_ALT = ""; // item/casino/titles/blackjack.png (alt)
    public static final String TITLE_LOTTERY = ""; // item/casino/titles/lottery.png
    public static final String TITLE_LOTTERY_ALT = ""; // item/casino/titles/lottery.png (alt)
    public static final String TITLE_SLOTS = ""; // item/casino/titles/slots.png
    public static final String TITLE_SLOTS_ALT = ""; // item/casino/titles/slots.png (alt)
    public static final String TITLE_CRASH = ""; // item/casino/titles/crash.png
    public static final String TITLE_CRASH_ALT = ""; // item/casino/titles/crash.png (alt)

    // Action buttons (small 13px icons)
    public static final String BTN_BET = ""; // item/casino/titles/bet.png
    public static final String BTN_CUSTOM_AMOUNT = ""; // item/casino/titles/custom_amount.png
    public static final String BTN_HIT = ""; // item/casino/titles/hit.png
    public static final String BTN_JOIN_CRASH = ""; // item/casino/titles/join_crash.png
    public static final String BTN_JOIN_LOTTERY = ""; // item/casino/titles/join_lottery.png
    public static final String BTN_REMOVE_BET = ""; // item/casino/titles/remove_bet.png
    public static final String BTN_SPIN = ""; // item/casino/titles/spin.png
    public static final String BTN_STAND = ""; // item/casino/titles/stand.png
    public static final String BTN_STOP = ""; // item/casino/titles/stop.png
    public static final String BTN_STOP_RED = ""; // item/casino/titles/stop_red.png

    /** Negative space character for positioning menu backgrounds (same as resource pack) */
    public static final String NEGATIVE_SPACE = "\u2ED4"; // ⻔

    /**
     * Create a GUI title with the font overlay (full-screen casino menu - hides inventory)
     *
     * @param fontChar The font character to display
     * @param fallbackText Text to show if resource pack isn't loaded (ignored, kept for
     *     compatibility)
     * @return Formatted title string with inventory hiding trigger
     */
    public static String createTitle(String fontChar, String fallbackText) {
        return createFullscreenTitle(fontChar, 11);
    }

    /**
     * Create a simple title with just the font character (default 11 negative spaces for arcade -
     * hides inventory) Note: Increased from 10 to 11 to fix left-alignment issue. Adjust if UI
     * appears off-center.
     *
     * @param fontChar The font character to display
     * @return Formatted title string with inventory hiding trigger
     */
    public static String createTitle(String fontChar) {
        return createFullscreenTitle(fontChar, 11);
    }

    /**
     * Create a full-screen casino menu title with custom negative space count Adds inventory hiding
     * trigger at the end
     *
     * @param fontChar The font character to display
     * @param negativeSpaceCount Number of negative space characters (arcade uses 11, range: 10-13)
     * @return Formatted title string with inventory hiding trigger
     */
    public static String createFullscreenTitle(String fontChar, int negativeSpaceCount) {
        StringBuilder sb = new StringBuilder("&f");
        for (int i = 0; i < negativeSpaceCount; i++) {
            sb.append(NEGATIVE_SPACE); // Font positioning (11 spaces for arcade)
        }
        sb.append(fontChar);
        sb.append("\uF000"); // Inventory hiding trigger (must match ServerAPI config)
        return sb.toString();
    }
}
