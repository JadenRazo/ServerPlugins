package net.serverplugins.api.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TextUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
            LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer LEGACY_SECTION =
            LegacyComponentSerializer.legacySection();

    public static Component parse(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        // Convert legacy color codes to MiniMessage format before parsing
        text = convertLegacyToMiniMessage(text);
        return MINI_MESSAGE.deserialize(text);
    }

    /** Converts legacy color codes (&x, §x, &#RRGGBB) to MiniMessage format */
    private static String convertLegacyToMiniMessage(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // Convert &#RRGGBB to <#RRGGBB>
        text = text.replaceAll("&#([A-Fa-f0-9]{6})", "<#$1>");

        // Convert §x section symbol codes to &x so they get handled below
        text = text.replace('§', '&');

        // Handle consecutive &k used as obfuscated filler (e.g., §k§k§k§k → scrambled divider)
        // Each &k becomes one scrambled character since &k is a format code with no visible content
        Pattern consecutiveObf = Pattern.compile("(?i)(&k){2,}");
        Matcher obfMatcher = consecutiveObf.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (obfMatcher.find()) {
            int count = obfMatcher.group().length() / 2;
            obfMatcher.appendReplacement(sb, "<obfuscated>" + "|".repeat(count) + "</obfuscated>");
        }
        obfMatcher.appendTail(sb);
        text = sb.toString();

        // Convert legacy &x codes
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

        return text;
    }

    public static List<Component> parse(String... texts) {
        List<Component> components = new ArrayList<>();
        for (String text : texts) components.add(parse(text));
        return components;
    }

    public static List<Component> parse(List<String> texts) {
        List<Component> components = new ArrayList<>();
        for (String text : texts) components.add(parse(text));
        return components;
    }

    public static Component parseLegacy(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        return LEGACY_SERIALIZER.deserialize(text);
    }

    public static Component parseLegacySection(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        return LEGACY_SECTION.deserialize(text);
    }

    public static String serialize(Component component) {
        if (component == null) return "";
        return MINI_MESSAGE.serialize(component);
    }

    public static String serializeLegacy(Component component) {
        if (component == null) return "";
        return LEGACY_SERIALIZER.serialize(component);
    }

    public static Component colored(String text, String color) {
        return parse("<" + color + ">" + text + "</" + color + ">");
    }

    public static Component bold(String text) {
        return parse("<bold>" + text + "</bold>");
    }

    public static Component gradient(String text, String startColor, String endColor) {
        return parse("<gradient:" + startColor + ":" + endColor + ">" + text + "</gradient>");
    }

    public static Component rainbow(String text) {
        return parse("<rainbow>" + text + "</rainbow>");
    }

    public static MiniMessage getMiniMessage() {
        return MINI_MESSAGE;
    }

    public static LegacyComponentSerializer getLegacySerializer() {
        return LEGACY_SERIALIZER;
    }

    public static void send(CommandSender sender, String message) {
        if (sender instanceof Audience audience) {
            audience.sendMessage(parse(message));
        } else {
            sender.sendMessage(serializeLegacy(parse(message)));
        }
    }

    public static void send(Player player, String message) {
        player.sendMessage(parse(message));
    }

    public static void send(Audience audience, String message) {
        audience.sendMessage(parse(message));
    }

    public static void send(CommandSender sender, Component message) {
        if (sender instanceof Audience audience) {
            audience.sendMessage(message);
        } else {
            sender.sendMessage(serializeLegacy(message));
        }
    }

    public static void send(Player player, Component message) {
        player.sendMessage(message);
    }

    public static void sendActionBar(Player player, String message) {
        player.sendActionBar(parse(message));
    }

    public static void sendActionBar(Player player, Component message) {
        player.sendActionBar(message);
    }

    public static void sendTitle(
            Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        player.showTitle(
                net.kyori.adventure.title.Title.title(
                        parse(title),
                        parse(subtitle),
                        net.kyori.adventure.title.Title.Times.times(
                                java.time.Duration.ofMillis(fadeIn * 50L),
                                java.time.Duration.ofMillis(stay * 50L),
                                java.time.Duration.ofMillis(fadeOut * 50L))));
    }

    /** Broadcasts a message to all online players with a styled prefix */
    public static void broadcast(String message) {
        broadcastRaw("<red><bold>[Broadcast]</bold></red> <white>" + message + "</white>");
    }

    /** Broadcasts a pre-formatted message to all online players */
    public static void broadcastRaw(String formattedMessage) {
        Component component = parse(formattedMessage);
        org.bukkit.Bukkit.getServer().sendMessage(component);
    }

    /** Broadcasts a message with a custom prefix to all online players */
    public static void broadcast(String prefix, String message) {
        broadcastRaw(prefix + " " + message);
    }

    // ========== QUICK MESSAGE METHODS ==========

    /**
     * Sends a success message with a green checkmark.
     *
     * @param sender The recipient
     * @param message The success message (without color codes)
     */
    public static void sendSuccess(CommandSender sender, String message) {
        send(sender, "<green>✓ " + message);
    }

    /**
     * Sends an error message with a red cross.
     *
     * @param sender The recipient
     * @param message The error message (without color codes)
     */
    public static void sendError(CommandSender sender, String message) {
        send(sender, "<red>✗ " + message);
    }

    /**
     * Sends a warning message with a yellow warning icon.
     *
     * @param sender The recipient
     * @param message The warning message (without color codes)
     */
    public static void sendWarning(CommandSender sender, String message) {
        send(sender, "<yellow>⚠ " + message);
    }

    /**
     * Sends an info message with a gray arrow.
     *
     * @param sender The recipient
     * @param message The info message (without color codes)
     */
    public static void sendInfo(CommandSender sender, String message) {
        send(sender, "<gray>→ " + message);
    }

    // ========== ICON UTILITY METHODS ==========

    /**
     * Prepends a green checkmark to the message.
     *
     * @param message The message to prefix
     * @return Message with checkmark
     */
    public static String withCheckmark(String message) {
        return "<green>✓</green> " + message;
    }

    /**
     * Prepends a red cross to the message.
     *
     * @param message The message to prefix
     * @return Message with cross
     */
    public static String withCross(String message) {
        return "<red>✗</red> " + message;
    }

    /**
     * Prepends a yellow warning icon to the message.
     *
     * @param message The message to prefix
     * @return Message with warning icon
     */
    public static String withWarning(String message) {
        return "<yellow>⚠</yellow> " + message;
    }

    /**
     * Prepends a gray arrow to the message.
     *
     * @param message The message to prefix
     * @return Message with arrow
     */
    public static String withArrow(String message) {
        return "<gray>→</gray> " + message;
    }
}
