package net.serverplugins.admin.commands;

import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.api.messages.CommonMessages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class XrayAlertsCommand implements CommandExecutor {

    private final ServerAdmin plugin;

    public XrayAlertsCommand(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("serveradmin.xray")) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        boolean xrayEnabled = plugin.getXrayManager() != null;
        boolean spawnerEnabled = plugin.getSpawnerManager() != null;

        if (!xrayEnabled && !spawnerEnabled) {
            plugin.getAdminConfig()
                    .getMessenger()
                    .sendError(player, "Detection systems are disabled.");
            return true;
        }

        // Toggle both xray and spawner alerts together
        if (xrayEnabled) {
            plugin.getXrayManager().toggleAlerts(player);
        }
        if (spawnerEnabled) {
            plugin.getSpawnerManager().toggleAlerts(player);
        }

        // Show combined status
        boolean hasAlerts =
                (xrayEnabled && plugin.getXrayManager().hasAlertsEnabled(player))
                        || (spawnerEnabled && plugin.getSpawnerManager().hasAlertsEnabled(player));

        if (hasAlerts) {
            plugin.getAdminConfig()
                    .getMessenger()
                    .sendSuccess(player, "Xray & Spawner alerts enabled");
        } else {
            plugin.getAdminConfig()
                    .getMessenger()
                    .sendInfo(player, "Xray & Spawner alerts disabled");
        }

        return true;
    }
}
