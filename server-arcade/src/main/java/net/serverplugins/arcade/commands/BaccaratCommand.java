package net.serverplugins.arcade.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.games.GameResult;
import net.serverplugins.arcade.games.baccarat.BaccaratGame;
import net.serverplugins.arcade.games.baccarat.BaccaratManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class BaccaratCommand implements CommandExecutor, TabCompleter {

    private final ServerArcade plugin;

    public BaccaratCommand(ServerArcade plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            TextUtil.send(sender, plugin.getArcadeConfig().getMessage("players-only"));
            return true;
        }

        if (!player.hasPermission("serverarcade.command.baccarat")
                && !player.hasPermission("serverarcade.admin")) {
            TextUtil.send(player, plugin.getArcadeConfig().getMessage("command-no-permission"));
            return true;
        }

        if (!plugin.getArcadeConfig().isBaccaratEnabled()) {
            TextUtil.send(
                    player,
                    plugin.getArcadeConfig()
                            .getMessage("game-disabled")
                            .replace("${game}", "Baccarat"));
            return true;
        }

        // Check self-exclusion
        if (plugin.getExclusionManager() != null
                && plugin.getExclusionManager().isExcluded(player.getUniqueId())) {
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

        if (args.length < 2) {
            TextUtil.send(
                    player,
                    plugin.getArcadeConfig()
                            .getMessage("invalid-usage")
                            .replace("${usage}", "/baccarat <bet> <player|banker|tie>"));
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

        BaccaratGame.BetSide betSide;
        switch (args[1].toLowerCase()) {
            case "player", "p" -> betSide = BaccaratGame.BetSide.PLAYER;
            case "banker", "b", "bank" -> betSide = BaccaratGame.BetSide.BANKER;
            case "tie", "t", "draw" -> betSide = BaccaratGame.BetSide.TIE;
            default -> {
                TextUtil.send(player, plugin.getArcadeConfig().getMessage("baccarat-invalid-side"));
                return true;
            }
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

        BaccaratManager manager = plugin.getBaccaratManager();
        BaccaratGame game = manager.playGame(player.getUniqueId(), bet, betSide);

        showGameResult(player, game);
        handleResult(player, game);

        return true;
    }

    private void showGameResult(Player player, BaccaratGame game) {
        TextUtil.send(player, "");
        TextUtil.send(player, "<gold>===== BACCARAT =====</gold>");
        TextUtil.send(
                player,
                "<gray>Your bet: <white>"
                        + game.getBetSide().name()
                        + " <dark_gray>($"
                        + String.format("%.0f", game.getBet())
                        + ")");
        TextUtil.send(player, "");

        // Show Player hand
        StringBuilder playerHand = new StringBuilder("<white>Player: ");
        for (BaccaratGame.Card card : game.getPlayerHand()) {
            playerHand.append(card.toString()).append(" ");
        }
        playerHand.append("<dark_gray>(").append(game.getPlayerValue()).append(")");
        TextUtil.send(player, playerHand.toString());

        // Show Banker hand
        StringBuilder bankerHand = new StringBuilder("<gray>Banker: ");
        for (BaccaratGame.Card card : game.getBankerHand()) {
            bankerHand.append(card.toString()).append(" ");
        }
        bankerHand.append("<dark_gray>(").append(game.getBankerValue()).append(")");
        TextUtil.send(player, bankerHand.toString());
    }

    private void handleResult(Player player, BaccaratGame game) {
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
            // Push - bet returned
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
        if (args.length == 2) {
            List<String> options = List.of("player", "banker", "tie");
            return options.stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
