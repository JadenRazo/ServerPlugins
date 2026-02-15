package net.serverplugins.admin.punishment.commands;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.admin.punishment.gui.ActivePunishmentsGui;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.messages.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class UnpunishCommand implements CommandExecutor, TabCompleter {

    private final ServerAdmin plugin;

    public UnpunishCommand(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("serveradmin.unpunish")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        if (!(sender instanceof Player staff)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (plugin.getPunishmentManager() == null) {
            plugin.getAdminConfig()
                    .getMessenger()
                    .sendError(staff, "Punishment system is not enabled.");
            return true;
        }

        if (args.length < 1) {
            CommonMessages.INVALID_USAGE.send(
                    staff, Placeholder.of("usage", "/unpunish <player> [punishment_id] [reason]"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target.getName() == null) {
            CommonMessages.PLAYER_NOT_FOUND.send(staff);
            return true;
        }

        if (args.length == 1) {
            // Use async to avoid blocking main thread
            ActivePunishmentsGui.openAsync(plugin, staff, target);
            return true;
        }

        int punishmentId;
        try {
            punishmentId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            CommonMessages.INVALID_NUMBER.send(staff, Placeholder.of("input", args[1]));
            return true;
        }

        String reason = null;
        if (args.length > 2) {
            StringBuilder sb = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                if (i > 2) sb.append(" ");
                sb.append(args[i]);
            }
            reason = sb.toString();
        }

        final String finalReason = reason;
        plugin.getPunishmentManager()
                .pardon(punishmentId, staff, reason)
                .thenAccept(
                        success -> {
                            Bukkit.getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                if (success) {
                                                    plugin.getAdminConfig()
                                                            .getMessenger()
                                                            .sendSuccess(
                                                                    staff,
                                                                    "Punishment #"
                                                                            + punishmentId
                                                                            + " has been pardoned.");
                                                } else {
                                                    plugin.getAdminConfig()
                                                            .getMessenger()
                                                            .sendError(
                                                                    staff,
                                                                    "Failed to pardon punishment. It may not exist or is already inactive.");
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
        }
        // Note: We don't provide punishment ID tab completion to avoid blocking DB calls
        // Users can use the GUI (/unpunish <player>) or type the ID manually

        return completions;
    }
}
