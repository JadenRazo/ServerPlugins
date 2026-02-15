package net.serverplugins.admin.punishment.commands;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.admin.punishment.Punishment;
import net.serverplugins.admin.punishment.gui.HistoryGui;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class HistoryCommand implements CommandExecutor, TabCompleter {

    private final ServerAdmin plugin;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yy HH:mm");

    public HistoryCommand(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("serveradmin.history")) {
            TextUtil.sendError(sender, "You don't have permission to use this command.");
            return true;
        }

        if (plugin.getPunishmentManager() == null) {
            TextUtil.sendError(sender, "Punishment system is not enabled.");
            return true;
        }

        if (args.length < 1) {
            TextUtil.sendError(sender, "Usage: /history <player> [page]");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target.getName() == null) {
            TextUtil.sendError(sender, "Player not found.");
            return true;
        }

        if (sender instanceof Player staff) {
            // Use async to avoid blocking main thread
            HistoryGui.openAsync(plugin, staff, target);
        } else {
            showTextHistoryAsync(sender, target, 1);
        }

        return true;
    }

    private void showTextHistoryAsync(CommandSender sender, OfflinePlayer target, int page) {
        // Load history asynchronously
        java.util.concurrent.CompletableFuture.supplyAsync(
                        () ->
                                plugin.getPunishmentManager()
                                        .getPunishmentHistory(target.getUniqueId(), 50))
                .thenAccept(
                        history -> {
                            Bukkit.getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> showTextHistory(sender, target, page, history));
                        });
    }

    private void showTextHistory(
            CommandSender sender, OfflinePlayer target, int page, List<Punishment> history) {

        if (history.isEmpty()) {
            TextUtil.sendWarning(sender, target.getName() + " has no punishment history.");
            return;
        }

        int perPage = 10;
        int maxPage = (int) Math.ceil(history.size() / (double) perPage);
        page = Math.max(1, Math.min(page, maxPage));

        TextUtil.send(
                sender,
                "<gold>=== Punishment History: "
                        + target.getName()
                        + " (Page "
                        + page
                        + "/"
                        + maxPage
                        + ") ===");

        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, history.size());

        for (int i = start; i < end; i++) {
            Punishment p = history.get(i);
            String status =
                    p.isActive()
                            ? "<red>ACTIVE"
                            : (p.getPardonedAt() != null ? "<yellow>PARDONED" : "<gray>EXPIRED");

            TextUtil.send(
                    sender,
                    "<gray>#"
                            + p.getId()
                            + " "
                            + status
                            + " <white>"
                            + p.getType().getDisplayName()
                            + " <gray>by <white>"
                            + p.getStaffName()
                            + " <gray>on <white>"
                            + dateFormat.format(new Date(p.getIssuedAt())));

            if (p.getReason() != null) {
                TextUtil.send(sender, "<gray>   Reason: <white>" + p.getReason());
            }

            if (p.getDurationMs() != null) {
                TextUtil.send(sender, "<gray>   Duration: <white>" + p.getFormattedDuration());
            }
        }

        TextUtil.send(sender, "<gold>Total: " + history.size() + " punishments");
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
