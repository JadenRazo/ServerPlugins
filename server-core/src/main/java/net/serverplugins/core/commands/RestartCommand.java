package net.serverplugins.core.commands;

import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.core.ServerCore;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RestartCommand implements CommandExecutor {

    private final ServerCore plugin;
    private boolean restartScheduled = false;

    public RestartCommand(ServerCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("servercore.restart")) {
            TextUtil.send(sender, "<red>You don't have permission to restart the server!");
            return true;
        }

        if (restartScheduled) {
            TextUtil.send(sender, "<red>A restart is already scheduled!");
            return true;
        }

        // Default countdown: 10 seconds
        int countdown = 10;

        // Parse countdown argument if provided
        if (args.length > 0) {
            try {
                countdown = Integer.parseInt(args[0]);
                if (countdown < 0 || countdown > 300) {
                    TextUtil.send(sender, "<red>Countdown must be between 0 and 300 seconds!");
                    return true;
                }
            } catch (NumberFormatException e) {
                TextUtil.send(sender, "<red>Invalid countdown value! Usage: /restart [seconds]");
                return true;
            }
        }

        restartScheduled = true;
        final int finalCountdown = countdown;

        // Get custom shutdown message from config
        String shutdownMessage = plugin.getCoreConfig().getShutdownMessage();

        if (finalCountdown == 0) {
            // Immediate restart
            broadcastShutdownMessage(shutdownMessage);
            scheduleRestart(0);
        } else {
            // Announce restart
            TextUtil.send(
                    sender,
                    "<green>Server restart scheduled in <white>"
                            + finalCountdown
                            + " <green>seconds.");
            broadcastMessage(
                    "<red><bold>SERVER RESTART</bold> <gray>- Server will restart in <white>"
                            + finalCountdown
                            + " <gray>seconds!");

            // Countdown warnings at specific intervals
            scheduleCountdownWarnings(finalCountdown, shutdownMessage);
        }

        return true;
    }

    private void scheduleCountdownWarnings(int totalSeconds, String shutdownMessage) {
        // Warning intervals: 60s, 30s, 15s, 10s, 5s, 4s, 3s, 2s, 1s
        int[] warnings = {60, 30, 15, 10, 5, 4, 3, 2, 1};

        for (int warning : warnings) {
            if (warning < totalSeconds) {
                int delay = (totalSeconds - warning) * 20; // Convert to ticks
                Bukkit.getScheduler()
                        .runTaskLater(
                                plugin,
                                () -> {
                                    broadcastMessage(
                                            "<red><bold>SERVER RESTART</bold> <gray>- Server will restart in <white>"
                                                    + warning
                                                    + " <gray>second"
                                                    + (warning == 1 ? "" : "s")
                                                    + "!");
                                },
                                delay);
            }
        }

        // Schedule final shutdown message and restart
        int shutdownDelay = (totalSeconds - 1) * 20; // 1 second before restart
        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            broadcastShutdownMessage(shutdownMessage);
                        },
                        shutdownDelay);

        scheduleRestart(totalSeconds * 20); // Convert to ticks
    }

    private void scheduleRestart(long delayTicks) {
        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            // Kick all players with custom message before restart
                            String kickMessage = plugin.getCoreConfig().getShutdownMessage();
                            for (Player player : Bukkit.getOnlinePlayers()) {
                                player.kick(TextUtil.parse(kickMessage));
                            }

                            // Restart server using Spigot's restart command
                            Bukkit.spigot().restart();
                        },
                        delayTicks);
    }

    private void broadcastMessage(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            TextUtil.send(player, message);
        }
        // Log without formatting codes
        plugin.getLogger().info("Broadcast: " + message.replaceAll("<[^>]+>", ""));
    }

    private void broadcastShutdownMessage(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            TextUtil.send(player, message);
        }
        // Log without formatting codes
        plugin.getLogger().info("Shutdown message: " + message.replaceAll("<[^>]+>", ""));
    }
}
