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

public class TpDenyCommand implements CommandExecutor {

    private final ServerCommands plugin;

    public TpDenyCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("servercommands.tpdeny")) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        TpaManager.TpaRequest request = plugin.getTpaManager().removeRequest(player.getUniqueId());
        if (request == null) {
            TextUtil.sendError(player, "You have no pending teleport requests!");
            return true;
        }

        Player requester = Bukkit.getPlayer(request.getSenderId());
        TextUtil.sendError(player, "Teleport request denied!");

        if (requester != null) {
            TextUtil.sendError(requester, player.getName() + " denied your teleport request.");
        }

        return true;
    }
}
