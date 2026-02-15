package net.serverplugins.filter.chat;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.serverplugins.filter.ServerFilter;
import net.serverplugins.filter.data.FilterLevel;
import net.serverplugins.filter.data.FilterPreferenceManager;
import net.serverplugins.filter.filter.FilterResult;
import net.serverplugins.filter.filter.MessageFilterService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

public class ChatFilterListener implements Listener {

    private final ServerFilter plugin;
    private final MessageFilterService filterService;
    private final FilterPreferenceManager preferences;
    private final ViolationHandler violationHandler;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final boolean hasPapi;

    // Legacy color code patterns
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern STANDALONE_HEX_PATTERN =
            Pattern.compile("(?<!&)#([A-Fa-f0-9]{6})");
    private static final Pattern LEGACY_PATTERN = Pattern.compile("&([0-9a-fk-orA-FK-OR])");

    public ChatFilterListener(
            ServerFilter plugin,
            MessageFilterService filterService,
            FilterPreferenceManager preferences,
            ViolationHandler violationHandler) {
        this.plugin = plugin;
        this.filterService = filterService;
        this.preferences = preferences;
        this.violationHandler = violationHandler;
        this.hasPapi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        String originalMessage =
                PlainTextComponentSerializer.plainText().serialize(event.message());

        // Check for bypass permission
        if (sender.hasPermission("serverfilter.bypass.filter")) {
            // Still apply chat format even if bypassing filter
            if (isChatFormatEnabled()) {
                event.renderer(
                        new FormattingChatRenderer(
                                plugin, sender, filterService, preferences, hasPapi, true));
            }
            return;
        }

        // Step 1: Check for slurs (always blocked)
        FilterResult slurResult =
                filterService.analyzeMessage(
                        originalMessage,
                        java.util.EnumSet.of(net.serverplugins.filter.data.WordCategory.SLURS));

        if (slurResult.hasMatches()) {
            event.setCancelled(true);
            violationHandler.handleSlurViolation(sender, originalMessage, slurResult);
            return;
        }

        // Step 2: Set up per-viewer rendering with format and filtering
        if (isChatFormatEnabled()) {
            event.renderer(
                    new FormattingChatRenderer(
                            plugin, sender, filterService, preferences, hasPapi, false));
        } else {
            event.renderer(
                    new FilteringChatRenderer(filterService, preferences, sender.getUniqueId()));
        }
    }

    private boolean isChatFormatEnabled() {
        return plugin.getConfig().getBoolean("chat-format.enabled", true);
    }

