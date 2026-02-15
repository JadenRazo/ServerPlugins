package net.serverplugins.commands.commands;

import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.messages.Placeholder;
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

public class BanCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public BanCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("servercommands.ban")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        if (args.length < 1) {
            CommonMessages.INVALID_USAGE.send(
                    sender, Placeholder.of("usage", "/ban <player> [reason]"));
            return true;
        }

        String targetName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(targetName);
        if (target == null) {
            target = Bukkit.getOfflinePlayer(targetName); // Try to find by name
        }

        // Check if already banned
        if (plugin.getBanManager().isBanned(target.getName())) {
            TextUtil.sendError(sender, "That player is already banned!");
            return true;
        }

        String reason =
                args.length > 1
                        ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                        : "Violating server rules";
        String staffName = sender instanceof Player ? sender.getName() : "Console";

        // Ban the player
        plugin.getBanManager().ban(target.getName(), reason, staffName);

        // Log to punishment history
        PunishmentRecord record =
                PunishmentRecord.builder()
                        .target(target.getUniqueId(), target.getName())
                        .staff(sender)
                        .type(PunishmentType.BAN)
                        .reason(reason)
                        .build();
        plugin.getPunishmentHistoryManager().logPunishment(record);

        // Kick if online
        if (target.isOnline()) {
            Player onlineTarget = target.getPlayer();
            String banMessage =
                    plugin.getCommandsConfig()
                            .getBanMessage()
                            .replace("{reason}", reason)
                            .replace("{staff}", staffName);
            onlineTarget.kick(mm.deserialize(banMessage));
        }

        // Broadcast
        String broadcastMessage =
                plugin.getCommandsConfig()
                        .getBanBroadcast()
                        .replace("{staff}", staffName)
                        .replace("{player}", target.getName())
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
        }
        return Collections.emptyList();
    }
}
