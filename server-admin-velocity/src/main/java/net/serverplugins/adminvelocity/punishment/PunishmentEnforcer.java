package net.serverplugins.adminvelocity.punishment;

import com.google.gson.JsonObject;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serverplugins.adminvelocity.database.PunishmentTable;
import net.serverplugins.adminvelocity.messaging.VelocityTextUtil;
import net.serverplugins.adminvelocity.redis.AdminRedisClient;
import org.slf4j.Logger;

/** Core enforcement logic for bans, mutes, and kicks on Velocity proxy. */
public class PunishmentEnforcer {

    private final ProxyServer server;
    private final Logger logger;
    private final PunishmentTable punishmentTable;
    private final AdminRedisClient redisClient;

    public PunishmentEnforcer(
            ProxyServer server,
            Logger logger,
            PunishmentTable punishmentTable,
            AdminRedisClient redisClient) {
        this.server = server;
        this.logger = logger;
        this.punishmentTable = punishmentTable;
        this.redisClient = redisClient;
    }

    /**
     * Checks if a player has an active ban.
     *
     * @param uuid Player UUID
     * @return CompletableFuture with Optional punishment
     */
    public CompletableFuture<Optional<VelocityPunishment>> checkBan(UUID uuid) {
        return punishmentTable
                .getActiveBan(uuid)
                .thenApply(
                        punishment -> {
                            if (punishment != null && !punishment.isExpired()) {
                                return Optional.of(punishment);
                            }
                            return Optional.empty();
                        });
    }

    /**
     * Enforces a ban by disconnecting the player with a formatted message.
     *
     * @param player Player to disconnect
     * @param punishment Ban punishment
     */
    public void enforceBan(Player player, VelocityPunishment punishment) {
        String message = formatBanMessage(punishment);
        player.disconnect(VelocityTextUtil.parse(message));
        logger.info(
                "Enforced ban on {} (reason: {})", player.getUsername(), punishment.getReason());
    }

    /**
     * Creates a new ban punishment.
     *
     * @param targetUuid Target player UUID
     * @param targetName Target player name
     * @param staffName Staff member name
     * @param reason Ban reason
     * @param durationMs Duration in milliseconds (null for permanent)
     * @return CompletableFuture with created punishment
     */
    public CompletableFuture<VelocityPunishment> createBan(
            UUID targetUuid, String targetName, String staffName, String reason, Long durationMs) {
        VelocityPunishment punishment =
                VelocityPunishment.builder()
                        .targetUuid(targetUuid)
                        .targetName(targetName)
                        .staffName(staffName)
                        .type(VelocityPunishmentType.BAN)
                        .reason(reason)
                        .durationMs(durationMs)
                        .sourceServer("velocity")
                        .build();

        return punishmentTable
                .createPunishment(punishment)
                .thenApply(
                        id -> {
                            punishment.setId(id);

                            // Publish to Redis
                            JsonObject json = new JsonObject();
                            json.addProperty("type", "PUNISHMENT_CREATED");
                            json.addProperty("id", id);
                            json.addProperty("targetUuid", targetUuid.toString());
                            json.addProperty("targetName", targetName);
                            json.addProperty("staffName", staffName);
                            json.addProperty("punishmentType", "BAN");
                            json.addProperty("reason", reason);
                            if (durationMs != null) {
                                json.addProperty("durationMs", durationMs);
                                json.addProperty("expiresAt", punishment.getExpiresAt());
                            }
                            json.addProperty("permanent", punishment.isPermanent());
                            json.addProperty("server", "velocity");
                            json.addProperty("timestamp", System.currentTimeMillis());
                            redisClient.publish(AdminRedisClient.CHANNEL_PUNISHMENT, json);

                            // Kick player if online
                            server.getPlayer(targetUuid)
                                    .ifPresent(player -> enforceBan(player, punishment));

                            return punishment;
                        });
    }

    /**
     * Pardons a ban.
     *
     * @param targetUuid Target player UUID
     * @param staffName Staff member name
     * @param reason Pardon reason
     * @return CompletableFuture with success status
     */
    public CompletableFuture<Boolean> pardonBan(UUID targetUuid, String staffName, String reason) {
        return punishmentTable
                .pardonPunishment(targetUuid, "BAN", staffName, reason)
                .thenApply(
                        success -> {
                            if (success) {
                                // Publish to Redis
                                JsonObject json = new JsonObject();
                                json.addProperty("type", "PUNISHMENT_PARDONED");
                                json.addProperty("targetUuid", targetUuid.toString());
                                json.addProperty("punishmentType", "BAN");
                                json.addProperty("staffName", staffName);
                                json.addProperty("reason", reason);
                                json.addProperty("timestamp", System.currentTimeMillis());
                                redisClient.publish(AdminRedisClient.CHANNEL_PUNISHMENT, json);
                            }
                            return success;
                        });
    }

