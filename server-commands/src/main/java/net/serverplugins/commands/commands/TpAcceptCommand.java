package net.serverplugins.commands.commands;

import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.ServerCommands;
import net.serverplugins.commands.data.TpaManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TpAcceptCommand implements CommandExecutor {

    private final ServerCommands plugin;

    public TpAcceptCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("servercommands.tpaccept")) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        TpaManager.TpaRequest request = plugin.getTpaManager().removeRequest(player.getUniqueId());
        if (request == null) {
            TextUtil.sendError(player, "You have no pending teleport requests!");
            return true;
        }

        Player requester = Bukkit.getPlayer(request.getSenderId());
        if (requester == null) {
            TextUtil.sendError(player, "The player who sent the request is no longer online!");
            return true;
        }

        if (request.isTpaHere()) {
            plugin.getPlayerDataManager()
                    .getPlayerData(player.getUniqueId())
                    .setLastLocation(player.getLocation());
            player.teleport(requester.getLocation());
            TextUtil.sendSuccess(player, "Teleported to " + requester.getName() + "!");
            TextUtil.sendSuccess(requester, player.getName() + " accepted your teleport request!");
        } else {
            plugin.getPlayerDataManager()
                    .getPlayerData(requester.getUniqueId())
                    .setLastLocation(requester.getLocation());
            requester.teleport(player.getLocation());
            TextUtil.sendSuccess(player, requester.getName() + " has teleported to you!");
            TextUtil.sendSuccess(requester, "Teleported to " + player.getName() + "!");
        }

        return true;
    }
}
