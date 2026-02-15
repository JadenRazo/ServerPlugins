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

public class WeatherCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;

    public WeatherCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("servercommands.weather")) {
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
            String current =
                    world.hasStorm() ? (world.isThundering() ? "thunder" : "rain") : "clear";
            TextUtil.sendError(sender, "Usage: /weather <clear|rain|thunder> [duration]");
            TextUtil.send(sender, "<gray>Current weather: " + current);
            return true;
        }

        String weather = args[0].toLowerCase();
        int duration = 6000;

        if (args.length > 1) {
            try {
                duration = Integer.parseInt(args[1]) * 20;
            } catch (NumberFormatException e) {
                TextUtil.sendError(sender, "Invalid duration!");
                return true;
            }
        }

        switch (weather) {
            case "clear", "sun", "sunny" -> {
                world.setStorm(false);
                world.setThundering(false);
                world.setWeatherDuration(duration);
                TextUtil.sendSuccess(sender, "Weather set to clear in " + world.getName() + "!");
            }
            case "rain", "storm" -> {
                world.setStorm(true);
                world.setThundering(false);
                world.setWeatherDuration(duration);
                TextUtil.sendSuccess(sender, "Weather set to rain in " + world.getName() + "!");
            }
            case "thunder", "thunderstorm" -> {
                world.setStorm(true);
                world.setThundering(true);
                world.setWeatherDuration(duration);
                world.setThunderDuration(duration);
                TextUtil.sendSuccess(sender, "Weather set to thunder in " + world.getName() + "!");
            }
            default ->
                    TextUtil.sendError(sender, "Unknown weather type! Use: clear, rain, thunder");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("clear", "rain", "thunder").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }
}
