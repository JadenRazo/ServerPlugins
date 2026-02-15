package net.serverplugins.commands.commands;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.ServerCommands;
import net.serverplugins.commands.data.PlayerDataManager;
import net.serverplugins.commands.data.punishment.PunishmentRecord;
import net.serverplugins.commands.data.punishment.PunishmentType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class WarnCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;

    public WarnCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("servercommands.warn")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        if (args.length < 1) {
            TextUtil.sendError(sender, "Usage: " + ColorScheme.COMMAND + "/warn <player> [reason]");
            TextUtil.sendError(sender, "Usage: " + ColorScheme.COMMAND + "/warn clear <player>");
            return true;
        }

        if (args[0].equalsIgnoreCase("clear") && args.length > 1) {
            return handleClearWarnings(sender, args[1]);
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            CommonMessages.PLAYER_NOT_FOUND.send(sender);
            return true;
        }

        String reason =
                args.length > 1
                        ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                        : "No reason provided";
        String warnerName = sender instanceof Player ? sender.getName() : "Console";

        PlayerDataManager.PlayerData data =
                plugin.getPlayerDataManager().getPlayerData(target.getUniqueId());
        data.addWarning();
        plugin.getPlayerDataManager().savePlayerData(target.getUniqueId());

        int totalWarnings = data.getWarnings();

        // Log to punishment history
        PunishmentRecord record =
                PunishmentRecord.builder()
                        .target(target.getUniqueId(), target.getName())
                        .staff(sender)
                        .type(PunishmentType.WARN)
                        .reason(reason)
                        .build();
        plugin.getPunishmentHistoryManager().logPunishment(record);

        TextUtil.sendSuccess(
                sender,
                "Warned "
                        + target.getName()
                        + "! They now have "
                        + ColorScheme.HIGHLIGHT
                        + totalWarnings
                        + ColorScheme.SUCCESS
                        + " warning(s).");

        if (target.isOnline()) {
            TextUtil.send(target.getPlayer(), ColorScheme.ERROR + "<bold>WARNING!");
            TextUtil.sendError(
                    target.getPlayer(),
                    "You have been warned by "
                            + ColorScheme.HIGHLIGHT
                            + warnerName
                            + ColorScheme.ERROR
                            + "!");
            TextUtil.sendError(target.getPlayer(), "Reason: " + ColorScheme.HIGHLIGHT + reason);
            TextUtil.sendError(
                    target.getPlayer(), "Total warnings: " + ColorScheme.HIGHLIGHT + totalWarnings);
        }

        int maxWarnings = plugin.getConfig().getInt("moderation.max-warnings-before-action", 3);
        String action = plugin.getConfig().getString("moderation.warning-action", "kick");

        if (totalWarnings >= maxWarnings) {
            if (target.isOnline()) {
                Player targetPlayer = target.getPlayer();
                switch (action.toLowerCase()) {
                    case "kick" ->
                            targetPlayer.kick(
                                    TextUtil.parse(
                                            "<red>You have been kicked for receiving too many warnings!\n<gray>Warnings: "
                                                    + totalWarnings));
                    case "ban" ->
                            Bukkit.getBanList(org.bukkit.BanList.Type.NAME)
                                    .addBan(
                                            target.getName(),
                                            "Too many warnings (" + totalWarnings + ")",
                                            null,
                                            warnerName);
                    case "tempban" -> {
                        long duration =
                                plugin.getConfig()
                                        .getLong("moderation.warning-tempban-duration", 86400000);
                        Bukkit.getBanList(org.bukkit.BanList.Type.NAME)
                                .addBan(
                                        target.getName(),
                                        "Too many warnings (" + totalWarnings + ")",
                                        new java.util.Date(System.currentTimeMillis() + duration),
                                        warnerName);
                        targetPlayer.kick(
                                TextUtil.parse(
                                        "<red>You have been temporarily banned for too many warnings!"));
                    }
                }
            }
            TextUtil.sendWarning(
                    sender,
                    target.getName()
                            + " has reached "
                            + ColorScheme.HIGHLIGHT
                            + maxWarnings
                            + ColorScheme.WARNING
                            + " warnings and has been "
                            + action
                            + "ed!");
        }

        return true;
    }

    private boolean handleClearWarnings(CommandSender sender, String playerName) {
        if (!sender.hasPermission("servercommands.warn.clear")) {
            TextUtil.sendError(sender, "You don't have permission to clear warnings!");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            CommonMessages.PLAYER_NOT_FOUND.send(sender);
            return true;
        }

        PlayerDataManager.PlayerData data =
                plugin.getPlayerDataManager().getPlayerData(target.getUniqueId());
        data.clearWarnings();
        plugin.getPlayerDataManager().savePlayerData(target.getUniqueId());

        TextUtil.sendSuccess(sender, "Cleared all warnings for " + target.getName() + "!");

        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions =
                    Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                            .collect(java.util.stream.Collectors.toList());
            if ("clear".startsWith(args[0].toLowerCase())) {
                suggestions.add("clear");
            }
            return suggestions;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("clear")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }
}
