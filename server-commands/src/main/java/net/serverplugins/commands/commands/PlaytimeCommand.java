package net.serverplugins.commands.commands;

import java.util.Collections;
import java.util.List;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.ServerCommands;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class PlaytimeCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;

    public PlaytimeCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("servercommands.playtime")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        OfflinePlayer target;
        if (args.length > 0) {
            if (!sender.hasPermission("servercommands.playtime.others")) {
                TextUtil.sendError(sender, "You don't have permission to view others' playtime!");
                return true;
            }
            target = Bukkit.getOfflinePlayer(args[0]);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                TextUtil.sendError(sender, "Player not found!");
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                TextUtil.sendError(sender, "Console must specify a player!");
                return true;
            }
            target = (Player) sender;
        }

        // Use vanilla Minecraft's PLAY_ONE_MINUTE statistic (measured in ticks, 20 ticks = 1
        // second)
        // This matches what the scoreboard displays
        int ticks = target.getStatistic(Statistic.PLAY_ONE_MINUTE);
        long totalSeconds = ticks / 20L;

        String formatted = formatPlaytime(totalSeconds);

        if (target.getUniqueId().equals(sender instanceof Player p ? p.getUniqueId() : null)) {
            TextUtil.send(sender, "<gold>Your playtime: <yellow>" + formatted);
        } else {
            TextUtil.send(
                    sender, "<gold>" + target.getName() + "'s playtime: <yellow>" + formatted);
        }

        return true;
    }

    /** Format playtime from seconds to human-readable string. */
    private String formatPlaytime(long totalSeconds) {
        long minutes = totalSeconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours % 24 > 0) sb.append(hours % 24).append("h ");
        if (minutes % 60 > 0) sb.append(minutes % 60).append("m ");
        if (totalSeconds % 60 > 0 || sb.isEmpty()) sb.append(totalSeconds % 60).append("s");

        return sb.toString().trim();
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("servercommands.playtime.others")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }
}
