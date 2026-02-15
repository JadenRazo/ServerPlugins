package net.serverplugins.commands.commands;

import java.util.Collections;
import java.util.List;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.commands.ServerCommands;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class FeedCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;

    public FeedCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("servercommands.feed")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        Player target;
        if (args.length > 0) {
            if (!sender.hasPermission("servercommands.feed.others")) {
                plugin.getCommandsConfig()
                        .getMessenger()
                        .sendError(sender, "You don't have permission to feed others!");
                return true;
            }
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                CommonMessages.PLAYER_NOT_FOUND.send(sender);
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                CommonMessages.PLAYERS_ONLY.send(sender);
                return true;
            }
            target = (Player) sender;
        }

        target.setFoodLevel(20);
        target.setSaturation(20f);
        target.setExhaustion(0f);

        if (target.equals(sender)) {
            plugin.getCommandsConfig().getMessenger().sendSuccess(sender, "You have been fed!");
        } else {
            plugin.getCommandsConfig()
                    .getMessenger()
                    .sendSuccess(sender, "Fed " + target.getName() + "!");
            plugin.getCommandsConfig().getMessenger().sendSuccess(target, "You have been fed!");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("servercommands.feed.others")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }
}
