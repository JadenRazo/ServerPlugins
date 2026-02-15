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

/** /gban <player> [duration] [reason] - Global ban command. */
public class GBanCommand implements SimpleCommand {

    private static final String PERMISSION = "serveradmin.gban";

    private final ProxyServer server;
    private final Logger logger;
    private final PunishmentEnforcer punishmentEnforcer;
    private final String defaultReason;

    public GBanCommand(
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
            VelocityTextUtil.sendError(source, "Usage: /gban <player> [duration] [reason]");
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

        // Parse duration and reason
        Long durationMs = null;
        String reason = defaultReason;

        if (args.length >= 2) {
            // Try to parse duration
            Optional<Long> parsedDuration = parseDuration(args[1]);
            if (parsedDuration.isPresent()) {
                durationMs = parsedDuration.get();
                // Reason starts at index 2
                if (args.length >= 3) {
                    reason = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                }
            } else {
                // First arg after player name is reason
                reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            }
        }

        // Execute ban
        final Long finalDuration = durationMs;
        final String finalReason = reason;

        punishmentEnforcer
                .createBan(
                        target.getUniqueId(),
                        target.getUsername(),
                        staffName,
                        finalReason,
                        finalDuration)
                .thenAccept(
                        punishment -> {
                            String durationStr =
                                    finalDuration == null
                                            ? "permanently"
                                            : "for " + punishment.getFormattedDuration();
                            VelocityTextUtil.sendSuccess(
                                    source,
                                    "Banned "
                                            + targetName
                                            + " "
                                            + durationStr
                                            + " - "
                                            + finalReason);
                        })
                .exceptionally(
                        ex -> {
                            VelocityTextUtil.sendError(
                                    source, "Failed to ban player: " + ex.getMessage());
                            logger.error("Error executing gban: ", ex);
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

        if (args.length == 2) {
            // Suggest duration formats
            return List.of("permanent", "30d", "7d", "1d", "12h", "1h");
        }

        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(PERMISSION);
    }

    /**
     * Parses a duration string like "30d", "7d12h", "1h30m" into milliseconds.
     *
     * @param input Duration string
     * @return Optional duration in milliseconds
     */
    private Optional<Long> parseDuration(String input) {
        if (input.equalsIgnoreCase("permanent") || input.equalsIgnoreCase("perm")) {
            return Optional.of(null);
        }

        try {
            long totalMs = 0;
            String remaining = input.toLowerCase();

            // Parse days
            if (remaining.contains("d")) {
                int idx = remaining.indexOf("d");
                long days = Long.parseLong(remaining.substring(0, idx));
                totalMs += days * 24 * 60 * 60 * 1000;
                remaining = remaining.substring(idx + 1);
            }

            // Parse hours
            if (remaining.contains("h")) {
                int idx = remaining.indexOf("h");
                long hours = Long.parseLong(remaining.substring(0, idx));
                totalMs += hours * 60 * 60 * 1000;
                remaining = remaining.substring(idx + 1);
            }

            // Parse minutes
            if (remaining.contains("m")) {
                int idx = remaining.indexOf("m");
                long minutes = Long.parseLong(remaining.substring(0, idx));
                totalMs += minutes * 60 * 1000;
                remaining = remaining.substring(idx + 1);
            }

            // Parse seconds
            if (remaining.contains("s")) {
                int idx = remaining.indexOf("s");
                long seconds = Long.parseLong(remaining.substring(0, idx));
                totalMs += seconds * 1000;
            }

            return totalMs > 0 ? Optional.of(totalMs) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