    /**
     * Creates a new mute punishment.
     *
     * @param targetUuid Target player UUID
     * @param targetName Target player name
     * @param staffName Staff member name
     * @param reason Mute reason
     * @param durationMs Duration in milliseconds (null for permanent)
     * @return CompletableFuture with created punishment
     */
    public CompletableFuture<VelocityPunishment> createMute(
            UUID targetUuid, String targetName, String staffName, String reason, Long durationMs) {
        VelocityPunishment punishment =
                VelocityPunishment.builder()
                        .targetUuid(targetUuid)
                        .targetName(targetName)
                        .staffName(staffName)
                        .type(VelocityPunishmentType.MUTE)
                        .reason(reason)
                        .durationMs(durationMs)
                        .sourceServer("velocity")
                        .build();

        return punishmentTable
                .createPunishment(punishment)
                .thenApply(
                        id -> {
                            punishment.setId(id);

                            // Publish to Redis (for Bukkit servers to sync)
                            JsonObject json = new JsonObject();
                            json.addProperty("type", "PUNISHMENT_CREATED");
                            json.addProperty("id", id);
                            json.addProperty("targetUuid", targetUuid.toString());
                            json.addProperty("targetName", targetName);
                            json.addProperty("staffName", staffName);
                            json.addProperty("punishmentType", "MUTE");
                            json.addProperty("reason", reason);
                            if (durationMs != null) {
                                json.addProperty("durationMs", durationMs);
                                json.addProperty("expiresAt", punishment.getExpiresAt());
                            }
                            json.addProperty("permanent", punishment.isPermanent());
                            json.addProperty("server", "velocity");
                            json.addProperty("timestamp", System.currentTimeMillis());
                            redisClient.publish(AdminRedisClient.CHANNEL_PUNISHMENT, json);

                            // Notify player if online
                            server.getPlayer(targetUuid)
                                    .ifPresent(
                                            player -> {
                                                String msg = formatMuteMessage(punishment);
                                                VelocityTextUtil.send(player, msg);
                                            });

                            return punishment;
                        });
    }

    /**
     * Pardons a mute.
     *
     * @param targetUuid Target player UUID
     * @param staffName Staff member name
     * @param reason Pardon reason
     * @return CompletableFuture with success status
     */
    public CompletableFuture<Boolean> pardonMute(UUID targetUuid, String staffName, String reason) {
        return punishmentTable
                .pardonPunishment(targetUuid, "MUTE", staffName, reason)
                .thenApply(
                        success -> {
                            if (success) {
                                // Publish to Redis
                                JsonObject json = new JsonObject();
                                json.addProperty("type", "PUNISHMENT_PARDONED");
                                json.addProperty("targetUuid", targetUuid.toString());
                                json.addProperty("punishmentType", "MUTE");
                                json.addProperty("staffName", staffName);
                                json.addProperty("reason", reason);
                                json.addProperty("timestamp", System.currentTimeMillis());
                                redisClient.publish(AdminRedisClient.CHANNEL_PUNISHMENT, json);

                                // Notify player if online
                                server.getPlayer(targetUuid)
                                        .ifPresent(
                                                player ->
                                                        VelocityTextUtil.sendSuccess(
                                                                player, "You have been unmuted."));
                            }
                            return success;
                        });
    }

    /**
     * Creates a kick punishment.
     *
     * @param targetUuid Target player UUID
     * @param targetName Target player name
     * @param staffName Staff member name
     * @param reason Kick reason
     * @return CompletableFuture with created punishment
     */
    public CompletableFuture<VelocityPunishment> createKick(
            UUID targetUuid, String targetName, String staffName, String reason) {
        VelocityPunishment punishment =
                VelocityPunishment.builder()
                        .targetUuid(targetUuid)
                        .targetName(targetName)
                        .staffName(staffName)
                        .type(VelocityPunishmentType.KICK)
                        .reason(reason)
                        .sourceServer("velocity")
                        .build();

        return punishmentTable
                .createPunishment(punishment)
                .thenApply(
                        id -> {
                            punishment.setId(id);

                            // Kick player from proxy
                            server.getPlayer(targetUuid)
                                    .ifPresent(
                                            player -> {
                                                String msg = formatKickMessage(punishment);
                                                player.disconnect(VelocityTextUtil.parse(msg));
                                            });

                            return punishment;
                        });
    }

    // ========== MESSAGE FORMATTING ==========

    private String formatBanMessage(VelocityPunishment punishment) {
        StringBuilder sb = new StringBuilder();
        sb.append("<red><bold>YOU HAVE BEEN BANNED\n\n");
        sb.append("<gray>Reason: <white>")
                .append(
                        punishment.getReason() != null
                                ? punishment.getReason()
                                : "No reason specified");

        if (punishment.isPermanent()) {
            sb.append("\n\n<red>This ban is permanent.");
        } else {
            sb.append("\n<gray>Duration: <white>").append(punishment.getFormattedDuration());
            sb.append("\n<gray>Expires: <white>")
                    .append(formatTimestamp(punishment.getExpiresAt()));
        }

        sb.append("\n\n<gray>Banned by: <white>").append(punishment.getStaffName());
        return sb.toString();
    }

    private String formatMuteMessage(VelocityPunishment punishment) {
        StringBuilder sb = new StringBuilder();
        sb.append("<red><bold>YOU HAVE BEEN MUTED\n");

        String duration =
                punishment.isPermanent()
                        ? "permanently"
                        : "for " + punishment.getFormattedDuration();

        sb.append("<gray>You were muted ").append(duration).append(" by <white>");
        sb.append(punishment.getStaffName());
        sb.append("\n<gray>Reason: <white>");
        sb.append(punishment.getReason() != null ? punishment.getReason() : "No reason specified");

        return sb.toString();
    }

    private String formatKickMessage(VelocityPunishment punishment) {
        StringBuilder sb = new StringBuilder();
        sb.append("<red><bold>You have been kicked\n\n");
        sb.append("<gray>Reason: <white>")
                .append(
                        punishment.getReason() != null
                                ? punishment.getReason()
                                : "No reason specified");
        sb.append("\n\n<gray>Kicked by: <white>").append(punishment.getStaffName());
        return sb.toString();
    }

    private String formatTimestamp(Long timestamp) {
        if (timestamp == null) return "Never";
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm");
        return sdf.format(new Date(timestamp));
    }
}
