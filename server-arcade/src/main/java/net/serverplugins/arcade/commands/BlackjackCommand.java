package net.serverplugins.arcade.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.games.GameResult;
import net.serverplugins.arcade.games.blackjack.BlackjackGame;
import net.serverplugins.arcade.games.blackjack.BlackjackManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class BlackjackCommand implements CommandExecutor, TabCompleter {

    private final ServerArcade plugin;

    public BlackjackCommand(ServerArcade plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            TextUtil.send(sender, plugin.getArcadeConfig().getMessage("players-only"));
            return true;
        }

        if (!player.hasPermission("serverarcade.command.blackjack")
                && !player.hasPermission("serverarcade.admin")) {
            TextUtil.send(player, plugin.getArcadeConfig().getMessage("command-no-permission"));
            return true;
        }

        if (!plugin.getArcadeConfig().isBlackjackEnabled()) {
            TextUtil.send(
                    player,
                    plugin.getArcadeConfig()
                            .getMessage("game-disabled")
                            .replace("${game}", "Blackjack"));
            return true;
        }

        // SECURITY: Check if player is self-excluded from gambling
        if (plugin.getExclusionManager().isExcluded(player.getUniqueId())) {
            var exclusion = plugin.getExclusionManager().getExclusion(player.getUniqueId());
            TextUtil.send(player, plugin.getArcadeConfig().getMessage("excluded"));
            if (exclusion != null) {
                TextUtil.send(
                        player,
                        plugin.getArcadeConfig()
                                .getMessage("excluded-time")
                                .replace("${time}", exclusion.getFormattedRemaining()));
                if (exclusion.getReason() != null && !exclusion.getReason().isEmpty()) {
                    TextUtil.send(
                            player,
                            plugin.getArcadeConfig()
                                    .getMessage("excluded-reason")
                                    .replace("${reason}", exclusion.getReason()));
                }
            }
            return true;
        }

        BlackjackManager manager = plugin.getBlackjackManager();

        if (manager.hasActiveGame(player.getUniqueId())) {
            return handleActiveGame(player, args);
        }

        if (args.length < 1) {
            TextUtil.send(
                    player,
                    plugin.getArcadeConfig()
                            .getMessage("invalid-usage")
                            .replace("${usage}", "/blackjack <bet>"));
            return true;
        }

        double bet;
        try {
            bet = Double.parseDouble(args[0]);
        } catch (NumberFormatException e) {
            TextUtil.send(player, plugin.getArcadeConfig().getMessage("invalid-bet"));
            return true;
        }

        double minBet = plugin.getArcadeConfig().getMinBet();
        double maxBet = plugin.getArcadeConfig().getMaxBet();

        if (bet < minBet || bet > maxBet) {
            TextUtil.send(
                    player,
                    plugin.getArcadeConfig()
                            .getMessage("invalid-bet-range")
                            .replace("${min}", String.format("%.0f", minBet))
                            .replace("${max}", String.format("%.0f", maxBet)));
            return true;
        }

        if (ServerArcade.getEconomy() == null || !ServerArcade.getEconomy().has(player, bet)) {
            TextUtil.send(
                    player,
                    plugin.getArcadeConfig()
                            .getMessage("insufficient-funds")
                            .replace("${amount}", String.format("%.0f", bet)));
            return true;
        }

        ServerArcade.getEconomy().withdrawPlayer(player, bet);

        BlackjackGame game = manager.startGame(player.getUniqueId(), bet);
        showGameState(player, game);

        if (game.getResult() != null) {
            handleResult(player, game);
        }

        return true;
    }

    private boolean handleActiveGame(Player player, String[] args) {
        BlackjackManager manager = plugin.getBlackjackManager();

        if (args.length < 1) {
            TextUtil.send(
                    player,
                    plugin.getArcadeConfig()
                            .getMessage("active-game")
                            .replace(
                                    "${actions}",
                                    plugin.getArcadeConfig().getMessage("blackjack-actions")));
            showGameState(player, manager.getGame(player.getUniqueId()));
            return true;
        }

        String action = args[0].toLowerCase();
        BlackjackGame game;

        switch (action) {
            case "hit" -> game = manager.hit(player.getUniqueId());
            case "stand" -> game = manager.stand(player.getUniqueId());
            case "double" -> {
                if (!manager.canDoubleDown(player.getUniqueId())) {
                    TextUtil.send(
                            player,
                            plugin.getArcadeConfig().getMessage("blackjack-double-restricted"));
                    return true;
                }
                BlackjackGame current = manager.getGame(player.getUniqueId());
                if (!ServerArcade.getEconomy().has(player, current.getBet())) {
                    TextUtil.send(
                            player,
                            plugin.getArcadeConfig().getMessage("blackjack-insufficient-double"));
                    return true;
                }
                ServerArcade.getEconomy().withdrawPlayer(player, current.getBet());
                game = manager.doubleDown(player.getUniqueId());
            }
            case "split" -> {
                if (!manager.canSplit(player.getUniqueId())) {
                    TextUtil.send(
                            player,
                            plugin.getArcadeConfig().getMessage("blackjack-split-restricted"));
                    return true;
                }
                BlackjackGame current = manager.getGame(player.getUniqueId());
                if (!ServerArcade.getEconomy().has(player, current.getBet())) {
                    TextUtil.send(
                            player,
                            plugin.getArcadeConfig().getMessage("blackjack-insufficient-split"));
                    return true;
                }
                ServerArcade.getEconomy().withdrawPlayer(player, current.getBet());
                game = manager.split(player.getUniqueId());
                TextUtil.send(
                        player, plugin.getArcadeConfig().getMessage("blackjack-split-success"));
            }
            case "insurance" -> {
                if (!manager.canInsurance(player.getUniqueId())) {
                    TextUtil.send(
                            player,
                            plugin.getArcadeConfig().getMessage("blackjack-insurance-restricted"));
                    return true;
                }
                BlackjackGame current = manager.getGame(player.getUniqueId());
                double insuranceCost = current.getBet() / 2;
                if (!ServerArcade.getEconomy().has(player, insuranceCost)) {
                    TextUtil.send(
                            player,
                            plugin.getArcadeConfig()
                                    .getMessage("blackjack-insufficient-insurance")
                                    .replace("${amount}", String.format("$%.0f", insuranceCost)));
                    return true;
                }
                ServerArcade.getEconomy().withdrawPlayer(player, insuranceCost);
                game = manager.takeInsurance(player.getUniqueId());
                TextUtil.send(
                        player,
                        plugin.getArcadeConfig()
                                .getMessage("blackjack-insurance-taken")
                                .replace("${amount}", String.format("$%.0f", insuranceCost)));
            }
            case "surrender" -> {
                if (!manager.canSurrender(player.getUniqueId())) {
                    TextUtil.send(
                            player,
                            plugin.getArcadeConfig().getMessage("blackjack-surrender-restricted"));
                    return true;
                }
                game = manager.surrender(player.getUniqueId());
            }
            case "cancel" -> {
                manager.cancelGame(player.getUniqueId());
                TextUtil.send(player, plugin.getArcadeConfig().getMessage("blackjack-cancelled"));
                return true;
            }
            default -> {
                TextUtil.send(
                        player, plugin.getArcadeConfig().getMessage("blackjack-unknown-action"));
                return true;
            }
        }

        if (game != null) {
            showGameState(player, game);
            if (game.getResult() != null) {
                handleResult(player, game);
            }
        }

        return true;
    }

    private void showGameState(Player player, BlackjackGame game) {
        TextUtil.send(player, "");
        TextUtil.send(player, "<gold>===== BLACKJACK =====</gold>");

        StringBuilder dealerHand = new StringBuilder("<gray>Dealer: ");
        if (game.isDealerCardHidden() && game.getResult() == null) {
            dealerHand.append(game.getDealerHand().get(0).toString()).append(" [?]");
            dealerHand.append(" <dark_gray>(").append(game.getDealerVisibleValue()).append(")");
        } else {
            for (BlackjackGame.Card card : game.getDealerHand()) {
                dealerHand.append(card.toString()).append(" ");
            }
            dealerHand.append("<dark_gray>(").append(game.getDealerValue()).append(")");
        }
        TextUtil.send(player, dealerHand.toString());

        StringBuilder playerHand = new StringBuilder("<white>You: ");
        for (BlackjackGame.Card card : game.getPlayerHand()) {
            playerHand.append(card.toString()).append(" ");
        }
        playerHand.append("<dark_gray>(").append(game.getPlayerValue()).append(")");
        TextUtil.send(player, playerHand.toString());

        if (game.getResult() == null) {
            TextUtil.send(player, "");
            // Show available actions based on game state
            StringBuilder actions =
                    new StringBuilder("<yellow>Actions: <green>hit</green> | <green>stand</green>");
            BlackjackManager manager = plugin.getBlackjackManager();
            if (manager.canDoubleDown(player.getUniqueId())) {
                actions.append(" | <green>double</green>");
            }
            if (manager.canSplit(player.getUniqueId())) {
                actions.append(" | <green>split</green>");
            }
            if (manager.canInsurance(player.getUniqueId())) {
                actions.append(" | <green>insurance</green>");
            }
            if (manager.canSurrender(player.getUniqueId())) {
                actions.append(" | <green>surrender</green>");
            }
            TextUtil.send(player, actions.toString());

            // Show split hand if player has split
            if (game.hasSplit() && game.getSplitHand() != null) {
                StringBuilder splitHand = new StringBuilder("<aqua>Split Hand: ");
                for (BlackjackGame.Card card : game.getSplitHand()) {
                    splitHand.append(card.toString()).append(" ");
                }
                splitHand.append("<dark_gray>(").append(game.getSplitHandValue()).append(")");
                TextUtil.send(player, splitHand.toString());
            }
        }
    }

    private void handleResult(Player player, BlackjackGame game) {
        GameResult result = game.getResult();

        TextUtil.send(player, "");
        if (result.won()) {
            ServerArcade.getEconomy().depositPlayer(player, result.payout());
            TextUtil.send(
                    player,
                    "<green>"
                            + result.message()
                            + " You won $"
                            + String.format("%.0f", result.payout())
                            + "!");
        } else if (result.payout() > 0) {
            ServerArcade.getEconomy().depositPlayer(player, result.payout());
            TextUtil.send(player, "<yellow>" + result.message());
        } else {
            TextUtil.send(
                    player,
                    "<red>"
                            + result.message()
                            + " You lost $"
                            + String.format("%.0f", result.bet())
                            + "!");
        }
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return new ArrayList<>();

        if (args.length == 1 && plugin.getBlackjackManager().hasActiveGame(player.getUniqueId())) {
            List<String> options = new ArrayList<>();
            options.add("hit");
            options.add("stand");

            BlackjackManager manager = plugin.getBlackjackManager();
            if (manager.canDoubleDown(player.getUniqueId())) options.add("double");
            if (manager.canSplit(player.getUniqueId())) options.add("split");
            if (manager.canInsurance(player.getUniqueId())) options.add("insurance");
            if (manager.canSurrender(player.getUniqueId())) options.add("surrender");
            options.add("cancel");

            return options.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
