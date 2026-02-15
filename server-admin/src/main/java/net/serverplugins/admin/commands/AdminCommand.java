package net.serverplugins.admin.commands;

import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.admin.gui.AdminMenuGui;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AdminCommand implements CommandExecutor {

    private final ServerAdmin plugin;

    public AdminCommand(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            TextUtil.sendError(sender, "This command can only be used by players.");
            return true;
        }

        if (!player.hasPermission("serveradmin.admin")) {
            TextUtil.send(player, "<red>You don't have permission to use this command.");
            return true;
        }

        // Open admin GUI
        new AdminMenuGui(plugin, player).open();
        return true;
    }
}
