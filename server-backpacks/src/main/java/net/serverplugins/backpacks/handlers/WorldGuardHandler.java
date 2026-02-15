package net.serverplugins.backpacks.handlers;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Handles WorldGuard integration for backpack region protection. Registers a custom flag
 * "backpacks" that controls whether players can open backpacks in a region.
 */
public class WorldGuardHandler {

    private StateFlag backpackUseFlag;
    private boolean initialized = false;

    /**
     * Registers the backpack flag with WorldGuard. Must be called during onLoad() BEFORE WorldGuard
     * enables.
     */
    public void registerFlag() {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();

        try {
            // Create flag that defaults to ALLOW
            StateFlag flag = new StateFlag("backpacks", true);
            registry.register(flag);
            this.backpackUseFlag = flag;
            this.initialized = true;
            Bukkit.getLogger().info("[ServerBackpacks] Registered WorldGuard flag: backpacks");
        } catch (FlagConflictException e) {
            // Flag already exists, try to use existing one
            Flag<?> existing = registry.get("backpacks");
            if (existing instanceof StateFlag stateFlag) {
                this.backpackUseFlag = stateFlag;
                this.initialized = true;
                Bukkit.getLogger()
                        .info("[ServerBackpacks] Using existing WorldGuard flag: backpacks");
            } else {
                Bukkit.getLogger()
                        .warning(
                                "[ServerBackpacks] Failed to register WorldGuard flag - 'backpacks' flag exists but is not a StateFlag!");
            }
        } catch (Exception e) {
            Bukkit.getLogger()
                    .warning(
                            "[ServerBackpacks] Failed to register WorldGuard flag: "
                                    + e.getMessage());
        }
    }

    /**
     * Check if a player can open a backpack at their current location.
     *
     * @param player The player to check
     * @return true if the player can open a backpack
     */
    public boolean canOpenBackpack(Player player) {
        if (!initialized || backpackUseFlag == null) {
            // If not initialized properly, allow by default
            return true;
        }

        return canOpenBackpackAt(player, player.getLocation());
    }

    /**
     * Check if a player can open a backpack at a specific location.
     *
     * @param player The player to check
     * @param location The location to check
     * @return true if the player can open a backpack at the location
     */
    public boolean canOpenBackpackAt(Player player, Location location) {
        if (!initialized || backpackUseFlag == null) {
            return true;
        }

        try {
            var query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();

            var adaptedLocation = BukkitAdapter.adapt(location);
            var wrappedPlayer = WorldGuardPlugin.inst().wrapPlayer(player);

            var state =
                    query.getApplicableRegions(adaptedLocation)
                            .queryState(wrappedPlayer, backpackUseFlag);

            // If state is null (no regions or flag not set), default to ALLOW
            // If state is DENY, return false
            return state != StateFlag.State.DENY;
        } catch (Exception e) {
            Bukkit.getLogger()
                    .warning("[ServerBackpacks] Error checking WorldGuard flag: " + e.getMessage());
            return true; // Allow by default on error
        }
    }

    /**
     * Check if the handler is properly initialized.
     *
     * @return true if WorldGuard integration is working
     */
    public boolean isInitialized() {
        return initialized && backpackUseFlag != null;
    }

    /**
     * Get the flag name.
     *
     * @return The flag name used for backpack access
     */
    public String getFlagName() {
        return "backpacks";
    }
}
