package net.serverplugins.api.messages;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Fluent API for building complex, multi-part messages.
 *
 * <p>MessageBuilder provides a clean, readable way to construct messages with consistent color
 * schemes, icons, and formatting.
 *
 * <h3>Usage Example:</h3>
 *
 * <pre>{@code
 * MessageBuilder.create()
 *     .prefix("<gradient:#ff6b6b:#feca57>[Claim]</gradient> ")
 *     .checkmark()
 *     .success("Claim created successfully!")
 *     .newLine()
 *     .arrow()
 *     .info("Size: ")
 *     .emphasis("16 chunks")
 *     .newLine()
 *     .arrow()
 *     .info("Cost: ")
 *     .emphasis("$500")
 *     .send(player);
 * }</pre>
 *
 * The above produces:
 *
 * <pre>
 * [Claim] ✓ Claim created successfully!
 * → Size: 16 chunks
 * → Cost: $500
 * </pre>
 *
 * @since 1.0.0
 */
public class MessageBuilder {

    private final List<String> parts;
    private String prefix;

    /** Private constructor. Use {@link #create()} instead. */
    private MessageBuilder() {
        this.parts = new ArrayList<>();
        this.prefix = "";
    }

    /**
     * Creates a new MessageBuilder instance.
     *
     * @return A new MessageBuilder
     */
    public static MessageBuilder create() {
        return new MessageBuilder();
    }

    // ========== PREFIX ==========

    /**
     * Sets the prefix for this message.
     *
     * @param prefix The prefix to prepend to the message
     * @return This builder for chaining
     */
    public MessageBuilder prefix(String prefix) {
        this.prefix = prefix != null ? prefix : "";
        return this;
    }

    // ========== COLOR METHODS ==========

    /**
     * Adds error-colored text (red).
     *
     * @param text The text to add
     * @return This builder for chaining
     */
    public MessageBuilder error(String text) {
        parts.add(ColorScheme.wrap(text, ColorScheme.ERROR));
        return this;
    }

    /**
     * Adds success-colored text (green).
     *
     * @param text The text to add
     * @return This builder for chaining
     */
    public MessageBuilder success(String text) {
        parts.add(ColorScheme.wrap(text, ColorScheme.SUCCESS));
        return this;
    }

    /**
     * Adds warning-colored text (yellow).
     *
     * @param text The text to add
     * @return This builder for chaining
     */
    public MessageBuilder warning(String text) {
        parts.add(ColorScheme.wrap(text, ColorScheme.WARNING));
        return this;
    }

    /**
     * Adds info-colored text (gray).
     *
     * @param text The text to add
     * @return This builder for chaining
     */
    public MessageBuilder info(String text) {
        parts.add(ColorScheme.wrap(text, ColorScheme.INFO));
        return this;
    }

    /**
     * Adds emphasis-colored text (gold).
     *
     * @param text The text to add
     * @return This builder for chaining
     */
    public MessageBuilder emphasis(String text) {
        parts.add(ColorScheme.wrap(text, ColorScheme.EMPHASIS));
        return this;
    }

    /**
     * Adds highlight-colored text (white).
     *
     * @param text The text to add
     * @return This builder for chaining
     */
    public MessageBuilder highlight(String text) {
        parts.add(ColorScheme.wrap(text, ColorScheme.HIGHLIGHT));
        return this;
    }

    /**
     * Adds secondary-colored text (dark gray).
     *
     * @param text The text to add
     * @return This builder for chaining
     */
    public MessageBuilder secondary(String text) {
        parts.add(ColorScheme.wrap(text, ColorScheme.SECONDARY));
        return this;
    }

    /**
     * Adds command-colored text (aqua).
     *
     * @param text The text to add
     * @return This builder for chaining
     */
    public MessageBuilder command(String text) {
        parts.add(ColorScheme.wrap(text, ColorScheme.COMMAND));
        return this;
    }

    /**
     * Adds custom-colored text.
     *
     * @param text The text to add
     * @param color The color tag (e.g., "&lt;red&gt;")
     * @return This builder for chaining
     */
    public MessageBuilder colored(String text, String color) {
        parts.add(ColorScheme.wrap(text, color));
        return this;
    }

