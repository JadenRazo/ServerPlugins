package net.serverplugins.claim.commands;

import java.util.List;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.Nation;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** Shortcut command for nation chat. Usage: /nc <message> */
public class NcCommand implements CommandExecutor, TabCompleter {

    private final ServerClaim plugin;

    public NcCommand(ServerClaim plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!plugin.getNationManager().isNationsEnabled()) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>The nations system is currently disabled.");
            return true;
        }

        if (args.length == 0) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix") + "<red>Usage: /nc <message>");
            return true;
        }

        List<Claim> claims = plugin.getClaimManager().getPlayerClaims(player.getUniqueId());
        if (claims.isEmpty()) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix") + "<red>You don't have a claim.");
            return true;
        }

        Nation nation = plugin.getNationManager().getNationForClaim(claims.get(0).getId());
        if (nation == null) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix") + "<red>You're not in a nation.");
            return true;
        }

        String message = String.join(" ", args);
        plugin.getNationManager()
                .broadcastToNation(
                        nation, "<gray>" + player.getName() + ": </gray><white>" + message);

        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
