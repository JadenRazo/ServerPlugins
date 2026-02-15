package net.serverplugins.admin.commands;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.admin.vanish.VanishMode;
import net.serverplugins.api.messages.CommonMessages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class VanishCommand implements CommandExecutor, TabCompleter {

    private final ServerAdmin plugin;

    public VanishCommand(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("serveradmin.vanish")) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        if (args.length == 0) {
            // Toggle vanish with default mode
            plugin.getVanishManager().toggleVanish(player);
            return true;
        }

        String modeArg = args[0].toUpperCase();

        switch (modeArg) {
            case "OFF":
                plugin.getVanishManager().unvanish(player);
                break;
            case "STAFF":
                plugin.getVanishManager().vanish(player, VanishMode.STAFF);
                break;
            case "FULL":
                plugin.getVanishManager().vanish(player, VanishMode.FULL);
                break;
            default:
                plugin.getAdminConfig()
                        .getMessenger()
                        .sendError(player, "Usage: /vanish [off|staff|full]");
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String option : List.of("off", "staff", "full")) {
                if (option.startsWith(partial)) {
                    completions.add(option);
                }
            }
        }

        return completions;
    }
}
