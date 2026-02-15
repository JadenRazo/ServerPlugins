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

public class RenameHomeCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;

    public RenameHomeCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("servercommands.renamehome")) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        if (args.length < 2) {
            TextUtil.sendError(player, "Usage: /renamehome <oldname> <newname>");
            return true;
        }

        String oldName = args[0].toLowerCase();
        String newName = args[1].toLowerCase();

        // Validate new name
        if (!newName.matches("^[a-zA-Z0-9_-]+$")) {
            TextUtil.sendError(
                    player,
                    "Home name can only contain letters, numbers, underscores, and hyphens!");
            return true;
        }

        if (newName.length() > 32) {
            TextUtil.sendError(player, "Home name cannot be longer than 32 characters!");
            return true;
        }

        PlayerDataManager.PlayerData data =
                plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Check old home exists
        if (!data.hasHome(oldName)) {
            TextUtil.sendError(player, "Home '" + oldName + "' not found!");
            return true;
        }

        // Check new name not already taken
        if (data.hasHome(newName)) {
            TextUtil.sendError(player, "You already have a home named '" + newName + "'!");
            return true;
        }

        // Same name check
        if (oldName.equals(newName)) {
            TextUtil.sendError(player, "The new name must be different from the old name!");
            return true;
        }

        data.renameHome(oldName, newName);
        plugin.getPlayerDataManager().savePlayerData(player.getUniqueId());
        TextUtil.sendSuccess(
                player, "Home '" + oldName + "' has been renamed to '" + newName + "'!");

        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return Collections.emptyList();

        if (args.length == 1) {
            // Suggest existing home names for old name
            PlayerDataManager.PlayerData data =
                    plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            return new ArrayList<>(data.getHomes().keySet())
                    .stream()
                            .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                            .toList();
        }

        if (args.length == 2) {
            // Suggest a new name - no specific suggestions, just return empty
            return Collections.emptyList();
        }

        return Collections.emptyList();
    }
}
