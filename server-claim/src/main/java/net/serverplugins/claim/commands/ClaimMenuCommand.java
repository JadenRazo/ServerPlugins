package net.serverplugins.claim.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.gui.ClaimMenuGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class ClaimMenuCommand implements CommandExecutor, TabCompleter {

    private final ServerClaim plugin;

    public ClaimMenuCommand(ServerClaim plugin) {
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

        new ClaimMenuGui(plugin, player).open();
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
                    Arrays.asList(
                            "open",
                            "help",
                            "info",
                            "list",
                            "manage",
                            "settings",
                            "members",
                            "visit");

            return suggestions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
