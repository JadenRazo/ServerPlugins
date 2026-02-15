package net.serverplugins.arcade.utils;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.milkbowl.vault.economy.Economy;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.arcade.ServerArcade;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Represents a command/action that can be executed as a reward. Supports: money, sound, command,
 * message
 */
public abstract class GameCommand {

    private static final Pattern COMMAND_PATTERN = Pattern.compile("^(\\w+):\\s*(.+)$");

    public abstract void execute(Player player, Map<String, String> placeholders);

    /** Parse a command string like "money: %bet% * 10" or "sound: ENTITY_PLAYER_LEVELUP 1 1" */
    public static GameCommand fromString(String input) {
        Matcher matcher = COMMAND_PATTERN.matcher(input.trim());
        if (!matcher.matches()) {
            return new ConsoleCommand(input);
        }

        String type = matcher.group(1).toLowerCase();
        String value = matcher.group(2).trim();

        return switch (type) {
            case "money" -> new MoneyCommand(value);
            case "sound" -> new SoundCommand(value);
            case "message" -> new MessageCommand(value);
            case "command" -> new ConsoleCommand(value);
            default -> new ConsoleCommand(input);
        };
    }

    /** Money reward command with expression support. */
    public static class MoneyCommand extends GameCommand {
        private final String expression;

        public MoneyCommand(String expression) {
            this.expression = expression;
        }

        @Override
        public void execute(Player player, Map<String, String> placeholders) {
            int amount = calculate(placeholders);
            if (amount > 0) {
                Economy economy = ServerArcade.getEconomy();
                if (economy != null) {
                    economy.depositPlayer(player, amount);
                }
            }
        }

        public int calculate(Map<String, String> placeholders) {
            return calculate(
                    placeholders.containsKey("%bet%")
                            ? Integer.parseInt(placeholders.get("%bet%"))
                            : 0);
        }

        public int calculate(int bet) {
            try {
                String expr = expression.replace("%bet%", String.valueOf(bet));
                // Simple expression evaluator for basic math
                return evaluateExpression(expr);
            } catch (Exception e) {
                return 0;
            }
        }

        private int evaluateExpression(String expr) {
            // Remove spaces and handle basic math: +, -, *, /
            expr = expr.replaceAll("\\s+", "");

            // Handle multiplication first
            if (expr.contains("*")) {
                String[] parts = expr.split("\\*");
                int result = Integer.parseInt(parts[0]);
                for (int i = 1; i < parts.length; i++) {
                    result *= Integer.parseInt(parts[i]);
                }
                return result;
            }

            // Handle division
            if (expr.contains("/")) {
                String[] parts = expr.split("/");
                int result = Integer.parseInt(parts[0]);
                for (int i = 1; i < parts.length; i++) {
                    int divisor = Integer.parseInt(parts[i]);
                    if (divisor != 0) result /= divisor;
                }
                return result;
            }

            // Handle addition
            if (expr.contains("+")) {
                String[] parts = expr.split("\\+");
                int result = 0;
                for (String part : parts) {
                    result += Integer.parseInt(part);
                }
                return result;
            }

            return Integer.parseInt(expr);
        }

        public String getExpression() {
            return expression;
        }
    }

    /** Sound effect command. */
    public static class SoundCommand extends GameCommand {
        private final Sound sound;
        private final float volume;
        private final float pitch;

        public SoundCommand(String value) {
            String[] parts = value.split("\\s+");
            Sound parsedSound = null;
            try {
                parsedSound = Sound.valueOf(parts[0].toUpperCase());
            } catch (IllegalArgumentException e) {
                parsedSound = Sound.BLOCK_NOTE_BLOCK_BELL;
            }
            this.sound = parsedSound;
            this.volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
            this.pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
        }

        @Override
        public void execute(Player player, Map<String, String> placeholders) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    /** Chat message command. */
    public static class MessageCommand extends GameCommand {
        private final String message;

        public MessageCommand(String message) {
            this.message = message;
        }

        @Override
        public void execute(Player player, Map<String, String> placeholders) {
            String msg = message;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                msg = msg.replace(entry.getKey(), entry.getValue());
            }
            TextUtil.send(player, msg);
        }
    }

    /** Console command execution. */
    public static class ConsoleCommand extends GameCommand {
        private final String command;

        public ConsoleCommand(String command) {
            this.command = command;
        }

        @Override
        public void execute(Player player, Map<String, String> placeholders) {
            String cmd = command;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                cmd = cmd.replace(entry.getKey(), entry.getValue());
            }
            cmd = cmd.replace("%player%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }
}
