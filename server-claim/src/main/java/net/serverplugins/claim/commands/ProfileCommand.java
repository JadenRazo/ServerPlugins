package net.serverplugins.claim.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.gui.ClaimSettingsGui;
import net.serverplugins.claim.models.Claim;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class ProfileCommand implements CommandExecutor, TabCompleter {

    private final ServerClaim plugin;

    public ProfileCommand(ServerClaim plugin) {
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

        Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation().getChunk());

        if (claim == null || !claim.isOwner(player.getUniqueId())) {
            TextUtil.send(player, plugin.getClaimConfig().getMessage("no-claim-here"));
            return true;
        }

        new ClaimSettingsGui(plugin, player, claim).open();
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> suggestions =
                    Arrays.asList("settings", "icon", "name", "description", "visibility");

            return suggestions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("icon")) {
            List<String> suggestions = new ArrayList<>();

            for (Material material : Material.values()) {
                if (material.isItem()
                        && material.name().toLowerCase().startsWith(args[1].toLowerCase())) {
                    suggestions.add(material.name().toLowerCase());
                    if (suggestions.size() >= 50) break;
                }
            }

            return suggestions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("visibility")) {
            List<String> suggestions = Arrays.asList("public", "private");

            return suggestions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
