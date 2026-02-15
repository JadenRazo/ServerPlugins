package net.serverplugins.commands.commands;

import java.util.Collections;
import java.util.List;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.ServerCommands;
import net.serverplugins.commands.data.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class GodCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;

    public GodCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("servercommands.god")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        Player target;
        if (args.length > 0) {
            if (!sender.hasPermission("servercommands.god.others")) {
                TextUtil.sendError(
                        sender, "You don't have permission to toggle god mode for others!");
                return true;
            }
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                CommonMessages.PLAYER_NOT_FOUND.send(sender);
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                CommonMessages.CONSOLE_ONLY.send(sender);
                return true;
            }
            target = (Player) sender;
        }

        PlayerDataManager.PlayerData data =
                plugin.getPlayerDataManager().getPlayerData(target.getUniqueId());
        boolean newState = !data.isGodMode();
        data.setGodMode(newState);
        plugin.getPlayerDataManager().savePlayerData(target.getUniqueId());

        String state = newState ? ColorScheme.SUCCESS + "enabled" : ColorScheme.ERROR + "disabled";
        if (target.equals(sender)) {
            TextUtil.send(sender, ColorScheme.INFO + "God mode " + state + ColorScheme.INFO + "!");
        } else {
            TextUtil.send(
                    sender,
                    ColorScheme.INFO
                            + "God mode "
                            + state
                            + ColorScheme.INFO
                            + " for "
                            + ColorScheme.HIGHLIGHT
                            + target.getName()
                            + ColorScheme.INFO
                            + "!");
            TextUtil.send(target, ColorScheme.INFO + "God mode " + state + ColorScheme.INFO + "!");
        }

        // Send action bar message to player
        if (newState) {
            TextUtil.sendActionBar(
                    target,
                    ColorScheme.EMPHASIS
                            + "<bold>GOD MODE: "
                            + ColorScheme.SUCCESS
                            + "<bold>ENABLED");
        } else {
            TextUtil.sendActionBar(
                    target,
                    ColorScheme.EMPHASIS
                            + "<bold>GOD MODE: "
                            + ColorScheme.ERROR
                            + "<bold>DISABLED");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("servercommands.god.others")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }
}
