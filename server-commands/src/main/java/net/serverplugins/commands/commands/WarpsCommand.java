package net.serverplugins.commands.commands;

import java.util.Set;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.ServerCommands;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class WarpsCommand implements CommandExecutor {

    private final ServerCommands plugin;

    public WarpsCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("servercommands.warps")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        Set<String> warpNames = plugin.getWarpManager().getWarpNames();

        if (warpNames.isEmpty()) {
            TextUtil.sendError(sender, "No warps are available!");
            return true;
        }

        TextUtil.send(
                sender,
                ColorScheme.EMPHASIS + "<bold>--- Server Warps (" + warpNames.size() + ") ---");
        for (String name : warpNames) {
            Location loc = plugin.getWarpManager().getWarp(name);
            if (loc != null) {
                String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "unknown";
                TextUtil.send(
                        sender,
                        ColorScheme.WARNING
                                + ColorScheme.BULLET
                                + " "
                                + ColorScheme.HIGHLIGHT
                                + name
                                + " "
                                + ColorScheme.INFO
                                + "- "
                                + worldName
                                + " ("
                                + loc.getBlockX()
                                + ", "
                                + loc.getBlockY()
                                + ", "
                                + loc.getBlockZ()
                                + ")");
            }
        }

        return true;
    }
}
