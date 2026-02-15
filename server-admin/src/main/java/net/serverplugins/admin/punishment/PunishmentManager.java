package net.serverplugins.admin.punishment;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.admin.redis.AdminRedisHandler;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class PunishmentManager {

    private final ServerAdmin plugin;
    private final PunishmentRepository repository;
    private final CategoryManager categoryManager;
    private final ReasonManager reasonManager;
    // Cache of active mutes to avoid DB calls on every chat message
    private final Map<UUID, Punishment> mutedPlayersCache = new ConcurrentHashMap<>();
    private final Map<UUID, PendingCustomReason> pendingCustomReasons = new ConcurrentHashMap<>();
    private AdminRedisHandler adminRedisHandler;

    public PunishmentManager(
            ServerAdmin plugin,
            PunishmentRepository repository,
            CategoryManager categoryManager,
            ReasonManager reasonManager) {
        this.plugin = plugin;
        this.repository = repository;
        this.categoryManager = categoryManager;
        this.reasonManager = reasonManager;

        loadActiveMutes();
        startExpirationChecker();
    }

    private static class PendingCustomReason {
        final OfflinePlayer target;
        final PunishmentType type;
        final long expiresAt;

        PendingCustomReason(OfflinePlayer target, PunishmentType type) {
            this.target = target;
            this.type = type;
            this.expiresAt = System.currentTimeMillis() + 60000; // 60 second timeout
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private void loadActiveMutes() {
        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            for (Player player : Bukkit.getOnlinePlayers()) {
                                Punishment mute =
                                        repository.getActivePunishment(
                                                player.getUniqueId(), PunishmentType.MUTE);
                                if (mute != null && !mute.isExpired()) {
                                    mutedPlayersCache.put(player.getUniqueId(), mute);
                                }
                            }
                        });
    }

    private void startExpirationChecker() {
        Bukkit.getScheduler()
                .runTaskTimerAsynchronously(
                        plugin,
                        () -> {
                            repository.expireOldPunishments();

                            // Check cached mutes for expiration
                            Iterator<Map.Entry<UUID, Punishment>> iter =
                                    mutedPlayersCache.entrySet().iterator();
                            while (iter.hasNext()) {
                                Map.Entry<UUID, Punishment> entry = iter.next();
                                UUID uuid = entry.getKey();
                                Punishment mute = entry.getValue();
                                if (mute.isExpired()) {
                                    iter.remove();
                                    Player player = Bukkit.getPlayer(uuid);
                                    if (player != null) {
                                        Bukkit.getScheduler()
                                                .runTask(
                                                        plugin,
                                                        () ->
                                                                TextUtil.sendSuccess(
                                                                        player,
                                                                        "Your mute has expired."));
                                    }
                                    // Publish mute expiration to Redis
                                    if (adminRedisHandler != null
                                            && adminRedisHandler.isAvailable()) {
                                        adminRedisHandler.publishMuteSync(uuid, false, null, null);
                                    }
                                }
                            }
                        },
                        20 * 60,
                        20 * 60);
    }

    /**
     * Set the AdminRedisHandler for cross-server coordination.
     *
     * @param handler Redis handler instance
     */
    public void setAdminRedisHandler(AdminRedisHandler handler) {
        this.adminRedisHandler = handler;
    }

    public CompletableFuture<PunishmentResult> punish(PunishmentContext context) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        // Track offense by reason if reasonId is provided
                        if (context.getReasonId() != null && reasonManager != null) {
                            reasonManager.incrementOffenseCount(
                                    context.getTargetUuid(), context.getReasonId());
                        }
                        // Legacy category tracking
                        if (context.getCategory() != null) {
                            categoryManager.incrementOffenseCount(
                                    context.getTargetUuid(), context.getCategory());
                        }

                        Punishment punishment =
                                Punishment.builder()
                                        .targetUuid(context.getTargetUuid())
                                        .targetName(context.getTargetName())
                                        .staffUuid(context.getStaffUuid())
                                        .staffName(context.getStaffName())
                                        .type(context.getType())
                                        .category(context.getCategory())
                                        .reasonId(context.getReasonId())
                                        .offenseNumber(context.getOffenseNumber())
                                        .reason(context.getReason())
                                        .durationMs(context.getDurationMs())
                                        .build();

                        int id = repository.savePunishment(punishment);
                        punishment.setId(id);

                        Bukkit.getScheduler()
                                .runTask(
                                        plugin,
                                        () -> {
                                            applyPunishment(punishment);
                                            notifyStaff(punishment);

                                            // Publish to Redis for cross-server coordination
                                            if (adminRedisHandler != null
                                                    && adminRedisHandler.isAvailable()) {
                                                adminRedisHandler.publishPunishment(
                                                        punishment, "PUNISHMENT_CREATED");

                                                // For mutes, also sync mute status
                                                if (punishment.getType() == PunishmentType.MUTE) {
                                                    adminRedisHandler.publishMuteSync(
                                                            punishment.getTargetUuid(),
                                                            true,
                                                            punishment.getExpiresAt(),
                                                            punishment.getReason());
                                                }

                                                // For bans, request Velocity to kick the player
                                                if (punishment.getType() == PunishmentType.BAN) {
                                                    adminRedisHandler.publishKick(
                                                            punishment.getTargetUuid(),
                                                            punishment.getTargetName(),
                                                            punishment.getStaffName(),
                                                            punishment.getReason());
                                                }
                                            }
                                        });

                        return new PunishmentResult(true, punishment, null);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to apply punishment: " + e.getMessage());
                        return new PunishmentResult(false, null, e.getMessage());
                    }
                });
    }

    private void applyPunishment(Punishment punishment) {
        Player target = Bukkit.getPlayer(punishment.getTargetUuid());

        switch (punishment.getType()) {
            case WARN -> {
                if (target != null) {
                    TextUtil.send(
                            target,
                            "<red><bold>WARNING\n<gray>You have been warned by <white>"
                                    + punishment.getStaffName()
                                    + "\n<gray>Reason: <white>"
                                    + (punishment.getReason() != null
                                            ? punishment.getReason()
                                            : "No reason specified")
                                    + "\n\n<yellow>This warning has been logged on your record.");
                }
            }
            case MUTE -> {
                mutedPlayersCache.put(punishment.getTargetUuid(), punishment);
                if (target != null) {
                    String duration =
                            punishment.isPermanent()
                                    ? "permanently"
                                    : "for " + punishment.getFormattedDuration();
                    TextUtil.send(
                            target,
                            "<red><bold>YOU HAVE BEEN MUTED\n<gray>You were muted "
                                    + duration
                                    + " by <white>"
                                    + punishment.getStaffName()
                                    + "\n<gray>Reason: <white>"
                                    + (punishment.getReason() != null
                                            ? punishment.getReason()
                                            : "No reason specified"));
                }
            }
            case KICK -> {
                if (target != null) {
                    String kickMsg =
                            "<red><bold>You have been kicked\n\n<gray>Reason: <white>"
                                    + (punishment.getReason() != null
                                            ? punishment.getReason()
                                            : "No reason specified")
                                    + "\n\n<gray>Kicked by: <white>"
                                    + punishment.getStaffName();
                    target.kickPlayer(TextUtil.serializeLegacy(TextUtil.parse(kickMsg)));
                }
            }
            case BAN -> {
                String banReason =
                        "<red><bold>You have been banned\n\n<gray>Reason: <white>"
                                + (punishment.getReason() != null
                                        ? punishment.getReason()
                                        : "No reason specified")
                                + (punishment.isPermanent()
                                        ? "\n\n<red>This ban is permanent."
                                        : "\n<gray>Duration: <white>"
                                                + punishment.getFormattedDuration()
                                                + "\n<gray>Expires: <white>"
                                                + formatTimestamp(punishment.getExpiresAt()))
                                + "\n\n<gray>Banned by: <white>"
                                + punishment.getStaffName();

                Date expiry =
                        punishment.getExpiresAt() != null
                                ? new Date(punishment.getExpiresAt())
                                : null;
                Bukkit.getBanList(BanList.Type.NAME)
                        .addBan(
                                punishment.getTargetName(),
                                TextUtil.serializeLegacy(TextUtil.parse(banReason)),
                                expiry,
                                punishment.getStaffName());

                if (target != null) {
                    target.kickPlayer(TextUtil.serializeLegacy(TextUtil.parse(banReason)));
                }
            }
            case FREEZE -> {
                if (target != null) {
                    Player staff =
                            punishment.getStaffUuid() != null
                                    ? Bukkit.getPlayer(punishment.getStaffUuid())
                                    : null;
                    if (staff != null) {
                        plugin.getFreezeManager().freeze(target, staff, punishment.getReason());
                    }
                }
            }
        }
    }

    private void notifyStaff(Punishment punishment) {
        String msg =
                plugin.getAdminConfig().getPrefix()
                        + "<yellow>[Punish] <white>"
                        + punishment.getStaffName()
                        + " <gray>"
                        + punishment.getType().getDisplayName().toLowerCase()
                        + "ed <white>"
                        + punishment.getTargetName()
                        + (punishment.getDurationMs() != null
                                ? " <gray>for <white>" + punishment.getFormattedDuration()
                                : "")
                        + (punishment.getReason() != null
                                ? " <gray>- <white>" + punishment.getReason()
                                : "");

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("serveradmin.punish")) {
                TextUtil.send(player, msg);
            }
        }
        plugin.getLogger()
                .info(
                        "[Punish] "
                                + punishment.getStaffName()
                                + " "
                                + punishment.getType().name().toLowerCase()
                                + "ed "
                                + punishment.getTargetName());
    }

    public CompletableFuture<Boolean> pardon(int punishmentId, Player staff, String reason) {
        return CompletableFuture.supplyAsync(
                () -> {
                    Punishment punishment = repository.getPunishment(punishmentId);
                    if (punishment == null || !punishment.isActive()) {
                        return false;
                    }

                    repository.pardonPunishment(
                            punishmentId,
                            staff != null ? staff.getUniqueId() : null,
                            staff != null ? staff.getName() : "Console",
                            reason);

                    Bukkit.getScheduler()
                            .runTask(
                                    plugin,
                                    () -> {
                                        removePunishmentEffects(punishment);
                                        notifyPardon(
                                                punishment,
                                                staff != null ? staff.getName() : "Console");

                                        // Publish pardon to Redis
                                        if (adminRedisHandler != null
                                                && adminRedisHandler.isAvailable()) {
                                            adminRedisHandler.publishPunishment(
                                                    punishment, "PUNISHMENT_PARDONED");

                                            // For mutes, sync unmute status
                                            if (punishment.getType() == PunishmentType.MUTE) {
                                                adminRedisHandler.publishMuteSync(
                                                        punishment.getTargetUuid(),
                                                        false,
                                                        null,
                                                        null);
                                            }
                                        }
                                    });

                    return true;
                });
    }

    private void removePunishmentEffects(Punishment punishment) {
        switch (punishment.getType()) {
            case MUTE -> {
                mutedPlayersCache.remove(punishment.getTargetUuid());
                Player target = Bukkit.getPlayer(punishment.getTargetUuid());
                if (target != null) {
                    TextUtil.sendSuccess(target, "You have been unmuted.");
                }
            }
            case BAN -> {
                Bukkit.getBanList(BanList.Type.NAME).pardon(punishment.getTargetName());
            }
            case FREEZE -> {
                Player target = Bukkit.getPlayer(punishment.getTargetUuid());
                if (target != null) {
                    plugin.getFreezeManager().unfreeze(target, null, false);
                }
            }
        }
    }

    private void notifyPardon(Punishment punishment, String pardonerName) {
        String msg =
                plugin.getAdminConfig().getPrefix()
                        + "<green>[Pardon] <white>"
                        + pardonerName
                        + " <gray>pardoned <white>"
                        + punishment.getTargetName()
                        + "<gray>'s <white>"
                        + punishment.getType().getDisplayName().toLowerCase();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("serveradmin.unpunish")) {
                TextUtil.send(player, msg);
            }
        }
    }

    public boolean isMuted(UUID uuid) {
        Punishment mute = mutedPlayersCache.get(uuid);
        if (mute == null) return false;

        // Check if cached mute has expired
        if (mute.isExpired()) {
            mutedPlayersCache.remove(uuid);
            return false;
        }
        return true;
    }

    public Punishment getActiveMute(UUID uuid) {
        Punishment mute = mutedPlayersCache.get(uuid);
        if (mute != null && mute.isExpired()) {
            mutedPlayersCache.remove(uuid);
            return null;
        }
        return mute;
    }

    public Punishment getActiveBan(UUID uuid) {
        return repository.getActivePunishment(uuid, PunishmentType.BAN);
    }

    public List<Punishment> getActivePunishments(UUID uuid) {
        return repository.getActivePunishments(uuid);
    }

    public List<Punishment> getPunishmentHistory(UUID uuid, int limit) {
        return repository.getPunishmentHistory(uuid, limit);
    }

    public int getPunishmentCount(UUID uuid) {
        return repository.getPunishmentCount(uuid);
    }

    public void handlePlayerJoin(Player player) {
        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Punishment mute =
                                    repository.getActivePunishment(
                                            player.getUniqueId(), PunishmentType.MUTE);
                            if (mute != null && !mute.isExpired()) {
                                mutedPlayersCache.put(player.getUniqueId(), mute);
                                Bukkit.getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    String msg =
                                                            mute.isPermanent()
                                                                    ? "permanently"
                                                                    : "for "
                                                                            + mute
                                                                                    .getFormattedRemainingTime();
                                                    TextUtil.send(
                                                            player,
                                                            "<red>Reminder: You are muted "
                                                                    + msg
                                                                    + ".");
                                                });
                            }
                        });
    }

    public void handlePlayerQuit(Player player) {
        mutedPlayersCache.remove(player.getUniqueId());
    }

    public CategoryManager getCategoryManager() {
        return categoryManager;
    }

    public ReasonManager getReasonManager() {
        return reasonManager;
    }

    public PunishmentRepository getRepository() {
        return repository;
    }

    public void awaitCustomReason(Player staff, OfflinePlayer target, PunishmentType type) {
        pendingCustomReasons.put(staff.getUniqueId(), new PendingCustomReason(target, type));
    }

    public boolean handleCustomReasonInput(Player staff, String message) {
        PendingCustomReason pending = pendingCustomReasons.remove(staff.getUniqueId());
        if (pending == null || pending.isExpired()) {
            return false;
        }

        if (message.equalsIgnoreCase("cancel")) {
            staff.sendMessage(TextUtil.parse("<yellow>Punishment cancelled."));
            return true;
        }

        PunishmentContext context =
                PunishmentContext.builder()
                        .target(pending.target)
                        .staff(staff)
                        .type(pending.type)
                        .reasonId("other")
                        .reason(message)
                        .build();

        punish(context)
                .thenAccept(
                        result -> {
                            Bukkit.getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                if (result.isSuccess()) {
                                                    Punishment p = result.getPunishment();
                                                    String duration =
                                                            p.getDurationMs() != null
                                                                    ? " for "
                                                                            + p
                                                                                    .getFormattedDuration()
                                                                    : "";
                                                    String msg =
                                                            "<green>Applied <white>"
                                                                    + p.getType().getDisplayName()
                                                                    + "<green> to <white>"
                                                                    + pending.target.getName()
                                                                    + duration
                                                                    + " <gray>("
                                                                    + message
                                                                    + ")";
                                                    staff.sendMessage(TextUtil.parse(msg));
                                                } else {
                                                    staff.sendMessage(
                                                            TextUtil.parse(
                                                                    "<red>Failed to apply punishment: "
                                                                            + result.getError()));
                                                }
                                            });
                        });

        return true;
    }

    public boolean hasPendingCustomReason(UUID staffUuid) {
        PendingCustomReason pending = pendingCustomReasons.get(staffUuid);
        if (pending != null && pending.isExpired()) {
            pendingCustomReasons.remove(staffUuid);
            return false;
        }
        return pending != null;
    }

    private String formatTimestamp(Long timestamp) {
        if (timestamp == null) return "Never";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm");
        return sdf.format(new Date(timestamp));
    }

    public static class PunishmentResult {
        private final boolean success;
        private final Punishment punishment;
        private final String error;

        public PunishmentResult(boolean success, Punishment punishment, String error) {
            this.success = success;
            this.punishment = punishment;
            this.error = error;
        }

        public boolean isSuccess() {
            return success;
        }

        public Punishment getPunishment() {
            return punishment;
        }

        public String getError() {
            return error;
        }
    }
}
