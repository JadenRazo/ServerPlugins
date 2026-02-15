package net.serverplugins.adminvelocity.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.List;
import java.util.Optional;
import net.serverplugins.adminvelocity.database.PunishmentTable;
import net.serverplugins.adminvelocity.messaging.VelocityTextUtil;
import net.serverplugins.adminvelocity.punishment.VelocityPunishment;
import org.slf4j.Logger;

/** /ghistory <player> - Show punishment history command. */
public class GHistoryCommand implements SimpleCommand {

    private static final String PERMISSION = "serveradmin.ghistory";
    private static final int DEFAULT_LIMIT = 10;

    private final ProxyServer server;
    private final Logger logger;
    private final PunishmentTable punishmentTable;

    public GHistoryCommand(ProxyServer server, Logger logger, PunishmentTable punishmentTable) {
        this.server = server;
        this.logger = logger;
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
            VelocityTextUtil.sendError(source, "Usage: /ghistory <player>");
            return;
        }

        String targetName = args[0];
        Optional<Player> targetOpt = server.getPlayer(targetName);

        if (targetOpt.isEmpty()) {
            VelocityTextUtil.sendError(source, "Player '" + targetName + "' not found.");
            return;
        }

        Player target = targetOpt.get();

        punishmentTable
                .getPunishmentHistory(target.getUniqueId(), DEFAULT_LIMIT)
                .thenAccept(
                        punishments -> {
                            if (punishments.isEmpty()) {
                                VelocityTextUtil.sendInfo(
                                        source, targetName + " has no punishment history.");
                                return;
                            }

                            VelocityTextUtil.send(
                                    source,
                                    "<gold>Punishment history for <white>"
                                            + targetName
                                            + "<gold> (last "
                                            + punishments.size()
                                            + "):");

                            for (VelocityPunishment punishment : punishments) {
                                String activeStatus =
                                        punishment.isActive() && !punishment.isExpired()
                                                ? " <red><bold>[ACTIVE]"
                                                : "";
                                String duration =
                                        punishment.isPermanent()
                                                ? "permanent"
                                                : punishment.getFormattedDuration();

                                VelocityTextUtil.send(
                                        source,
                                        "  <white>#"
                                                + punishment.getId()
                                                + " <gray>"
                                                + punishment.getType().getDisplayName()
                                                + " <white>("
                                                + duration
                                                + ")"
                                                + activeStatus);
                                VelocityTextUtil.send(
                                        source,
                                        "    <gray>Reason: <white>"
                                                + (punishment.getReason() != null
                                                        ? punishment.getReason()
                                                        : "None"));
                                VelocityTextUtil.send(
                                        source,
                                        "    <gray>By: <white>"
                                                + punishment.getStaffName()
                                                + " <gray>on <white>"
                                                + punishment.getFormattedCreatedDate());

                                if (punishment.getPardonedBy() != null) {
                                    VelocityTextUtil.send(
                                            source,
                                            "    <green>Pardoned by: <white>"
                                                    + punishment.getPardonedBy());
                                }
                            }
                        })
                .exceptionally(
                        ex -> {
                            VelocityTextUtil.sendError(
                                    source, "Failed to retrieve history: " + ex.getMessage());
                            logger.error("Error executing ghistory: ", ex);
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
