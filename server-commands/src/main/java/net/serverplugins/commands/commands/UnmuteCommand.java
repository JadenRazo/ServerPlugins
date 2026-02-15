package net.serverplugins.commands.commands;

import java.util.Collections;
import java.util.List;
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

public class UnmuteCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;

    public UnmuteCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("servercommands.unmute")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        if (args.length < 1) {
            CommonMessages.INVALID_USAGE.send(sender, Placeholder.of("usage", "/unmute <player>"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            CommonMessages.PLAYER_NOT_FOUND.send(sender);
            return true;
        }

        if (!plugin.getMuteManager().isMuted(target.getUniqueId())) {
            TextUtil.sendError(sender, target.getName() + " is not muted!");
            return true;
        }

        plugin.getMuteManager().unmute(target.getUniqueId());

        // Log to punishment history
        PunishmentRecord record =
                PunishmentRecord.builder()
                        .target(target.getUniqueId(), target.getName())
                        .staff(sender)
                        .type(PunishmentType.UNMUTE)
                        .reason("Unmuted")
                        .build();
        plugin.getPunishmentHistoryManager().logPunishment(record);

        TextUtil.sendSuccess(sender, target.getName() + " has been unmuted!");

        if (target.isOnline()) {
            TextUtil.sendSuccess(target.getPlayer(), "You have been unmuted!");
        }

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
