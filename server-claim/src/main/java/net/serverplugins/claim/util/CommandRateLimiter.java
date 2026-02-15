package net.serverplugins.claim.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.entity.Player;

/**
 * Simple rate limiter for commands to prevent spam and abuse. Tracks last command execution time
 * per player.
 */
public class CommandRateLimiter {

    private final Map<UUID, Long> lastCommandTime = new ConcurrentHashMap<>();
    private final long cooldownMillis;
    private final String commandName;

    /**
     * Create a rate limiter for a specific command
     *
     * @param commandName Name of the command (for messages)
     * @param cooldownSeconds Cooldown between command uses in seconds
     */
    public CommandRateLimiter(String commandName, int cooldownSeconds) {
        this.commandName = commandName;
        this.cooldownMillis = cooldownSeconds * 1000L;
    }

    /**
     * Check if player can execute the command
     *
     * @param player Player to check
     * @return true if player can execute, false if on cooldown
     */
    public boolean canExecute(Player player) {
        // Admins bypass rate limiting
        if (player.hasPermission("serverclaim.admin")
                || player.hasPermission("serverclaim.bypasscooldown")) {
            return true;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastTime = lastCommandTime.get(uuid);

        if (lastTime == null) {
            lastCommandTime.put(uuid, now);
            return true;
        }

        long timeSince = now - lastTime;
        if (timeSince >= cooldownMillis) {
            lastCommandTime.put(uuid, now);
            return true;
        }

        return false;
    }

    /**
     * Get remaining cooldown time in seconds
     *
     * @param player Player to check
     * @return Remaining cooldown in seconds, or 0 if no cooldown
     */
    public long getRemainingCooldown(Player player) {
        if (player.hasPermission("serverclaim.admin")
                || player.hasPermission("serverclaim.bypasscooldown")) {
            return 0;
        }

        UUID uuid = player.getUniqueId();
        Long lastTime = lastCommandTime.get(uuid);

        if (lastTime == null) {
            return 0;
        }

        long now = System.currentTimeMillis();
        long timeSince = now - lastTime;
        long remaining = cooldownMillis - timeSince;

        return remaining > 0 ? (remaining / 1000) + 1 : 0;
    }

    /**
     * Send cooldown message to player
     *
     * @param player Player to message
     */
    public void sendCooldownMessage(Player player) {
        long remaining = getRemainingCooldown(player);
        if (remaining > 0) {
            TextUtil.sendError(
                    player,
                    "Please wait <yellow>"
                            + remaining
                            + "s</yellow> before using <yellow>/"
                            + commandName
                            + "</yellow> again.");
        }
    }

    /**
     * Reset cooldown for a player (useful for admin commands)
     *
     * @param player Player to reset
     */
    public void reset(Player player) {
        lastCommandTime.remove(player.getUniqueId());
    }

    /** Clear all cooldowns (useful for plugin reload) */
    public void clearAll() {
        lastCommandTime.clear();
    }
}
