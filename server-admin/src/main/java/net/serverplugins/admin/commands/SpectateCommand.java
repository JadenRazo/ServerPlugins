package net.serverplugins.admin.commands;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.admin.spectate.SpectateSession;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.messages.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class SpectateCommand implements CommandExecutor, TabCompleter {

    private final ServerAdmin plugin;

    public SpectateCommand(ServerAdmin plugin) {
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

        // If already spectating and no args, stop spectating
        if (args.length == 0) {
            if (plugin.getSpectateManager().isSpectating(player)) {
                plugin.getSpectateManager().stopSpectating(player);
            } else {
                CommonMessages.INVALID_USAGE.send(
                        player, Placeholder.of("usage", "/spectate <player>"));
            }
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            CommonMessages.PLAYER_NOT_FOUND.send(player);
            return true;
        }

        plugin.getSpectateManager()
                .startSpectating(player, target, SpectateSession.SpectateType.SPECTATE);
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1 && sender instanceof Player player) {
            String partial = args[0].toLowerCase();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.equals(player)) continue;
                if (online.getName().toLowerCase().startsWith(partial)) {
                    completions.add(online.getName());
                }
            }
        }

        return completions;
    }
}
