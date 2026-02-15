package net.serverplugins.commands.commands;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.ServerCommands;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** Command to adjust player movement speed. Usage: /speed <walk|fly> <1-10> [player] */
public class SpeedCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;

    public SpeedCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("servercommands.speed")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        // Parse speed type (walk or fly)
        String type = args[0].toLowerCase();
        if (!type.equals("walk") && !type.equals("fly")) {
            TextUtil.sendError(sender, "Invalid speed type! Use 'walk' or 'fly'.");
            return true;
        }

        // Parse speed value (1-10)
        int speedValue;
        try {
            speedValue = Integer.parseInt(args[1]);
            if (speedValue < 1 || speedValue > 10) {
                TextUtil.sendError(sender, "Speed must be between 1 and 10!");
                return true;
            }
        } catch (NumberFormatException e) {
            TextUtil.sendError(sender, "Speed must be a number between 1 and 10!");
            return true;
        }

        // Determine target player
        Player target;
        if (args.length > 2) {
            if (!sender.hasPermission("servercommands.speed.others")) {
                TextUtil.sendError(sender, "You don't have permission to change speed for others!");
                return true;
            }
            target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                TextUtil.sendError(sender, "Player not found!");
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                TextUtil.sendError(sender, "Console must specify a player!");
                return true;
            }
            target = (Player) sender;
        }

        // Convert 1-10 scale to Bukkit's 0.0-1.0 scale
        float speed = speedValue / 10f;

        // Apply speed
        if (type.equals("walk")) {
            target.setWalkSpeed(speed);
        } else {
            target.setFlySpeed(speed);
        }

        // Send feedback
        String speedType = type.equals("walk") ? "Walk" : "Fly";
        if (target.equals(sender)) {
            TextUtil.send(
                    sender,
                    "<gray>" + speedType + " speed set to <green>" + speedValue + "<gray>!");
        } else {
            TextUtil.send(
                    sender,
                    "<gray>"
                            + speedType
                            + " speed set to <green>"
                            + speedValue
                            + " <gray>for "
                            + target.getName()
                            + "!");
            TextUtil.send(
                    target,
                    "<gray>Your "
                            + type
                            + " speed has been set to <green>"
                            + speedValue
                            + "<gray>!");
        }

        return true;
    }

    private void sendUsage(CommandSender sender) {
        TextUtil.send(sender, "<gold><bold>=== Speed Command ===");
        TextUtil.send(sender, "<yellow>Usage: <white>/speed <walk|fly> <1-10> [player]");
        TextUtil.send(sender, "");
        TextUtil.send(sender, "<gold>Examples:");
        TextUtil.send(sender, "<white>/speed walk 5 <gray>- Set your walk speed to 5");
        TextUtil.send(sender, "<white>/speed fly 8 <gray>- Set your fly speed to 8");
        TextUtil.send(sender, "<white>/speed walk 3 Steve <gray>- Set Steve's walk speed to 3");
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("walk");
            completions.add("fly");
        } else if (args.length == 2) {
            for (int i = 1; i <= 10; i++) {
                completions.add(String.valueOf(i));
            }
        } else if (args.length == 3 && sender.hasPermission("servercommands.speed.others")) {
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        }

        String current = args[args.length - 1].toLowerCase();
        return completions.stream().filter(s -> s.toLowerCase().startsWith(current)).toList();
    }
}
