package net.serverplugins.api.messages.types;

import javax.annotation.Nonnull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.serverplugins.api.handlers.PlaceholderHandler;
import net.serverplugins.api.messages.Message;
import net.serverplugins.api.messages.MessageType;
import net.serverplugins.api.messages.utils.TextConverter;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Modern message implementation using Adventure/MiniMessage API. Used on Paper servers with native
 * Adventure support.
 */
public class ModernMessage implements Message {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final Component message;
    private final String rawMessage;
    private final boolean hasPlaceholders;

    public ModernMessage(@Nonnull String message) {
        this(message, false);
    }

    public ModernMessage(@Nonnull String message, boolean hasPlaceholders) {
        // Convert legacy formatting to modern MiniMessage
        String converted = TextConverter.legacyToModern(message);
        this.message = MINI_MESSAGE.deserialize(converted).decoration(TextDecoration.ITALIC, false);
        this.rawMessage = converted;
        this.hasPlaceholders = hasPlaceholders;
    }

    public ModernMessage(@Nonnull Component component) {
        this.message = component;
        this.rawMessage = MINI_MESSAGE.serialize(component);
        this.hasPlaceholders = false;
    }

    @Override
    public MessageType getType() {
        return MessageType.MODERN;
    }

    @Override
    public void sendMessage(@Nonnull CommandSender receiver) {
        if (receiver instanceof Player player) {
            receiver.sendMessage(getModifiedMessage(player));
        } else {
            receiver.sendMessage(this.message);
        }
    }

    @Override
    public void sendActionbar(@Nonnull Player player) {
        player.sendActionBar(getModifiedMessage(player));
    }

    @Override
    public Inventory createInventory(InventoryHolder owner, int size) {
        Component title =
                owner instanceof Player player ? getModifiedMessage(player) : this.message;
        return Bukkit.createInventory(owner, size, title);
    }

    @Override
    public Inventory createInventory(InventoryHolder owner, InventoryType type) {
        Component title =
                owner instanceof Player player ? getModifiedMessage(player) : this.message;
        return Bukkit.createInventory(owner, type, title);
    }

    @Override
    public Message replaceText(String oldText, String newText) {
        return new ModernMessage(this.rawMessage.replace(oldText, newText), this.hasPlaceholders);
    }

    @Override
    public Message concat(String text, boolean atEnd) {
        String newMessage = atEnd ? this.rawMessage + text : text + this.rawMessage;
        return new ModernMessage(newMessage, this.hasPlaceholders);
    }

    @Override
    public Message concat(Message message) {
        if (message instanceof ModernMessage modern) {
            return new ModernMessage(this.rawMessage + modern.rawMessage, this.hasPlaceholders);
        }
        return new ModernMessage(this.rawMessage + message.toString(), this.hasPlaceholders);
    }

    @Override
    public Message clone() {
        return new ModernMessage(this.rawMessage, this.hasPlaceholders);
    }

    /**
     * Get the message Component with placeholders replaced for a specific player.
     *
     * @param player The player context
     * @return The processed Component
     */
    private Component getModifiedMessage(Player player) {
        if (this.hasPlaceholders && player != null) {
            String processed = PlaceholderHandler.parse(player, this.rawMessage);
            String converted = TextConverter.legacyToModern(processed);
            return MINI_MESSAGE.deserialize(converted).decoration(TextDecoration.ITALIC, false);
        }
        return this.message;
    }

    @Override
    public String toString() {
        return this.rawMessage;
    }

    @Override
    public int hashCode() {
        return this.rawMessage.hashCode();
    }

    @Override
    public boolean equals(Message message) {
        if (message == null || message.getType() != MessageType.MODERN) {
            return false;
        }
        if (message instanceof ModernMessage modern) {
            return this.message.equals(modern.message);
        }
        return this.rawMessage.equals(message.toString());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Message message) {
            return equals(message);
        }
        return false;
    }

    /**
     * Get the Adventure Component.
     *
     * @return The message Component
     */
    public Component getMessage() {
        return this.message;
    }

    /**
     * Get the raw MiniMessage string.
     *
     * @return The raw message
     */
    public String getRawMessage() {
        return this.rawMessage;
    }

    /**
     * Check if this message has placeholders.
     *
     * @return true if placeholders should be processed
     */
    public boolean hasPlaceholders() {
        return this.hasPlaceholders;
    }
}
