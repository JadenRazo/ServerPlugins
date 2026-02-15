package net.serverplugins.core.features;

import org.bukkit.entity.Player;

/**
 * Extension of Feature that supports per-player toggles. Features implementing this interface can
 * be enabled/disabled individually by each player.
 */
public interface PerPlayerFeature {

    /**
     * Check if this feature is enabled for a specific player. This should check both the global
     * enabled state AND the player's personal preference.
     *
     * @param player The player to check
     * @return true if the feature is enabled for this player
     */
    boolean isEnabledForPlayer(Player player);

    /**
     * Get the internal name used for this feature in the player data system. This should match the
     * feature key used in ServerCore.registerFeature()
     *
     * @return The feature's internal name
     */
    String getFeatureKey();
}
