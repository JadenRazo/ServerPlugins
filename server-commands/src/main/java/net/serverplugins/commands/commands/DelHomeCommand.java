package net.serverplugins.commands.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.serverplugins.commands.ServerCommands;
import net.serverplugins.commands.data.PlayerDataManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class DelHomeCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;

    public DelHomeCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getCommandsConfig().getMessenger().send(sender, "players-only");
            return true;
        }

        if (!player.hasPermission("servercommands.delhome")) {
            plugin.getCommandsConfig().getMessenger().send(player, "no-permission");
            return true;
        }

        if (args.length < 1) {
            plugin.getCommandsConfig()
                    .getMessenger()
                    .send(
                            player,
                            "invalid-usage",
                            net.serverplugins.api.messages.Placeholder.of("usage", "/delhome <name>"));
            return true;
        }

        String homeName = args[0].toLowerCase();
        PlayerDataManager.PlayerData data =
                plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (!data.hasHome(homeName)) {
            plugin.getCommandsConfig()
                    .getMessenger()
                    .send(
                            player,
                            "home-not-found",
                            net.serverplugins.api.messages.Placeholder.of("home", homeName));
            return true;
        }

        data.removeHome(homeName);
        plugin.getPlayerDataManager().savePlayerData(player.getUniqueId());
        plugin.getCommandsConfig()
                .getMessenger()
                .send(
                        player,
                        "home-deleted",
                        net.serverplugins.api.messages.Placeholder.of("home", homeName));

        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return Collections.emptyList();
        if (args.length == 1) {
            PlayerDataManager.PlayerData data =
                    plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            return new ArrayList<>(data.getHomes().keySet())
                    .stream()
                            .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                            .toList();
        }
        return Collections.emptyList();
    }
}
