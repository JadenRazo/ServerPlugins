package net.serverplugins.admin.commands;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class DisguiseCommand implements CommandExecutor, TabCompleter {

    private final ServerAdmin plugin;

    public DisguiseCommand(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("serveradmin.disguise")) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        if (args.length < 1) {
            TextUtil.sendError(player, "Usage: /disguise <player|off>");
            return true;
        }

        String targetArg = args[0].toLowerCase();

        // Check if removing disguise
        if (targetArg.equals("off") || targetArg.equals("remove") || targetArg.equals("reset")) {
            if (!plugin.getDisguiseManager().isDisguised(player)) {
                TextUtil.sendWarning(player, "You are not currently disguised.");
                return true;
            }

            plugin.getDisguiseManager().undisguise(player);
            TextUtil.sendSuccess(player, "Disguise removed. You are now visible as yourself.");

            // Notify staff
            notifyStaff(player, null);
            return true;
        }

        // Disguising as someone - player must be online
        String targetName = args[0];
        Player targetPlayer = Bukkit.getPlayer(targetName);

        if (targetPlayer == null) {
            TextUtil.sendError(player, "Player must be online to disguise as them.");
            return true;
        }

        if (targetPlayer.equals(player)) {
            TextUtil.sendError(player, "You cannot disguise as yourself.");
            return true;
        }

        // Apply disguise
        plugin.getDisguiseManager().disguise(player, targetPlayer);

        TextUtil.sendSuccess(
                player, "You are now disguised as <white>" + targetPlayer.getName() + "<reset>");
        TextUtil.sendInfo(
                player,
                "Your chat messages will appear as theirs. Use <yellow>/disguise off<gray> to remove.");

        // Notify staff
        notifyStaff(player, targetPlayer.getName());

        return true;
    }

    private void notifyStaff(Player player, String disguisedAs) {
        String message;
        if (disguisedAs != null) {
            message =
                    "<dark_gray>[<light_purple>DISGUISE</light_purple>] <gray>"
                            + player.getName()
                            + " disguised as <white>"
                            + disguisedAs;
        } else {
            message =
                    "<dark_gray>[<light_purple>DISGUISE</light_purple>] <gray>"
                            + player.getName()
                            + " removed their disguise";
        }

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("serveradmin.disguise.notify") && !staff.equals(player)) {
                TextUtil.send(staff, message);
            }
        }
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();

            // Add "off" option
            if ("off".startsWith(partial)) completions.add("off");

            // Add online players
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partial)) {
                    completions.add(player.getName());
                }
            }
        }

        return completions;
    }
}
