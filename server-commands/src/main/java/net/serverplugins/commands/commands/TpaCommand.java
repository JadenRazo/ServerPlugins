package net.serverplugins.commands.commands;

import java.util.Collections;
import java.util.List;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.messages.Placeholder;
import net.serverplugins.commands.ServerCommands;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class TpaCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;
    private final boolean tpaHere;

    public TpaCommand(ServerCommands plugin, boolean tpaHere) {
        this.plugin = plugin;
        this.tpaHere = tpaHere;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        String permission = tpaHere ? "servercommands.tpahere" : "servercommands.tpa";
        if (!player.hasPermission(permission)) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        if (args.length < 1) {
            CommonMessages.INVALID_USAGE.send(
                    player,
                    Placeholder.of("usage", "/" + (tpaHere ? "tpahere" : "tpa") + " <player>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            CommonMessages.PLAYER_NOT_FOUND.send(player);
            return true;
        }

        if (target.equals(player)) {
            CommonMessages.CANNOT_TARGET_SELF.send(player);
            return true;
        }

        boolean created = plugin.getTpaManager().createRequest(player, target, tpaHere);
        if (!created) {
            plugin.getCommandsConfig()
                    .getMessenger()
                    .sendError(player, "You already have a pending request to this player!");
            return true;
        }

        if (tpaHere) {
            plugin.getCommandsConfig()
                    .getMessenger()
                    .sendSuccess(
                            player,
                            "Teleport request sent to "
                                    + ColorScheme.HIGHLIGHT
                                    + target.getName()
                                    + ColorScheme.SUCCESS
                                    + "! Asking them to teleport to you.");
            plugin.getCommandsConfig()
                    .getMessenger()
                    .sendRaw(
                            target,
                            ColorScheme.HIGHLIGHT
                                    + player.getName()
                                    + ColorScheme.SUCCESS
                                    + " wants you to teleport to them.");
        } else {
            plugin.getCommandsConfig()
                    .getMessenger()
                    .sendSuccess(
                            player,
                            "Teleport request sent to "
                                    + ColorScheme.HIGHLIGHT
                                    + target.getName()
                                    + ColorScheme.SUCCESS
                                    + "!");
            plugin.getCommandsConfig()
                    .getMessenger()
                    .sendRaw(
                            target,
                            ColorScheme.HIGHLIGHT
                                    + player.getName()
                                    + ColorScheme.SUCCESS
                                    + " wants to teleport to you.");
        }
        plugin.getCommandsConfig()
                .getMessenger()
                .sendRaw(
                        target,
                        ColorScheme.SUCCESS
                                + "Type "
                                + ColorScheme.COMMAND
                                + "/tpaccept"
                                + ColorScheme.SUCCESS
                                + " to accept or "
                                + ColorScheme.COMMAND
                                + "/tpdeny"
                                + ColorScheme.SUCCESS
                                + " to deny.");

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
