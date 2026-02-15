package net.serverplugins.claim.commands;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.gui.VisitClaimsGui;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.PlayerClaimData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class WarpCommand implements CommandExecutor, TabCompleter {

    private final ServerClaim plugin;

    public WarpCommand(ServerClaim plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        // No args or "list" - open GUI
        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("list"))) {
            new VisitClaimsGui(plugin, player).open();
            return true;
        }

        // /warp <name> - teleport to claim by name or owner name
        String searchTerm = String.join(" ", args).toLowerCase();

        // Search for claim by name or owner name asynchronously
        plugin.getVisitationManager()
                .getVisitableClaims(player.getUniqueId())
                .thenAccept(
                        claims -> {
                            Claim foundClaim = null;

                            // First try exact match on claim name
                            for (Claim claim : claims) {
                                if (claim.getName().equalsIgnoreCase(searchTerm)) {
                                    foundClaim = claim;
                                    break;
                                }
                            }

                            // Then try exact match on owner name
                            if (foundClaim == null) {
                                for (Claim claim : claims) {
                                    PlayerClaimData ownerData =
                                            plugin.getRepository()
                                                    .getPlayerData(claim.getOwnerUuid());
                                    if (ownerData != null
                                            && ownerData
                                                    .getUsername()
                                                    .equalsIgnoreCase(searchTerm)) {
                                        foundClaim = claim;
                                        break;
                                    }
                                }
                            }

                            // Then try partial match on claim name
                            if (foundClaim == null) {
                                for (Claim claim : claims) {
                                    if (claim.getName().toLowerCase().contains(searchTerm)) {
                                        foundClaim = claim;
                                        break;
                                    }
                                }
                            }

                            // Finally try partial match on owner name
                            if (foundClaim == null) {
                                for (Claim claim : claims) {
                                    PlayerClaimData ownerData =
                                            plugin.getRepository()
                                                    .getPlayerData(claim.getOwnerUuid());
                                    if (ownerData != null
                                            && ownerData
                                                    .getUsername()
                                                    .toLowerCase()
                                                    .contains(searchTerm)) {
                                        foundClaim = claim;
                                        break;
                                    }
                                }
                            }

                            final Claim targetClaim = foundClaim;

                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                if (targetClaim == null) {
                                                    TextUtil.send(
                                                            player,
                                                            "<red>No visitable claim found matching: <white>"
                                                                    + searchTerm);
                                                    TextUtil.send(
                                                            player,
                                                            "<gray>Use <yellow>/playerwarps<gray> to see all available claims.");
                                                } else {
                                                    plugin.getVisitationManager()
                                                            .visitClaim(player, targetClaim);
                                                }
                                            });
                        });

        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("list");

            // Add public claim names asynchronously
            plugin.getVisitationManager()
                    .getVisitableClaims(player.getUniqueId())
                    .thenAccept(
                            claims -> {
                                for (Claim claim : claims) {
                                    if (claim.getName()
                                            .toLowerCase()
                                            .startsWith(args[0].toLowerCase())) {
                                        suggestions.add(claim.getName());
                                    }

                                    // Also suggest owner names
                                    PlayerClaimData ownerData =
                                            plugin.getRepository()
                                                    .getPlayerData(claim.getOwnerUuid());
                                    if (ownerData != null
                                            && ownerData
                                                    .getUsername()
                                                    .toLowerCase()
                                                    .startsWith(args[0].toLowerCase())) {
                                        suggestions.add(ownerData.getUsername());
                                    }
                                }
                            });

            return suggestions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }

        return new ArrayList<>();
    }
}
