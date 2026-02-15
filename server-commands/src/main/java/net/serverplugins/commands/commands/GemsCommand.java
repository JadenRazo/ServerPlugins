package net.serverplugins.commands.commands;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.api.ServerAPI;
import net.serverplugins.api.gems.GemsProvider;
import net.serverplugins.api.gems.GemsRepository;
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

public class GemsCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;

    public GemsCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        GemsProvider gems = ServerAPI.getInstance().getGemsProvider();
        if (gems == null) {
            plugin.getCommandsConfig()
                    .getMessenger()
                    .sendError(sender, "Gems system is not available!");
            return true;
        }

        if (args.length == 0
                || args[0].equalsIgnoreCase("balance")
                || args[0].equalsIgnoreCase("bal")) {
            handleBalance(sender, args, gems);
            return true;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "pay", "send", "transfer" -> handlePay(sender, args, gems);
            case "top", "leaderboard" -> handleTop(sender, gems);
            case "give", "add" -> handleGive(sender, args, gems);
            case "take", "remove" -> handleTake(sender, args, gems);
            case "set" -> handleSet(sender, args, gems);
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

    private void handleBalance(CommandSender sender, String[] args, GemsProvider gems) {
        // /gems balance [player] - check another player (if admin or own)
        if (args.length >= 2
                && (args[0].equalsIgnoreCase("balance") || args[0].equalsIgnoreCase("bal"))) {
            if (!sender.hasPermission("servercommands.gems.admin")) {
                CommonMessages.NO_PERMISSION.send(sender);
                return;
            }
            OfflinePlayer target = getPlayer(args[1]);
            if (target == null) {
                CommonMessages.INVALID_PLAYER.send(sender, Placeholder.of("player", args[1]));
                return;
            }
            int balance = gems.getBalance(target);
            plugin.getCommandsConfig()
                    .getMessenger()
                    .sendRaw(
                            sender,
                            ColorScheme.HIGHLIGHT
                                    + target.getName()
                                    + ColorScheme.SUCCESS
                                    + "'s gems: "
                                    + ColorScheme.EMPHASIS
                                    + gems.format(balance));
            return;
        }

        // /gems - show own balance
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return;
        }

        int balance = gems.getBalance(player);
        plugin.getCommandsConfig()
                .getMessenger()
                .sendRaw(
                        sender,
                        ColorScheme.SUCCESS
                                + "Your gems: "
                                + ColorScheme.EMPHASIS
                                + gems.format(balance));
    }

    private void handlePay(CommandSender sender, String[] args, GemsProvider gems) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return;
        }

        if (args.length < 3) {
            CommonMessages.INVALID_USAGE.send(
                    sender, Placeholder.of("usage", "/gems pay <player> <amount>"));
            return;
        }

        OfflinePlayer target = getPlayer(args[1]);
        if (target == null) {
            CommonMessages.INVALID_PLAYER.send(sender, Placeholder.of("player", args[1]));
            return;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            plugin.getCommandsConfig().getMessenger().sendError(sender, "You can't pay yourself!");
            return;
        }

        int amount = parseAmount(args[2]);
        if (amount <= 0) {
            plugin.getCommandsConfig()
                    .getMessenger()
                    .sendError(sender, "Amount must be a positive number!");
            return;
        }

        if (!gems.has(player, amount)) {
            plugin.getCommandsConfig()
                    .getMessenger()
                    .sendError(
                            sender,
                            "You don't have enough gems! Balance: "
                                    + ColorScheme.EMPHASIS
                                    + gems.format(gems.getBalance(player)));
            return;
        }

        if (gems.transfer(player.getUniqueId(), target.getUniqueId(), amount)) {
            String formatted = gems.format(amount);
            plugin.getCommandsConfig()
                    .getMessenger()
                    .sendSuccess(
                            sender,
                            "Sent "
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
                                "Received "
                                        + ColorScheme.EMPHASIS
                                        + formatted
                                        + ColorScheme.SUCCESS
                                        + " from "
                                        + ColorScheme.HIGHLIGHT
                                        + player.getName()
                                        + ColorScheme.SUCCESS
                                        + "!");
            }
        } else {
            plugin.getCommandsConfig().getMessenger().sendError(sender, "Transfer failed!");
        }
    }

    private void handleTop(CommandSender sender, GemsProvider gems) {
        List<GemsRepository.GemsEntry> top = gems.getTopBalances(10);

        MessageBuilder builder =
                MessageBuilder.create()
                        .text(ColorScheme.EMPHASIS + "<bold>=== Gems Leaderboard ===");

        if (top.isEmpty()) {
            builder.newLine().text(ColorScheme.INFO + "No entries yet.");
        } else {
            for (int i = 0; i < top.size(); i++) {
                GemsRepository.GemsEntry entry = top.get(i);
                OfflinePlayer player = Bukkit.getOfflinePlayer(entry.uuid());
                String name = player.getName() != null ? player.getName() : entry.uuid().toString();
                builder.newLine()
                        .text(
                                ColorScheme.EMPHASIS
                                        + "#"
                                        + (i + 1)
                                        + " "
                                        + ColorScheme.HIGHLIGHT
                                        + name
                                        + ColorScheme.INFO
                                        + " - "
                                        + ColorScheme.SUCCESS
                                        + gems.format(entry.balance()));
            }
        }

        builder.send(sender);
    }

    private void handleGive(CommandSender sender, String[] args, GemsProvider gems) {
        if (!sender.hasPermission("servercommands.gems.admin")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return;
        }

        if (args.length < 3) {
            CommonMessages.INVALID_USAGE.send(
                    sender, Placeholder.of("usage", "/gems give <player> <amount>"));
            return;
        }

        OfflinePlayer target = getPlayer(args[1]);
        if (target == null) {
            CommonMessages.INVALID_PLAYER.send(sender, Placeholder.of("player", args[1]));
            return;
        }

        int amount = parseAmount(args[2]);
        if (amount <= 0) {
            plugin.getCommandsConfig()
                    .getMessenger()
                    .sendError(sender, "Amount must be a positive number!");
            return;
        }

        if (gems.deposit(target, amount)) {
            String formatted = gems.format(amount);
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
                    .sendError(sender, "Failed to give gems to " + target.getName() + "!");
        }
    }

    private void handleTake(CommandSender sender, String[] args, GemsProvider gems) {
        if (!sender.hasPermission("servercommands.gems.admin")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return;
        }

        if (args.length < 3) {
            CommonMessages.INVALID_USAGE.send(
                    sender, Placeholder.of("usage", "/gems take <player> <amount>"));
            return;
        }

        OfflinePlayer target = getPlayer(args[1]);
        if (target == null) {
            CommonMessages.INVALID_PLAYER.send(sender, Placeholder.of("player", args[1]));
            return;
        }

        int amount = parseAmount(args[2]);
        if (amount <= 0) {
            plugin.getCommandsConfig()
                    .getMessenger()
                    .sendError(sender, "Amount must be a positive number!");
            return;
        }

        int balance = gems.getBalance(target);
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
                                    + gems.format(balance)
                                    + ColorScheme.ERROR
                                    + "!");
            return;
        }

        if (gems.withdraw(target, amount)) {
            String formatted = gems.format(amount);
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
                                        + " was taken from your gems!");
            }
        } else {
            plugin.getCommandsConfig()
                    .getMessenger()
                    .sendError(sender, "Failed to take gems from " + target.getName() + "!");
        }
    }

    private void handleSet(CommandSender sender, String[] args, GemsProvider gems) {
        if (!sender.hasPermission("servercommands.gems.admin")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return;
        }

        if (args.length < 3) {
            CommonMessages.INVALID_USAGE.send(
                    sender, Placeholder.of("usage", "/gems set <player> <amount>"));
            return;
        }

        OfflinePlayer target = getPlayer(args[1]);
        if (target == null) {
            CommonMessages.INVALID_PLAYER.send(sender, Placeholder.of("player", args[1]));
            return;
        }

        int amount = parseAmount(args[2]);
        if (amount < 0) {
            plugin.getCommandsConfig()
                    .getMessenger()
                    .sendError(sender, "Amount cannot be negative!");
            return;
        }

        if (gems.setBalance(target, amount)) {
            String formatted = gems.format(amount);
            plugin.getCommandsConfig()
                    .getMessenger()
                    .sendSuccess(
                            sender,
                            "Set "
                                    + ColorScheme.HIGHLIGHT
                                    + target.getName()
                                    + ColorScheme.SUCCESS
                                    + "'s gems to "
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
                                        + "Your gems were set to "
                                        + ColorScheme.SUCCESS
                                        + formatted
                                        + ColorScheme.WARNING
                                        + "!");
            }
        } else {
            plugin.getCommandsConfig()
                    .getMessenger()
                    .sendError(sender, "Failed to set gems for " + target.getName() + "!");
        }
    }

    private void sendUsage(CommandSender sender) {
        MessageBuilder builder =
                MessageBuilder.create()
                        .text(ColorScheme.EMPHASIS + "<bold>=== Gems Commands ===")
                        .newLine()
                        .text(
                                ColorScheme.COMMAND
                                        + "/gems "
                                        + ColorScheme.INFO
                                        + "- View your gem balance")
                        .newLine()
                        .text(
                                ColorScheme.COMMAND
                                        + "/gems pay <player> <amount> "
                                        + ColorScheme.INFO
                                        + "- Send gems to a player")
                        .newLine()
                        .text(
                                ColorScheme.COMMAND
                                        + "/gems top "
                                        + ColorScheme.INFO
                                        + "- View the gems leaderboard");

        if (sender.hasPermission("servercommands.gems.admin")) {
            builder.newLine()
                    .text(
                            ColorScheme.COMMAND
                                    + "/gems give <player> <amount> "
                                    + ColorScheme.INFO
                                    + "- Give gems to a player")
                    .newLine()
                    .text(
                            ColorScheme.COMMAND
                                    + "/gems take <player> <amount> "
                                    + ColorScheme.INFO
                                    + "- Take gems from a player")
                    .newLine()
                    .text(
                            ColorScheme.COMMAND
                                    + "/gems set <player> <amount> "
                                    + ColorScheme.INFO
                                    + "- Set a player's gems");
        }

        builder.send(sender);
    }

    private OfflinePlayer getPlayer(String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null) return online;

        @SuppressWarnings("deprecation")
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() || offline.isOnline() ? offline : null;
    }

    private int parseAmount(String str) {
        try {
            String lower = str.toLowerCase();
            double multiplier = 1;

            if (lower.endsWith("k")) {
                multiplier = 1_000;
                lower = lower.substring(0, lower.length() - 1);
            } else if (lower.endsWith("m")) {
                multiplier = 1_000_000;
                lower = lower.substring(0, lower.length() - 1);
            }

            return (int) (Double.parseDouble(lower) * multiplier);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(List.of("balance", "pay", "top", "help"));
            if (sender.hasPermission("servercommands.gems.admin")) {
                completions.addAll(List.of("give", "take", "set"));
            }
        } else if (args.length == 2) {
            String action = args[0].toLowerCase();
            if (List.of("pay", "give", "take", "set", "balance", "bal").contains(action)) {
                Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
            }
        } else if (args.length == 3) {
            String action = args[0].toLowerCase();
            if (List.of("pay", "give", "take", "set").contains(action)) {
                completions.addAll(List.of("1", "10", "50", "100", "500", "1k", "10k"));
            }
        }

        String current = args[args.length - 1].toLowerCase();
        return completions.stream().filter(s -> s.toLowerCase().startsWith(current)).toList();
    }
}
