package net.serverplugins.commands.commands;

import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.serverplugins.commands.ServerCommands;
import net.serverplugins.commands.data.punishment.PunishmentRecord;
import net.serverplugins.commands.data.punishment.PunishmentType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class KickCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;

    public KickCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("servercommands.kick")) {
            plugin.getCommandsConfig().getMessenger().send(sender, "no-permission");
            return true;
        }

        if (args.length < 1) {
            plugin.getCommandsConfig().getMessenger().send(sender, "kick-usage");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            plugin.getCommandsConfig().getMessenger().send(sender, "player-not-found");
            return true;
        }

        if (target.hasPermission("servercommands.kick.exempt")) {
            plugin.getCommandsConfig().getMessenger().send(sender, "kick-exempt");
            return true;
        }

        String reason =
                args.length > 1
                        ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                        : "No reason provided";
        String kickerName = sender instanceof Player ? sender.getName() : "Console";

        // Log to punishment history before kick (target object still valid)
        PunishmentRecord record =
                PunishmentRecord.builder()
                        .target(target.getUniqueId(), target.getName())
                        .staff(sender)
                        .type(PunishmentType.KICK)
                        .reason(reason)
                        .build();
        plugin.getPunishmentHistoryManager().logPunishment(record);

        // Send kick message
        String kickMessage =
                plugin.getCommandsConfig()
                        .getRawMessage("kick-message")
                        .replace("{reason}", reason);
        target.kick(MiniMessage.miniMessage().deserialize(kickMessage));

        // Broadcast
        String broadcastMessage =
                plugin.getCommandsConfig()
                        .getRawMessage("kick-broadcast")
                        .replace("{player}", target.getName())
                        .replace("{staff}", kickerName)
                        .replace("{reason}", reason);
        Bukkit.broadcast(MiniMessage.miniMessage().deserialize(broadcastMessage));

        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.hasPermission("servercommands.kick.exempt"))
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }
}
