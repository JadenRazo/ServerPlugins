package net.serverplugins.claim.commands;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.gui.NotificationsGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** Command to open the notifications GUI. Usage: /claim notifications */
public class ClaimNotificationsCommand implements CommandExecutor, TabCompleter {

    private final ServerClaim plugin;

    public ClaimNotificationsCommand(ServerClaim plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        // Open notifications GUI
        new NotificationsGui(plugin, player).open();
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        return new ArrayList<>();
    }
}
