package net.serverplugins.adminvelocity.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.List;
import net.serverplugins.adminvelocity.messaging.VelocityTextUtil;
import net.serverplugins.adminvelocity.redis.AdminRedisClient;
import org.slf4j.Logger;

/** Command to coordinate server restart across the network. */
public class GRestartCommand implements SimpleCommand {

    private final ProxyServer server;
    private final Logger logger;
    private final AdminRedisClient redisClient;
    private final int defaultDelay;

    public GRestartCommand(
            ProxyServer server, Logger logger, AdminRedisClient redisClient, int defaultDelay) {
        this.server = server;
        this.logger = logger;
        this.redisClient = redisClient;
        this.defaultDelay = defaultDelay;
    }

    @Override
    public void execute(Invocation invocation) {
        var sender = invocation.source();
        String[] args = invocation.arguments();

        if (!sender.hasPermission("serveradmin.grestart")) {
            sender.sendMessage(VelocityTextUtil.parse("<red>You don't have permission!"));
            return;
        }

        // Usage: /grestart <server|all> [delay] [reason...]
        if (args.length < 1) {
            sender.sendMessage(
                    VelocityTextUtil.parse(
                            "<red>Usage: /grestart <server|all> [delay] [reason...]"));
            sender.sendMessage(
                    VelocityTextUtil.parse("<gray>Example: /grestart smp 30 Applying updates"));
            return;
        }

        String targetServer = args[0];
        int delay = defaultDelay;
        String reason = "Server maintenance";

        // Parse optional delay and reason
        if (args.length >= 2) {
            try {
                delay = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                // Args[1] is part of the reason
                reason = String.join(" ", args).substring(targetServer.length() + 1);
            }

            if (args.length >= 3) {
                // Extract reason starting from args[2]
                StringBuilder reasonBuilder = new StringBuilder();
                for (int i = 2; i < args.length; i++) {
                    if (i > 2) reasonBuilder.append(" ");
                    reasonBuilder.append(args[i]);
                }
                reason = reasonBuilder.toString();
            }
        }

        // Validate target server
        if (!targetServer.equalsIgnoreCase("all")
                && !targetServer.equalsIgnoreCase("smp")
                && !targetServer.equalsIgnoreCase("lobby")) {
            sender.sendMessage(
                    VelocityTextUtil.parse("<red>Invalid server! Use 'all', 'smp', or 'lobby'."));
            return;
        }

        // Get staff name
        String staffName =
                sender instanceof com.velocitypowered.api.proxy.Player
                        ? ((com.velocitypowered.api.proxy.Player) sender).getUsername()
                        : "Console";

        // Publish server control signal
        redisClient.publishServerControl("RESTART", targetServer, delay, reason, staffName);

        // Confirm to sender
        sender.sendMessage(
                VelocityTextUtil.parse(
                        "<green>Restart signal sent to <white>"
                                + targetServer
                                + "</white> with <white>"
                                + delay
                                + "</white> second delay."));
        sender.sendMessage(VelocityTextUtil.parse("<gray>Reason: " + reason));

        logger.info(
                "{} initiated restart of {} (delay: {}s, reason: {})",
                staffName,
                targetServer,
                delay,
                reason);
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length == 1) {
            return List.of("all", "smp", "lobby").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        } else if (args.length == 2) {
            return List.of("10", "30", "60", "120");
        }

        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("serveradmin.grestart");
    }
}
