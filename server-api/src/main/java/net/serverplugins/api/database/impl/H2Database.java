package net.serverplugins.api.database.impl;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.sql.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.serverplugins.api.database.Database;
import net.serverplugins.api.database.DatabaseType;
import net.serverplugins.api.utils.AsyncExecutor;

public class H2Database implements Database {

    private final String path;
    private final String dbName;
    private HikariDataSource dataSource;

    public H2Database(String path, String dbName) {
        this.path = path;
        this.dbName = dbName;
    }

    @Override
    public void connect() throws SQLException {
        File dbDir = new File(path);
        if (!dbDir.exists()) dbDir.mkdirs();

        HikariConfig config = new HikariConfig();
        String absolutePath = dbDir.getAbsolutePath();
        // Add H2 parameters for better concurrency handling:
        // LOCK_TIMEOUT=10000 - wait up to 10 seconds for locks
        // DB_CLOSE_DELAY=-1 - keep database open between connections
        // AUTO_SERVER=TRUE - allow multiple connections
        String jdbcUrl =
                DatabaseType.H2.getJdbcPrefix()
                        + "file:"
                        + absolutePath
                        + "/"
                        + dbName
                        + ";LOCK_TIMEOUT=10000;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE";
        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName(DatabaseType.H2.getDriverClass());
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.setMaximumPoolSize(15);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setValidationTimeout(5000);
        config.setIdleTimeout(300000);
        config.setMaxLifetime(1800000);
        config.setLeakDetectionThreshold(30000);

        dataSource = new HikariDataSource(config);
    }

    @Override
    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Override
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (!isConnected()) throw new SQLException("Database is not connected");
        return dataSource.getConnection();
    }

    @Override
    public ResultSet executeQuery(String query, Object... params) throws SQLException {
        Connection conn = getConnection();
        PreparedStatement stmt = conn.prepareStatement(query);
        setParameters(stmt, params);
        ResultSet rs = stmt.executeQuery();
        // Return a wrapper that closes Connection when ResultSet is closed
        return new ConnectionAwareResultSet(rs, stmt, conn);
    }

    /**
     * ResultSet wrapper that properly closes the underlying Connection and Statement when the
     * ResultSet is closed. This prevents connection leaks.
     */
    private static class ConnectionAwareResultSet extends ResultSetWrapper {
        private final Statement stmt;
        private final Connection conn;

        ConnectionAwareResultSet(ResultSet rs, Statement stmt, Connection conn) {
            super(rs);
            this.stmt = stmt;
            this.conn = conn;
        }

        @Override
        public void close() throws SQLException {
            try {
                super.close();
            } finally {
                try {
                    if (stmt != null) stmt.close();
                } finally {
                    if (conn != null) conn.close();
                }
            }
        }
    }

    @Override
    public int executeUpdate(String query, Object... params) throws SQLException {
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(query)) {
            setParameters(stmt, params);
            return stmt.executeUpdate();
        }
    }

    @Override
    public int[] executeBatch(String... queries) throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                for (String query : queries) stmt.addBatch(query);
                int[] results = stmt.executeBatch();
                conn.commit();
                return results;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    @Override
    public CompletableFuture<ResultSet> executeQueryAsync(String query, Object... params) {
        return AsyncExecutor.supplyAsync(
                () -> {
                    try {
                        return executeQuery(query, params);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    @Override
    public CompletableFuture<Integer> executeUpdateAsync(String query, Object... params) {
        return AsyncExecutor.supplyAsync(
                () -> {
                    try {
                        return executeUpdate(query, params);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    @Override
    public void executeQueryWithConsumer(
            String query, Consumer<ResultSet> consumer, Object... params) throws SQLException {
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(query)) {
            setParameters(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                consumer.accept(rs);
            }
        }
    }

    @Override
    public CompletableFuture<Void> executeQueryAsyncWithConsumer(
            String query, Consumer<ResultSet> consumer, Object... params) {
        return AsyncExecutor.runAsync(
                () -> {
                    try {
                        executeQueryWithConsumer(query, consumer, params);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    @Override
    public PreparedStatement prepareStatement(String query) throws SQLException {
        return getConnection().prepareStatement(query);
    }

    @Override
    public DatabaseType getType() {
        return DatabaseType.H2;
    }
}
