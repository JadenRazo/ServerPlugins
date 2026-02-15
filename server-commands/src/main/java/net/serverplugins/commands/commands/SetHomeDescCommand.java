package net.serverplugins.commands.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.ServerCommands;
import net.serverplugins.commands.data.PlayerDataManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class SetHomeDescCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;

    public SetHomeDescCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("servercommands.sethomedesc")) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        if (args.length < 1) {
            TextUtil.sendError(player, "Usage: /sethomedesc <home> [description]");
            TextUtil.send(player, "<gray>Use without description to clear it.");
            return true;
        }

        String homeName = args[0].toLowerCase();
        PlayerDataManager.PlayerData data =
                plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (!data.hasHome(homeName)) {
            TextUtil.sendError(player, "Home '" + homeName + "' not found!");
            return true;
        }

        // Build description from remaining args
        String description = null;
        if (args.length > 1) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                if (i > 1) sb.append(" ");
                sb.append(args[i]);
            }
            description = sb.toString();

            // Validate description length
            if (description.length() > 256) {
                TextUtil.sendError(player, "Description cannot be longer than 256 characters!");
                return true;
            }
        }

        data.updateHomeDescription(homeName, description);
        plugin.getPlayerDataManager().savePlayerData(player.getUniqueId());

        if (description != null) {
            TextUtil.sendSuccess(
                    player,
                    "Description for home '" + homeName + "' set to: <white>" + description);
        } else {
            TextUtil.sendSuccess(
                    player, "Description for home '" + homeName + "' has been cleared!");
        }

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
