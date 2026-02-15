package net.serverplugins.commands.dynamic.placeholders;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.serverplugins.api.handlers.PlaceholderHandler;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.entity.Player;

/**
 * Processes placeholder patterns in commands to create a list of executable actions. Supports:
 * $text$, $delay$, $OPEN_URL$, $RUN_COMMAND$
 */
public class PlaceholderProcessor {

    // Regex patterns for different placeholder types
    private static final Pattern TEXT_PATTERN = Pattern.compile("\\$text\\$(.+)");
    private static final Pattern DELAY_PATTERN = Pattern.compile("\\$delay\\$<(\\d+)>");

    // Support both Dream format (no brackets) AND Server format (with brackets)
    // Groups 1-3: Angle bracket format <text;hover;url>
    // Groups 4-6: Dream format text;hover;url
    private static final Pattern OPEN_URL_PATTERN =
            Pattern.compile("\\$OPEN_URL\\$(?:<([^;]+);([^;]+);([^>]+)>|([^;]+);([^;]+);(.+))");
    private static final Pattern RUN_COMMAND_PATTERN =
            Pattern.compile("\\$RUN_COMMAND\\$(?:<([^;]+);([^;]+);([^>]+)>|([^;]+);([^;]+);(.+))");

    /**
     * Parse a list of command strings into executable actions
     *
     * @param commands List of command strings with placeholders
     * @param player Player to parse placeholders for (can be null)
     * @return List of parsed command actions
     */
    public static List<ParsedCommand> parseCommands(List<String> commands, Player player) {
        List<ParsedCommand> result = new ArrayList<>();

        for (String cmd : commands) {
            if (cmd == null || cmd.isEmpty()) continue;

            // Apply player placeholders if available
            String processed = player != null ? PlaceholderHandler.parse(player, cmd) : cmd;

            ParsedCommand parsed = parseCommand(processed);
            if (parsed != null) {
                result.add(parsed);
            }
        }

        return result;
    }

    /**
     * Parse a single command string into a ParsedCommand
     *
     * @param command Command string with placeholders
     * @return ParsedCommand or null if invalid
     */
    private static ParsedCommand parseCommand(String command) {
        // Check for $text$ placeholder
        Matcher textMatcher = TEXT_PATTERN.matcher(command);
        if (textMatcher.matches()) {
            String message = textMatcher.group(1);
            Component component = TextUtil.parse(message);
            return new ParsedCommand(ParsedCommand.ActionType.TEXT, component);
        }

        // Check for $delay$ placeholder
        Matcher delayMatcher = DELAY_PATTERN.matcher(command);
        if (delayMatcher.matches()) {
            int ticks = Integer.parseInt(delayMatcher.group(1));
            return new ParsedCommand(ParsedCommand.ActionType.DELAY, ticks);
        }

        // Check for $OPEN_URL$ placeholder
        Matcher urlMatcher = OPEN_URL_PATTERN.matcher(command);
        if (urlMatcher.matches()) {
            String displayText, hoverText, url;

            // Check which format matched
            if (urlMatcher.group(1) != null) {
                // Angle bracket format: <text;hover;url>
                displayText = urlMatcher.group(1);
                hoverText = urlMatcher.group(2);
                url = urlMatcher.group(3);
            } else {
                // Dream format: text;hover;url
                displayText = urlMatcher.group(4);
                hoverText = urlMatcher.group(5);
                url = urlMatcher.group(6);
            }

            Component component =
                    TextUtil.parse(displayText)
                            .hoverEvent(HoverEvent.showText(TextUtil.parse(hoverText)))
                            .clickEvent(ClickEvent.openUrl(url));

            return new ParsedCommand(ParsedCommand.ActionType.TEXT, component);
        }

        // Check for $RUN_COMMAND$ placeholder
        Matcher commandMatcher = RUN_COMMAND_PATTERN.matcher(command);
        if (commandMatcher.matches()) {
            String displayText, hoverText, commandToRun;

            // Check which format matched
            if (commandMatcher.group(1) != null) {
                // Angle bracket format: <text;hover;cmd>
                displayText = commandMatcher.group(1);
                hoverText = commandMatcher.group(2);
                commandToRun = commandMatcher.group(3);
            } else {
                // Dream format: text;hover;cmd
                displayText = commandMatcher.group(4);
                hoverText = commandMatcher.group(5);
                commandToRun = commandMatcher.group(6);
            }

            Component component =
                    TextUtil.parse(displayText)
                            .hoverEvent(HoverEvent.showText(TextUtil.parse(hoverText)))
                            .clickEvent(ClickEvent.runCommand(commandToRun));

            return new ParsedCommand(ParsedCommand.ActionType.TEXT, component);
        }

        // If no placeholder matched, treat as raw command
        return new ParsedCommand(ParsedCommand.ActionType.COMMAND, command);
    }

    /** Represents a parsed command action */
    public static class ParsedCommand {
        private final ActionType type;
        private final Object value;

        public ParsedCommand(ActionType type, Object value) {
            this.type = type;
            this.value = value;
        }

        public ActionType getType() {
            return type;
        }

        public Object getValue() {
            return value;
        }

        public Component asComponent() {
            return (Component) value;
        }

        public String asString() {
            return (String) value;
        }

        public int asInt() {
            return (Integer) value;
        }

        public enum ActionType {
            TEXT, // Send a text message (Component)
            DELAY, // Delay in ticks (Integer)
            COMMAND // Execute a command (String)
        }
    }
}
