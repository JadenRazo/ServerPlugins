package net.serverplugins.api.database.impl;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.serverplugins.api.database.Database;
import net.serverplugins.api.database.DatabaseType;
import net.serverplugins.api.utils.AsyncExecutor;

/**
 * MariaDB database implementation using HikariCP connection pooling. Compatible with MySQL-based
 * hosting providers like UltraServers.
 */
public class MariaDBDatabase implements Database {

    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final int poolSize;
    private final long connectionTimeout;
    private HikariDataSource dataSource;

    public MariaDBDatabase(
            String host, int port, String database, String username, String password) {
        this(host, port, database, username, password, 10, 30000);
    }

    public MariaDBDatabase(
            String host,
            int port,
            String database,
            String username,
            String password,
            int poolSize,
            long connectionTimeout) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.poolSize = poolSize;
        this.connectionTimeout = connectionTimeout;
    }

    @Override
    public void connect() throws SQLException {
        // Explicitly load the MariaDB driver class before HikariCP uses it
        try {
            Class.forName(DatabaseType.MARIADB.getDriverClass());
        } catch (ClassNotFoundException e) {
            throw new SQLException("MariaDB driver not found: " + e.getMessage(), e);
        }

        HikariConfig config = new HikariConfig();

        String jdbcUrl = DatabaseType.MARIADB.getJdbcPrefix() + host + ":" + port + "/" + database;
        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName(DatabaseType.MARIADB.getDriverClass());
        config.setUsername(username);
        config.setPassword(password);

        // Connection pool settings
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(connectionTimeout);
        config.setValidationTimeout(5000);
        config.setIdleTimeout(300000);
        config.setMaxLifetime(1800000);
        config.setKeepaliveTime(120000);
        config.setLeakDetectionThreshold(30000);

        // MariaDB/MySQL specific optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
        config.addDataSourceProperty("tcpKeepAlive", "true");

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
        if (!isConnected()) {
            throw new SQLException("Database is not connected");
        }
        return dataSource.getConnection();
    }

    @Override
    public ResultSet executeQuery(String query, Object... params) throws SQLException {
        Connection conn = getConnection();
        PreparedStatement stmt = conn.prepareStatement(query);
        setParameters(stmt, params);
        ResultSet rs = stmt.executeQuery();
        // Wrap the ResultSet so that closing it also closes the Statement and Connection
        return new ResultSetWrapper.ConnectionAwareResultSet(rs, stmt, conn);
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
                for (String query : queries) {
                    stmt.addBatch(query);
                }
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
        return DatabaseType.MARIADB;
    }

    /** Execute an update and return the generated key (for INSERT with auto-increment) */
    public int executeUpdateWithGeneratedKey(String query, Object... params) throws SQLException {
        try (Connection conn = getConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            setParameters(stmt, params);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
            return -1;
        }
    }

    /** Async version of executeUpdateWithGeneratedKey */
    public CompletableFuture<Integer> executeUpdateWithGeneratedKeyAsync(
            String query, Object... params) {
        return AsyncExecutor.supplyAsync(
                () -> {
                    try {
                        return executeUpdateWithGeneratedKey(query, params);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getDatabase() {
        return database;
    }
}
