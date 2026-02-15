package net.serverplugins.commands.commands;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.ServerCommands;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class GamemodeCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;
    private final GameMode forcedMode;

    public GamemodeCommand(ServerCommands plugin) {
        this(plugin, null);
    }

    public GamemodeCommand(ServerCommands plugin, GameMode forcedMode) {
        this.plugin = plugin;
        this.forcedMode = forcedMode;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("servercommands.gamemode")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        GameMode mode = forcedMode;
        Player target;
        int argOffset = 0;

        if (forcedMode == null) {
            if (args.length < 1) {
                TextUtil.sendError(
                        sender, "Usage: " + ColorScheme.COMMAND + "/gamemode <mode> [player]");
                return true;
            }
            mode = parseGameMode(args[0]);
            if (mode == null) {
                TextUtil.sendError(
                        sender, "Invalid game mode! Use: survival, creative, adventure, spectator");
                return true;
            }
            argOffset = 1;
        }

        if (args.length > argOffset) {
            if (!sender.hasPermission("servercommands.gamemode.others")) {
                TextUtil.sendError(
                        sender, "You don't have permission to change other players' gamemodes!");
                return true;
            }
            target = Bukkit.getPlayer(args[argOffset]);
            if (target == null) {
                CommonMessages.PLAYER_NOT_FOUND.send(sender);
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                CommonMessages.CONSOLE_ONLY.send(sender);
                return true;
            }
            target = (Player) sender;
        }

        String modePerm = "servercommands.gamemode." + mode.name().toLowerCase();
        if (!sender.hasPermission(modePerm) && !sender.hasPermission("servercommands.gamemode.*")) {
            TextUtil.sendError(
                    sender,
                    "You don't have permission to use " + mode.name().toLowerCase() + " mode!");
            return true;
        }

        target.setGameMode(mode);

        String modeName = mode.name().charAt(0) + mode.name().substring(1).toLowerCase();
        if (target.equals(sender)) {
            TextUtil.sendSuccess(
                    sender,
                    "Gamemode set to "
                            + ColorScheme.HIGHLIGHT
                            + modeName
                            + ColorScheme.SUCCESS
                            + "!");
        } else {
            TextUtil.sendSuccess(
                    sender,
                    "Set "
                            + ColorScheme.HIGHLIGHT
                            + target.getName()
                            + ColorScheme.SUCCESS
                            + "'s gamemode to "
                            + ColorScheme.HIGHLIGHT
                            + modeName
                            + ColorScheme.SUCCESS
                            + "!");
            TextUtil.sendSuccess(
                    target,
                    "Your gamemode has been set to "
                            + ColorScheme.HIGHLIGHT
                            + modeName
                            + ColorScheme.SUCCESS
                            + "!");
        }

        return true;
    }

    private GameMode parseGameMode(String input) {
        return switch (input.toLowerCase()) {
            case "0", "s", "survival" -> GameMode.SURVIVAL;
            case "1", "c", "creative" -> GameMode.CREATIVE;
            case "2", "a", "adventure" -> GameMode.ADVENTURE;
            case "3", "sp", "spectator" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (forcedMode != null) {
            if (args.length == 1) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                        .toList();
            }
        } else {
            if (args.length == 1) {
                return Arrays.asList("survival", "creative", "adventure", "spectator").stream()
                        .filter(m -> m.startsWith(args[0].toLowerCase()))
                        .toList();
            }
            if (args.length == 2) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
            }
        }
        return Collections.emptyList();
    }
}
