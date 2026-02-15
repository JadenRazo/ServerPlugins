package net.serverplugins.parkour.listeners;

import net.serverplugins.parkour.ServerParkour;
import net.serverplugins.parkour.game.ParkourSession;
import net.serverplugins.parkour.gui.ParkourGui;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitRunnable;

public class ParkourListener implements Listener {

    private final ServerParkour plugin;

    public ParkourListener(ServerParkour plugin) {
        this.plugin = plugin;

        // Main game tick - runs every tick (like InfiniteParkour plugin)
        new BukkitRunnable() {
            @Override
            public void run() {
                for (ParkourSession session :
                        plugin.getParkourManager().getActiveSessions().values()) {
                    if (session.isActive()) {
                        session.tick(); // Main game logic
                        session.checkFall(); // Fall detection
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 1L); // Run every tick
    }

    @EventHandler
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        ParkourSession session = plugin.getParkourManager().getSession(player);

        if (session == null || !session.isActive()) {
            return;
        }

        // Handle double jump
        if (session.hasDoubleJump() && !player.isOnGround()) {
            event.setCancelled(true);
            player.setAllowFlight(false);

            if (session.useDoubleJump()) {
                // Apply double jump velocity
                player.setVelocity(player.getLocation().getDirection().multiply(0.5).setY(0.8));
            }
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        ParkourSession session = plugin.getParkourManager().getSession(player);
        if (session == null || !session.isActive()) {
            return;
        }

        // Cancel all damage during parkour
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL
                || event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);

            // End game on void damage
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                session.end(true);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Load player data into cache asynchronously
        if (plugin.getDatabase() != null) {
            plugin.getDatabase().loadPlayerCache(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        ParkourSession session = plugin.getParkourManager().getSession(player);

        if (session != null) {
            session.end(false);
        }

        // Unload player from cache
        if (plugin.getDatabase() != null) {
            plugin.getDatabase().unloadPlayerCache(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        ParkourSession session = plugin.getParkourManager().getSession(player);

        if (session == null || !session.isActive()) {
            return;
        }

        // Allow teleports within the game, but end if going far away
        Location to = event.getTo();
        if (to != null) {
            // If teleporting to a completely different location (not game-related)
            if (event.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND
                    || event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
                // Check if this is a far teleport (likely not game-related)
                double distance = event.getFrom().distance(to);
                if (distance > 100) {
                    session.end(false);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        ParkourSession session = plugin.getParkourManager().getSession(player);

        if (session != null) {
            session.end(false);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!(event.getInventory().getHolder() instanceof ParkourGui gui)) {
            return;
        }

        event.setCancelled(true);

        int slot = event.getRawSlot();

        switch (slot) {
            case ParkourGui.START_SLOT -> {
                // Start parkour game
                player.closeInventory();
                if (!plugin.getParkourManager().isPlaying(player)) {
                    plugin.getParkourManager().startGame(player);
                }
            }
            case 11 -> {
                // Stats
                player.closeInventory();
                player.performCommand("parkour stats");
            }
            case 15 -> {
                // Leaderboard
                player.closeInventory();
                player.performCommand("parkour top");
            }
        }
    }
}
