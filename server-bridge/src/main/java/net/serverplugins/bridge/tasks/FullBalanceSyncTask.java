package net.serverplugins.bridge.tasks;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import net.milkbowl.vault.economy.Economy;
import net.serverplugins.bridge.ServerBridge;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/**
 * Full balance sync task that processes all offline players to maintain accurate leaderboard data.
 * Runs every 30 minutes using an optimized O(n log k) algorithm with a min-heap to extract the top
 * 100 players without sorting the entire dataset.
 *
 * <p>Yields periodically to avoid blocking the thread pool for extended periods.
 */
public class FullBalanceSyncTask implements Runnable {

    private static final int TOP_PLAYER_COUNT = 100;
    private static final int YIELD_INTERVAL = 500;

    private final ServerBridge plugin;
    private final Economy economy;

    public FullBalanceSyncTask(ServerBridge plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    @Override
    public void run() {
        if (!plugin.getDatabaseManager().isAvailable() || economy == null) {
            return;
        }

        try {
            long startTime = System.currentTimeMillis();

            // Use a min-heap to efficiently track top 100 players
            // Min-heap keeps the smallest balance at the root, allowing us to
            // maintain only the top K elements in O(n log k) time
            PriorityQueue<PlayerBalance> topPlayers =
                    new PriorityQueue<>(
                            TOP_PLAYER_COUNT + 1, Comparator.comparingDouble(pb -> pb.balance));

            OfflinePlayer[] allPlayers = Bukkit.getOfflinePlayers();
            int processedCount = 0;
            int validPlayerCount = 0;

            for (OfflinePlayer player : allPlayers) {
                if (player.getName() == null) {
                    processedCount++;
                    continue;
                }

                double balance = economy.getBalance(player);
                if (balance > 0) {
                    validPlayerCount++;

                    topPlayers.offer(
                            new PlayerBalance(
                                    player.getUniqueId().toString(), player.getName(), balance));

                    // Keep only top 100 by removing the smallest when we exceed the limit
                    if (topPlayers.size() > TOP_PLAYER_COUNT) {
                        topPlayers.poll(); // Remove player with smallest balance
                    }
                }

                processedCount++;

                // Yield periodically to avoid blocking the async thread pool
                if (processedCount % YIELD_INTERVAL == 0) {
                    try {
                        Thread.sleep(50); // Brief yield to let other tasks execute
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        plugin.getLogger().warning("Full balance sync interrupted");
                        return;
                    }
                }
            }

            // Convert min-heap to list and reverse for descending order
            List<PlayerBalance> top100 = new ArrayList<>(topPlayers);
            top100.sort((a, b) -> Double.compare(b.balance, a.balance)); // Sort descending

            // Sync to database
            syncToDatabase(top100);

            long duration = System.currentTimeMillis() - startTime;
            plugin.getLogger()
                    .info(
                            String.format(
                                    "Full balance sync completed: processed %d players, found %d with balances, synced top %d to database (took %dms)",
                                    processedCount, validPlayerCount, top100.size(), duration));

        } catch (Exception e) {
            plugin.getLogger().warning("Error in full balance sync: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void syncToDatabase(List<PlayerBalance> balances) throws SQLException {
        if (balances.isEmpty()) {
            plugin.getLogger().warning("No player balances to sync");
            return;
        }

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

            for (PlayerBalance balance : balances) {
                stmt.setString(1, balance.uuid);
                stmt.setString(2, balance.username);
                stmt.setDouble(3, balance.balance);
                stmt.addBatch();
            }

            stmt.executeBatch();
        }
    }

    private record PlayerBalance(String uuid, String username, double balance) {}
}
