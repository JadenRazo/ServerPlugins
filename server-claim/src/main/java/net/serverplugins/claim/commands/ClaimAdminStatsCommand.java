package net.serverplugins.claim.commands;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.gui.ServerStatsGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Admin command to view server-wide claim statistics. This is a subcommand of /claimadmin. Usage:
 * /claimadmin stats Requires serverclaim.admin.stats permission.
 */
public class ClaimAdminStatsCommand implements CommandExecutor, TabCompleter {

    private final ServerClaim plugin;

    public ClaimAdminStatsCommand(ServerClaim plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("serverclaim.admin.stats")) {
            TextUtil.send(player, "<red>You don't have permission to view server statistics.");
            TextUtil.send(player, "<gray>Required permission: <yellow>serverclaim.admin.stats");
            return true;
        }

        // Open the server stats GUI
        new ServerStatsGui(plugin, player).open();
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        // No tab completion needed for this command
        return new ArrayList<>();
    }

    /**
     * Handle the stats subcommand within /claimadmin. This method should be called from
     * ClaimAdminCommand.
     */
    public static void handleStatsSubcommand(ServerClaim plugin, Player player) {
        if (!player.hasPermission("serverclaim.admin.stats")) {
            TextUtil.send(player, "<red>You don't have permission to view server statistics.");
            TextUtil.send(player, "<gray>Required permission: <yellow>serverclaim.admin.stats");
            return;
        }

        new ServerStatsGui(plugin, player).open();
    }
}
