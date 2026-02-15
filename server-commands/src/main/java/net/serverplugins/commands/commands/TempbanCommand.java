package net.serverplugins.commands.commands;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.ServerCommands;
import net.serverplugins.commands.data.punishment.PunishmentRecord;
import net.serverplugins.commands.data.punishment.PunishmentType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class TempbanCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public TempbanCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("servercommands.ban")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        if (args.length < 2) {
            TextUtil.sendError(
                    sender,
                    "Usage: " + ColorScheme.COMMAND + "/tempban <player> <duration> [reason]");
            TextUtil.sendInfo(
                    sender, "Duration examples: 1d (1 day), 12h (12 hours), 30m (30 minutes)");
            return true;
        }

        String targetName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(targetName);
        if (target == null) {
            target = Bukkit.getOfflinePlayer(targetName);
        }

        // Check if already banned
        if (plugin.getBanManager().isBanned(target.getName())) {
            TextUtil.sendError(sender, "That player is already banned!");
            return true;
        }

        // Parse duration
        long durationMillis;
        try {
            durationMillis = plugin.getBanManager().parseDuration(args[1]);
        } catch (IllegalArgumentException e) {
            TextUtil.sendError(sender, "Invalid duration: " + e.getMessage());
            TextUtil.sendInfo(sender, "Examples: 1d, 12h, 30m, 45s");
            return true;
        }

        String reason =
                args.length > 2
                        ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length))
                        : "Violating server rules";
        String staffName = sender instanceof Player ? sender.getName() : "Console";

        // Calculate expiry date
        Date expiryDate = new Date(System.currentTimeMillis() + durationMillis);
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm");

        // Temp ban the player
        plugin.getBanManager().tempBan(target.getName(), durationMillis, reason, staffName);

        // Log to punishment history
        PunishmentRecord record =
                PunishmentRecord.builder()
                        .target(target.getUniqueId(), target.getName())
                        .staff(sender)
                        .type(PunishmentType.TEMPBAN)
                        .reason(reason)
                        .duration(durationMillis)
                        .build();
        plugin.getPunishmentHistoryManager().logPunishment(record);

        // Kick if online
        if (target.isOnline()) {
            Player onlineTarget = target.getPlayer();
            String banMessage =
                    plugin.getCommandsConfig()
                            .getTempBanMessage()
                            .replace(
                                    "{duration}",
                                    plugin.getBanManager().formatDuration(durationMillis))
                            .replace("{reason}", reason)
                            .replace("{staff}", staffName)
                            .replace("{expiry}", dateFormat.format(expiryDate));
            onlineTarget.kick(mm.deserialize(banMessage));
        }

        // Broadcast
        String broadcastMessage =
                plugin.getCommandsConfig()
                        .getTempBanBroadcast()
                        .replace("{staff}", staffName)
                        .replace("{player}", target.getName())
                        .replace(
                                "{duration}", plugin.getBanManager().formatDuration(durationMillis))
                        .replace("{reason}", reason);
        Bukkit.broadcast(mm.deserialize(broadcastMessage));

        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        } else if (args.length == 2) {
            return List.of("1h", "6h", "12h", "1d", "7d", "30d");
        }
        return Collections.emptyList();
    }
}
