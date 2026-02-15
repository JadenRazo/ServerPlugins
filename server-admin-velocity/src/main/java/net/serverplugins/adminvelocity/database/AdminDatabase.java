package net.serverplugins.adminvelocity.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import net.serverplugins.adminvelocity.AdminVelocityConfig;
import org.slf4j.Logger;

/**
 * Database connection pool manager for ServerAdmin Velocity.
 *
 * <p>Uses HikariCP for efficient connection pooling to MariaDB.
 */
public class AdminDatabase {

    private final Logger logger;
    private HikariDataSource dataSource;

    public AdminDatabase(Logger logger) {
        this.logger = logger;
    }

    /**
     * Connects to the database using configuration settings.
     *
     * @param config the configuration containing database connection details
     * @throws SQLException if connection cannot be established
     */
    public void connect(AdminVelocityConfig config) throws SQLException {
        HikariConfig hikariConfig = new HikariConfig();

        String jdbcUrl =
                String.format(
                        "jdbc:mariadb://%s:%d/%s",
                        config.getDatabaseHost(),
                        config.getDatabasePort(),
                        config.getDatabaseName());

        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(config.getDatabaseUsername());
        hikariConfig.setPassword(config.getDatabasePassword());

        // Connection pool settings
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setIdleTimeout(600000);
        hikariConfig.setMaxLifetime(1800000);
        hikariConfig.setLeakDetectionThreshold(60000);

        // Performance settings
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
        hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
        hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
        hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
        hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
        hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
        hikariConfig.addDataSourceProperty("maintainTimeStats", "false");

        hikariConfig.setPoolName("ServerAdminVelocity-Pool");

        dataSource = new HikariDataSource(hikariConfig);

        // Test connection
        try (Connection conn = dataSource.getConnection()) {
            logger.info(
                    "Successfully connected to MariaDB at {}:{}",
                    config.getDatabaseHost(),
                    config.getDatabasePort());
        } catch (SQLException e) {
            logger.error("Failed to connect to database: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Gets a connection from the pool.
     *
     * @return a database connection
     * @throws SQLException if connection cannot be obtained
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database connection pool is not initialized or is closed");
        }
        return dataSource.getConnection();
    }

    /**
     * Executes the schema SQL file to initialize tables.
     *
     * @param sql the schema SQL content
     */
    public void executeSchema(String sql) {
        CompletableFuture.runAsync(
                () -> {
                    try (Connection conn = getConnection();
                            Statement stmt = conn.createStatement()) {
                        // Split on semicolons and execute each statement separately
                        String[] statements = sql.split(";");
                        for (String statement : statements) {
                            String trimmed = statement.trim();
                            if (!trimmed.isEmpty()) {
                                stmt.execute(trimmed);
                            }
                        }
                        logger.info("Database schema initialized successfully");
                    } catch (SQLException e) {
                        logger.error("Failed to execute schema: {}", e.getMessage());
                    }
                });
    }

    /**
     * Executes an async database operation.
     *
     * @param operation the database operation to execute
     * @return a CompletableFuture that completes when the operation is done
     */
    public <T> CompletableFuture<T> executeAsync(DatabaseOperation<T> operation) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try (Connection conn = getConnection()) {
                        return operation.execute(conn);
                    } catch (SQLException e) {
                        logger.error("Database operation failed: {}", e.getMessage());
                        throw new RuntimeException(e);
                    }
                });
    }

    /**
     * Closes the database connection pool.
     *
     * <p>Should be called during plugin shutdown.
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool closed");
        }
    }

    /**
     * Checks if the database connection is active.
     *
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    /**
     * Functional interface for database operations.
     *
     * @param <T> the return type of the operation
     */
    @FunctionalInterface
    public interface DatabaseOperation<T> {
        T execute(Connection connection) throws SQLException;
    }
}
