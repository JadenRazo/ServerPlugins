package net.serverplugins.api.messages.types;

import javax.annotation.Nonnull;
import net.serverplugins.api.messages.Message;
import net.serverplugins.api.messages.MessageType;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Null-object pattern implementation for empty messages. Operations on empty messages are no-ops.
 */
public final class EmptyMessage implements Message {

    private static final EmptyMessage INSTANCE = new EmptyMessage();

    private EmptyMessage() {}

    public static EmptyMessage getInstance() {
        return INSTANCE;
    }

    @Override
    public MessageType getType() {
        return MessageType.EMPTY;
    }

    @Override
    public void sendMessage(@Nonnull CommandSender receiver) {
        // No-op for empty messages
    }

    @Override
    public void sendActionbar(@Nonnull Player player) {
        // No-op for empty messages
    }

    @Override
    public Inventory createInventory(InventoryHolder owner, int size) {
        return Bukkit.createInventory(owner, size);
    }

    @Override
    public Inventory createInventory(InventoryHolder owner, InventoryType type) {
        return Bukkit.createInventory(owner, type);
    }

    @Override
    public Message replaceText(String oldText, String newText) {
        return this;
    }

    @Override
    public Message concat(String text, boolean atEnd) {
        return Message.fromText(text);
    }

    @Override
    public Message concat(Message message) {
        return message;
    }

    @Override
    public Message clone() {
        return this;
    }

    @Override
    public String toString() {
        return "";
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public boolean equals(Message message) {
        return message != null && message.getType() == MessageType.EMPTY;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Message message) {
            return equals(message);
        }
        return false;
    }
}
