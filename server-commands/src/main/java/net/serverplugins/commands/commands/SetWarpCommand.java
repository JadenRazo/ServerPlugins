package net.serverplugins.commands.commands;

import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.messages.Placeholder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.ServerCommands;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetWarpCommand implements CommandExecutor {

    private final ServerCommands plugin;

    public SetWarpCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("servercommands.setwarp")) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        if (args.length < 1) {
            CommonMessages.INVALID_USAGE.send(player, Placeholder.of("usage", "/setwarp <name>"));
            return true;
        }

        String warpName = args[0].toLowerCase();

        if (!warpName.matches("^[a-zA-Z0-9_-]+$")) {
            TextUtil.sendError(
                    player,
                    "Warp name can only contain letters, numbers, underscores, and dashes!");
            return true;
        }

        if (warpName.length() > 16) {
            TextUtil.sendError(player, "Warp name cannot be longer than 16 characters!");
            return true;
        }

        boolean isUpdate = plugin.getWarpManager().warpExists(warpName);
        plugin.getWarpManager().setWarp(warpName, player.getLocation());

        if (isUpdate) {
            TextUtil.sendSuccess(
                    player,
                    "Warp '"
                            + ColorScheme.HIGHLIGHT
                            + warpName
                            + ColorScheme.SUCCESS
                            + "' has been updated!");
        } else {
            TextUtil.sendSuccess(
                    player,
                    "Warp '"
                            + ColorScheme.HIGHLIGHT
                            + warpName
                            + ColorScheme.SUCCESS
                            + "' has been created!");
        }

        return true;
    }
}
