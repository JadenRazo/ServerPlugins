package net.serverplugins.adminvelocity.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.serverplugins.adminvelocity.alts.AltDetector;
import net.serverplugins.adminvelocity.database.PunishmentTable;
import net.serverplugins.adminvelocity.messaging.VelocityTextUtil;
import org.slf4j.Logger;

/** /galts <player> - Show alt accounts command. */
public class GAltsCommand implements SimpleCommand {

    private static final String PERMISSION = "serveradmin.galts";

    private final ProxyServer server;
    private final Logger logger;
    private final AltDetector altDetector;
    private final PunishmentTable punishmentTable;

    public GAltsCommand(
            ProxyServer server,
            Logger logger,
            AltDetector altDetector,
            PunishmentTable punishmentTable) {
        this.server = server;
        this.logger = logger;
        this.altDetector = altDetector;
        this.punishmentTable = punishmentTable;
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
            VelocityTextUtil.sendError(source, "Usage: /galts <player>");
            return;
        }

        String targetName = args[0];
        Optional<Player> targetOpt = server.getPlayer(targetName);

        if (targetOpt.isEmpty()) {
            VelocityTextUtil.sendError(source, "Player '" + targetName + "' not found.");
            return;
        }

        Player target = targetOpt.get();

        altDetector
                .getAlts(target.getUniqueId())
                .thenAccept(
                        altMap -> {
                            if (altMap.isEmpty() || altMap.size() == 1) {
                                VelocityTextUtil.sendInfo(
                                        source, targetName + " has no known alt accounts.");
                                return;
                            }

                            VelocityTextUtil.send(
                                    source,
                                    "<gold>Alt accounts for <white>"
                                            + targetName
                                            + "<gold> ("
                                            + (altMap.size() - 1)
                                            + "):");

                            // Check each alt for bans
                            for (Map.Entry<UUID, String> entry : altMap.entrySet()) {
                                if (entry.getKey().equals(target.getUniqueId())) {
                                    continue; // Skip the player themselves
                                }

                                String altName = entry.getValue();
                                UUID altUuid = entry.getKey();

                                punishmentTable
                                        .getActiveBan(altUuid)
                                        .thenAccept(
                                                ban -> {
                                                    boolean isOnline =
                                                            server.getPlayer(altUuid).isPresent();
                                                    String status =
                                                            isOnline
                                                                    ? "<green>(Online)"
                                                                    : "<gray>(Offline)";
                                                    String banStatus =
                                                            ban != null && !ban.isExpired()
                                                                    ? " <red><bold>[BANNED]"
                                                                    : "";

                                                    VelocityTextUtil.send(
                                                            source,
                                                            "  <white>"
                                                                    + altName
                                                                    + " "
                                                                    + status
                                                                    + banStatus);
                                                });
                            }
                        })
                .exceptionally(
                        ex -> {
                            VelocityTextUtil.sendError(
                                    source, "Failed to retrieve alts: " + ex.getMessage());
                            logger.error("Error executing galts: ", ex);
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
