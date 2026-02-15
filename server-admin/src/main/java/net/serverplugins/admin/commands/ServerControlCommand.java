package net.serverplugins.admin.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class ServerControlCommand implements CommandExecutor, TabCompleter {

    private final ServerAdmin plugin;

    public ServerControlCommand(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmdName = command.getName().toLowerCase();

        if (cmdName.equals("shutdown") || cmdName.equals("stopserver")) {
            return handleShutdown(sender, args);
        } else if (cmdName.equals("restart") || cmdName.equals("reboot")) {
            return handleRestart(sender, args);
        }

        return false;
    }

    private boolean handleShutdown(CommandSender sender, String[] args) {
        if (!sender.hasPermission("serveradmin.shutdown")) {
            TextUtil.send(sender, "<red>You don't have permission to shutdown the server!");
            return true;
        }

        int delay = 10; // default 10 seconds
        String reason = "Server is shutting down";

        if (args.length > 0) {
            try {
                delay = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                // First arg is part of reason
                reason = String.join(" ", args);
                delay = 10;
            }

            if (args.length > 1) {
                String[] reasonArgs = new String[args.length - 1];
                System.arraycopy(args, 1, reasonArgs, 0, args.length - 1);
                reason = String.join(" ", reasonArgs);
            }
        }

        final String finalReason = reason;
        final int finalDelay = delay;

        // Announce shutdown
        broadcastServerMessage("<red><bold>SERVER SHUTDOWN</bold></red>");
        broadcastServerMessage(
                "<yellow>The server will shut down in <red>" + delay + "</red> seconds.");
        if (!finalReason.equals("Server is shutting down")) {
            broadcastServerMessage("<gray>Reason: <white>" + finalReason);
        }

        // Log the action
        String senderName = sender instanceof Player ? sender.getName() : "Console";
        plugin.getLogger()
                .info(
                        "Server shutdown initiated by "
                                + senderName
                                + " with "
                                + delay
                                + "s delay. Reason: "
                                + finalReason);

        // Schedule countdown and shutdown
        scheduleShutdown(finalDelay, finalReason, false);

        TextUtil.send(
                sender,
                "<green>Server shutdown initiated. Shutting down in " + delay + " seconds.");
        return true;
    }

    private boolean handleRestart(CommandSender sender, String[] args) {
        if (!sender.hasPermission("serveradmin.restart")) {
            TextUtil.send(sender, "<red>You don't have permission to restart the server!");
            return true;
        }

        int delay = 10; // default 10 seconds
        String reason = "Server is restarting";

        if (args.length > 0) {
            try {
                delay = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                // First arg is part of reason
                reason = String.join(" ", args);
                delay = 10;
            }

            if (args.length > 1) {
                String[] reasonArgs = new String[args.length - 1];
                System.arraycopy(args, 1, reasonArgs, 0, args.length - 1);
                reason = String.join(" ", reasonArgs);
            }
        }

        final String finalReason = reason;
        final int finalDelay = delay;

        // Announce restart
        broadcastServerMessage("<gold><bold>SERVER RESTART</bold></gold>");
        broadcastServerMessage(
                "<yellow>The server will restart in <gold>" + delay + "</gold> seconds.");
        if (!finalReason.equals("Server is restarting")) {
            broadcastServerMessage("<gray>Reason: <white>" + finalReason);
        }

        // Log the action
        String senderName = sender instanceof Player ? sender.getName() : "Console";
        plugin.getLogger()
                .info(
                        "Server restart initiated by "
                                + senderName
                                + " with "
                                + delay
                                + "s delay. Reason: "
                                + finalReason);

        // Schedule countdown and restart
        scheduleShutdown(finalDelay, finalReason, true);

        TextUtil.send(
                sender, "<green>Server restart initiated. Restarting in " + delay + " seconds.");
        return true;
    }

    private void scheduleShutdown(int delaySeconds, String reason, boolean isRestart) {
        String action = isRestart ? "restart" : "shutdown";

        // Countdown warnings at specific intervals
        int[] warnings = {60, 30, 15, 10, 5, 4, 3, 2, 1};

        for (int warning : warnings) {
            if (warning < delaySeconds) {
                Bukkit.getScheduler()
                        .runTaskLater(
                                plugin,
                                () -> {
                                    broadcastServerMessage(
                                            "<yellow>Server "
                                                    + action
                                                    + " in <red>"
                                                    + warning
                                                    + "</red> second"
                                                    + (warning != 1 ? "s" : "")
                                                    + "...");
                                },
                                (delaySeconds - warning) * 20L);
            }
        }

        // Actual shutdown/restart
        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            broadcastServerMessage(
                                    "<red>Server "
                                            + (isRestart ? "restarting" : "shutting down")
                                            + " now!");

                            // Kick all players with a nice message
                            net.kyori.adventure.text.Component kickMessage =
                                    isRestart
                                            ? net.kyori.adventure.text.minimessage.MiniMessage
                                                    .miniMessage()
                                                    .deserialize(
                                                            "<gold>Server is restarting\n<gray>Please reconnect in a moment!")
                                            : net.kyori.adventure.text.minimessage.MiniMessage
                                                    .miniMessage()
                                                    .deserialize(
                                                            "<red>Server is shutting down\n<gray>"
                                                                    + reason);

                            for (Player player : Bukkit.getOnlinePlayers()) {
                                player.kick(kickMessage);
                            }

                            // Small delay to ensure kicks are processed
                            Bukkit.getScheduler()
                                    .runTaskLater(
                                            plugin,
                                            () -> {
                                                if (isRestart) {
                                                    // For hosted servers, shutdown triggers
                                                    // auto-restart
                                                    // Spigot's restart-script in spigot.yml can
                                                    // also handle this
                                                    Bukkit.spigot().restart();
                                                } else {
                                                    Bukkit.shutdown();
                                                }
                                            },
                                            20L); // 1 second delay
                        },
                        delaySeconds * 20L);
    }

    private void broadcastServerMessage(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            TextUtil.send(player, message);
        }
        // Also log to console (strip formatting)
        plugin.getLogger().info(message.replaceAll("<[^>]+>", ""));
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("5");
            suggestions.add("10");
            suggestions.add("30");
            suggestions.add("60");
            return suggestions.stream()
                    .filter(s -> s.startsWith(args[0]))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
