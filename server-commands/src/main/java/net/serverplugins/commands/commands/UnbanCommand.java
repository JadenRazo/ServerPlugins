package net.serverplugins.commands.commands;

import java.util.Collections;
import java.util.List;
import java.util.stream.StreamSupport;
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

public class UnbanCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public UnbanCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("servercommands.ban")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        if (args.length < 1) {
            CommonMessages.INVALID_USAGE.send(sender, Placeholder.of("usage", "/unban <player>"));
            return true;
        }

        String targetName = args[0];

        // Check if player is banned
        if (!plugin.getBanManager().isBanned(targetName)) {
            TextUtil.sendError(sender, "That player is not banned!");
            return true;
        }

        String staffName = sender instanceof Player ? sender.getName() : "Console";

        // Unban the player
        plugin.getBanManager().unban(targetName);

        // Log to punishment history
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        PunishmentRecord record =
                PunishmentRecord.builder()
                        .target(target.getUniqueId(), targetName)
                        .staff(sender)
                        .type(PunishmentType.UNBAN)
                        .reason("Unbanned")
                        .build();
        plugin.getPunishmentHistoryManager().logPunishment(record);

        // Broadcast
        String broadcastMessage =
                plugin.getCommandsConfig()
                        .getUnbanBroadcast()
                        .replace("{staff}", staffName)
                        .replace("{player}", targetName);
        Bukkit.broadcast(mm.deserialize(broadcastMessage));

        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            // Get banned players from BanList
            return StreamSupport.stream(
                            Bukkit.getBanList(org.bukkit.BanList.Type.NAME)
                                    .getBanEntries()
                                    .spliterator(),
                            false)
                    .map(entry -> entry.getTarget())
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }
}
