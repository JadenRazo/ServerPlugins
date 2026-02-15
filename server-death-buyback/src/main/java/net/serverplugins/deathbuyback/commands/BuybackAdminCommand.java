package net.serverplugins.deathbuyback.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.deathbuyback.ServerDeathBuyback;
import net.serverplugins.deathbuyback.models.DeathInventory;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BuybackAdminCommand implements CommandExecutor, TabCompleter {

    private final ServerDeathBuyback plugin;

    public BuybackAdminCommand(ServerDeathBuyback plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (!sender.hasPermission("deathbuyback.admin")) {
            TextUtil.send(sender, "<red>You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload" -> handleReload(sender);
            case "clear" -> handleClear(sender, args);
            case "info" -> handleInfo(sender, args);
            case "cleanup" -> handleCleanup(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        TextUtil.send(sender, "<gradient:#e74c3c:#9b59b6>Death Buyback Admin Commands");
        TextUtil.send(sender, "<gray>/buybackadmin reload <white>- Reload configuration");
        TextUtil.send(sender, "<gray>/buybackadmin clear <player> <white>- Clear player's deaths");
        TextUtil.send(sender, "<gray>/buybackadmin info <player> <white>- View player's deaths");
        TextUtil.send(sender, "<gray>/buybackadmin cleanup <white>- Run expiration cleanup");
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfiguration();
        TextUtil.send(sender, "<green>Configuration reloaded.");
    }

    private void handleClear(CommandSender sender, String[] args) {
        if (args.length < 2) {
            TextUtil.send(sender, "<red>Usage: /buybackadmin clear <player>");
            return;
        }

        String playerName = args[1];
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            TextUtil.send(sender, "<red>Player not found: " + playerName);
            return;
        }

        int deleted = plugin.getRepository().deletePlayerInventories(target.getUniqueId());
        TextUtil.send(sender, "<green>Cleared " + deleted + " death inventories for " + playerName);
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            TextUtil.send(sender, "<red>Usage: /buybackadmin info <player>");
            return;
        }

        String playerName = args[1];
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            TextUtil.send(sender, "<red>Player not found: " + playerName);
            return;
        }

        List<DeathInventory> deaths =
                plugin.getDeathInventoryManager().getActiveInventories(target.getUniqueId());

        if (deaths.isEmpty()) {
            TextUtil.send(sender, "<gray>" + playerName + " has no stored death inventories.");
            return;
        }

        TextUtil.send(sender, "<gradient:#e74c3c:#9b59b6>" + playerName + "'s Death Inventories:");
        TextUtil.send(sender, "");

        for (int i = 0; i < deaths.size(); i++) {
            DeathInventory death = deaths.get(i);
            String price = plugin.getPricingManager().formatPrice(death.getBuybackPrice());

            TextUtil.send(sender, "<gold>#" + (i + 1) + " <gray>- " + death.getFormattedLocation());
            TextUtil.send(
                    sender,
                    "    <gray>Items: <white>"
                            + death.getItemCount()
                            + " <gray>| Price: <yellow>"
                            + price
                            + " <gray>| Expires: <white>"
                            + death.getTimeUntilExpiry());
        }
    }

    private void handleCleanup(CommandSender sender) {
        int deleted = plugin.getRepository().deleteExpiredInventories();
        TextUtil.send(sender, "<green>Deleted " + deleted + " expired death inventories.");
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        if (!sender.hasPermission("deathbuyback.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            return Arrays.asList("reload", "clear", "info", "cleanup").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2
                && (args[0].equalsIgnoreCase("clear") || args[0].equalsIgnoreCase("info"))) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
