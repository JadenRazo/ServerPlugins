package net.serverplugins.commands.commands;

import java.util.Collections;
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

public class ClearCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;

    public ClearCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("servercommands.clear")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        Player target;
        if (args.length > 0) {
            if (!sender.hasPermission("servercommands.clear.others")) {
                TextUtil.sendError(
                        sender, "You don't have permission to clear others' inventories!");
                return true;
            }
            target = Bukkit.getPlayer(args[0]);
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

        target.getInventory().clear();
        target.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[4]);
        target.getInventory().setItemInOffHand(null);

        if (target.equals(sender)) {
            TextUtil.sendSuccess(sender, "Your inventory has been cleared!");
        } else {
            TextUtil.sendSuccess(sender, "Cleared " + target.getName() + "'s inventory!");
            TextUtil.sendSuccess(target, "Your inventory has been cleared!");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("servercommands.clear.others")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }
}
