package net.serverplugins.api.messages;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.serverplugins.api.ServerType;
import net.serverplugins.api.handlers.PlaceholderHandler;
import net.serverplugins.api.messages.types.EmptyMessage;
import net.serverplugins.api.messages.types.LegacyMessage;
import net.serverplugins.api.messages.types.ModernMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Interface representing a formatted message that can be sent to players. Automatically handles
 * both legacy (Spigot) and modern (Paper) message formats.
 */
public interface Message {

    /**
     * Create a Message from text, auto-detecting server type.
     *
     * @param message The message text
     * @return A Message instance appropriate for the server type
     */
    static Message fromText(@Nullable String message) {
        return fromText(message, true);
    }

    /**
     * Create a Message from text with placeholder control.
     *
     * @param message The message text
     * @param hasPlaceholders Whether to process PAPI placeholders
     * @return A Message instance appropriate for the server type
     */
    static Message fromText(@Nullable String message, boolean hasPlaceholders) {
        if (message == null || message.isEmpty()) {
            return EmptyMessage.getInstance();
        }

        boolean checkPlaceholders =
                hasPlaceholders && PlaceholderHandler.containsPlaceholders(message);

        return switch (ServerType.detect()) {
            case SPIGOT -> new LegacyMessage(message, checkPlaceholders);
            case PAPER -> new ModernMessage(message, checkPlaceholders);
        };
    }

    /**
     * Create a legacy-format message regardless of server type.
     *
     * @param message The message text
     * @return A LegacyMessage instance
     */
    static Message legacy(@Nullable String message) {
        if (message == null || message.isEmpty()) {
            return EmptyMessage.getInstance();
        }
        return new LegacyMessage(message, PlaceholderHandler.containsPlaceholders(message));
    }

    /**
     * Create a modern-format message regardless of server type. Note: This will only work properly
     * on Paper servers.
     *
     * @param message The message text
     * @return A ModernMessage instance
     */
    static Message modern(@Nullable String message) {
        if (message == null || message.isEmpty()) {
            return EmptyMessage.getInstance();
        }
        return new ModernMessage(message, PlaceholderHandler.containsPlaceholders(message));
    }

    /**
     * Get an empty message singleton.
     *
     * @return The empty message instance
     */
    static Message empty() {
        return EmptyMessage.getInstance();
    }

    /**
     * Get the type of this message.
     *
     * @return The MessageType
     */
    MessageType getType();

    /**
     * Send this message to a command sender.
     *
     * @param receiver The receiver (player or console)
     */
    void sendMessage(@Nonnull CommandSender receiver);

    /**
     * Send this message as an action bar to a player.
     *
     * @param player The player to send to
     */
    void sendActionbar(@Nonnull Player player);

    /**
     * Create an inventory with this message as the title.
     *
     * @param owner The inventory owner
     * @param size The inventory size (must be multiple of 9)
     * @return The created inventory
     */
    Inventory createInventory(InventoryHolder owner, int size);

    /**
     * Create an inventory with this message as the title.
     *
     * @param owner The inventory owner
     * @param type The inventory type
     * @return The created inventory
     */
    Inventory createInventory(InventoryHolder owner, InventoryType type);

    /**
     * Replace text in this message.
     *
     * @param oldText The text to find
     * @param newText The replacement text
     * @return A new Message with the replacement made
     */
    Message replaceText(String oldText, String newText);

    /**
     * Concatenate text to this message.
     *
     * @param text The text to add
     * @param atEnd Whether to add at the end (true) or beginning (false)
     * @return A new Message with the text added
     */
    Message concat(String text, boolean atEnd);

    /**
     * Concatenate another message to this one.
     *
     * @param message The message to concatenate
     * @return A new Message combining both
     */
    Message concat(Message message);

    /**
     * Clone this message.
     *
     * @return A copy of this message
     */
    Message clone();

    /**
     * Get the raw string representation.
     *
     * @return The raw message string
     */
    @Override
    String toString();

    /**
     * Check equality with another message.
     *
     * @param message The message to compare
     * @return true if messages are equal
     */
    boolean equals(Message message);
}
