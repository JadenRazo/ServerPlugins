package net.serverplugins.arcade.games.dice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.serverplugins.api.messages.MessageBuilder;
import net.serverplugins.api.messages.PluginMessenger;
import net.serverplugins.arcade.ServerArcade;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Command handler for /dice command.
 *
 * <p>Usage: /dice bet <amount> over <number> - Roll over N /dice bet <amount> under <number> - Roll
 * under N /dice bet <amount> exact <number> - Roll exact N /dice bet <amount> odd - Roll odd number
 * /dice bet <amount> even - Roll even number /dice stats - Show statistics
 */
public class DiceCommand implements CommandExecutor, TabCompleter {

    private final ServerArcade plugin;
    private final DiceGame game;
    private final DiceConfig config;

    public DiceCommand(ServerArcade plugin, DiceGame game, DiceConfig config) {
        this.plugin = plugin;
        this.game = game;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        PluginMessenger messenger = plugin.getArcadeConfig().getMessenger();

        if (!(sender instanceof Player player)) {
            messenger.sendError(sender, "This command can only be used by players!");
            return true;
        }

        if (!player.hasPermission("serverarcade.command.dice")
                && !player.hasPermission("serverarcade.admin")) {
            net.serverplugins.api.utils.TextUtil.send(
                    player, plugin.getArcadeConfig().getMessage("command-no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        // Handle /dice stats
        if (args[0].equalsIgnoreCase("stats")) {
            // TODO: Show player dice statistics
            MessageBuilder.create()
                    .prefix(messenger.getPrefix())
                    .emphasis("Dice Statistics")
                    .newLine()
                    .info("Statistics coming soon!")
                    .send(player);
            return true;
        }

        // Handle /dice bet <amount> <type> [target]
        if (!args[0].equalsIgnoreCase("bet")) {
            sendUsage(player);
            return true;
        }

        // SECURITY: Check if player is self-excluded from gambling
        if (plugin.getExclusionManager().isExcluded(player.getUniqueId())) {
            var exclusion = plugin.getExclusionManager().getExclusion(player.getUniqueId());
            if (exclusion != null) {
                MessageBuilder.create()
                        .prefix(messenger.getPrefix())
                        .error("You are currently excluded from gambling.")
                        .newLine()
                        .info("Time remaining: ")
                        .emphasis(exclusion.getFormattedRemaining())
                        .send(player);
                if (exclusion.getReason() != null && !exclusion.getReason().isEmpty()) {
                    MessageBuilder.create()
                            .prefix(messenger.getPrefix())
                            .info("Reason: ")
                            .emphasis(exclusion.getReason())
                            .send(player);
                }
            } else {
                messenger.sendError(player, "You are currently excluded from gambling.");
            }
            return true;
        }

        if (args.length < 3) {
            sendUsage(player);
            return true;
        }

        // Parse bet amount
        int betAmount;
        try {
            betAmount = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            messenger.sendError(player, "Invalid bet amount!");
            return true;
        }

        // SECURITY: Check for potential integer overflow
        if (!net.serverplugins.arcade.games.GameType.isValidBet(betAmount, 36.0)) {
            messenger.sendError(player, "Bet amount too high - risk of overflow!");
            plugin.getLogger()
                    .warning(
                            "[SECURITY] Overflow attempt blocked: player="
                                    + player.getName()
                                    + " bet="
                                    + betAmount);
            return true;
        }

        // Validate bet amount
        if (betAmount < config.getMinBet()) {
            player.sendMessage(
                    config.getMessage("bet_too_low")
                            .replace("%min%", String.valueOf(config.getMinBet())));
            return true;
        }

        if (betAmount > config.getMaxBet()) {
            player.sendMessage(
                    config.getMessage("bet_too_high")
                            .replace("%max%", String.valueOf(config.getMaxBet())));
            return true;
        }

        // Check balance
        if (!ServerArcade.getEconomy().has(player, betAmount)) {
            player.sendMessage(config.getMessage("insufficient_funds"));
            return true;
        }

        // Withdraw bet amount
        ServerArcade.getEconomy().withdrawPlayer(player, betAmount);

        // Parse bet type
        String betType = args[2].toLowerCase();

        switch (betType) {
            case "over":
                if (args.length < 4) {
                    messenger.sendError(player, "Usage: /dice bet <amount> over <number>");
                    refundBet(player, betAmount);
                    return true;
                }
                try {
                    int threshold = Integer.parseInt(args[3]);
                    game.rollOver(player, betAmount, threshold);
                } catch (NumberFormatException e) {
                    messenger.sendError(player, "Invalid number!");
                    refundBet(player, betAmount);
                }
                break;

            case "under":
                if (args.length < 4) {
                    messenger.sendError(player, "Usage: /dice bet <amount> under <number>");
                    refundBet(player, betAmount);
                    return true;
                }
                try {
                    int threshold = Integer.parseInt(args[3]);
                    game.rollUnder(player, betAmount, threshold);
                } catch (NumberFormatException e) {
                    messenger.sendError(player, "Invalid number!");
                    refundBet(player, betAmount);
                }
                break;

            case "exact":
                if (args.length < 4) {
                    messenger.sendError(player, "Usage: /dice bet <amount> exact <number>");
                    refundBet(player, betAmount);
                    return true;
                }
                try {
                    int target = Integer.parseInt(args[3]);
                    game.rollExact(player, betAmount, target);
                } catch (NumberFormatException e) {
                    messenger.sendError(player, "Invalid number!");
                    refundBet(player, betAmount);
                }
                break;

            case "odd":
                game.rollOdd(player, betAmount);
                break;

            case "even":
                game.rollEven(player, betAmount);
                break;

            default:
                messenger.sendError(player, "Invalid bet type!");
                refundBet(player, betAmount);
                break;
        }

        return true;
    }

    private void refundBet(Player player, int amount) {
        ServerArcade.getEconomy().depositPlayer(player, amount);
    }

    private void sendUsage(Player player) {
        PluginMessenger messenger = plugin.getArcadeConfig().getMessenger();
        MessageBuilder.create()
                .prefix(messenger.getPrefix())
                .emphasis("=== Dice Roll ===")
                .newLine()
                .info("Roll a die and bet on the outcome!")
                .newLine()
                .newLine()
                .command("/dice bet <amount> over <number>  ")
                .info("- Roll over N")
                .newLine()
                .command("/dice bet <amount> under <number> ")
                .info("- Roll under N")
                .newLine()
                .command("/dice bet <amount> exact <number> ")
                .info("- Roll exact N")
                .newLine()
                .command("/dice bet <amount> odd           ")
                .info("- Roll odd number")
                .newLine()
                .command("/dice bet <amount> even          ")
                .info("- Roll even number")
                .newLine()
                .command("/dice stats                      ")
                .info("- Show statistics")
                .send(player);
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("bet", "stats"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("bet")) {
            completions.addAll(Arrays.asList("100", "500", "1000", "2500", "5000"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("bet")) {
            completions.addAll(Arrays.asList("over", "under", "exact", "odd", "even"));
        } else if (args.length == 4 && args[0].equalsIgnoreCase("bet")) {
            String betType = args[2].toLowerCase();
            if (betType.equals("over")) {
                completions.addAll(Arrays.asList("1", "2", "3", "4", "5"));
            } else if (betType.equals("under")) {
                completions.addAll(Arrays.asList("2", "3", "4", "5", "6"));
            } else if (betType.equals("exact")) {
                completions.addAll(Arrays.asList("1", "2", "3", "4", "5", "6"));
            }
        }

        return completions;
    }
}
