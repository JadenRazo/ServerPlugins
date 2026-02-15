package net.serverplugins.claim.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class TrustCommand implements CommandExecutor, TabCompleter {

    private final ServerClaim plugin;

    public TrustCommand(ServerClaim plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("serverclaim.claim")) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        if (args.length < 1) {
            TextUtil.send(player, "<red>✗ Usage: /trust <player>");
            return true;
        }

        Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation().getChunk());

        if (claim == null) {
            TextUtil.send(player, "<red>✗ You are not standing in a claimed area");
            TextUtil.send(player, "<gray>→ Stand in your claim to trust players");
            return true;
        }

        if (!claim.isOwner(player.getUniqueId())) {
            TextUtil.send(player, "<red>✗ You do not own this claim");
            TextUtil.send(player, "<gray>→ Only the claim owner can trust players");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            CommonMessages.PLAYER_NOT_FOUND.send(player);
            TextUtil.send(player, "<gray>→ Use /claim group add <player> to add offline players");
            return true;
        }

        if (claim.isTrusted(target.getUniqueId())) {
            TextUtil.send(
                    player, "<yellow>⚠ <white>" + target.getName() + " <yellow>is already trusted");
            return true;
        }

        plugin.getProfileManager().trustPlayer(claim, target);
        TextUtil.send(
                player,
                "<green>✓ Added <white>" + target.getName() + " <green>as a trusted member");
        TextUtil.send(player, "<gray>→ They can now build and interact in your claim");

        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
