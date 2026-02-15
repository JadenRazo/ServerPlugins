package net.serverplugins.admin.reset;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.admin.reset.gui.ResetGui;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class ResetCommand implements CommandExecutor, TabCompleter {

    private final ServerAdmin plugin;

    public ResetCommand(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("serveradmin.reset")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        if (!(sender instanceof Player staff)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (plugin.getResetManager() == null) {
            TextUtil.sendError(sender, "Reset system is not enabled.");
            return true;
        }

        if (args.length < 1) {
            TextUtil.sendError(staff, "Usage: <aqua>/reset <player> [type]");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target.getName() == null) {
            CommonMessages.PLAYER_NOT_FOUND.send(staff);
            return true;
        }

        if (args.length == 1) {
            new ResetGui(plugin, staff, target).open();
            return true;
        }

        ResetType type = ResetType.fromString(args[1]);
        if (type == null) {
            TextUtil.sendError(
                    staff,
                    "Invalid reset type. Valid types: CLAIMS, ECONOMY, PLAYTIME, RANK, PUNISHMENTS, ALL");
            return true;
        }

        if (!staff.hasPermission(type.getPermission())) {
            TextUtil.sendError(
                    staff,
                    "You don't have permission to reset "
                            + type.getDisplayName().toLowerCase()
                            + ".");
            return true;
        }

        TextUtil.sendWarning(
                staff, "Resetting " + type.getDisplayName() + " for " + target.getName() + "...");

        plugin.getResetManager()
                .reset(target.getUniqueId(), target.getName(), type, staff)
                .thenAccept(
                        result -> {
                            Bukkit.getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                if (result.isSuccess()) {
                                                    TextUtil.sendSuccess(
                                                            staff,
                                                            "Successfully reset "
                                                                    + type.getDisplayName()
                                                                    + " for "
                                                                    + target.getName()
                                                                    + ": "
                                                                    + result.getDetails());
                                                } else {
                                                    TextUtil.sendError(
                                                            staff,
                                                            "Failed to reset: "
                                                                    + result.getError());
                                                }
                                            });
                        });

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
        } else if (args.length == 2) {
            String partial = args[1].toLowerCase();
            for (ResetType type : ResetType.values()) {
                if (type.name().toLowerCase().startsWith(partial)) {
                    completions.add(type.name().toLowerCase());
                }
            }
        }

        return completions;
    }
}
