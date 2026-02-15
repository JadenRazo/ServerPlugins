package net.serverplugins.bridge.tasks;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import net.milkbowl.vault.economy.Economy;
import net.serverplugins.bridge.BridgeConfig;
import net.serverplugins.bridge.ServerBridge;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public class BalanceSyncTask implements Runnable {

    private final ServerBridge plugin;
    private final Economy economy;
    private HikariDataSource dataSource;

    public BalanceSyncTask(ServerBridge plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
        initDatabase();
    }

    private void initDatabase() {
        BridgeConfig config = plugin.getBridgeConfig();

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName("org.mariadb.jdbc.Driver");
        hikariConfig.setJdbcUrl(
                String.format(
                        "jdbc:mariadb://%s:%d/%s",
                        config.getDatabaseHost(),
                        config.getDatabasePort(),
                        config.getDatabaseName()));
        hikariConfig.setUsername(config.getDatabaseUsername());
        hikariConfig.setPassword(config.getDatabasePassword());
        hikariConfig.setMaximumPoolSize(2);
        hikariConfig.setMinimumIdle(1);
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setValidationTimeout(5000);
        hikariConfig.setIdleTimeout(300000);
        hikariConfig.setMaxLifetime(1800000);
        hikariConfig.setKeepaliveTime(120000);
        hikariConfig.setPoolName("ServerBridge-BalanceSync");
        hikariConfig.addDataSourceProperty("tcpKeepAlive", "true");

        try {
            this.dataSource = new HikariDataSource(hikariConfig);
            createTable();
            plugin.getLogger().info("Balance sync database connection established.");
        } catch (Exception e) {
            plugin.getLogger()
                    .severe("Failed to connect to database for balance sync: " + e.getMessage());
        }
    }

    private void createTable() {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(
                                """
                CREATE TABLE IF NOT EXISTS player_balances (
                    minecraft_uuid VARCHAR(36) PRIMARY KEY,
                    minecraft_username VARCHAR(16) NOT NULL,
                    balance DECIMAL(15,2) NOT NULL DEFAULT 0,
                    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_balance (balance DESC)
                )
                """)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create player_balances table: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        if (dataSource == null || economy == null) return;

        try {
            // Get all offline players and their balances
            List<PlayerBalance> balances = new ArrayList<>();

            for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                if (player.getName() == null) continue;

                double balance = economy.getBalance(player);
                if (balance > 0) {
                    balances.add(
                            new PlayerBalance(
                                    player.getUniqueId().toString(), player.getName(), balance));
                }
            }

            // Sort by balance descending
            balances.sort((a, b) -> Double.compare(b.balance, a.balance));

            // Take top 100
            List<PlayerBalance> top100 = balances.stream().limit(100).toList();

            // Sync to database
            syncToDatabase(top100);

            plugin.getLogger().info("Synced " + top100.size() + " player balances to database.");

        } catch (Exception e) {
            plugin.getLogger().warning("Error syncing balances: " + e.getMessage());
        }
    }

    private void syncToDatabase(List<PlayerBalance> balances) throws SQLException {
        if (balances.isEmpty()) return;

        try (Connection conn = dataSource.getConnection();
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

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private record PlayerBalance(String uuid, String username, double balance) {}
}
