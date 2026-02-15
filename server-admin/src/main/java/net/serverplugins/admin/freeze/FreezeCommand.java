package net.serverplugins.admin.freeze;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.messages.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class FreezeCommand implements CommandExecutor, TabCompleter {

    private final ServerAdmin plugin;

    public FreezeCommand(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("serveradmin.freeze")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        if (!(sender instanceof Player staff)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (plugin.getFreezeManager() == null) {
            plugin.getAdminConfig()
                    .getMessenger()
                    .sendError(staff, "Freeze system is not enabled.");
            return true;
        }

        String cmdName = cmd.getName().toLowerCase();

        if (cmdName.equals("freeze")) {
            return handleFreeze(staff, args);
        } else if (cmdName.equals("unfreeze")) {
            return handleUnfreeze(staff, args);
        }

        return false;
    }

    private boolean handleFreeze(Player staff, String[] args) {
        if (args.length < 1) {
            CommonMessages.INVALID_USAGE.send(
                    staff, Placeholder.of("usage", "/freeze <player> [reason]"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            CommonMessages.PLAYER_NOT_FOUND.send(staff);
            return true;
        }

        if (target.equals(staff)) {
            CommonMessages.CANNOT_TARGET_SELF.send(staff);
            return true;
        }

        if (target.hasPermission("serveradmin.freeze.exempt")) {
            plugin.getAdminConfig()
                    .getMessenger()
                    .sendError(staff, "You cannot freeze this player.");
            return true;
        }

        if (plugin.getFreezeManager().isFrozen(target)) {
            plugin.getAdminConfig()
                    .getMessenger()
                    .sendError(staff, target.getName() + " is already frozen.");
            return true;
        }

        // Build reason from remaining args
        String reason = null;
        if (args.length > 1) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                if (i > 1) sb.append(" ");
                sb.append(args[i]);
            }
            reason = sb.toString();
        }

        plugin.getFreezeManager().freeze(target, staff, reason);
        plugin.getAdminConfig().getMessenger().sendSuccess(staff, "Froze " + target.getName());

        return true;
    }

    private boolean handleUnfreeze(Player staff, String[] args) {
        if (args.length < 1) {
            CommonMessages.INVALID_USAGE.send(staff, Placeholder.of("usage", "/unfreeze <player>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            CommonMessages.PLAYER_NOT_FOUND.send(staff);
            return true;
        }

        if (!plugin.getFreezeManager().isFrozen(target)) {
            plugin.getAdminConfig()
                    .getMessenger()
                    .sendError(staff, target.getName() + " is not frozen.");
            return true;
        }

        plugin.getFreezeManager().unfreeze(target, staff, false);
        plugin.getAdminConfig().getMessenger().sendSuccess(staff, "Unfroze " + target.getName());

        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partial)) {
                    completions.add(player.getName());
                }
            }
        }

        return completions;
    }
}
