package net.serverplugins.afk.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.serverplugins.afk.ServerAFK;
import net.serverplugins.afk.models.PlayerAfkSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Manages global AFK detection and manual /afk toggling. Tracks player idle time and automatically
 * marks players as AFK.
 */
public class GlobalAfkManager {

    private final ServerAFK plugin;
    private final Map<UUID, Long> lastActivityTime;
    private final Map<UUID, Boolean> afkStatus;
    private BukkitTask idleCheckTask;

    // Configuration values
    private int autoAfkTimeSeconds;
    private boolean autoAfkEnabled;

    public GlobalAfkManager(ServerAFK plugin) {
        this.plugin = plugin;
        this.lastActivityTime = new HashMap<>();
        this.afkStatus = new HashMap<>();
        this.autoAfkTimeSeconds = 300; // 5 minutes default
        this.autoAfkEnabled = true;

        loadConfig();
    }

    public void start() {
        // Start idle detection task (runs every 10 seconds)
        idleCheckTask =
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        checkIdlePlayers();
                    }
                }.runTaskTimer(plugin, 200L, 200L); // 10 seconds

        plugin.getLogger()
                .info(
                        "GlobalAfkManager started with auto-AFK at "
                                + autoAfkTimeSeconds
                                + " seconds");
    }

    public void stop() {
        if (idleCheckTask != null) {
            idleCheckTask.cancel();
        }
        lastActivityTime.clear();
        afkStatus.clear();
    }

    public final void loadConfig() {
        this.autoAfkTimeSeconds = plugin.getAfkConfig().getAutoAfkTimeSeconds();
        this.autoAfkEnabled = plugin.getAfkConfig().isAutoAfkEnabled();
    }

    /**
     * Updates the last activity time for a player. This should be called whenever a player performs
     * any action.
     */
    public void updateActivity(UUID playerId) {
        lastActivityTime.put(playerId, System.currentTimeMillis());

        // If player was AFK and performed activity, remove AFK status
        if (isAfk(playerId)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                // Only remove auto-AFK, not manual AFK
                PlayerAfkSession session = plugin.getPlayerTracker().getSession(playerId);
                if (session != null && !session.isManuallyAfk()) {
                    setAfk(playerId, false);
                    plugin.getAfkConfig().getMessenger().send(player, "auto-afk-removed");
                }
            }
        }
    }

    /**
     * Manually toggles AFK status for a player.
     *
     * @return true if now AFK, false if no longer AFK
     */
    public boolean toggleAfk(UUID playerId) {
        boolean newStatus = !isAfk(playerId);
        setAfk(playerId, newStatus);

        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            PlayerAfkSession session = plugin.getPlayerTracker().getSession(playerId);
            if (session != null) {
                session.setManuallyAfk(newStatus);
            }

            var messenger = plugin.getAfkConfig().getMessenger();
            if (newStatus) {
                messenger.send(player, "manual-afk-enabled");
            } else {
                messenger.send(player, "manual-afk-disabled");
            }
        }

        return newStatus;
    }

    /** Sets AFK status for a player. */
    public void setAfk(UUID playerId, boolean afk) {
        afkStatus.put(playerId, afk);

        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            // Update player display name or other visual indicators
            updatePlayerDisplay(player, afk);
        }
    }

    /** Checks if a player is marked as AFK. */
    public boolean isAfk(UUID playerId) {
        return afkStatus.getOrDefault(playerId, false);
    }

    /** Gets idle time in seconds for a player. */
    public long getIdleTimeSeconds(UUID playerId) {
        Long lastActivity = lastActivityTime.get(playerId);
        if (lastActivity == null) {
            return 0;
        }
        return (System.currentTimeMillis() - lastActivity) / 1000;
    }

    /** Removes a player from tracking when they log out. */
    public void removePlayer(UUID playerId) {
        lastActivityTime.remove(playerId);
        afkStatus.remove(playerId);
    }

    /** Checks all online players for idle status and auto-marks as AFK. */
    private void checkIdlePlayers() {
        if (!autoAfkEnabled) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();

            // Skip if already manually AFK
            PlayerAfkSession session = plugin.getPlayerTracker().getSession(playerId);
            if (session != null && session.isManuallyAfk()) {
                continue;
            }

            // Skip if already auto-AFK
            if (isAfk(playerId)) {
                continue;
            }

            // Check idle time
            long idleSeconds = getIdleTimeSeconds(playerId);
            if (idleSeconds >= autoAfkTimeSeconds) {
                setAfk(playerId, true);
                plugin.getAfkConfig().getMessenger().send(player, "auto-afk-activated");
                plugin.getLogger()
                        .info(
                                player.getName()
                                        + " is now auto-AFK (idle for "
                                        + idleSeconds
                                        + "s)");
            }
        }
    }

    /** Updates player display to show AFK status. */
    private void updatePlayerDisplay(Player player, boolean afk) {
        // Could update player list name, display name, or add title/action bar
        // For now, we'll keep it simple and just track the state
        // Future enhancement: Add player list name prefix [AFK]

        if (afk) {
            // player.setPlayerListName(TextUtil.color("&7[AFK] &f" + player.getName()));
        } else {
            // player.setPlayerListName(player.getName());
        }
    }

    /** Gets the configured auto-AFK time in seconds. */
    public int getAutoAfkTimeSeconds() {
        return autoAfkTimeSeconds;
    }

    /** Checks if auto-AFK is enabled. */
    public boolean isAutoAfkEnabled() {
        return autoAfkEnabled;
    }
}
