package net.serverplugins.adminvelocity.listeners;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.serverplugins.adminvelocity.alts.AltDetector;
import net.serverplugins.adminvelocity.messaging.VelocityTextUtil;
import net.serverplugins.adminvelocity.punishment.PunishmentEnforcer;
import net.serverplugins.adminvelocity.punishment.VelocityPunishment;
import org.slf4j.Logger;

/** Handles player connection events for ban checking and alt detection. */
public class ConnectionListener {

    private final Logger logger;
    private final PunishmentEnforcer punishmentEnforcer;
    private final AltDetector altDetector;
    private final boolean altsEnabled;
    private final boolean denyBannedAlts;

    public ConnectionListener(
            Logger logger,
            PunishmentEnforcer punishmentEnforcer,
            AltDetector altDetector,
            boolean altsEnabled,
            boolean denyBannedAlts) {
        this.logger = logger;
        this.punishmentEnforcer = punishmentEnforcer;
        this.altDetector = altDetector;
        this.altsEnabled = altsEnabled;
        this.denyBannedAlts = denyBannedAlts;
    }

    /**
     * Checks for active bans and alt accounts on login.
     *
     * <p>This runs synchronously on Velocity's event loop. We do a quick database check here - if
     * it times out, we let them through (fail-open).
     */
    @Subscribe(order = PostOrder.EARLY)
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();

        // Check for active ban
        try {
            Optional<VelocityPunishment> ban =
                    punishmentEnforcer.checkBan(player.getUniqueId()).get();
            if (ban.isPresent()) {
                VelocityPunishment punishment = ban.get();
                Component banMessage = VelocityTextUtil.parse(formatBanDenial(punishment));
                event.setResult(LoginEvent.ComponentResult.denied(banMessage));
                logger.info(
                        "Denied login for banned player: {} (reason: {})",
                        player.getUsername(),
                        punishment.getReason());
                return;
            }
        } catch (Exception e) {
            logger.error(
                    "Error checking ban status for {}: {}", player.getUsername(), e.getMessage());
            // Fail open - let them through
        }

        // Check for alts (non-blocking, happens after login)
        if (altsEnabled) {
            altDetector
                    .checkAndRecordPlayer(player)
                    .thenAccept(
                            result -> {
                                if (denyBannedAlts && result.hasBannedAlt()) {
                                    player.disconnect(
                                            VelocityTextUtil.parse(
                                                    "<red><bold>Connection Denied\n\n"
                                                            + "<gray>An account associated with your IP address is banned.\n"
                                                            + "<gray>If you believe this is an error, please contact staff."));
                                    logger.info(
                                            "Denied login for {} - banned alt detected",
                                            player.getUsername());
                                }
                            })
                    .exceptionally(
                            ex -> {
                                logger.error(
                                        "Error checking alts for {}: {}",
                                        player.getUsername(),
                                        ex.getMessage());
                                return null;
                            });
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        // Cleanup if needed (currently nothing to clean up)
        // Note: Vanished players are tracked in ServerAdminVelocity.vanishedPlayers
        // The set is updated via Redis messages from Bukkit servers
    }

    private String formatBanDenial(VelocityPunishment punishment) {
        StringBuilder sb = new StringBuilder();
        sb.append("<red><bold>YOU ARE BANNED\n\n");
        sb.append("<gray>Reason: <white>")
                .append(
                        punishment.getReason() != null
                                ? punishment.getReason()
                                : "No reason specified");

        if (punishment.isPermanent()) {
            sb.append("\n\n<red>This ban is permanent.");
        } else {
            sb.append("\n<gray>Duration: <white>").append(punishment.getFormattedDuration());
            sb.append("\n<gray>Expires: <white>").append(punishment.getFormattedExpiryDate());
        }

        sb.append("\n\n<gray>Banned by: <white>").append(punishment.getStaffName());
        return sb.toString();
    }
}
