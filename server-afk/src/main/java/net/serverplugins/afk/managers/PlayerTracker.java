package net.serverplugins.afk.managers;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.afk.ServerAFK;
import net.serverplugins.afk.models.AfkZone;
import net.serverplugins.afk.models.PlayerAfkSession;
import org.bukkit.entity.Player;

public class PlayerTracker {

    private final ServerAFK plugin;
    private final ConcurrentHashMap<UUID, PlayerAfkSession> activeSessions;

    public PlayerTracker(ServerAFK plugin) {
        this.plugin = plugin;
        this.activeSessions = new ConcurrentHashMap<>();
    }

    public PlayerAfkSession getSession(UUID playerId) {
        return activeSessions.get(playerId);
    }

    public PlayerAfkSession getSession(Player player) {
        return getSession(player.getUniqueId());
    }

    public Optional<PlayerAfkSession> getSessionOptional(UUID playerId) {
        return Optional.ofNullable(activeSessions.get(playerId));
    }

    public Optional<PlayerAfkSession> getSessionOptional(Player player) {
        return getSessionOptional(player.getUniqueId());
    }

    public boolean hasSession(UUID playerId) {
        return activeSessions.containsKey(playerId);
    }

    public boolean hasSession(Player player) {
        return hasSession(player.getUniqueId());
    }

    public PlayerAfkSession startSession(Player player, AfkZone zone, int initialY) {
        PlayerAfkSession session = new PlayerAfkSession(player.getUniqueId(), zone, initialY);
        activeSessions.put(player.getUniqueId(), session);
        return session;
    }

    public void endSession(UUID playerId) {
        endSession(playerId, false);
    }

    public void endSession(UUID playerId, boolean endedByCombat) {
        PlayerAfkSession session = activeSessions.remove(playerId);

        if (session != null && plugin.getStatsManager() != null) {
            // Save session to history and update stats
            long sessionSeconds = session.getTimeInZoneSeconds();
            plugin.getStatsManager().incrementSession(playerId, sessionSeconds);

            // Save session record to database
            if (plugin.getRepository() != null) {
                plugin.getRepository()
                        .endSessionRecord(
                                session.getSessionRecordId(),
                                session.getRewardsEarnedThisSession(),
                                session.getCurrencyEarnedThisSession(),
                                session.getXpEarnedThisSession(),
                                endedByCombat);
            }
        }
    }

    public void endSession(Player player) {
        endSession(player.getUniqueId(), false);
    }

    public void endSession(Player player, boolean endedByCombat) {
        endSession(player.getUniqueId(), endedByCombat);
    }

    public Collection<PlayerAfkSession> getAllSessions() {
        return activeSessions.values();
    }

    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    public void clearAllSessions() {
        activeSessions.clear();
    }

    public boolean isInZone(Player player, AfkZone zone) {
        Optional<PlayerAfkSession> session = getSessionOptional(player);
        return session.isPresent() && session.get().getCurrentZone().getId() == zone.getId();
    }

    public Collection<UUID> getPlayersInZone(int zoneId) {
        return activeSessions.entrySet().stream()
                .filter(entry -> entry.getValue().getCurrentZone().getId() == zoneId)
                .map(entry -> entry.getKey())
                .toList();
    }
}
