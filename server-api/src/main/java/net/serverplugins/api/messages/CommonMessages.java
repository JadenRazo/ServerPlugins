package net.serverplugins.api.messages;

import net.kyori.adventure.text.Component;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Standard messages used across all ServerPlugins plugins.
 *
 * <p>This enum provides type-safe, IDE-autocomplete-friendly access to common messages that every
 * plugin needs. Using this enum ensures consistency across the server.
 *
 * <h3>Usage Examples:</h3>
 *
 * <pre>{@code
 * // Simple permission check
 * if (!player.hasPermission("plugin.use")) {
 *     CommonMessages.NO_PERMISSION.send(player);
 *     return;
 * }
 *
 * // Message with placeholder
 * CommonMessages.ON_COOLDOWN.send(player,
 *     Placeholder.of("time", "30 seconds")
 * );
 *
 * // Get as Component for further manipulation
 * Component message = CommonMessages.SUCCESS.toComponent(
 *     Placeholder.of("message", "Claim created!")
 * );
 * }</pre>
 *
 * @since 1.0.0
 */
public enum CommonMessages {

    // ========== PERMISSION & ACCESS ==========

    /** Displayed when a player lacks permission to perform an action */
    NO_PERMISSION(ColorScheme.ERROR + "You don't have permission to do that!"),

    /** Displayed when a command can only be executed from console */
    CONSOLE_ONLY(ColorScheme.ERROR + "This command can only be executed from console!"),

    /** Displayed when a command can only be used by players */
    PLAYERS_ONLY(ColorScheme.ERROR + "This command can only be used by players!"),

    // ========== PLAYER LOOKUP ==========

    /** Displayed when a player is not found or is offline */
    PLAYER_NOT_FOUND(ColorScheme.ERROR + "Player not found or is offline!"),

    /** Displayed when a specific player is not currently online */
    PLAYER_OFFLINE(ColorScheme.ERROR + "That player is not online!"),

    /** Displayed when a player tries to target themselves inappropriately */
    CANNOT_TARGET_SELF(ColorScheme.ERROR + "You cannot target yourself!"),

    // ========== COMMAND USAGE ==========

    /** Displayed for invalid command usage (requires {usage} placeholder) */
    INVALID_USAGE(ColorScheme.ERROR + "Invalid usage! " + ColorScheme.INFO + "{usage}"),

    /** Displayed for invalid number input (requires {input} placeholder) */
    INVALID_NUMBER(ColorScheme.ERROR + "Invalid number: " + ColorScheme.HIGHLIGHT + "{input}"),

    /** Displayed for invalid player name (requires {player} placeholder) */
    INVALID_PLAYER(ColorScheme.ERROR + "Invalid player: " + ColorScheme.HIGHLIGHT + "{player}"),

    /** Displayed for invalid arguments */
    INVALID_ARGUMENTS(ColorScheme.ERROR + "Invalid arguments!"),

    /** Displayed when too few arguments are provided */
    TOO_FEW_ARGUMENTS(ColorScheme.ERROR + "Too few arguments! " + ColorScheme.INFO + "{usage}"),

    /** Displayed when too many arguments are provided */
    TOO_MANY_ARGUMENTS(ColorScheme.ERROR + "Too many arguments! " + ColorScheme.INFO + "{usage}"),

    // ========== GENERAL FEEDBACK ==========

    /** Success message template (requires {message} placeholder) */
    SUCCESS(ColorScheme.SUCCESS + ColorScheme.CHECKMARK + " {message}"),

    /** Error message template (requires {message} placeholder) */
    ERROR(ColorScheme.ERROR + ColorScheme.CROSS + " {message}"),

    /** Warning message template (requires {message} placeholder) */
    WARNING(ColorScheme.WARNING + ColorScheme.WARNING_ICON + " {message}"),

    /** Info message template (requires {message} placeholder) */
    INFO(ColorScheme.INFO + ColorScheme.ARROW + " {message}"),

    // ========== COOLDOWNS & DELAYS ==========

    /** Displayed when an action is on cooldown (requires {time} placeholder) */
    ON_COOLDOWN(
            ColorScheme.ERROR
                    + "You must wait "
                    + ColorScheme.HIGHLIGHT
                    + "{time}"
                    + ColorScheme.ERROR
                    + " before using this again!"),

