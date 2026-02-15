package net.serverplugins.adminvelocity.messaging;

import com.velocitypowered.api.proxy.Player;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Text utility class for Velocity plugin messaging.
 *
 * <p>This class provides message formatting and sending capabilities compatible with Velocity's
 * Adventure API.
 */
public class VelocityTextUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    /** Parses a MiniMessage string into a Component. */
    public static Component parse(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        text = convertLegacyToMiniMessage(text);
        return MINI_MESSAGE.deserialize(text);
    }

    /**
     * Converts legacy color codes to MiniMessage format. This ensures backward compatibility with
     * legacy &-codes while promoting MiniMessage usage.
     */
    private static String convertLegacyToMiniMessage(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // Convert &#RRGGBB hex codes to <#RRGGBB>
        text = text.replaceAll("&#([A-Fa-f0-9]{6})", "<#$1>");

        // Convert legacy & color codes to MiniMessage tags
        text =
                text.replace("&0", "<black>")
                        .replace("&1", "<dark_blue>")
                        .replace("&2", "<dark_green>")
                        .replace("&3", "<dark_aqua>")
                        .replace("&4", "<dark_red>")
                        .replace("&5", "<dark_purple>")
                        .replace("&6", "<gold>")
                        .replace("&7", "<gray>")
                        .replace("&8", "<dark_gray>")
                        .replace("&9", "<blue>")
                        .replace("&a", "<green>")
                        .replace("&A", "<green>")
                        .replace("&b", "<aqua>")
                        .replace("&B", "<aqua>")
                        .replace("&c", "<red>")
                        .replace("&C", "<red>")
                        .replace("&d", "<light_purple>")
                        .replace("&D", "<light_purple>")
                        .replace("&e", "<yellow>")
                        .replace("&E", "<yellow>")
                        .replace("&f", "<white>")
                        .replace("&F", "<white>")
                        .replace("&k", "<obfuscated>")
                        .replace("&K", "<obfuscated>")
                        .replace("&l", "<bold>")
                        .replace("&L", "<bold>")
                        .replace("&m", "<strikethrough>")
                        .replace("&M", "<strikethrough>")
                        .replace("&n", "<underlined>")
                        .replace("&N", "<underlined>")
                        .replace("&o", "<italic>")
                        .replace("&O", "<italic>")
                        .replace("&r", "<reset>")
                        .replace("&R", "<reset>");

        // Convert section sign § codes as well (for complete legacy support)
        text =
                text.replace("§0", "<black>")
                        .replace("§1", "<dark_blue>")
                        .replace("§2", "<dark_green>")
                        .replace("§3", "<dark_aqua>")
                        .replace("§4", "<dark_red>")
                        .replace("§5", "<dark_purple>")
                        .replace("§6", "<gold>")
                        .replace("§7", "<gray>")
                        .replace("§8", "<dark_gray>")
                        .replace("§9", "<blue>")
                        .replace("§a", "<green>")
                        .replace("§A", "<green>")
                        .replace("§b", "<aqua>")
                        .replace("§B", "<aqua>")
                        .replace("§c", "<red>")
                        .replace("§C", "<red>")
                        .replace("§d", "<light_purple>")
                        .replace("§D", "<light_purple>")
                        .replace("§e", "<yellow>")
                        .replace("§E", "<yellow>")
                        .replace("§f", "<white>")
                        .replace("§F", "<white>")
                        .replace("§k", "<obfuscated>")
                        .replace("§K", "<obfuscated>")
                        .replace("§l", "<bold>")
                        .replace("§L", "<bold>")
                        .replace("§m", "<strikethrough>")
                        .replace("§M", "<strikethrough>")
                        .replace("§n", "<underlined>")
                        .replace("§N", "<underlined>")
                        .replace("§o", "<italic>")
                        .replace("§O", "<italic>")
                        .replace("§r", "<reset>")
                        .replace("§R", "<reset>");

        return text;
    }

    /** Parses multiple messages. */
    public static List<Component> parse(String... texts) {
        List<Component> components = new ArrayList<>();
        for (String text : texts) components.add(parse(text));
        return components;
    }

    /** Parses a list of messages. */
    public static List<Component> parse(List<String> texts) {
        List<Component> components = new ArrayList<>();
        for (String text : texts) components.add(parse(text));
        return components;
    }

    /** Serializes a Component to MiniMessage format. */
    public static String serialize(Component component) {
        if (component == null) return "";
        return MINI_MESSAGE.serialize(component);
    }

    /** Sends a message to an Audience. */
    public static void send(Audience audience, String message) {
        audience.sendMessage(parse(message));
    }

    /** Sends a Component to an Audience. */
    public static void send(Audience audience, Component message) {
        audience.sendMessage(message);
    }

    /** Sends a message to a Player. */
    public static void send(Player player, String message) {
        player.sendMessage(parse(message));
    }

    /** Sends a Component to a Player. */
    public static void send(Player player, Component message) {
        player.sendMessage(message);
    }

    // ========== QUICK MESSAGE METHODS ==========

    /** Sends a success message with a green checkmark. */
    public static void sendSuccess(Audience audience, String message) {
        send(audience, VelocityColorScheme.SUCCESS + VelocityColorScheme.CHECKMARK + " " + message);
    }

    /** Sends an error message with a red cross. */
    public static void sendError(Audience audience, String message) {
        send(audience, VelocityColorScheme.ERROR + VelocityColorScheme.CROSS + " " + message);
    }

    /** Sends a warning message with a yellow warning icon. */
    public static void sendWarning(Audience audience, String message) {
        send(
                audience,
                VelocityColorScheme.WARNING + VelocityColorScheme.WARNING_ICON + " " + message);
    }

    /** Sends an info message with a gray arrow. */
    public static void sendInfo(Audience audience, String message) {
        send(audience, VelocityColorScheme.INFO + VelocityColorScheme.ARROW + " " + message);
    }
}
