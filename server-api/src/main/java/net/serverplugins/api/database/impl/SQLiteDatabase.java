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

public class SQLiteDatabase implements Database {

    private final String path;
    private HikariDataSource dataSource;

    public SQLiteDatabase(String path) {
        this.path = path;
    }

    @Override
    public void connect() throws SQLException {
        File dbFile = new File(path);
        File parentDir = dbFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) parentDir.mkdirs();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DatabaseType.SQLITE.getJdbcPrefix() + path);
        config.setDriverClassName(DatabaseType.SQLITE.getDriverClass());
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");
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
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
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
        return DatabaseType.SQLITE;
    }
}
