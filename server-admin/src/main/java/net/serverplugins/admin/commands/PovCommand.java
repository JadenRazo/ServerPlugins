package net.serverplugins.admin.commands;

import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.admin.spectate.SpectateSession;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.messages.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PovCommand implements CommandExecutor {

    private final ServerAdmin plugin;

    public PovCommand(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("serveradmin.spectate")) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        // If already spectating and no args, stop spectating
        if (args.length == 0) {
            if (plugin.getSpectateManager().isSpectating(player)) {
                plugin.getSpectateManager().stopSpectating(player);
            } else {
                CommonMessages.INVALID_USAGE.send(player, Placeholder.of("usage", "/pov <player>"));
            }
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            CommonMessages.PLAYER_NOT_FOUND.send(player);
            return true;
        }

        plugin.getSpectateManager()
                .startSpectating(player, target, SpectateSession.SpectateType.POV);
        return true;
    }
}
