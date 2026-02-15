package net.serverplugins.commands.commands;

import java.util.Collections;
import java.util.List;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.commands.ServerCommands;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class HealCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;

    public HealCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("servercommands.heal")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        Player target;
        if (args.length > 0) {
            if (!sender.hasPermission("servercommands.heal.others")) {
                plugin.getCommandsConfig()
                        .getMessenger()
                        .sendError(sender, "You don't have permission to heal others!");
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

        var maxHealth = target.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            target.setHealth(maxHealth.getValue());
        }

        target.setFireTicks(0);
        target.getActivePotionEffects()
                .forEach(effect -> target.removePotionEffect(effect.getType()));

        if (target.equals(sender)) {
            plugin.getCommandsConfig().getMessenger().sendSuccess(sender, "You have been healed!");
        } else {
            plugin.getCommandsConfig()
                    .getMessenger()
                    .sendSuccess(sender, "Healed " + target.getName() + "!");
            plugin.getCommandsConfig().getMessenger().sendSuccess(target, "You have been healed!");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("servercommands.heal.others")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }
}
