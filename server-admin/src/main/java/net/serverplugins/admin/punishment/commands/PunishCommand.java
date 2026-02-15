package net.serverplugins.admin.punishment.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.admin.punishment.EscalationPreset;
import net.serverplugins.admin.punishment.Punishment;
import net.serverplugins.admin.punishment.PunishmentContext;
import net.serverplugins.admin.punishment.PunishmentType;
import net.serverplugins.admin.punishment.gui.PunishGui;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.messages.MessageBuilder;
import net.serverplugins.api.messages.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class PunishCommand implements CommandExecutor, TabCompleter {

    private final ServerAdmin plugin;

    public PunishCommand(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("serveradmin.punish")) {
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
                    staff, Placeholder.of("usage", "/punish <player> [type] [duration] [reason]"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target.getName() == null) {
            CommonMessages.PLAYER_NOT_FOUND.send(staff);
            return true;
        }

        if (target.getUniqueId().equals(staff.getUniqueId())) {
            CommonMessages.CANNOT_TARGET_SELF.send(staff);
            return true;
        }

        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null && onlineTarget.hasPermission("serveradmin.punish.exempt")) {
            plugin.getAdminConfig()
                    .getMessenger()
                    .sendError(staff, "You cannot punish this player.");
            return true;
        }

        if (args.length == 1) {
            // Use async loading to avoid blocking main thread
            PunishGui.openAsync(plugin, staff, target);
            return true;
        }

        PunishmentType type = PunishmentType.fromString(args[1]);
        if (type == null) {
            plugin.getAdminConfig()
                    .getMessenger()
                    .sendError(
                            staff,
                            "Invalid punishment type. Valid types: WARN, MUTE, KICK, BAN, FREEZE");
            return true;
        }

        if (!hasPermissionForType(staff, type)) {
            plugin.getAdminConfig()
                    .getMessenger()
                    .sendError(
                            staff,
                            "You don't have permission to issue "
                                    + type.getDisplayName().toLowerCase()
                                    + "s.");
            return true;
        }

        Long durationMs = null;
        String reason = null;
        int reasonStartIndex = 2;

        if (type.hasDuration() && args.length > 2) {
            durationMs = EscalationPreset.parseDuration(args[2]);
            if (durationMs != null) {
                reasonStartIndex = 3;
            } else if (!args[2].equalsIgnoreCase("permanent")
                    && !args[2].equalsIgnoreCase("perm")) {
                durationMs = EscalationPreset.parseDuration(args[2]);
                if (durationMs == null) {
                    reasonStartIndex = 2;
                }
            }
        }

        if (durationMs == null
                && type.hasDuration()
                && !staff.hasPermission("serveradmin.punish.permanent")) {
            plugin.getAdminConfig()
                    .getMessenger()
                    .sendError(
                            staff,
                            "You don't have permission to issue permanent punishments. Please specify a duration.");
            return true;
        }

        if (args.length > reasonStartIndex) {
            StringBuilder sb = new StringBuilder();
            for (int i = reasonStartIndex; i < args.length; i++) {
                if (i > reasonStartIndex) sb.append(" ");
                sb.append(args[i]);
            }
            reason = sb.toString();
        }

        if (reason == null || reason.isEmpty()) {
            reason = "Rule violation";
        }

        PunishmentContext context =
                PunishmentContext.builder()
                        .target(target)
                        .staff(staff)
                        .type(type)
                        .durationMs(durationMs)
                        .reason(reason)
                        .build();

        final String finalReason = reason;
        final Long finalDurationMs = durationMs;
        plugin.getPunishmentManager()
                .punish(context)
                .thenAccept(
                        result -> {
                            Bukkit.getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                if (result.isSuccess()) {
                                                    Punishment p = result.getPunishment();
                                                    MessageBuilder builder =
                                                            MessageBuilder.create()
                                                                    .prefix(
                                                                            plugin.getAdminConfig()
                                                                                    .getMessenger()
                                                                                    .getPrefix())
                                                                    .checkmark()
                                                                    .success(
                                                                            "Successfully "
                                                                                    + type.getDisplayName()
                                                                                            .toLowerCase()
                                                                                    + "ed ")
                                                                    .highlight(target.getName());
                                                    if (finalDurationMs != null) {
                                                        builder.success(" for ")
                                                                .highlight(
                                                                        EscalationPreset
                                                                                .formatDuration(
                                                                                        finalDurationMs));
                                                    }
                                                    builder.send(staff);
                                                } else {
                                                    plugin.getAdminConfig()
                                                            .getMessenger()
                                                            .sendError(
                                                                    staff,
                                                                    "Failed to apply punishment: "
                                                                            + result.getError());
                                                }
                                            });
                        });

        return true;
    }

    private boolean hasPermissionForType(Player player, PunishmentType type) {
        return switch (type) {
            case WARN -> player.hasPermission("serveradmin.punish.warn");
            case MUTE -> player.hasPermission("serveradmin.punish.mute");
            case KICK -> player.hasPermission("serveradmin.punish.kick");
            case BAN -> player.hasPermission("serveradmin.punish.ban");
            case FREEZE -> player.hasPermission("serveradmin.freeze");
        };
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
            for (PunishmentType type : PunishmentType.values()) {
                if (type.name().toLowerCase().startsWith(partial)) {
                    completions.add(type.name().toLowerCase());
                }
            }
        } else if (args.length == 3) {
            PunishmentType type = PunishmentType.fromString(args[1]);
            if (type != null && type.hasDuration()) {
                completions.addAll(
                        Arrays.asList("1h", "6h", "12h", "1d", "3d", "7d", "30d", "permanent"));
            }
        }

        return completions;
    }
}
