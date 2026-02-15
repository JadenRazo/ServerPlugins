package net.serverplugins.commands.commands;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.api.ServerAPI;
import net.serverplugins.api.economy.EconomyProvider;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.messages.MessageBuilder;
import net.serverplugins.api.messages.Placeholder;
import net.serverplugins.commands.ServerCommands;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Economy management command for administrators. Usage: /eco give|take|set <player> <amount> /eco
 * balance <player>
 */
public class EcoCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;

    public EcoCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("servercommands.eco")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        EconomyProvider economy = ServerAPI.getInstance().getEconomyProvider();
        if (economy == null || !economy.isAvailable()) {
            plugin.getCommandsConfig()
                    .getMessenger()
                    .sendError(sender, "Economy system is not available!");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "give", "add", "deposit" -> handleGive(sender, args, economy);
            case "take", "remove", "withdraw" -> handleTake(sender, args, economy);
            case "set" -> handleSet(sender, args, economy);
            case "balance", "bal", "check" -> handleBalance(sender, args, economy);
            case "help" -> sendUsage(sender);
            default -> {
                plugin.getCommandsConfig()
                        .getMessenger()
                        .sendError(sender, "Unknown action: " + ColorScheme.HIGHLIGHT + action);
                sendUsage(sender);
            }
        }

        return true;
    }

    private void handleGive(CommandSender sender, String[] args, EconomyProvider economy) {
        if (args.length < 3) {
            CommonMessages.INVALID_USAGE.send(
                    sender, Placeholder.of("usage", "/eco give <player> <amount>"));
            return;
        }

        OfflinePlayer target = getPlayer(args[1]);
        if (target == null) {
            CommonMessages.INVALID_PLAYER.send(sender, Placeholder.of("player", args[1]));
            return;
        }

        double amount = parseAmount(args[2]);
        if (amount <= 0) {
            plugin.getCommandsConfig()
                    .getMessenger()
                    .sendError(sender, "Amount must be a positive number!");
            return;
        }

        if (economy.deposit(target, amount)) {
            String formatted = economy.format(amount);
            plugin.getCommandsConfig()
                    .getMessenger()
                    .sendSuccess(
                            sender,
                            "Gave "
                                    + ColorScheme.EMPHASIS
                                    + formatted
                                    + ColorScheme.SUCCESS
                                    + " to "
                                    + ColorScheme.HIGHLIGHT
                                    + target.getName()
                                    + ColorScheme.SUCCESS
                                    + "!");

            if (target.isOnline() && target.getPlayer() != null) {
                plugin.getCommandsConfig()
                        .getMessenger()
                        .sendSuccess(
                                target.getPlayer(),
                                "You received "
                                        + ColorScheme.EMPHASIS
                                        + formatted
                                        + ColorScheme.SUCCESS
                                        + "!");
            }
        } else {
            plugin.getCommandsConfig()
                    .getMessenger()
                    .sendError(sender, "Failed to give money to " + target.getName() + "!");
        }
    }

    private void handleTake(CommandSender sender, String[] args, EconomyProvider economy) {
        if (args.length < 3) {
            CommonMessages.INVALID_USAGE.send(
                    sender, Placeholder.of("usage", "/eco take <player> <amount>"));
            return;
        }

        OfflinePlayer target = getPlayer(args[1]);
        if (target == null) {
            CommonMessages.INVALID_PLAYER.send(sender, Placeholder.of("player", args[1]));
            return;
        }

        double amount = parseAmount(args[2]);
        if (amount <= 0) {
            plugin.getCommandsConfig()
                    .getMessenger()
                    .sendError(sender, "Amount must be a positive number!");
            return;
        }

        double balance = economy.getBalance(target);
        if (balance < amount) {
            plugin.getCommandsConfig()
                    .getMessenger()
                    .sendError(
                            sender,
                            ColorScheme.HIGHLIGHT
                                    + target.getName()
                                    + ColorScheme.ERROR
                                    + " only has "
                                    + ColorScheme.EMPHASIS
                                    + economy.format(balance)
                                    + ColorScheme.ERROR
                                    + "!");
            return;
        }

        if (economy.withdraw(target, amount)) {
            String formatted = economy.format(amount);
            plugin.getCommandsConfig()
                    .getMessenger()
                    .sendSuccess(
                            sender,
                            "Took "
                                    + ColorScheme.EMPHASIS
                                    + formatted
                                    + ColorScheme.SUCCESS
                                    + " from "
                                    + ColorScheme.HIGHLIGHT
                                    + target.getName()
                                    + ColorScheme.SUCCESS
                                    + "!");

            if (target.isOnline() && target.getPlayer() != null) {
                plugin.getCommandsConfig()
                        .getMessenger()
                        .sendError(
                                target.getPlayer(),
                                ColorScheme.EMPHASIS
                                        + formatted
                                        + ColorScheme.ERROR
                                        + " was taken from your account!");
            }
        } else {
            plugin.getCommandsConfig()
                    .getMessenger()
                    .sendError(sender, "Failed to take money from " + target.getName() + "!");
        }
    }

    private void handleSet(CommandSender sender, String[] args, EconomyProvider economy) {
        if (args.length < 3) {
            CommonMessages.INVALID_USAGE.send(
                    sender, Placeholder.of("usage", "/eco set <player> <amount>"));
            return;
        }

        OfflinePlayer target = getPlayer(args[1]);
        if (target == null) {
            CommonMessages.INVALID_PLAYER.send(sender, Placeholder.of("player", args[1]));
            return;
        }

        double amount = parseAmount(args[2]);
        if (amount < 0) {
            plugin.getCommandsConfig()
                    .getMessenger()
                    .sendError(sender, "Amount cannot be negative!");
            return;
        }

        if (economy.setBalance(target, amount)) {
            String formatted = economy.format(amount);
            plugin.getCommandsConfig()
                    .getMessenger()
                    .sendSuccess(
                            sender,
                            "Set "
                                    + ColorScheme.HIGHLIGHT
                                    + target.getName()
                                    + ColorScheme.SUCCESS
                                    + "'s balance to "
                                    + ColorScheme.EMPHASIS
                                    + formatted
                                    + ColorScheme.SUCCESS
                                    + "!");

            if (target.isOnline() && target.getPlayer() != null) {
                plugin.getCommandsConfig()
                        .getMessenger()
                        .sendRaw(
                                target.getPlayer(),
                                ColorScheme.WARNING
                                        + "Your balance was set to "
                                        + ColorScheme.SUCCESS
                                        + formatted
                                        + ColorScheme.WARNING
                                        + "!");
            }
        } else {
            plugin.getCommandsConfig()
                    .getMessenger()
                    .sendError(sender, "Failed to set balance for " + target.getName() + "!");
        }
    }

    private void handleBalance(CommandSender sender, String[] args, EconomyProvider economy) {
        if (args.length < 2) {
            if (sender instanceof Player player) {
                double balance = economy.getBalance(player);
                plugin.getCommandsConfig()
                        .getMessenger()
                        .sendRaw(
                                sender,
                                ColorScheme.SUCCESS
                                        + "Your balance: "
                                        + ColorScheme.EMPHASIS
                                        + economy.format(balance));
            } else {
                CommonMessages.INVALID_USAGE.send(
                        sender, Placeholder.of("usage", "/eco balance <player>"));
            }
            return;
        }

        OfflinePlayer target = getPlayer(args[1]);
        if (target == null) {
            CommonMessages.INVALID_PLAYER.send(sender, Placeholder.of("player", args[1]));
            return;
        }

        double balance = economy.getBalance(target);
        plugin.getCommandsConfig()
                .getMessenger()
                .sendRaw(
                        sender,
                        ColorScheme.HIGHLIGHT
                                + target.getName()
                                + ColorScheme.SUCCESS
                                + "'s balance: "
                                + ColorScheme.EMPHASIS
                                + economy.format(balance));
    }

    private void sendUsage(CommandSender sender) {
        MessageBuilder.create()
                .text(ColorScheme.EMPHASIS + "<bold>=== Economy Commands ===")
                .newLine()
                .text(
                        ColorScheme.COMMAND
                                + "/eco give <player> <amount> "
                                + ColorScheme.INFO
                                + "- Give money to a player")
                .newLine()
                .text(
                        ColorScheme.COMMAND
                                + "/eco take <player> <amount> "
                                + ColorScheme.INFO
                                + "- Take money from a player")
                .newLine()
                .text(
                        ColorScheme.COMMAND
                                + "/eco set <player> <amount> "
                                + ColorScheme.INFO
                                + "- Set a player's balance")
                .newLine()
                .text(
                        ColorScheme.COMMAND
                                + "/eco balance [player] "
                                + ColorScheme.INFO
                                + "- Check a player's balance")
                .send(sender);
    }

    private OfflinePlayer getPlayer(String name) {
        // Try online player first
        Player online = Bukkit.getPlayer(name);
        if (online != null) return online;

        // Try offline player
        @SuppressWarnings("deprecation")
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() || offline.isOnline() ? offline : null;
    }

    private double parseAmount(String str) {
        try {
            // Support for shorthand like "1k", "1m", "1b"
            String lower = str.toLowerCase();
            double multiplier = 1;

            if (lower.endsWith("k")) {
                multiplier = 1_000;
                lower = lower.substring(0, lower.length() - 1);
            } else if (lower.endsWith("m")) {
                multiplier = 1_000_000;
                lower = lower.substring(0, lower.length() - 1);
            } else if (lower.endsWith("b")) {
                multiplier = 1_000_000_000;
                lower = lower.substring(0, lower.length() - 1);
            }

            return Double.parseDouble(lower) * multiplier;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(List.of("give", "take", "set", "balance", "help"));
        } else if (args.length == 2) {
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        } else if (args.length == 3 && !args[0].equalsIgnoreCase("balance")) {
            completions.addAll(List.of("100", "1000", "10000", "1k", "10k", "100k", "1m"));
        }

        String current = args[args.length - 1].toLowerCase();
        return completions.stream().filter(s -> s.toLowerCase().startsWith(current)).toList();
    }
}
