package net.serverplugins.admin.commands;

import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.admin.freecam.FreecamManager;
import net.serverplugins.api.messages.CommonMessages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FreecamCommand implements CommandExecutor {

    private final ServerAdmin plugin;

    public FreecamCommand(ServerAdmin plugin) {
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

        FreecamManager freecamManager = plugin.getFreecamManager();

        // Toggle freecam mode
        if (freecamManager.isInFreecam(player)) {
            freecamManager.stopFreecam(player);
            plugin.getAdminConfig().getMessenger().sendInfo(player, "Exited freecam mode.");
        } else {
            // Exit spectate mode if currently spectating
            if (plugin.getSpectateManager().isSpectating(player)) {
                plugin.getSpectateManager().stopSpectating(player);
            }

            freecamManager.startFreecam(player);
            plugin.getAdminConfig()
                    .getMessenger()
                    .sendSuccess(
                            player, "Entered freecam mode. Use <white>/freecam</white> to exit.");
        }

        return true;
    }
}