    /** Displayed while processing a request */
    PROCESSING(ColorScheme.WARNING + "Processing... please wait."),

    // ========== ECONOMY ==========

    /** Displayed when a player lacks sufficient funds (requires {amount} placeholder) */
    INSUFFICIENT_FUNDS(
            ColorScheme.ERROR
                    + "You don't have enough money! "
                    + ColorScheme.INFO
                    + "Need: "
                    + ColorScheme.EMPHASIS
                    + "${amount}"),

    /** Displayed on successful purchase (requires {amount} placeholder) */
    PURCHASE_SUCCESS(
            ColorScheme.SUCCESS
                    + ColorScheme.CHECKMARK
                    + " Purchased for "
                    + ColorScheme.EMPHASIS
                    + "${amount}"),

    /** Displayed when money is added (requires {amount} placeholder) */
    MONEY_ADDED(
            ColorScheme.SUCCESS
                    + ColorScheme.CHECKMARK
                    + " Added "
                    + ColorScheme.EMPHASIS
                    + "${amount}"
                    + ColorScheme.SUCCESS
                    + " to your balance!"),

    /** Displayed when money is removed (requires {amount} placeholder) */
    MONEY_REMOVED(
            ColorScheme.ERROR
                    + ColorScheme.CROSS
                    + " Removed "
                    + ColorScheme.EMPHASIS
                    + "${amount}"
                    + ColorScheme.ERROR
                    + " from your balance!"),

    // ========== WORLD/REGION RESTRICTIONS ==========

    /** Displayed when an action is not allowed in the current world */
    WORLD_DISABLED(ColorScheme.ERROR + "This action is not allowed in this world!"),

    /** Displayed when an action is not allowed in the current region */
    REGION_DISABLED(ColorScheme.ERROR + "You cannot do that in this region!"),

    // ========== DATABASE/DATA ==========

    /** Displayed when there's a database error */
    DATABASE_ERROR(
            ColorScheme.ERROR + "A database error occurred. Please contact an administrator."),

    /** Displayed when data is being loaded */
    DATA_LOADING(ColorScheme.WARNING + "Loading data... please wait."),

    /** Displayed when data fails to load */
    DATA_LOAD_FAILED(ColorScheme.ERROR + "Failed to load data. Please try again later."),

    // ========== GENERAL ERRORS ==========

    /** Generic error message */
    SOMETHING_WENT_WRONG(ColorScheme.ERROR + "Something went wrong! Please try again."),

    /** Displayed when a feature is not yet implemented */
    NOT_IMPLEMENTED(ColorScheme.WARNING + "This feature is not yet implemented."),

    /** Displayed when an action is cancelled */
    ACTION_CANCELLED(ColorScheme.WARNING + "Action cancelled.");

    private final String defaultMessage;

    /**
     * Constructs a CommonMessage with a default message template.
     *
     * @param defaultMessage The default message (may contain placeholders)
     */
    CommonMessages(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    /**
     * Gets the default message template.
     *
     * @return The default message string
     */
    public String getDefault() {
        return defaultMessage;
    }

    /**
     * Sends this message to a CommandSender.
     *
     * @param sender The recipient (player or console)
     * @param placeholders Optional placeholders to replace in the message
     */
    public void send(CommandSender sender, Placeholder... placeholders) {
        String message = Placeholder.replaceAll(defaultMessage, placeholders);
        TextUtil.send(sender, message);
    }

    /**
     * Sends this message to a Player.
     *
     * @param player The recipient player
     * @param placeholders Optional placeholders to replace in the message
     */
    public void send(Player player, Placeholder... placeholders) {
        String message = Placeholder.replaceAll(defaultMessage, placeholders);
        TextUtil.send(player, message);
    }

    /**
     * Converts this message to a Component with placeholders replaced.
     *
     * @param placeholders Optional placeholders to replace in the message
     * @return The formatted Component
     */
    public Component toComponent(Placeholder... placeholders) {
        String message = Placeholder.replaceAll(defaultMessage, placeholders);
        return TextUtil.parse(message);
    }

    /**
     * Formats this message as a string with placeholders replaced.
     *
     * @param placeholders Optional placeholders to replace in the message
     * @return The formatted string (with MiniMessage tags)
     */
    public String format(Placeholder... placeholders) {
        return Placeholder.replaceAll(defaultMessage, placeholders);
    }
}
