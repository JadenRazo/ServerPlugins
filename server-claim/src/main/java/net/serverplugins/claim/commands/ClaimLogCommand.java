package net.serverplugins.claim.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.gui.ActivityLogGui;
import net.serverplugins.claim.models.Claim;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Command to view claim activity logs. Usage: /claimlog [claimId] [page] Opens a GUI showing the
 * activity log for a specific claim.
 */
public class ClaimLogCommand implements CommandExecutor, TabCompleter {

    private final ServerClaim plugin;

    public ClaimLogCommand(ServerClaim plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        // No args - show log for current claim
        if (args.length == 0) {
            Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation().getChunk());

            if (claim == null) {
                TextUtil.send(player, "<red>You are not standing in a claimed area!");
                TextUtil.send(
                        player,
                        "<gray>Use <yellow>/claimlog <claimId><gray> to view a specific claim's log.");
                return true;
            }

            // Check if player has permission to view this claim's log
            if (!canViewLog(player, claim)) {
                TextUtil.send(
                        player,
                        "<red>You don't have permission to view this claim's activity log.");
                return true;
            }

            new ActivityLogGui(plugin, player, claim, 0).open();
            return true;
        }

        // Parse claim ID
        int claimId;
        try {
            claimId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            TextUtil.send(player, "<red>Invalid claim ID! Must be a number.");
            return true;
        }

        // Get claim
        Claim claim = plugin.getClaimManager().getClaimById(claimId);
        if (claim == null) {
            TextUtil.send(player, "<red>Claim not found with ID: " + claimId);
            return true;
        }

        // Check permissions
        if (!canViewLog(player, claim)) {
            TextUtil.send(
                    player, "<red>You don't have permission to view this claim's activity log.");
            return true;
        }

        // Parse page number if provided
        int page = 0;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]) - 1; // Convert to 0-indexed
                if (page < 0) page = 0;
            } catch (NumberFormatException e) {
                TextUtil.send(player, "<yellow>Invalid page number, showing page 1.");
                page = 0;
            }
        }

        new ActivityLogGui(plugin, player, claim, page).open();
        return true;
    }

    /**
     * Check if player can view the log for a claim. Owner, admins, and members with management
     * permissions can view.
     */
    private boolean canViewLog(Player player, Claim claim) {
        if (player.hasPermission("serverclaim.admin")) {
            return true;
        }

        if (claim.isOwner(player.getUniqueId())) {
            return true;
        }

        // Check if player is a member with any management permission
        return claim.isMember(player.getUniqueId());
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            // Suggest claim IDs that the player owns or has access to
            List<Claim> accessibleClaims =
                    plugin.getClaimManager().getAccessibleClaims(player.getUniqueId());
            return accessibleClaims.stream()
                    .map(c -> String.valueOf(c.getId()))
                    .filter(id -> id.startsWith(args[0]))
                    .toList();
        }

        if (args.length == 2) {
            // Suggest page numbers
            return Arrays.asList("1", "2", "3").stream()
                    .filter(page -> page.startsWith(args[1]))
                    .toList();
        }

        return new ArrayList<>();
    }
}
