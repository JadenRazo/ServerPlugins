package net.serverplugins.commands.commands;

import java.util.Collections;
import java.util.List;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.messages.Placeholder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.ServerCommands;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public class DelWarpCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;

    public DelWarpCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("servercommands.delwarp")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        if (args.length < 1) {
            CommonMessages.INVALID_USAGE.send(sender, Placeholder.of("usage", "/delwarp <name>"));
            return true;
        }

        String warpName = args[0].toLowerCase();

        if (!plugin.getWarpManager().warpExists(warpName)) {
            TextUtil.sendError(
                    sender,
                    "Warp '"
                            + ColorScheme.HIGHLIGHT
                            + warpName
                            + ColorScheme.ERROR
                            + "' not found!");
            return true;
        }

        plugin.getWarpManager().deleteWarp(warpName);
        TextUtil.sendSuccess(
                sender,
                "Warp '"
                        + ColorScheme.HIGHLIGHT
                        + warpName
                        + ColorScheme.SUCCESS
                        + "' has been deleted!");

        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return plugin.getWarpManager().getWarpNames().stream()
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }
}
