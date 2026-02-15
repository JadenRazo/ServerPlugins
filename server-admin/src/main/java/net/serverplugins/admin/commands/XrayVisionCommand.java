package net.serverplugins.admin.commands;

import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.api.messages.CommonMessages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class XrayVisionCommand implements CommandExecutor {

    private final ServerAdmin plugin;

    public XrayVisionCommand(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("serveradmin.xrayvision")) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        if (plugin.getXrayVisionManager() == null) {
            plugin.getAdminConfig()
                    .getMessenger()
                    .sendError(
                            player,
                            "Xray vision is not available. ProtocolLib may not be installed.");
            return true;
        }

        plugin.getXrayVisionManager().toggle(player);
        return true;
    }
}