    /** Converts legacy color codes (&a, &f, &#aabbcc, #aabbcc) to MiniMessage format */
    private static String convertLegacyToMiniMessage(String text) {
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

    /** Chat renderer that applies formatting AND filtering */
    private static class FormattingChatRenderer implements ChatRenderer {

        private final ServerFilter plugin;
        private final Player sender;
        private final MessageFilterService filterService;
        private final FilterPreferenceManager preferences;
        private final boolean hasPapi;
        private final boolean bypassFilter;
        private final MiniMessage miniMessage = MiniMessage.miniMessage();

        public FormattingChatRenderer(
                ServerFilter plugin,
                Player sender,
                MessageFilterService filterService,
                FilterPreferenceManager preferences,
                boolean hasPapi,
                boolean bypassFilter) {
            this.plugin = plugin;
            this.sender = sender;
            this.filterService = filterService;
            this.preferences = preferences;
            this.hasPapi = hasPapi;
            this.bypassFilter = bypassFilter;
        }

        @Override
        public @NotNull Component render(
                @NotNull Player source,
                @NotNull Component sourceDisplayName,
                @NotNull Component message,
                @NotNull Audience viewer) {
            String plainMessage = PlainTextComponentSerializer.plainText().serialize(message);
            String displayName =
                    PlainTextComponentSerializer.plainText().serialize(sourceDisplayName);

            // Apply filtering if viewer is a different player and not bypassing
            if (!bypassFilter && viewer instanceof Player viewerPlayer) {
                if (!viewerPlayer.getUniqueId().equals(source.getUniqueId())) {
                    FilterLevel viewerLevel =
                            preferences.getFilterLevel(viewerPlayer.getUniqueId());
                    plainMessage = filterService.filterMessage(plainMessage, viewerLevel);
                }
            }

            // Get the format from config
            String format =
                    plugin.getConfig()
                            .getString(
                                    "chat-format.format", "{displayname}<gray>: <white>{message}");

            // Get nickname from LuckPerms meta with fallback
            String nickname = getNickname(source, displayName);

            // Replace placeholders
            format = format.replace("{nickname}", nickname);
            format = format.replace("{displayname}", displayName);
            format = format.replace("{name}", source.getName());
            // Strip color codes from user messages to prevent injection
            format = format.replace("{message}", plainMessage.replace("&", ""));

            // Apply PlaceholderAPI if available
            if (hasPapi) {
                format = PlaceholderAPI.setPlaceholders(source, format);
            }

            // Use LegacyComponentSerializer to preserve Unicode characters (like custom font icons)
            // Convert standalone hex colors to legacy format first
            format = format.replaceAll("(?<!&)#([A-Fa-f0-9]{6})", "&#$1");

            // Also convert MiniMessage color tags to legacy format for compatibility
            format =
                    format.replace("<dark_gray>", "&8")
                            .replace("<gray>", "&7")
                            .replace("<white>", "&f")
                            .replace("<reset>", "&r");

            try {
                return LegacyComponentSerializer.legacyAmpersand().deserialize(format);
            } catch (Exception e) {
                // Ultimate fallback
                return Component.text()
                        .append(sourceDisplayName)
                        .append(Component.text(": "))
                        .append(Component.text(plainMessage))
                        .build();
            }
        }

        private String escapeForMiniMessage(String text) {
            // Escape MiniMessage special characters in user messages
            return text.replace("<", "\\<").replace(">", "\\>");
        }

        /** Gets the player's nickname from LuckPerms meta, falling back to display name */
        private String getNickname(Player player, String fallback) {
            if (!hasPapi) {
                return fallback;
            }

            // Try to get nickname from LuckPerms meta
            String nickname = PlaceholderAPI.setPlaceholders(player, "%luckperms_meta_nickname%");

            // If empty or just the placeholder text, use fallback
            if (nickname == null
                    || nickname.isEmpty()
                    || nickname.equals("%luckperms_meta_nickname%")
                    || nickname.equalsIgnoreCase("null")) {
                return fallback;
            }

            return nickname;
        }
    }

    /** Simple filtering renderer without custom format (fallback) */
    private static class FilteringChatRenderer implements ChatRenderer {

        private final MessageFilterService filterService;
        private final FilterPreferenceManager preferences;
        private final UUID senderUuid;

        public FilteringChatRenderer(
                MessageFilterService filterService,
                FilterPreferenceManager preferences,
                UUID senderUuid) {
            this.filterService = filterService;
            this.preferences = preferences;
            this.senderUuid = senderUuid;
        }

        @Override
        public @NotNull Component render(
                @NotNull Player source,
                @NotNull Component sourceDisplayName,
                @NotNull Component message,
                @NotNull Audience viewer) {
            String plainMessage = PlainTextComponentSerializer.plainText().serialize(message);

            if (viewer instanceof Player viewerPlayer) {
                if (!viewerPlayer.getUniqueId().equals(senderUuid)) {
                    FilterLevel viewerLevel =
                            preferences.getFilterLevel(viewerPlayer.getUniqueId());
                    String filteredMessage = filterService.filterMessage(plainMessage, viewerLevel);

                    if (!filteredMessage.equals(plainMessage)) {
                        return Component.text()
                                .append(sourceDisplayName)
                                .append(Component.text(": "))
                                .append(Component.text(filteredMessage))
                                .build();
                    }
                }
            }

            // Default format
            return Component.text()
                    .append(sourceDisplayName)
                    .append(Component.text(": "))
                    .append(message)
                    .build();
        }
    }
}
