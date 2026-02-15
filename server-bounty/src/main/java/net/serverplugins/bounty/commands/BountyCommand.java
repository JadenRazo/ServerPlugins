package net.serverplugins.bounty.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.messages.MessageBuilder;
import net.serverplugins.api.messages.Placeholder;
import net.serverplugins.bounty.ServerBounty;
import net.serverplugins.bounty.gui.BountyBoardGui;
import net.serverplugins.bounty.gui.HeadCollectionGui;
import net.serverplugins.bounty.managers.BountyManager.PlacementResult;
import net.serverplugins.bounty.models.Bounty;
import net.serverplugins.bounty.models.Contribution;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class BountyCommand implements CommandExecutor, TabCompleter {

    private final ServerBounty plugin;
    private static final List<String> SUBCOMMANDS =
            Arrays.asList("list", "top", "check", "heads", "reload");

    public BountyCommand(ServerBounty plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (args.length == 0) {
            new BountyBoardGui(plugin, player).open(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "list" -> new BountyBoardGui(plugin, player).open(player);

            case "top" -> showTopBounties(player);

            case "check" -> {
                if (args.length < 2) {
                    checkBounty(player, player.getUniqueId(), player.getName());
                } else {
                    OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
                    if (target == null) {
                        Player online = Bukkit.getPlayerExact(args[1]);
                        if (online != null) {
                            target = online;
                        }
                    }
                    if (target == null) {
                        CommonMessages.PLAYER_NOT_FOUND.send(player);
                        return true;
                    }
                    checkBounty(player, target.getUniqueId(), target.getName());
                }
            }

            case "heads" -> new HeadCollectionGui(plugin, player, null).open(player);

            case "reload" -> {
                if (!player.hasPermission("serverbounty.admin")) {
                    CommonMessages.NO_PERMISSION.send(player);
                    return true;
                }
                plugin.reloadConfiguration();
                plugin.getBountyConfig()
                        .getMessenger()
                        .sendSuccess(player, "Bounty configuration reloaded!");
            }

            default -> {
                if (args.length < 2) {
                    sendUsage(player);
                    return true;
                }

                String targetName = args[0];
                double amount;
                try {
                    amount = Double.parseDouble(args[1]);
                } catch (NumberFormatException e) {
                    plugin.getBountyConfig().getMessenger().send(player, "invalid-amount");
                    return true;
                }

                OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(targetName);
                if (target == null) {
                    Player online = Bukkit.getPlayerExact(targetName);
                    if (online != null) {
                        target = online;
                    }
                }
                if (target == null) {
                    CommonMessages.PLAYER_NOT_FOUND.send(player);
                    return true;
                }

                PlacementResult result =
                        plugin.getBountyManager().placeBounty(player, target, amount);
                if (result.isSuccess()) {
                    plugin.getBountyConfig()
                            .getMessenger()
                            .send(
                                    player,
                                    "bounty-placed",
                                    Placeholder.of(
                                            "target",
                                            target.getName() != null
                                                    ? target.getName()
                                                    : "Unknown"),
                                    Placeholder.of("amount", plugin.formatCurrency(amount)),
                                    Placeholder.of(
                                            "total",
                                            plugin.formatCurrency(
                                                    result.getBounty().getTotalAmount())));
                } else {
                    String errorKey = result.getErrorKey();
                    List<Placeholder> placeholders = new ArrayList<>();
                    result.getReplacements()
                            .forEach((key, value) -> placeholders.add(Placeholder.of(key, value)));
                    plugin.getBountyConfig()
                            .getMessenger()
                            .send(player, errorKey, placeholders.toArray(new Placeholder[0]));
                }
            }
        }
        return true;
    }

    private void showTopBounties(Player player) {
        List<Bounty> top = plugin.getRepository().getTopBounties(10);
        if (top.isEmpty()) {
            plugin.getBountyConfig().getMessenger().send(player, "no-active-bounties");
            return;
        }

        MessageBuilder message = MessageBuilder.create().emphasis("Top 10 Most Wanted:").newLine();

        int rank = 1;
        for (Bounty bounty : top) {
            message.warning(rank + ". ")
                    .highlight(bounty.getTargetName())
                    .info(" - ")
                    .success(plugin.formatCurrency(bounty.getTotalAmount()))
                    .newLine();
            rank++;
        }

        message.send(player);
    }

    private void checkBounty(Player player, UUID targetUuid, String targetName) {
        Bounty bounty = plugin.getRepository().getActiveBounty(targetUuid);
        if (bounty == null) {
            plugin.getBountyConfig()
                    .getMessenger()
                    .send(player, "no-bounty-on-player", Placeholder.of("player", targetName));
            return;
        }

        MessageBuilder message =
                MessageBuilder.create()
                        .emphasis("Bounty on " + targetName + ":")
                        .newLine()
                        .arrow()
                        .info("Total: ")
                        .success(plugin.formatCurrency(bounty.getTotalAmount()))
                        .newLine();

        List<Contribution> contributions = plugin.getRepository().getContributions(bounty.getId());
        message.arrow()
                .info("Contributors: ")
                .highlight(String.valueOf(contributions.size()))
                .newLine();

        contributions.stream()
                .sorted((a, b) -> Double.compare(b.getAmount(), a.getAmount()))
                .limit(3)
                .forEach(
                        c -> {
                            message.text("  ")
                                    .warning(c.getContributorName())
                                    .info(": ")
                                    .success(plugin.formatCurrency(c.getAmount()))
                                    .newLine();
                        });

        message.send(player);
    }

    private void sendUsage(Player player) {
        MessageBuilder.create()
                .emphasis("Bounty Commands:")
                .newLine()
                .command("/bounty <player> <amount>")
                .info(" - Place a bounty")
                .newLine()
                .command("/bounty list")
                .info(" - View bounty board")
                .newLine()
                .command("/bounty top")
                .info(" - Most wanted list")
                .newLine()
                .command("/bounty check [player]")
                .info(" - Check bounty")
                .newLine()
                .command("/bounty heads")
                .info(" - Collect trophy heads")
                .send(player);
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>(SUBCOMMANDS);
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));

            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();

            if (sub.equals("check")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
            }

            if (!SUBCOMMANDS.contains(sub)) {
                return Collections.emptyList();
            }
        }

        return Collections.emptyList();
    }
}
