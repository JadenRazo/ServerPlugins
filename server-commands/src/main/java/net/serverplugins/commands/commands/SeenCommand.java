package net.serverplugins.commands.commands;

import java.util.Collections;
import java.util.List;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.ServerCommands;
import net.serverplugins.commands.data.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class SeenCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;

    public SeenCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("servercommands.seen")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        if (args.length < 1) {
            TextUtil.sendError(sender, "Usage: /seen <player>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            TextUtil.sendError(sender, "Player not found!");
            return true;
        }

        if (target.isOnline()) {
            TextUtil.sendSuccess(sender, target.getName() + " is currently online!");
            return true;
        }

        PlayerDataManager.PlayerData data =
                plugin.getPlayerDataManager().getPlayerData(target.getUniqueId());
        long lastSeen = data.getLastSeen();

        if (lastSeen == 0) {
            lastSeen = target.getLastPlayed();
        }

        String timeAgo = formatTimeAgo(System.currentTimeMillis() - lastSeen);
        TextUtil.send(
                sender,
                "<gold>"
                        + target.getName()
                        + " <gray>was last seen <yellow>"
                        + timeAgo
                        + " ago<gray>.");

        return true;
    }

    private String formatTimeAgo(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        long weeks = days / 7;
        long months = days / 30;

        if (months > 0) return months + " month" + (months > 1 ? "s" : "");
        if (weeks > 0) return weeks + " week" + (weeks > 1 ? "s" : "");
        if (days > 0) return days + " day" + (days > 1 ? "s" : "");
        if (hours > 0) return hours + " hour" + (hours > 1 ? "s" : "");
        if (minutes > 0) return minutes + " minute" + (minutes > 1 ? "s" : "");
        return seconds + " second" + (seconds > 1 ? "s" : "");
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }
}
