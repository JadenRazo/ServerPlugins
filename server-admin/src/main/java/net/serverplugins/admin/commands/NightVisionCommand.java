package net.serverplugins.admin.commands;

import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.api.messages.CommonMessages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class NightVisionCommand implements CommandExecutor {

    private final ServerAdmin plugin;

    public NightVisionCommand(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("serveradmin.nightvision")) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        boolean enabled = plugin.getNightVisionManager().toggle(player);

        if (enabled) {
            plugin.getAdminConfig().getMessenger().sendSuccess(player, "Night vision enabled");
        } else {
            plugin.getAdminConfig().getMessenger().sendInfo(player, "Night vision disabled");
        }

        return true;
    }
}
