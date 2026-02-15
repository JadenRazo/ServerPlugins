package net.serverplugins.enchants.commands;

import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.enchants.ServerEnchants;
import net.serverplugins.enchants.gui.EnchanterMainGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Main command for the enchanter system. Opens the enchanter GUI for players. */
public class EnchanterCommand implements CommandExecutor {

    private final ServerEnchants plugin;

    public EnchanterCommand(ServerEnchants plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("server.enchants.use")) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        new EnchanterMainGui(plugin, player).open();
        return true;
    }
}
