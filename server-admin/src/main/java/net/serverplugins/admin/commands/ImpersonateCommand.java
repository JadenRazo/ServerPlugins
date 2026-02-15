package net.serverplugins.admin.commands;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class ImpersonateCommand implements CommandExecutor, TabCompleter {

    private final ServerAdmin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    // Legacy color code patterns
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern STANDALONE_HEX_PATTERN =
            Pattern.compile("(?<!&)#([A-Fa-f0-9]{6})");
    private static final Pattern LEGACY_PATTERN = Pattern.compile("&([0-9a-fk-orA-FK-OR])");

    public ImpersonateCommand(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("serveradmin.impersonate")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        if (args.length < 2) {
            TextUtil.sendError(sender, "Usage: /impersonate <player> <message>");
            return true;
        }

        String targetName = args[0];

        // Build the message from remaining args
        StringBuilder messageBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) messageBuilder.append(" ");
            messageBuilder.append(args[i]);
        }
        String message = messageBuilder.toString();

        // Try to find the player
        Player targetPlayer = Bukkit.getPlayer(targetName);

        if (targetPlayer == null) {
            TextUtil.sendError(sender, "Player must be online to impersonate them.");
            return true;
        }

        // Get the chat format - same as ServerFilter uses
        String format = "%vault_prefix%{nickname}%vault_suffix%<gray>: <white>{message}";

        // Get nickname (fallback to player name)
        String nickname = targetPlayer.getName();
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            String nickMeta = getPlaceholder(targetPlayer, "%luckperms_meta_nickname%");
            if (nickMeta != null
                    && !nickMeta.isEmpty()
                    && !nickMeta.equals("%luckperms_meta_nickname%")
                    && !nickMeta.equalsIgnoreCase("null")) {
                nickname = nickMeta;
            }
        }

        // Replace placeholders
        format = format.replace("{nickname}", nickname);
        format = format.replace("{displayname}", targetPlayer.getName());
        format = format.replace("{name}", targetPlayer.getName());
        format = format.replace("{message}", escapeForMiniMessage(message));

        // Apply PlaceholderAPI if available
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            format = getPlaceholder(targetPlayer, format);
        }

        // Convert legacy color codes to MiniMessage format
        format = convertLegacyToMiniMessage(format);

        // Parse with MiniMessage and broadcast
        try {
            Component chatMessage = miniMessage.deserialize(format);

            // Send to all players
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(chatMessage);
            }

            // Send to console
            Bukkit.getConsoleSender().sendMessage(chatMessage);

        } catch (Exception e) {
            // Fallback to legacy parsing
            Component chatMessage =
                    LegacyComponentSerializer.legacyAmpersand()
                            .deserialize(
                                    format.replaceAll(
                                            "<[^>]+>", "") // Strip MiniMessage tags for legacy
                                    );
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(chatMessage);
            }
        }

        // Log the impersonation for audit purposes
        plugin.getLogger()
                .info(
                        "[IMPERSONATE] "
                                + sender.getName()
                                + " impersonated "
                                + targetPlayer.getName()
                                + ": "
                                + message);

        // Notify staff (silently)
        String staffNotice =
                "<dark_gray>[<red>IMP</red>] "
                        + sender.getName()
                        + " -> "
                        + targetPlayer.getName()
                        + ": "
                        + message;
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("serveradmin.impersonate.notify") && !staff.equals(sender)) {
                TextUtil.send(staff, staffNotice);
            }
        }

        return true;
    }

    private String getPlaceholder(Player player, String text) {
        try {
            Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method setPlaceholders =
                    papiClass.getMethod(
                            "setPlaceholders", org.bukkit.OfflinePlayer.class, String.class);
            return (String) setPlaceholders.invoke(null, player, text);
        } catch (Exception e) {
            return text;
        }
    }

    private String escapeForMiniMessage(String text) {
        return text.replace("<", "\\<").replace(">", "\\>");
    }

    /** Converts legacy color codes (&a, &f, &#aabbcc, #aabbcc) to MiniMessage format */
    private String convertLegacyToMiniMessage(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // Convert &#RRGGBB hex codes to MiniMessage <#RRGGBB>
        Matcher hexMatcher = HEX_PATTERN.matcher(text);
        StringBuilder hexResult = new StringBuilder();
        while (hexMatcher.find()) {
            hexMatcher.appendReplacement(hexResult, "<#" + hexMatcher.group(1) + ">");
        }
        hexMatcher.appendTail(hexResult);
        text = hexResult.toString();

        // Convert standalone #RRGGBB hex codes (without &) to MiniMessage <#RRGGBB>
        Matcher standaloneHexMatcher = STANDALONE_HEX_PATTERN.matcher(text);
        StringBuilder standaloneResult = new StringBuilder();
        while (standaloneHexMatcher.find()) {
            standaloneHexMatcher.appendReplacement(
                    standaloneResult, "<#" + standaloneHexMatcher.group(1) + ">");
        }
        standaloneHexMatcher.appendTail(standaloneResult);
        text = standaloneResult.toString();

        // Convert &x codes to MiniMessage tags
        Matcher legacyMatcher = LEGACY_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();
        while (legacyMatcher.find()) {
            String code = legacyMatcher.group(1).toLowerCase();
            String replacement =
                    switch (code) {
                        case "0" -> "<black>";
                        case "1" -> "<dark_blue>";
                        case "2" -> "<dark_green>";
                        case "3" -> "<dark_aqua>";
                        case "4" -> "<dark_red>";
                        case "5" -> "<dark_purple>";
                        case "6" -> "<gold>";
                        case "7" -> "<gray>";
                        case "8" -> "<dark_gray>";
                        case "9" -> "<blue>";
                        case "a" -> "<green>";
                        case "b" -> "<aqua>";
                        case "c" -> "<red>";
                        case "d" -> "<light_purple>";
                        case "e" -> "<yellow>";
                        case "f" -> "<white>";
                        case "k" -> "<obfuscated>";
                        case "l" -> "<bold>";
                        case "m" -> "<strikethrough>";
                        case "n" -> "<underlined>";
                        case "o" -> "<italic>";
                        case "r" -> "<reset>";
                        default -> "&" + code;
                    };
            legacyMatcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        legacyMatcher.appendTail(result);

        return result.toString();
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partial)) {
                    completions.add(player.getName());
                }
            }
        }

        return completions;
    }
}
