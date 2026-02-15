package net.serverplugins.api.database;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DatabaseTypeTest {

    @Test
    @DisplayName("H2 should have correct driver class")
    void h2ShouldHaveCorrectDriverClass() {
        assertThat(DatabaseType.H2.getDriverClass()).isEqualTo("org.h2.Driver");
    }

    @Test
    @DisplayName("SQLITE should have correct driver class")
    void sqliteShouldHaveCorrectDriverClass() {
        assertThat(DatabaseType.SQLITE.getDriverClass()).isEqualTo("org.sqlite.JDBC");
    }

    @Test
    @DisplayName("MYSQL should have correct driver class")
    void mysqlShouldHaveCorrectDriverClass() {
        assertThat(DatabaseType.MYSQL.getDriverClass()).isEqualTo("com.mysql.cj.jdbc.Driver");
    }

    @Test
    @DisplayName("MARIADB should have correct driver class")
    void mariadbShouldHaveCorrectDriverClass() {
        assertThat(DatabaseType.MARIADB.getDriverClass()).isEqualTo("org.mariadb.jdbc.Driver");
    }

    @Test
    @DisplayName("H2 should have correct JDBC prefix")
    void h2ShouldHaveCorrectJdbcPrefix() {
        assertThat(DatabaseType.H2.getJdbcPrefix()).isEqualTo("jdbc:h2:");
    }

    @Test
    @DisplayName("SQLITE should have correct JDBC prefix")
    void sqliteShouldHaveCorrectJdbcPrefix() {
        assertThat(DatabaseType.SQLITE.getJdbcPrefix()).isEqualTo("jdbc:sqlite:");
    }

    @Test
    @DisplayName("MYSQL should have correct JDBC prefix")
    void mysqlShouldHaveCorrectJdbcPrefix() {
        assertThat(DatabaseType.MYSQL.getJdbcPrefix()).isEqualTo("jdbc:mysql://");
    }

    @Test
    @DisplayName("MARIADB should have correct JDBC prefix")
    void mariadbShouldHaveCorrectJdbcPrefix() {
        assertThat(DatabaseType.MARIADB.getJdbcPrefix()).isEqualTo("jdbc:mariadb://");
    }

    @Test
    @DisplayName("H2 should be embedded")
    void h2ShouldBeEmbedded() {
        assertThat(DatabaseType.H2.isEmbedded()).isTrue();
    }

    @Test
    @DisplayName("SQLITE should be embedded")
    void sqliteShouldBeEmbedded() {
        assertThat(DatabaseType.SQLITE.isEmbedded()).isTrue();
    }

    @Test
    @DisplayName("MYSQL should not be embedded")
    void mysqlShouldNotBeEmbedded() {
        assertThat(DatabaseType.MYSQL.isEmbedded()).isFalse();
    }

    @Test
    @DisplayName("MARIADB should not be embedded")
    void mariadbShouldNotBeEmbedded() {
        assertThat(DatabaseType.MARIADB.isEmbedded()).isFalse();
    }

    @Test
    @DisplayName("Should have exactly 4 enum values")
    void shouldHaveExactlyFourEnumValues() {
        assertThat(DatabaseType.values()).hasSize(4);
    }

    @Test
    @DisplayName("Should verify all enum values exist")
    void shouldVerifyAllEnumValuesExist() {
        assertThat(DatabaseType.values())
                .extracting(Enum::name)
                .containsExactlyInAnyOrder("H2", "SQLITE", "MYSQL", "MARIADB");
    }

    @Test
    @DisplayName("All driver classes should be non-null")
    void allDriverClassesShouldBeNonNull() {
        for (DatabaseType type : DatabaseType.values()) {
            assertThat(type.getDriverClass())
                    .as("Driver class for %s should not be null", type.name())
                    .isNotNull()
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("All JDBC prefixes should be non-null")
    void allJdbcPrefixesShouldBeNonNull() {
        for (DatabaseType type : DatabaseType.values()) {
            assertThat(type.getJdbcPrefix())
                    .as("JDBC prefix for %s should not be null", type.name())
                    .isNotNull()
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("All JDBC prefixes should start with jdbc:")
    void allJdbcPrefixesShouldStartWithJdbc() {
        for (DatabaseType type : DatabaseType.values()) {
            assertThat(type.getJdbcPrefix())
                    .as("JDBC prefix for %s should start with 'jdbc:'", type.name())
                    .startsWith("jdbc:");
        }
    }

    @Test
    @DisplayName("Embedded databases should have file-based JDBC prefixes")
    void embeddedDatabasesShouldHaveFileBasedJdbcPrefixes() {
        assertThat(DatabaseType.H2.getJdbcPrefix()).endsWith(":");
        assertThat(DatabaseType.SQLITE.getJdbcPrefix()).endsWith(":");
    }

    @Test
    @DisplayName("Remote databases should have network-based JDBC prefixes")
    void remoteDatabasesShouldHaveNetworkBasedJdbcPrefixes() {
        assertThat(DatabaseType.MYSQL.getJdbcPrefix()).endsWith("//");
        assertThat(DatabaseType.MARIADB.getJdbcPrefix()).endsWith("//");
    }
}
