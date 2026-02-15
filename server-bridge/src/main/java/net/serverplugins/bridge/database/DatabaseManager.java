package net.serverplugins.bridge.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import net.serverplugins.bridge.BridgeConfig;
import net.serverplugins.bridge.ServerBridge;

public class DatabaseManager {

    private final ServerBridge plugin;
    private final BridgeConfig config;
    private HikariDataSource dataSource;

    public DatabaseManager(ServerBridge plugin) {
        this.plugin = plugin;
        this.config = plugin.getBridgeConfig();
    }

    public void initialize() {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(
                String.format(
                        "jdbc:mariadb://%s:%d/%s",
                        config.getDatabaseHost(),
                        config.getDatabasePort(),
                        config.getDatabaseName()));
        hikariConfig.setUsername(config.getDatabaseUsername());
        hikariConfig.setPassword(config.getDatabasePassword());

        // Connection pool settings
        hikariConfig.setMaximumPoolSize(8);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setPoolName("ServerBridge-Shared");

        // Connection behavior
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setValidationTimeout(5000);
        hikariConfig.setIdleTimeout(300000);
        hikariConfig.setMaxLifetime(1800000);
        hikariConfig.setKeepaliveTime(120000);
        hikariConfig.setLeakDetectionThreshold(60000);

        // Performance optimizations
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
        hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
        hikariConfig.addDataSourceProperty("tcpKeepAlive", "true");

        try {
            // Load MariaDB driver explicitly before HikariCP uses it
            Class.forName("org.mariadb.jdbc.Driver");
            this.dataSource = new HikariDataSource(hikariConfig);
            createTables();
            plugin.getLogger().info("Database connection pool initialized (ServerBridge-Shared).");
        } catch (Exception e) {
            plugin.getLogger()
                    .severe("Failed to initialize database connection pool: " + e.getMessage());
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    private void createTables() {
        String[] tableCreations = {
            // Player balances table (synced from SMP server)
            """
            CREATE TABLE IF NOT EXISTS player_balances (
                minecraft_uuid VARCHAR(36) PRIMARY KEY,
                minecraft_username VARCHAR(16) NOT NULL,
                balance DECIMAL(15,2) NOT NULL DEFAULT 0,
                last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_balance (balance DESC)
            )
            """,

            // Linked accounts table
            """
            CREATE TABLE IF NOT EXISTS linked_accounts (
                discord_id BIGINT PRIMARY KEY,
                minecraft_uuid VARCHAR(36) UNIQUE NOT NULL,
                minecraft_username VARCHAR(16) NOT NULL,
                linked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_minecraft_uuid (minecraft_uuid)
            )
            """,

            // Link verification codes
            """
            CREATE TABLE IF NOT EXISTS link_codes (
                code VARCHAR(6) PRIMARY KEY,
                discord_id BIGINT NOT NULL,
                discord_username VARCHAR(32) NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                expires_at TIMESTAMP NOT NULL,
                INDEX idx_expires (expires_at)
            )
            """,

            // Daily rewards tracking
            """
            CREATE TABLE IF NOT EXISTS daily_rewards (
                minecraft_uuid VARCHAR(36) PRIMARY KEY,
                last_claim DATE,
                current_streak INT DEFAULT 0,
                longest_streak INT DEFAULT 0,
                total_claims INT DEFAULT 0,
                total_earned DECIMAL(15,2) DEFAULT 0
            )
            """
        };

        try (Connection conn = getConnection()) {
            for (String sql : tableCreations) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                }
            }
            plugin.getLogger().info("Database tables initialized.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create database tables: " + e.getMessage());
            throw new RuntimeException("Table creation failed", e);
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Database connection pool is not initialized");
        }
        return dataSource.getConnection();
    }

    public boolean isAvailable() {
        if (dataSource == null || dataSource.isClosed()) {
            return false;
        }

        try (Connection conn = getConnection()) {
            return conn.isValid(2);
        } catch (SQLException e) {
            plugin.getLogger().warning("Database availability check failed: " + e.getMessage());
            return false;
        }
    }

    // Backward compatibility method
    public boolean isConnected() {
        return isAvailable();
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection pool closed.");
        }
    }
}
