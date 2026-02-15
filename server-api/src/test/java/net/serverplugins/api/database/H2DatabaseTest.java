package net.serverplugins.api.database;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.serverplugins.api.database.impl.H2Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class H2DatabaseTest {

    @TempDir Path tempDir;

    private H2Database database;

    @BeforeEach
    void setUp() {
        database = new H2Database(tempDir.toString(), "testdb");
    }

    @AfterEach
    void tearDown() {
        if (database != null && database.isConnected()) {
            database.disconnect();
        }
    }

    @Test
    @DisplayName("Should connect successfully")
    void shouldConnectSuccessfully() throws SQLException {
        database.connect();
        assertThat(database.isConnected()).isTrue();
    }

    @Test
    @DisplayName("Should disconnect successfully")
    void shouldDisconnectSuccessfully() throws SQLException {
        database.connect();
        assertThat(database.isConnected()).isTrue();

        database.disconnect();
        assertThat(database.isConnected()).isFalse();
    }

    @Test
    @DisplayName("Should return H2 database type")
    void shouldReturnH2DatabaseType() {
        assertThat(database.getType()).isEqualTo(DatabaseType.H2);
    }

    @Test
    @DisplayName("Should create table successfully")
    void shouldCreateTableSuccessfully() throws SQLException {
        database.connect();

        String createTableSQL =
                "CREATE TABLE test_users ("
                        + "id INT PRIMARY KEY AUTO_INCREMENT, "
                        + "username VARCHAR(255) NOT NULL, "
                        + "balance DOUBLE NOT NULL"
                        + ")";

        int result = database.executeUpdate(createTableSQL);
        assertThat(result).isEqualTo(0); // CREATE TABLE returns 0
    }

    @Test
    @DisplayName("Should insert and select data")
    void shouldInsertAndSelectData() throws SQLException {
        database.connect();

        database.executeUpdate(
                "CREATE TABLE test_users (id INT PRIMARY KEY AUTO_INCREMENT, username VARCHAR(255), balance DOUBLE)");

        int affectedRows =
                database.executeUpdate(
                        "INSERT INTO test_users (username, balance) VALUES (?, ?)",
                        "Steve",
                        1234.56);

        assertThat(affectedRows).isEqualTo(1);

        try (ResultSet rs =
                database.executeQuery("SELECT * FROM test_users WHERE username = ?", "Steve")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("username")).isEqualTo("Steve");
            assertThat(rs.getDouble("balance")).isEqualTo(1234.56);
            assertThat(rs.next()).isFalse();
        }
    }

    @Test
    @DisplayName("Should handle parameterized queries")
    void shouldHandleParameterizedQueries() throws SQLException {
        database.connect();

        database.executeUpdate(
                "CREATE TABLE test_items (id INT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(255), quantity INT)");

        database.executeUpdate(
                "INSERT INTO test_items (name, quantity) VALUES (?, ?)", "Diamond", 64);

        try (ResultSet rs =
                database.executeQuery(
                        "SELECT * FROM test_items WHERE name = ? AND quantity > ?",
                        "Diamond",
                        50)) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("name")).isEqualTo("Diamond");
            assertThat(rs.getInt("quantity")).isEqualTo(64);
        }
    }

    @Test
    @DisplayName("Should execute batch operations")
    void shouldExecuteBatchOperations() throws SQLException {
        database.connect();

        database.executeUpdate(
                "CREATE TABLE test_logs (id INT PRIMARY KEY AUTO_INCREMENT, message VARCHAR(255))");

        int[] results =
                database.executeBatch(
                        "INSERT INTO test_logs (message) VALUES ('First log')",
                        "INSERT INTO test_logs (message) VALUES ('Second log')",
                        "INSERT INTO test_logs (message) VALUES ('Third log')");

        assertThat(results).hasSize(3);
        assertThat(results).containsExactly(1, 1, 1);

        try (ResultSet rs = database.executeQuery("SELECT COUNT(*) as count FROM test_logs")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("count")).isEqualTo(3);
        }
    }

    @Test
    @DisplayName("Should rollback batch on error")
    void shouldRollbackBatchOnError() throws SQLException {
        database.connect();

        database.executeUpdate("CREATE TABLE test_data (id INT PRIMARY KEY, val VARCHAR(255))");

        assertThatThrownBy(
                        () ->
                                database.executeBatch(
                                        "INSERT INTO test_data (id, val) VALUES (1, 'First')",
                                        "INSERT INTO test_data (id, val) VALUES (1, 'Duplicate')", // Should fail
                                        "INSERT INTO test_data (id, val) VALUES (2, 'Second')"))
                .isInstanceOf(SQLException.class);

        // Verify rollback - no rows should be inserted
        try (ResultSet rs = database.executeQuery("SELECT COUNT(*) as count FROM test_data")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("count")).isEqualTo(0);
        }
    }

    @Test
    @DisplayName("Should execute query with consumer")
    void shouldExecuteQueryWithConsumer() throws SQLException {
        database.connect();

        database.executeUpdate("CREATE TABLE test_scores (player VARCHAR(255), score INT)");

        database.executeUpdate(
                "INSERT INTO test_scores (player, score) VALUES (?, ?)", "Alice", 100);
        database.executeUpdate("INSERT INTO test_scores (player, score) VALUES (?, ?)", "Bob", 200);
        database.executeUpdate(
                "INSERT INTO test_scores (player, score) VALUES (?, ?)", "Charlie", 150);

        List<String> players = new ArrayList<>();
        AtomicInteger totalScore = new AtomicInteger(0);

        database.executeQueryWithConsumer(
                "SELECT * FROM test_scores ORDER BY score DESC",
                rs -> {
                    try {
                        while (rs.next()) {
                            players.add(rs.getString("player"));
                            totalScore.addAndGet(rs.getInt("score"));
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });

        assertThat(players).containsExactly("Bob", "Charlie", "Alice");
        assertThat(totalScore.get()).isEqualTo(450);
    }

    @Test
    @DisplayName("Should handle multiple inserts")
    void shouldHandleMultipleInserts() throws SQLException {
        database.connect();

        database.executeUpdate(
                "CREATE TABLE test_players (id INT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(255))");

        for (int i = 0; i < 10; i++) {
            database.executeUpdate("INSERT INTO test_players (name) VALUES (?)", "Player" + i);
        }

        try (ResultSet rs = database.executeQuery("SELECT COUNT(*) as count FROM test_players")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("count")).isEqualTo(10);
        }
    }

    @Test
    @DisplayName("Should handle update operations")
    void shouldHandleUpdateOperations() throws SQLException {
        database.connect();

        database.executeUpdate(
                "CREATE TABLE test_accounts (id INT PRIMARY KEY, username VARCHAR(255), coins INT)");

        database.executeUpdate(
                "INSERT INTO test_accounts (id, username, coins) VALUES (?, ?, ?)",
                1,
                "TestUser",
                100);

        int updated =
                database.executeUpdate("UPDATE test_accounts SET coins = ? WHERE id = ?", 500, 1);

        assertThat(updated).isEqualTo(1);

        try (ResultSet rs =
                database.executeQuery("SELECT coins FROM test_accounts WHERE id = ?", 1)) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("coins")).isEqualTo(500);
        }
    }

    @Test
    @DisplayName("Should handle delete operations")
    void shouldHandleDeleteOperations() throws SQLException {
        database.connect();

        database.executeUpdate("CREATE TABLE test_temp (id INT PRIMARY KEY, data VARCHAR(255))");

        database.executeUpdate("INSERT INTO test_temp (id, data) VALUES (?, ?)", 1, "Delete me");

        int deleted = database.executeUpdate("DELETE FROM test_temp WHERE id = ?", 1);

        assertThat(deleted).isEqualTo(1);

        try (ResultSet rs = database.executeQuery("SELECT COUNT(*) as count FROM test_temp")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("count")).isEqualTo(0);
        }
    }

    @Test
    @DisplayName("Should throw SQLException when not connected")
    void shouldThrowSQLExceptionWhenNotConnected() {
        assertThatThrownBy(() -> database.executeQuery("SELECT 1"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("not connected");
    }

    @Test
    @DisplayName("Should handle null parameters safely")
    void shouldHandleNullParametersSafely() throws SQLException {
        database.connect();

        database.executeUpdate(
                "CREATE TABLE test_nullable (id INT PRIMARY KEY, data VARCHAR(255))");

        database.executeUpdate("INSERT INTO test_nullable (id, data) VALUES (?, ?)", 1, null);

        try (ResultSet rs =
                database.executeQuery("SELECT data FROM test_nullable WHERE id = ?", 1)) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("data")).isNull();
        }
    }

    @Test
    @DisplayName("Should properly close resources with try-with-resources")
    void shouldProperlyCloseResourcesWithTryWithResources() throws SQLException {
        database.connect();

        database.executeUpdate("CREATE TABLE test_close (id INT PRIMARY KEY, val VARCHAR(255))");
        database.executeUpdate("INSERT INTO test_close (id, val) VALUES (?, ?)", 1, "test");

        ResultSet rs = database.executeQuery("SELECT * FROM test_close");
        assertThat(rs.next()).isTrue();
        rs.close();

        // ResultSet should be closed and isClosed should return true
        assertThat(rs.isClosed()).isTrue();
    }

    @Test
    @DisplayName("Should handle empty result sets")
    void shouldHandleEmptyResultSets() throws SQLException {
        database.connect();

        database.executeUpdate("CREATE TABLE test_empty (id INT PRIMARY KEY, name VARCHAR(255))");

        try (ResultSet rs = database.executeQuery("SELECT * FROM test_empty")) {
            assertThat(rs.next()).isFalse();
        }
    }

    @Test
    @DisplayName("Should handle complex queries with joins")
    void shouldHandleComplexQueriesWithJoins() throws SQLException {
        database.connect();

        database.executeUpdate("CREATE TABLE test_users2 (id INT PRIMARY KEY, name VARCHAR(255))");
        database.executeUpdate(
                "CREATE TABLE test_orders (id INT PRIMARY KEY, user_id INT, amount DOUBLE)");

        database.executeUpdate("INSERT INTO test_users2 (id, name) VALUES (?, ?)", 1, "Alice");
        database.executeUpdate(
                "INSERT INTO test_orders (id, user_id, amount) VALUES (?, ?, ?)", 1, 1, 99.99);

        String query =
                "SELECT u.name, o.amount FROM test_users2 u "
                        + "INNER JOIN test_orders o ON u.id = o.user_id WHERE u.id = ?";

        try (ResultSet rs = database.executeQuery(query, 1)) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("name")).isEqualTo("Alice");
            assertThat(rs.getDouble("amount")).isEqualTo(99.99);
        }
    }
}
