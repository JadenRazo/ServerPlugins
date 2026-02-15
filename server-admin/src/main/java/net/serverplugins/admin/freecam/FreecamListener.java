package net.serverplugins.admin.freecam;

import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;

/**
 * Event listener for freecam functionality. Handles spectator mode restrictions and state
 * management.
 */
public class FreecamListener implements Listener {

    private final ServerAdmin plugin;

    public FreecamListener(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    /** Handle player quit - clean up freecam session and ghost entity. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getFreecamManager().handleQuit(event.getPlayer());
    }

    /** Handle player join - spawn ghost entities for existing freecam sessions. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getFreecamManager().handleJoin(event.getPlayer());
    }

    /** Handle world change - exit freecam to prevent ghost entity issues. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (plugin.getFreecamManager().isInFreecam(player)) {
            plugin.getFreecamManager().stopFreecam(player);
            TextUtil.send(
                    player,
                    plugin.getAdminConfig().getPrefix()
                            + "<red>Freecam disabled due to world change.");
        }
    }

    /**
     * Handle player death - exit freecam before respawn. Note: Spectators shouldn't die, but handle
     * edge cases.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (plugin.getFreecamManager().isInFreecam(player)) {
            plugin.getFreecamManager().stopFreecam(player);
        }
    }

    /**
     * Prevent spectator teleport-to-entity feature while in freecam. This blocks left-clicking
     * entities to teleport to them.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getFreecamManager().isInFreecam(player)) {
            return;
        }

        // Block spectator teleporting (clicking on entities)
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE) {
            event.setCancelled(true);
            TextUtil.send(
                    player,
                    plugin.getAdminConfig().getPrefix()
                            + "<red>Teleporting to entities is disabled in freecam.");
            return;
        }

        // Allow other teleports (commands, etc.) but update session location
        // This ensures /tp works while in freecam
    }

    /** Handle gamemode change - exit freecam if gamemode changed externally. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getFreecamManager().isInFreecam(player)) {
            return;
        }

        // If someone changed their gamemode away from spectator while in freecam,
        // the freecam is effectively broken, so clean up
        if (event.getNewGameMode() != GameMode.SPECTATOR) {
            FreecamSession session = plugin.getFreecamManager().getSession(player);
            if (session != null) {
                // Update the session's previous gamemode to the new one
                // since that's what they want to be in
                // Then properly exit freecam
                plugin.getFreecamManager().stopFreecam(player);
                // Re-apply the new gamemode since stopFreecam restored the old one
                player.setGameMode(event.getNewGameMode());
            }
        }
    }

    /** Prevent damage to freecam players (shouldn't happen in spectator, but safety check). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (plugin.getFreecamManager().isInFreecam(player)) {
            event.setCancelled(true);
        }
    }

    /** Handle respawn - restore proper state if player died during freecam somehow. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        FreecamSession session = plugin.getFreecamManager().getSession(player);

        if (session != null) {
            // Player somehow died in freecam, clean up the session
            plugin.getFreecamManager().stopFreecam(player);
        }
    }

    /**
     * Handle interact entity - prevent spectator interactions while in freecam. This is a backup in
     * case spectator mode allows any entity interaction.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (plugin.getFreecamManager().isInFreecam(player)) {
            // Allow staff to still use admin tools on entities if needed
            // But block normal spectator interactions
            if (event.getRightClicked() instanceof Player) {
                // Might want to allow /invsee type functionality
                // For now, allow right-click on players
                return;
            }
            // Block other entity interactions in freecam
            event.setCancelled(true);
        }
    }
}
