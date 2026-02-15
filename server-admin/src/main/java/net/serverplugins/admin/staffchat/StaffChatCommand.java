package net.serverplugins.admin.staffchat;

import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.messages.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StaffChatCommand implements CommandExecutor {

    private final ServerAdmin plugin;

    public StaffChatCommand(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("serveradmin.staffchat")) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        if (plugin.getStaffChatManager() == null) {
            plugin.getAdminConfig().getMessenger().sendError(player, "Staff chat is not enabled.");
            return true;
        }

        String cmdName = cmd.getName().toLowerCase();

        if (cmdName.equals("sc") || cmdName.equals("staffchat")) {
            // Send message to staff chat
            if (args.length == 0) {
                CommonMessages.INVALID_USAGE.send(player, Placeholder.of("usage", "/sc <message>"));
                return true;
            }

            String message = String.join(" ", args);
            plugin.getStaffChatManager().sendMessage(player, message);

        } else if (cmdName.equals("sctoggle")) {
            // Toggle staff chat mode
            plugin.getStaffChatManager().toggleStaffChat(player);

            boolean toggled = plugin.getStaffChatManager().isToggled(player);
            if (toggled) {
                plugin.getAdminConfig()
                        .getMessenger()
                        .sendSuccess(
                                player,
                                "Staff chat mode enabled. All messages will go to staff chat.");
            } else {
                plugin.getAdminConfig()
                        .getMessenger()
                        .sendInfo(player, "Staff chat mode disabled.");
            }
        }

        return true;
    }
}