    // ========== ICONS ==========

    /**
     * Adds a green checkmark (✓).
     *
     * @return This builder for chaining
     */
    public MessageBuilder checkmark() {
        parts.add(ColorScheme.wrap(ColorScheme.CHECKMARK, ColorScheme.SUCCESS));
        parts.add(" ");
        return this;
    }

    /**
     * Adds a red cross (✗).
     *
     * @return This builder for chaining
     */
    public MessageBuilder cross() {
        parts.add(ColorScheme.wrap(ColorScheme.CROSS, ColorScheme.ERROR));
        parts.add(" ");
        return this;
    }

    /**
     * Adds a yellow warning icon (⚠).
     *
     * @return This builder for chaining
     */
    public MessageBuilder warningIcon() {
        parts.add(ColorScheme.wrap(ColorScheme.WARNING_ICON, ColorScheme.WARNING));
        parts.add(" ");
        return this;
    }

    /**
     * Adds a gray arrow (→).
     *
     * @return This builder for chaining
     */
    public MessageBuilder arrow() {
        parts.add(ColorScheme.wrap(ColorScheme.ARROW, ColorScheme.INFO));
        parts.add(" ");
        return this;
    }

    /**
     * Adds a gray bullet point (•).
     *
     * @return This builder for chaining
     */
    public MessageBuilder bullet() {
        parts.add(ColorScheme.wrap(ColorScheme.BULLET, ColorScheme.INFO));
        parts.add(" ");
        return this;
    }

    /**
     * Adds a gold star (★).
     *
     * @return This builder for chaining
     */
    public MessageBuilder star() {
        parts.add(ColorScheme.wrap(ColorScheme.STAR, ColorScheme.EMPHASIS));
        parts.add(" ");
        return this;
    }

    // ========== TEXT MANIPULATION ==========

    /**
     * Adds a newline to the message.
     *
     * @return This builder for chaining
     */
    public MessageBuilder newLine() {
        parts.add("\n");
        return this;
    }

    /**
     * Adds a space to the message.
     *
     * @return This builder for chaining
     */
    public MessageBuilder space() {
        parts.add(" ");
        return this;
    }

    /**
     * Adds plain text without any color formatting.
     *
     * @param text The text to add
     * @return This builder for chaining
     */
    public MessageBuilder text(String text) {
        parts.add(text);
        return this;
    }

    /**
     * Adds a formatted text segment (preserves existing MiniMessage tags).
     *
     * @param formattedText Text with MiniMessage formatting
     * @return This builder for chaining
     */
    public MessageBuilder append(String formattedText) {
        parts.add(formattedText);
        return this;
    }

    // ========== BUILD METHODS ==========

    /**
     * Builds the message as a formatted string.
     *
     * @return The complete message string with prefix
     */
    public String build() {
        StringBuilder builder = new StringBuilder();
        builder.append(prefix);
        for (String part : parts) {
            builder.append(part);
        }
        return builder.toString();
    }

    /**
     * Builds the message as a Component.
     *
     * @return The complete message as a Component
     */
    public Component buildComponent() {
        return TextUtil.parse(build());
    }

    /**
     * Builds and sends the message to a CommandSender.
     *
     * @param sender The recipient
     */
    public void send(CommandSender sender) {
        TextUtil.send(sender, build());
    }

    /**
     * Builds and sends the message to a Player.
     *
     * @param player The recipient player
     */
    public void send(Player player) {
        TextUtil.send(player, build());
    }

    /**
     * Resets the builder, clearing all parts and prefix. Allows reuse of the same builder instance.
     *
     * @return This builder for chaining
     */
    public MessageBuilder reset() {
        parts.clear();
        prefix = "";
        return this;
    }

    /**
     * Gets the current number of parts in the message.
     *
     * @return The part count
     */
    public int size() {
        return parts.size();
    }

    /**
     * Checks if the message is empty.
     *
     * @return true if no parts have been added
     */
    public boolean isEmpty() {
        return parts.isEmpty();
    }

    @Override
    public String toString() {
        return build();
    }
}
