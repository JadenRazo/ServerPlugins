package net.serverplugins.claim.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.gui.ClaimStatsGui;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Command to view claim statistics for players. Usage: /claimstats [player] Opens a GUI showing
 * comprehensive claim statistics.
 */
public class ClaimStatsCommand implements CommandExecutor, TabCompleter {

    private final ServerClaim plugin;

    public ClaimStatsCommand(ServerClaim plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        // No args - show own stats
        if (args.length == 0) {
            new ClaimStatsGui(plugin, player, player.getUniqueId()).open();
            return true;
        }

        // Show stats for another player (requires permission or viewing public stats)
        String targetName = args[0];

        // Try to find the player
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            TextUtil.send(player, "<red>Player not found: " + targetName);
            TextUtil.send(player, "<gray>They must have joined the server at least once.");
            return true;
        }

        UUID targetUuid = target.getUniqueId();
        String resolvedName = target.getName() != null ? target.getName() : targetName;

        // Check permissions (admins can view anyone's stats)
        if (!player.getUniqueId().equals(targetUuid)
                && !player.hasPermission("serverclaim.admin")) {
            TextUtil.send(
                    player, "<red>You don't have permission to view other players' statistics.");
            TextUtil.send(player, "<gray>Use <yellow>/claimstats<gray> to view your own stats.");
            return true;
        }

        new ClaimStatsGui(plugin, player, targetUuid).open();
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            // Only suggest player names if they have admin permission
            if (player.hasPermission("serverclaim.admin")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                        .toList();
            }
        }

        return new ArrayList<>();
    }
}
