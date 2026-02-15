package net.serverplugins.bridge.tasks;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.milkbowl.vault.economy.Economy;
import net.serverplugins.bridge.ServerBridge;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Incremental balance sync task that only syncs online players and recently active players. Runs
 * every 1 minute to keep online player balances up-to-date without the expensive iteration of all
 * offline players.
 */
public class IncrementalBalanceSyncTask implements Runnable, Listener {

    private final ServerBridge plugin;
    private final Economy economy;
    private final Set<UUID> dirtyPlayers;

    public IncrementalBalanceSyncTask(ServerBridge plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.dirtyPlayers = ConcurrentHashMap.newKeySet();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        dirtyPlayers.add(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Immediately sync the player's balance asynchronously
        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            syncPlayerBalance(player.getUniqueId(), player.getName());
                        });
    }

    @Override
    public void run() {
        if (!plugin.getDatabaseManager().isAvailable() || economy == null) {
            return;
        }

        try {
            // Sync all online players
            for (Player player : Bukkit.getOnlinePlayers()) {
                dirtyPlayers.add(player.getUniqueId());
            }

            // Sync all dirty players
            if (!dirtyPlayers.isEmpty()) {
                int syncCount = syncDirtyPlayers();
                if (syncCount > 0) {
                    plugin.getLogger()
                            .fine("Incrementally synced " + syncCount + " player balances.");
                }
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Error in incremental balance sync: " + e.getMessage());
        }
    }

    private int syncDirtyPlayers() {
        int count = 0;

        // Process dirty players in batches
        for (UUID uuid : dirtyPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                syncPlayerBalance(uuid, player.getName());
                count++;
            } else {
                // Player went offline, remove from dirty set
                dirtyPlayers.remove(uuid);
            }
        }

        return count;
    }

    private void syncPlayerBalance(UUID uuid, String username) {
        if (!plugin.getDatabaseManager().isAvailable() || economy == null) {
            return;
        }

        try {
            double balance = economy.getBalance(Bukkit.getOfflinePlayer(uuid));

            try (Connection conn = plugin.getDatabaseManager().getConnection();
                    PreparedStatement stmt =
                            conn.prepareStatement(
                                    """
                    INSERT INTO player_balances (minecraft_uuid, minecraft_username, balance)
                    VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        minecraft_username = VALUES(minecraft_username),
                        balance = VALUES(balance),
                        last_updated = CURRENT_TIMESTAMP
                    """)) {

                stmt.setString(1, uuid.toString());
                stmt.setString(2, username);
                stmt.setDouble(3, balance);
                stmt.executeUpdate();

                // Remove from dirty set after successful sync
                dirtyPlayers.remove(uuid);
            }
        } catch (SQLException e) {
            plugin.getLogger()
                    .warning("Failed to sync balance for " + username + ": " + e.getMessage());
        }
    }
}
