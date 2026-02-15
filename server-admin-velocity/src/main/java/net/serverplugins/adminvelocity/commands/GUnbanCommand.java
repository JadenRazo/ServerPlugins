package net.serverplugins.adminvelocity.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.serverplugins.adminvelocity.messaging.VelocityTextUtil;
import net.serverplugins.adminvelocity.punishment.PunishmentEnforcer;
import org.slf4j.Logger;

/** /gunban <player> [reason] - Global unban command. */
public class GUnbanCommand implements SimpleCommand {

    private static final String PERMISSION = "serveradmin.gunban";

    private final ProxyServer server;
    private final Logger logger;
    private final PunishmentEnforcer punishmentEnforcer;

    public GUnbanCommand(ProxyServer server, Logger logger, PunishmentEnforcer punishmentEnforcer) {
        this.server = server;
        this.logger = logger;
        this.punishmentEnforcer = punishmentEnforcer;
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
            VelocityTextUtil.sendError(source, "Usage: /gunban <player> [reason]");
            return;
        }

        String targetName = args[0];
        String staffName = source instanceof Player ? ((Player) source).getUsername() : "Console";
        String reason =
                args.length > 1
                        ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                        : "Appealed";

        // Try to get UUID from online player or offline lookup
        Optional<Player> targetOpt = server.getPlayer(targetName);
        if (targetOpt.isPresent()) {
            UUID targetUuid = targetOpt.get().getUniqueId();
            pardonBan(source, targetUuid, targetName, staffName, reason);
        } else {
            // For offline players, we'd need a UUID lookup service
            // For now, inform the user to use the online player
            VelocityTextUtil.sendError(
                    source,
                    "Player must be online to unban via Velocity. Use /gunban when they try to"
                            + " connect, or use Bukkit-side commands.");
        }
    }

    private void pardonBan(
            CommandSource source,
            UUID targetUuid,
            String targetName,
            String staffName,
            String reason) {
        punishmentEnforcer
                .pardonBan(targetUuid, staffName, reason)
                .thenAccept(
                        success -> {
                            if (success) {
                                VelocityTextUtil.sendSuccess(
                                        source, "Unbanned " + targetName + " - " + reason);
                            } else {
                                VelocityTextUtil.sendError(
                                        source, "No active ban found for " + targetName);
                            }
                        })
                .exceptionally(
                        ex -> {
                            VelocityTextUtil.sendError(
                                    source, "Failed to unban player: " + ex.getMessage());
                            logger.error("Error executing gunban: ", ex);
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
