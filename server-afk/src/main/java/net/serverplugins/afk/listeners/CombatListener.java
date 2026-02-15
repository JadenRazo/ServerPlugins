package net.serverplugins.afk.listeners;

import com.github.sirblobman.combatlogx.api.ICombatLogX;
import com.github.sirblobman.combatlogx.api.manager.ICombatManager;
import java.util.UUID;
import net.serverplugins.afk.ServerAFK;
import net.serverplugins.afk.models.PlayerAfkSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * Integrates with CombatLogX to prevent AFK farming during combat. Players in combat cannot receive
 * AFK rewards and their session is ended.
 */
public class CombatListener implements Listener {

    private final ServerAFK plugin;
    private ICombatLogX combatLogX;
    private boolean combatLogXAvailable;

    public CombatListener(ServerAFK plugin) {
        this.plugin = plugin;
        this.combatLogXAvailable = setupCombatLogX();
    }

    private boolean setupCombatLogX() {
        Plugin combatPlugin = Bukkit.getPluginManager().getPlugin("CombatLogX");
        if (combatPlugin == null) {
            plugin.getLogger().warning("CombatLogX not found. Combat integration disabled.");
            return false;
        }

        try {
            combatLogX = (ICombatLogX) combatPlugin;
            plugin.getLogger().info("CombatLogX integration enabled!");
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to hook into CombatLogX: " + e.getMessage());
            return false;
        }
    }

    /** Checks if a player is in combat. */
    public boolean isInCombat(Player player) {
        if (!combatLogXAvailable || combatLogX == null) {
            return false;
        }

        try {
            ICombatManager combatManager = combatLogX.getCombatManager();
            return combatManager.isInCombat(player);
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking combat status: " + e.getMessage());
            return false;
        }
    }

    /**
     * Handles when a player enters combat. Called from the RewardScheduler or PlayerTracker when
     * checking combat status.
     */
    public void onPlayerEnterCombat(Player player) {
        UUID playerId = player.getUniqueId();
        PlayerAfkSession session = plugin.getPlayerTracker().getSession(playerId);

        if (session == null) {
            return;
        }

        // End the AFK session due to combat
        plugin.getPlayerTracker().endSession(playerId, true); // true = ended by combat

        plugin.getAfkConfig().getMessenger().send(player, "session-ended-combat");

        plugin.getLogger().info(player.getName() + "'s AFK session ended due to combat.");
    }

    /** Checks if CombatLogX integration is available. */
    public boolean isCombatLogXAvailable() {
        return combatLogXAvailable;
    }

    /** Gets the remaining combat time for a player in seconds. */
    public long getRemainingCombatTime(Player player) {
        if (!combatLogXAvailable || combatLogX == null) {
            return 0;
        }

        try {
            ICombatManager combatManager = combatLogX.getCombatManager();
            if (!combatManager.isInCombat(player)) {
                return 0;
            }
            // CombatLogX API doesn't provide timer access in this version
            // Return a default combat duration (usually 10 seconds)
            return 10;
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting combat status: " + e.getMessage());
            return 0;
        }
    }
}
