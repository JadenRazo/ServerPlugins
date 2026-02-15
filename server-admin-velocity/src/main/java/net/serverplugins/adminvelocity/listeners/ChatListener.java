package net.serverplugins.adminvelocity.listeners;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.adminvelocity.database.PunishmentTable;
import net.serverplugins.adminvelocity.messaging.VelocityTextUtil;
import net.serverplugins.adminvelocity.punishment.VelocityPunishment;
import net.serverplugins.adminvelocity.staffchat.StaffChatRouter;
import org.slf4j.Logger;

/** Handles player chat events for mute enforcement and staff chat. */
public class ChatListener {

    private final Logger logger;
    private final PunishmentTable punishmentTable;
    private final StaffChatRouter staffChatRouter;
    private final boolean staffChatEnabled;

    // Cache of muted players to avoid database lookups on every message
    private final Map<UUID, VelocityPunishment> muteCache = new ConcurrentHashMap<>();
    // Track players in staff chat mode
    private final Map<UUID, Boolean> staffChatToggles = new ConcurrentHashMap<>();

    public ChatListener(
            Logger logger,
            PunishmentTable punishmentTable,
            StaffChatRouter staffChatRouter,
            boolean staffChatEnabled) {
        this.logger = logger;
        this.punishmentTable = punishmentTable;
        this.staffChatRouter = staffChatRouter;
        this.staffChatEnabled = staffChatEnabled;
    }

    @Subscribe(order = PostOrder.EARLY)
    public void onChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // Check for staff chat toggle command
        if (staffChatEnabled && message.startsWith("/sc")) {
            // Let the command handler deal with it
            return;
        }

        // Check if player is in staff chat toggle mode
        if (staffChatEnabled && staffChatToggles.getOrDefault(player.getUniqueId(), false)) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
            String serverName =
                    player.getCurrentServer()
                            .map(s -> s.getServerInfo().getName())
                            .orElse("Velocity");
            staffChatRouter.sendMessage(player, serverName, message);
            return;
        }

        // Check for active mute
        VelocityPunishment mute = muteCache.get(player.getUniqueId());
        if (mute != null) {
            if (mute.isExpired()) {
                muteCache.remove(player.getUniqueId());
            } else {
                event.setResult(PlayerChatEvent.ChatResult.denied());
                sendMutedMessage(player, mute);
                return;
            }
        } else {
            // Check database (async, but we need sync result for event cancellation)
            // This is a limitation of Velocity's chat event - we rely on the cache
            // Players will be added to cache on login or when a mute is applied
        }
    }

    /**
     * Adds a player to the mute cache.
     *
     * @param uuid Player UUID
     * @param mute Mute punishment
     */
    public void cacheMute(UUID uuid, VelocityPunishment mute) {
        if (mute != null && !mute.isExpired()) {
            muteCache.put(uuid, mute);
        }
    }

    /**
     * Removes a player from the mute cache.
     *
     * @param uuid Player UUID
     */
    public void removeMute(UUID uuid) {
        muteCache.remove(uuid);
    }

    /**
     * Toggles staff chat mode for a player.
     *
     * @param uuid Player UUID
     * @return New toggle state
     */
    public boolean toggleStaffChat(UUID uuid) {
        boolean current = staffChatToggles.getOrDefault(uuid, false);
        staffChatToggles.put(uuid, !current);
        return !current;
    }

    /**
     * Checks if a player is in staff chat mode.
     *
     * @param uuid Player UUID
     * @return True if in staff chat mode
     */
    public boolean isInStaffChatMode(UUID uuid) {
        return staffChatToggles.getOrDefault(uuid, false);
    }

    /**
     * Loads active mutes from database on player join.
     *
     * @param uuid Player UUID
     */
    public void loadMuteOnJoin(UUID uuid) {
        punishmentTable
                .getActiveMute(uuid)
                .thenAccept(
                        mute -> {
                            if (mute != null && !mute.isExpired()) {
                                muteCache.put(uuid, mute);
                            }
                        })
                .exceptionally(
                        ex -> {
                            logger.error("Error loading mute for {}: {}", uuid, ex.getMessage());
                            return null;
                        });
    }

    private void sendMutedMessage(Player player, VelocityPunishment mute) {
        String duration =
                mute.isPermanent() ? "permanently" : "for " + mute.getFormattedRemainingDuration();

        VelocityTextUtil.sendError(
                player,
                "You are muted "
                        + duration
                        + ". Reason: "
                        + (mute.getReason() != null ? mute.getReason() : "No reason specified"));
    }
}
