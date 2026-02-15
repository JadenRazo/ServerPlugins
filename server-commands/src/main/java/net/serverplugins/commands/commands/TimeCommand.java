package net.serverplugins.commands.commands;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.ServerCommands;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class TimeCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;

    public TimeCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("servercommands.time")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        World world;
        if (sender instanceof Player player) {
            world = player.getWorld();
        } else {
            TextUtil.sendError(
                    sender, "Console cannot use this command without specifying a world!");
            return true;
        }

        if (args.length < 1) {
            TextUtil.sendError(sender, "Usage: /time <day|night|noon|midnight|set <ticks>>");
            TextUtil.send(sender, "<gray>Current time: " + world.getTime());
            return true;
        }

        String action = args[0].toLowerCase();
        long time =
                switch (action) {
                    case "day" -> 1000;
                    case "night" -> 13000;
                    case "noon" -> 6000;
                    case "midnight" -> 18000;
                    case "sunrise" -> 23000;
                    case "sunset" -> 12000;
                    case "set" -> {
                        if (args.length < 2) {
                            TextUtil.sendError(sender, "Usage: /time set <ticks>");
                            yield -1;
                        }
                        try {
                            yield Long.parseLong(args[1]);
                        } catch (NumberFormatException e) {
                            TextUtil.sendError(sender, "Invalid time value!");
                            yield -1;
                        }
                    }
                    case "add" -> {
                        if (args.length < 2) {
                            TextUtil.sendError(sender, "Usage: /time add <ticks>");
                            yield -1;
                        }
                        try {
                            yield world.getTime() + Long.parseLong(args[1]);
                        } catch (NumberFormatException e) {
                            TextUtil.sendError(sender, "Invalid time value!");
                            yield -1;
                        }
                    }
                    default -> {
                        try {
                            yield Long.parseLong(action);
                        } catch (NumberFormatException e) {
                            TextUtil.sendError(
                                    sender,
                                    "Unknown time! Use: day, night, noon, midnight, set, add");
                            yield -1;
                        }
                    }
                };

        if (time < 0) return true;

        world.setTime(time);
        TextUtil.sendSuccess(sender, "Time set to " + time + " in " + world.getName() + "!");

        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList(
                            "day", "night", "noon", "midnight", "sunrise", "sunset", "set", "add")
                    .stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }
}
