package net.serverplugins.arcade.commands;

import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.games.jackpot.JackpotType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class JackpotCommand implements CommandExecutor {

    private final ServerArcade plugin;

    public JackpotCommand(ServerArcade plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("serverarcade.command.jackpot")
                && !player.hasPermission("serverarcade.admin")) {
            TextUtil.send(player, plugin.getArcadeConfig().getMessage("command-no-permission"));
            return true;
        }

        JackpotType jackpot = plugin.getJackpotType();
        if (jackpot == null) {
            TextUtil.sendError(player, "Jackpot is not available!");
            return true;
        }

        // Open the jackpot game
        jackpot.open(player, null);

        return true;
    }
}
