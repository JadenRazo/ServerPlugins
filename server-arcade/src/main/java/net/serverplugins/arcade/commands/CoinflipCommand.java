package net.serverplugins.arcade.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.games.coinflip.CoinflipManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class CoinflipCommand implements CommandExecutor, TabCompleter {

    private final ServerArcade plugin;

    public CoinflipCommand(ServerArcade plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            TextUtil.send(sender, plugin.getArcadeConfig().getMessage("players-only"));
            return true;
        }

        if (!player.hasPermission("serverarcade.command.coinflip")
                && !player.hasPermission("serverarcade.admin")) {
            TextUtil.send(player, plugin.getArcadeConfig().getMessage("command-no-permission"));
            return true;
        }

        if (!plugin.getArcadeConfig().isCoinflipEnabled()) {
            TextUtil.send(
                    player,
                    plugin.getArcadeConfig()
                            .getMessage("game-disabled")
                            .replace("${game}", "Coinflip"));
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

        CoinflipManager manager = plugin.getCoinflipManager();

        if (args.length == 0) {
            showPendingGames(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("cancel")) {
            if (manager.cancelGame(player.getUniqueId())) {
                TextUtil.send(player, plugin.getArcadeConfig().getMessage("coinflip-cancelled"));
            } else {
                TextUtil.send(player, plugin.getArcadeConfig().getMessage("coinflip-no-active"));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("accept") && args.length >= 2) {
            Player creator = Bukkit.getPlayer(args[1]);
            if (creator == null) {
                TextUtil.send(
                        player, plugin.getArcadeConfig().getMessage("coinflip-player-not-found"));
                return true;
            }

            CoinflipManager.CoinflipGame game = manager.getGame(creator.getUniqueId());
            if (game == null) {
                TextUtil.send(player, plugin.getArcadeConfig().getMessage("coinflip-no-game"));
                return true;
            }

            if (ServerArcade.getEconomy() == null
                    || !ServerArcade.getEconomy().has(player, game.getAmount())) {
                TextUtil.send(
                        player,
                        plugin.getArcadeConfig()
                                .getMessage("insufficient-funds")
                                .replace("${amount}", String.format("%.0f", game.getAmount())));
                return true;
            }

            ServerArcade.getEconomy().withdrawPlayer(player, game.getAmount());

            CoinflipManager.CoinflipResult result =
                    manager.acceptGame(player.getUniqueId(), creator.getUniqueId());
            if (result == null) {
                ServerArcade.getEconomy().depositPlayer(player, game.getAmount());
                TextUtil.send(
                        player, plugin.getArcadeConfig().getMessage("coinflip-accept-failed"));
                return true;
            }

            Player winner = Bukkit.getPlayer(result.winnerId());
            Player loser = Bukkit.getPlayer(result.loserId());

            if (winner != null) {
                ServerArcade.getEconomy().depositPlayer(winner, result.pot());
                TextUtil.send(
                        winner,
                        plugin.getArcadeConfig()
                                .getMessage("coinflip-win")
                                .replace("${result}", result.result())
                                .replace("${amount}", String.format("$%.0f", result.pot())));
            }

            if (loser != null) {
                TextUtil.send(
                        loser,
                        plugin.getArcadeConfig()
                                .getMessage("coinflip-lose")
                                .replace(
                                        "${result}",
                                        result.result().equals("Heads") ? "Tails" : "Heads"));
            }

            return true;
        }

        double bet;
        try {
            bet = Double.parseDouble(args[0]);
        } catch (NumberFormatException e) {
            TextUtil.send(
                    player,
                    plugin.getArcadeConfig()
                            .getMessage("invalid-usage")
                            .replace("${usage}", "/coinflip <bet> or /coinflip accept <player>"));
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

        if (manager.hasGame(player.getUniqueId())) {
            TextUtil.send(
                    player,
                    plugin.getArcadeConfig()
                            .getMessage("already-active")
                            .replace("${game}", "coinflip")
                            .replace("${cancel_command}", "/coinflip cancel"));
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

        CoinflipManager.CoinflipGame game = manager.createGame(player.getUniqueId(), bet);
        if (game == null) {
            ServerArcade.getEconomy().depositPlayer(player, bet);
            TextUtil.send(player, plugin.getArcadeConfig().getMessage("coinflip-accept-failed"));
            return true;
        }

        TextUtil.send(
                player,
                plugin.getArcadeConfig()
                        .getMessage("coinflip-created")
                        .replace("${amount}", String.format("$%.0f", bet)));
        TextUtil.send(player, plugin.getArcadeConfig().getMessage("coinflip-waiting"));

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player)) {
                TextUtil.send(
                        online,
                        plugin.getArcadeConfig()
                                .getMessage("coinflip-broadcast")
                                .replace("${player}", player.getName())
                                .replace("${amount}", String.format("$%.0f", bet)));
            }
        }

        return true;
    }

    private void showPendingGames(Player player) {
        CoinflipManager manager = plugin.getCoinflipManager();
        var games = manager.getPendingGames();

        TextUtil.send(player, "");
        TextUtil.send(player, "<gold>=== Active Coinflips ===</gold>");

        if (games.isEmpty()) {
            TextUtil.send(player, plugin.getArcadeConfig().getMessage("coinflip-no-pending"));
        } else {
            for (var game : games) {
                Player creator = Bukkit.getPlayer(game.getCreatorId());
                String creatorName = creator != null ? creator.getName() : "Unknown";
                TextUtil.send(
                        player,
                        String.format(
                                "<yellow>%s</yellow> <gray>-</gray> <gold>$%.0f</gold> "
                                        + "<click:run_command:'/coinflip accept %s'><green>[Accept]</green></click>",
                                creatorName, game.getAmount(), creatorName));
            }
        }
        TextUtil.send(player, "");
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("accept");
            options.add("cancel");
            options.add("100");
            options.add("500");
            options.add("1000");
            return options.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("accept")) {
            return plugin.getCoinflipManager().getPendingGames().stream()
                    .map(game -> Bukkit.getPlayer(game.getCreatorId()))
                    .filter(p -> p != null)
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
