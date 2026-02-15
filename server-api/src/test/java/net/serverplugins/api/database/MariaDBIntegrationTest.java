package net.serverplugins.api.database;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.serverplugins.api.database.impl.MariaDBDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MariaDBIntegrationTest {

    @Container
    private static final MariaDBContainer<?> mariadb =
            new MariaDBContainer<>("mariadb:10.11")
                    .withDatabaseName("testdb")
                    .withUsername("testuser")
                    .withPassword("testpass");

    private MariaDBDatabase database;

    @BeforeEach
    void setUp() throws SQLException {
        database =
                new MariaDBDatabase(
                        mariadb.getHost(),
                        mariadb.getFirstMappedPort(),
                        "testdb",
                        "testuser",
                        "testpass",
                        5,
                        10000);
        database.connect();
    }

    @AfterEach
    void tearDown() {
        if (database != null) {
            database.disconnect();
        }
    }

    @Test
    void testConnectAndDisconnect() {
        assertTrue(database.isConnected(), "Should be connected after connect()");

        database.disconnect();
        assertFalse(database.isConnected(), "Should not be connected after disconnect()");
    }

    @Test
    void testGetType() {
        assertEquals(DatabaseType.MARIADB, database.getType());
    }

    @Test
    void testGetConnectionInfo() {
        assertEquals(mariadb.getHost(), database.getHost());
        assertEquals(mariadb.getFirstMappedPort(), database.getPort());
        assertEquals("testdb", database.getDatabase());
    }

    @Test
    void testCreateTableAndInsert() throws SQLException {
        database.executeUpdate(
                "CREATE TABLE IF NOT EXISTS test_table (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(64), value INT)");

        int rows =
                database.executeUpdate(
                        "INSERT INTO test_table (name, value) VALUES (?, ?)", "test_entry", 42);
        assertEquals(1, rows, "Should insert 1 row");

        try (ResultSet rs =
                database.executeQuery(
                        "SELECT name, value FROM test_table WHERE name = ?", "test_entry")) {
            assertTrue(rs.next(), "Should have a result");
            assertEquals("test_entry", rs.getString("name"));
            assertEquals(42, rs.getInt("value"));
            assertFalse(rs.next(), "Should only have 1 result");
        }

        database.executeUpdate("DROP TABLE test_table");
    }

    @Test
    void testParameterizedQueries() throws SQLException {
        database.executeUpdate(
                "CREATE TABLE IF NOT EXISTS param_test (id INT PRIMARY KEY, data VARCHAR(100))");

        database.executeUpdate("INSERT INTO param_test (id, data) VALUES (?, ?)", 1, "first");
        database.executeUpdate("INSERT INTO param_test (id, data) VALUES (?, ?)", 2, "second");
        database.executeUpdate("INSERT INTO param_test (id, data) VALUES (?, ?)", 3, "third");

        try (ResultSet rs = database.executeQuery("SELECT COUNT(*) AS cnt FROM param_test")) {
            assertTrue(rs.next());
            assertEquals(3, rs.getInt("cnt"));
        }

        try (ResultSet rs =
                database.executeQuery("SELECT data FROM param_test WHERE id > ? ORDER BY id", 1)) {
            assertTrue(rs.next());
            assertEquals("second", rs.getString("data"));
            assertTrue(rs.next());
            assertEquals("third", rs.getString("data"));
            assertFalse(rs.next());
        }

        database.executeUpdate("DROP TABLE param_test");
    }

    @Test
    void testBatchExecution() throws SQLException {
        database.executeBatch(
                "CREATE TABLE IF NOT EXISTS batch_test (id INT PRIMARY KEY, name VARCHAR(50))",
                "INSERT INTO batch_test VALUES (1, 'one')",
                "INSERT INTO batch_test VALUES (2, 'two')",
                "INSERT INTO batch_test VALUES (3, 'three')");

        try (ResultSet rs = database.executeQuery("SELECT COUNT(*) AS cnt FROM batch_test")) {
            assertTrue(rs.next());
            assertEquals(3, rs.getInt("cnt"));
        }

        database.executeUpdate("DROP TABLE batch_test");
    }

    @Test
    void testBatchRollbackOnError() {
        assertThrows(
                SQLException.class,
                () -> {
                    database.executeBatch(
                            "CREATE TABLE IF NOT EXISTS rollback_test (id INT PRIMARY KEY)",
                            "INSERT INTO rollback_test VALUES (1)",
                            "INSERT INTO rollback_test VALUES (1)" // Duplicate key should cause
                            // rollback
                            );
                });
    }

    @Test
    void testExecuteQueryWithConsumer() throws SQLException {
        database.executeUpdate(
                "CREATE TABLE IF NOT EXISTS consumer_test (id INT PRIMARY KEY, val VARCHAR(20))");
        database.executeUpdate("INSERT INTO consumer_test VALUES (1, 'hello')");

        AtomicReference<String> result = new AtomicReference<>();
        database.executeQueryWithConsumer(
                "SELECT val FROM consumer_test WHERE id = ?",
                rs -> {
                    try {
                        if (rs.next()) {
                            result.set(rs.getString("val"));
                        }
                    } catch (SQLException e) {
                        fail("Consumer should not throw: " + e.getMessage());
                    }
                },
                1);

        assertEquals("hello", result.get());
        database.executeUpdate("DROP TABLE consumer_test");
    }

    @Test
    void testExecuteUpdateWithGeneratedKey() throws SQLException {
        database.executeUpdate(
                "CREATE TABLE IF NOT EXISTS genkey_test (id INT AUTO_INCREMENT PRIMARY KEY, data VARCHAR(50))");

        int key1 =
                database.executeUpdateWithGeneratedKey(
                        "INSERT INTO genkey_test (data) VALUES (?)", "first");
        int key2 =
                database.executeUpdateWithGeneratedKey(
                        "INSERT INTO genkey_test (data) VALUES (?)", "second");

        assertTrue(key1 > 0, "First generated key should be positive");
        assertTrue(key2 > key1, "Second key should be greater than first");

        database.executeUpdate("DROP TABLE genkey_test");
    }

    @Test
    void testSchemaExecution() throws SQLException {
        String schema =
                """
                CREATE TABLE IF NOT EXISTS players (
                    uuid VARCHAR(36) PRIMARY KEY,
                    name VARCHAR(16) NOT NULL,
                    balance DOUBLE DEFAULT 0.0,
                    first_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );

                CREATE TABLE IF NOT EXISTS homes (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    player_uuid VARCHAR(36) NOT NULL,
                    home_name VARCHAR(32) NOT NULL,
                    world VARCHAR(64) NOT NULL,
                    x DOUBLE NOT NULL,
                    y DOUBLE NOT NULL,
                    z DOUBLE NOT NULL,
                    FOREIGN KEY (player_uuid) REFERENCES players(uuid)
                );
                """;

        // Execute schema statements individually
        for (String stmt : schema.split(";")) {
            String trimmed = stmt.trim();
            if (!trimmed.isEmpty()) {
                database.executeUpdate(trimmed);
            }
        }

        // Insert test data
        database.executeUpdate(
                "INSERT INTO players (uuid, name, balance) VALUES (?, ?, ?)",
                "abc-123",
                "TestPlayer",
                1000.50);
        database.executeUpdate(
                "INSERT INTO homes (player_uuid, home_name, world, x, y, z) VALUES (?, ?, ?, ?, ?, ?)",
                "abc-123",
                "base",
                "playworld",
                100.5,
                64.0,
                -200.3);

        // Verify data
        AtomicInteger homeCount = new AtomicInteger();
        database.executeQueryWithConsumer(
                "SELECT COUNT(*) AS cnt FROM homes WHERE player_uuid = ?",
                rs -> {
                    try {
                        if (rs.next()) homeCount.set(rs.getInt("cnt"));
                    } catch (SQLException e) {
                        fail(e);
                    }
                },
                "abc-123");
        assertEquals(1, homeCount.get());

        database.executeUpdate("DROP TABLE homes");
        database.executeUpdate("DROP TABLE players");
    }

    @Test
    void testUpdateAndDelete() throws SQLException {
        database.executeUpdate(
                "CREATE TABLE IF NOT EXISTS crud_test (id INT PRIMARY KEY, val INT)");
        database.executeUpdate("INSERT INTO crud_test VALUES (1, 100)");

        // Update
        int updated = database.executeUpdate("UPDATE crud_test SET val = ? WHERE id = ?", 200, 1);
        assertEquals(1, updated);

        try (ResultSet rs = database.executeQuery("SELECT val FROM crud_test WHERE id = ?", 1)) {
            assertTrue(rs.next());
            assertEquals(200, rs.getInt("val"));
        }

        // Delete
        int deleted = database.executeUpdate("DELETE FROM crud_test WHERE id = ?", 1);
        assertEquals(1, deleted);

        try (ResultSet rs = database.executeQuery("SELECT COUNT(*) AS cnt FROM crud_test")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt("cnt"));
        }

        database.executeUpdate("DROP TABLE crud_test");
    }
}
