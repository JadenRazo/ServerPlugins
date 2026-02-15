package net.serverplugins.adminvelocity.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.List;
import java.util.Optional;
import net.serverplugins.adminvelocity.messaging.VelocityTextUtil;
import net.serverplugins.adminvelocity.punishment.PunishmentEnforcer;
import org.slf4j.Logger;

/** /gkick <player> [reason] - Global kick command. */
public class GKickCommand implements SimpleCommand {

    private static final String PERMISSION = "serveradmin.gkick";

    private final ProxyServer server;
    private final Logger logger;
    private final PunishmentEnforcer punishmentEnforcer;
    private final String defaultReason;

    public GKickCommand(
            ProxyServer server,
            Logger logger,
            PunishmentEnforcer punishmentEnforcer,
            String defaultReason) {
        this.server = server;
        this.logger = logger;
        this.punishmentEnforcer = punishmentEnforcer;
        this.defaultReason = defaultReason;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (!source.hasPermission(PERMISSION)) {
            VelocityTextUtil.sendError(source, "You don't have permission to use this command.");
            return;
        }

        if (args.length < 1) {
            VelocityTextUtil.sendError(source, "Usage: /gkick <player> [reason]");
            return;
        }

        String targetName = args[0];
        Optional<Player> targetOpt = server.getPlayer(targetName);

        if (targetOpt.isEmpty()) {
            VelocityTextUtil.sendError(source, "Player '" + targetName + "' not found.");
            return;
        }

        Player target = targetOpt.get();
        String staffName = source instanceof Player ? ((Player) source).getUsername() : "Console";
        String reason =
                args.length > 1
                        ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                        : defaultReason;

        punishmentEnforcer
                .createKick(target.getUniqueId(), target.getUsername(), staffName, reason)
                .thenAccept(
                        punishment -> {
                            VelocityTextUtil.sendSuccess(
                                    source, "Kicked " + targetName + " - " + reason);
                        })
                .exceptionally(
                        ex -> {
                            VelocityTextUtil.sendError(
                                    source, "Failed to kick player: " + ex.getMessage());
                            logger.error("Error executing gkick: ", ex);
                            return null;
                        });
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length == 1) {
            // Suggest online players
            String partial = args[0].toLowerCase();
            return server.getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .toList();
        }

        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(PERMISSION);
    }
}
