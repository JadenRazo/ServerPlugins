package net.serverplugins.api.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface Database {

    void connect() throws SQLException;

    void disconnect();

    boolean isConnected();

    Connection getConnection() throws SQLException;

    ResultSet executeQuery(String query, Object... params) throws SQLException;

    int executeUpdate(String query, Object... params) throws SQLException;

    int[] executeBatch(String... queries) throws SQLException;

    CompletableFuture<ResultSet> executeQueryAsync(String query, Object... params);

    CompletableFuture<Integer> executeUpdateAsync(String query, Object... params);

    void executeQueryWithConsumer(String query, Consumer<ResultSet> consumer, Object... params)
            throws SQLException;

    CompletableFuture<Void> executeQueryAsyncWithConsumer(
            String query, Consumer<ResultSet> consumer, Object... params);

    PreparedStatement prepareStatement(String query) throws SQLException;

    default void setParameters(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
    }

    DatabaseType getType();

    @FunctionalInterface
    interface ResultSetMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    default <T> T query(String sql, ResultSetMapper<T> mapper, Object... params) {
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            setParameters(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                return mapper.map(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database query failed: " + sql, e);
        }
    }

    default void execute(String sql, Object... params) {
        try {
            executeUpdate(sql, params);
        } catch (SQLException e) {
            throw new RuntimeException("Database execute failed: " + sql, e);
        }
    }
}
