package net.serverplugins.api.configuration.parsers;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.api.messages.Message;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Parser for Message objects. Automatically handles server type detection for legacy vs modern
 * formatting.
 */
public class MessageParser extends Parser<Message> {

    private static final MessageParser INSTANCE = new MessageParser();

    private MessageParser() {}

    public static MessageParser getInstance() {
        return INSTANCE;
    }

    @Override
    public Message loadFromConfig(ConfigurationSection config, String path) {
        if (config == null) return Message.empty();

        String value = path != null ? config.getString(path) : null;
        if (value == null || value.isEmpty()) {
            return Message.empty();
        }

        return Message.fromText(value);
    }

    /**
     * Load a list of messages from config.
     *
     * @param config The configuration section
     * @param path The path to the list
     * @return List of Messages
     */
    public List<Message> loadListFromConfig(ConfigurationSection config, String path) {
        List<Message> messages = new ArrayList<>();

        if (config == null || path == null) {
            return messages;
        }

        List<String> strings = config.getStringList(path);
        for (String s : strings) {
            messages.add(Message.fromText(s));
        }

        return messages;
    }

    @Override
    public void saveToConfig(ConfigurationSection config, String path, Message message) {
        if (config == null) return;

        if (message == null || message.getType().isEmpty()) {
            config.set(path, null);
        } else {
            config.set(path, message.toString());
        }
    }

    /**
     * Save a list of messages to config.
     *
     * @param config The configuration section
     * @param path The path to save at
     * @param messages The list of messages
     */
    public void saveListToConfig(ConfigurationSection config, String path, List<Message> messages) {
        if (config == null || messages == null) return;

        List<String> strings = new ArrayList<>();
        for (Message message : messages) {
            if (!message.getType().isEmpty()) {
                strings.add(message.toString());
            }
        }
        config.set(path, strings);
    }
}
