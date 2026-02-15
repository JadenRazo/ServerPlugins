package net.serverplugins.commands.commands;

import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.commands.ServerCommands;
import net.serverplugins.commands.gui.HomesGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HomesCommand implements CommandExecutor {

    private final ServerCommands plugin;

    public HomesCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("servercommands.homes")) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        // Open the homes GUI
        new HomesGui(plugin, player).open();

        return true;
    }
}
