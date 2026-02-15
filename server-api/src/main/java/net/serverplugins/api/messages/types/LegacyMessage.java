package net.serverplugins.api.messages.types;

import javax.annotation.Nonnull;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.serverplugins.api.handlers.PlaceholderHandler;
import net.serverplugins.api.messages.Message;
import net.serverplugins.api.messages.MessageType;
import net.serverplugins.api.messages.utils.LegacyText;
import net.serverplugins.api.messages.utils.TextConverter;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Legacy message implementation using BungeeCord chat API. Used on Spigot servers without Adventure
 * API support.
 */
public class LegacyMessage implements Message {

    private final String message;
    private final boolean hasPlaceholders;

    public LegacyMessage(@Nonnull String message) {
        this(message, false);
    }

    public LegacyMessage(@Nonnull String message, boolean hasPlaceholders) {
        // Convert any modern formatting to legacy, then apply color codes
        this.message = LegacyText.replaceAllColorCodes(TextConverter.modernToLegacy(message));
        this.hasPlaceholders = hasPlaceholders;
    }

    @Override
    public MessageType getType() {
        return MessageType.LEGACY;
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
        String msg = getModifiedMessage(player);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
    }

    @Override
    public Inventory createInventory(InventoryHolder owner, int size) {
        String title = owner instanceof Player player ? getModifiedMessage(player) : this.message;
        return Bukkit.createInventory(owner, size, title);
    }

    @Override
    public Inventory createInventory(InventoryHolder owner, InventoryType type) {
        String title = owner instanceof Player player ? getModifiedMessage(player) : this.message;
        return Bukkit.createInventory(owner, type, title);
    }

    @Override
    public Message replaceText(String oldText, String newText) {
        return new LegacyMessage(this.message.replace(oldText, newText), this.hasPlaceholders);
    }

    @Override
    public Message concat(String text, boolean atEnd) {
        String newMessage = atEnd ? this.message + text : text + this.message;
        return new LegacyMessage(newMessage, this.hasPlaceholders);
    }

    @Override
    public Message concat(Message message) {
        return new LegacyMessage(this.message + message.toString(), this.hasPlaceholders);
    }

    @Override
    public Message clone() {
        return new LegacyMessage(this.message, this.hasPlaceholders);
    }

    /**
     * Get the message with placeholders replaced for a specific player.
     *
     * @param player The player context
     * @return The processed message
     */
    private String getModifiedMessage(Player player) {
        if (this.hasPlaceholders && player != null) {
            return PlaceholderHandler.parse(player, this.message);
        }
        return this.message;
    }

    @Override
    public String toString() {
        return this.message;
    }

    @Override
    public int hashCode() {
        return this.message.hashCode();
    }

    @Override
    public boolean equals(Message message) {
        return message != null
                && message.getType() == MessageType.LEGACY
                && message.toString().equals(this.message);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Message message) {
            return equals(message);
        }
        return false;
    }

    /**
     * Get the raw message string.
     *
     * @return The message
     */
    public String getMessage() {
        return this.message;
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
