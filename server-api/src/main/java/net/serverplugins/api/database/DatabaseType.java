package net.serverplugins.api.database;

public enum DatabaseType {
    H2,
    SQLITE,
    MYSQL,
    MARIADB;

    public String getDriverClass() {
        return switch (this) {
            case H2 -> "org.h2.Driver";
            case SQLITE -> "org.sqlite.JDBC";
            case MYSQL -> "com.mysql.cj.jdbc.Driver";
            case MARIADB -> "org.mariadb.jdbc.Driver";
        };
    }

    public String getJdbcPrefix() {
        return switch (this) {
            case H2 -> "jdbc:h2:";
            case SQLITE -> "jdbc:sqlite:";
            case MYSQL -> "jdbc:mysql://";
            case MARIADB -> "jdbc:mariadb://";
        };
    }

    public boolean isEmbedded() {
        return this == H2 || this == SQLITE;
    }
}
