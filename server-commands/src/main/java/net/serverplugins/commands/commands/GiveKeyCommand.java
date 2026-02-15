package net.serverplugins.commands.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.ServerCommands;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Command for administrators to give crate keys to players. Works with ExcellentCrates plugin via
 * console command. Usage: /givekey <player> <crate> [amount]
 */
public class GiveKeyCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;

    // Available crate types (loaded from ExcellentCrates configuration)
    private static final Set<String> CRATE_TYPES = Set.of("daily", "balanced", "diversity", "epic");

    public GiveKeyCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("servercommands.givekey")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        // Parse arguments
        String playerName = args[0];
        String crateType = args[1].toLowerCase();
        int amount = 1;

        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
                if (amount < 1) amount = 1;
                if (amount > 64) amount = 64;
            } catch (NumberFormatException e) {
                TextUtil.sendError(sender, "Amount must be a number between 1 and 64!");
                return true;
            }
        }

        // Validate player
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            TextUtil.sendError(sender, "Player not found: " + playerName);
            return true;
        }

        // Validate crate type
        if (!CRATE_TYPES.contains(crateType)) {
            TextUtil.sendError(sender, "Unknown crate type: " + crateType);
            TextUtil.send(
                    sender, "<yellow>Available crates: <white>" + String.join(", ", CRATE_TYPES));
            return true;
        }

        // Give the key via ExcellentCrates command
        boolean success = giveKey(target, crateType, amount);

        if (success) {
            String keyName = formatCrateName(crateType);
            String amountStr = amount > 1 ? amount + "x " : "";

            TextUtil.sendSuccess(
                    sender,
                    "Gave <yellow>"
                            + amountStr
                            + keyName
                            + " Key<green> to <yellow>"
                            + target.getName()
                            + "<green>!");
            TextUtil.sendSuccess(
                    target, "You received <yellow>" + amountStr + keyName + " Key<green>!");
        } else {
            TextUtil.sendError(sender, "Failed to give key. Is ExcellentCrates installed?");
        }

        return true;
    }

    /** Give a crate key to a player using ExcellentCrates console command. */
    private boolean giveKey(Player player, String crateType, int amount) {
        // Check if ExcellentCrates is available
        if (Bukkit.getPluginManager().getPlugin("ExcellentCrates") == null) {
            return false;
        }

        try {
            // ExcellentCrates command format: /crate key give <player> <key> [amount]
            String command = "crate key give " + player.getName() + " " + crateType + " " + amount;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to give crate key: " + e.getMessage());
            return false;
        }
    }

    private String formatCrateName(String crateType) {
        return switch (crateType) {
            case "daily" -> "Daily";
            case "balanced" -> "Balanced";
            case "diversity" -> "Diversity";
            case "epic" -> "Epic";
            default -> crateType.substring(0, 1).toUpperCase() + crateType.substring(1);
        };
    }

    private void sendUsage(CommandSender sender) {
        TextUtil.send(sender, "<gold><bold>=== Give Key Command ===");
        TextUtil.send(sender, "<yellow>Usage: <white>/givekey <player> <crate> [amount]");
        TextUtil.send(sender, "");
        TextUtil.send(sender, "<gold>Available Crates:");
        TextUtil.send(sender, "<yellow>- daily <gray>- Daily crate key");
        TextUtil.send(sender, "<yellow>- balanced <gray>- Balanced crate key");
        TextUtil.send(sender, "<yellow>- diversity <gray>- Diversity crate key");
        TextUtil.send(sender, "<yellow>- epic <gray>- Epic crate key");
        TextUtil.send(sender, "");
        TextUtil.send(sender, "<gold>Examples:");
        TextUtil.send(sender, "<white>/givekey Steve daily");
        TextUtil.send(sender, "<white>/givekey Steve epic 5");
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        } else if (args.length == 2) {
            completions.addAll(CRATE_TYPES);
        } else if (args.length == 3) {
            completions.addAll(List.of("1", "5", "10", "25", "64"));
        }

        String current = args[args.length - 1].toLowerCase();
        return completions.stream().filter(s -> s.toLowerCase().startsWith(current)).toList();
    }
}
