package net.serverplugins.api.messages;

import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * Instance-based messaging manager for individual plugins.
 *
 * <p>Each plugin should create its own PluginMessenger instance to handle configuration-driven
 * messages with automatic prefix handling and caching.
 *
 * <h3>Setup Example:</h3>
 *
 * <pre>{@code
 * // In your plugin config class
 * private PluginMessenger messenger;
 *
 * public void reload() {
 *     plugin.reloadConfig();
 *     this.messenger = new PluginMessenger(
 *         plugin.getConfig(),
 *         "messages",
 *         "<gradient:#ff6b6b:#feca57>[MyPlugin]</gradient> "
 *     );
 * }
 *
 * public PluginMessenger getMessenger() {
 *     return messenger;
 * }
 * }</pre>
 *
 * <h3>Config.yml Structure:</h3>
 *
 * <pre>{@code
 * messages:
 *   prefix: "<gradient:#ff6b6b:#feca57>[MyPlugin]</gradient> "
 *   claim-success: "<green>Claim created successfully!"
 *   claim-error: "<red>Failed to create claim: {reason}"
 * }</pre>
 *
 * <h3>Usage Example:</h3>
 *
 * <pre>{@code
 * // Send a configured message
 * messenger.send(player, "claim-success");
 *
 * // Send with placeholders
 * messenger.send(player, "claim-error",
 *     Placeholder.of("reason", "Not enough money")
 * );
 *
 * // Quick success message
 * messenger.sendSuccess(player, "Teleported successfully!");
 * }</pre>
 *
 * @since 1.0.0
 */
public class PluginMessenger {

    private final FileConfiguration config;
    private final String messagesSection;
    private final String defaultPrefix;
    private final Map<String, String> messageCache;

    /**
     * Creates a new PluginMessenger instance.
     *
     * @param config The plugin's FileConfiguration
     * @param messagesSection The config section containing messages (e.g., "messages")
     * @param defaultPrefix The default prefix if not specified in config
     */
    public PluginMessenger(FileConfiguration config, String messagesSection, String defaultPrefix) {
        this.config = config;
        this.messagesSection = messagesSection;
        this.defaultPrefix = defaultPrefix;
        this.messageCache = new HashMap<>();
        loadMessages();
    }

    /** Loads all messages from the config into the cache. */
    private void loadMessages() {
        messageCache.clear();
        if (config.isConfigurationSection(messagesSection)) {
            var section = config.getConfigurationSection(messagesSection);
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    if (!key.equals("prefix")) {
                        String message = section.getString(key);
                        if (message != null) {
                            messageCache.put(key, message);
                        }
                    }
                }
            }
        }
    }

    /** Reloads messages from the config. Call this after reloading your plugin's configuration. */
    public void reload() {
        loadMessages();
    }

    /**
     * Gets the configured prefix.
     *
     * @return The prefix from config, or default prefix if not set
     */
    public String getPrefix() {
        String prefix = config.getString(messagesSection + ".prefix");
        return prefix != null ? prefix : defaultPrefix;
    }

    /**
     * Gets a raw message from config without the prefix.
     *
     * @param key The message key
     * @return The raw message, or the key in brackets if not found
     */
    public String getRawMessage(String key) {
        String message = messageCache.get(key);
        if (message == null) {
            message = config.getString(messagesSection + "." + key);
            if (message == null) {
                return "[" + key + "]";
            }
            messageCache.put(key, message);
        }
        return message;
    }

    /**
     * Gets a message from config with the prefix prepended.
     *
     * @param key The message key
     * @return The message with prefix
     */
    public String getMessage(String key) {
        return getPrefix() + getRawMessage(key);
    }

    /**
     * Sends a configured message to a CommandSender with prefix.
     *
     * @param sender The recipient
     * @param messageKey The message key in config
     * @param placeholders Optional placeholders to replace
     */
    public void send(CommandSender sender, String messageKey, Placeholder... placeholders) {
        String message = getMessage(messageKey);
        message = Placeholder.replaceAll(message, placeholders);
        TextUtil.send(sender, message);
    }

    /**
     * Sends a configured message to a Player with prefix.
     *
     * @param player The recipient player
     * @param messageKey The message key in config
     * @param placeholders Optional placeholders to replace
     */
    public void send(Player player, String messageKey, Placeholder... placeholders) {
        send((CommandSender) player, messageKey, placeholders);
    }

    /**
     * Sends a raw formatted message to a CommandSender (no prefix, no config lookup).
     *
     * @param sender The recipient
     * @param message The pre-formatted message
     */
    public void sendRaw(CommandSender sender, String message) {
        TextUtil.send(sender, message);
    }

    /**
     * Sends a raw formatted message to a Player (no prefix, no config lookup).
     *
     * @param player The recipient player
     * @param message The pre-formatted message
     */
    public void sendRaw(Player player, String message) {
        TextUtil.send(player, message);
    }

    /**
     * Sends a success message with the plugin prefix.
     *
     * @param sender The recipient
     * @param message The success message (without color codes)
     */
    public void sendSuccess(CommandSender sender, String message) {
        String formatted =
                getPrefix() + ColorScheme.SUCCESS + ColorScheme.CHECKMARK + " " + message;
        TextUtil.send(sender, formatted);
    }

    /**
     * Sends an error message with the plugin prefix.
     *
     * @param sender The recipient
     * @param message The error message (without color codes)
     */
    public void sendError(CommandSender sender, String message) {
        String formatted = getPrefix() + ColorScheme.ERROR + ColorScheme.CROSS + " " + message;
        TextUtil.send(sender, formatted);
    }

    /**
     * Sends a warning message with the plugin prefix.
     *
     * @param sender The recipient
     * @param message The warning message (without color codes)
     */
    public void sendWarning(CommandSender sender, String message) {
        String formatted =
                getPrefix() + ColorScheme.WARNING + ColorScheme.WARNING_ICON + " " + message;
        TextUtil.send(sender, formatted);
    }

    /**
     * Sends an info message with the plugin prefix.
     *
     * @param sender The recipient
     * @param message The info message (without color codes)
     */
    public void sendInfo(CommandSender sender, String message) {
        String formatted = getPrefix() + ColorScheme.INFO + ColorScheme.ARROW + " " + message;
        TextUtil.send(sender, formatted);
    }

    /**
     * Gets a message as a Component without sending it.
     *
     * @param messageKey The message key in config
     * @param placeholders Optional placeholders to replace
     * @return The formatted Component
     */
    public Component getComponent(String messageKey, Placeholder... placeholders) {
        String message = getMessage(messageKey);
        message = Placeholder.replaceAll(message, placeholders);
        return TextUtil.parse(message);
    }

    /**
     * Gets a raw message as a Component without prefix.
     *
     * @param messageKey The message key in config
     * @param placeholders Optional placeholders to replace
     * @return The formatted Component without prefix
     */
    public Component getRawComponent(String messageKey, Placeholder... placeholders) {
        String message = getRawMessage(messageKey);
        message = Placeholder.replaceAll(message, placeholders);
        return TextUtil.parse(message);
    }

    /**
     * Checks if a message exists in the configuration.
     *
     * @param messageKey The message key to check
     * @return true if the message exists
     */
    public boolean hasMessage(String messageKey) {
        return messageCache.containsKey(messageKey)
                || config.contains(messagesSection + "." + messageKey);
    }
}
