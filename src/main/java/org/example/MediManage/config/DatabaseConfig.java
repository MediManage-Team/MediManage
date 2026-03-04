package org.example.MediManage.config;

import java.io.IOException;
import java.io.InputStream;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.prefs.Preferences;

public class DatabaseConfig {
    public enum Backend {
        SQLITE,
        POSTGRESQL;

        public static Backend from(String raw) {
            if (raw == null || raw.isBlank()) {
                return SQLITE;
            }
            String normalized = raw.trim().toLowerCase();
            return switch (normalized) {
                case "postgres", "postgresql", "pg" -> POSTGRESQL;
                default -> SQLITE;
            };
        }
    }

    public record ConnectionSettings(
            Backend backend,
            String sqlitePath,
            String postgresHost,
            int postgresPort,
            String postgresDatabase,
            String postgresUser,
            String postgresPassword) {
    }

    private static Properties properties = new Properties();
    private static final String PREF_NODE = "/org/example/MediManage";
    public static final String PREF_DB_BACKEND = "db_backend";
    public static final String PREF_DB_PATH = "db_path";
    public static final String PREF_PG_HOST = "pg_host";
    public static final String PREF_PG_PORT = "pg_port";
    public static final String PREF_PG_DATABASE = "pg_database";
    public static final String PREF_PG_USER = "pg_user";
    public static final String PREF_PG_PASSWORD = "pg_password";

    public static final String DB_BACKEND_PROPERTY = "medimanage.db.backend";
    public static final String DB_BACKEND_ENV = "MEDIMANAGE_DB_BACKEND";
    public static final String DB_PATH_PROPERTY = "medimanage.db.path";
    public static final String DB_PATH_ENV = "MEDIMANAGE_DB_PATH";
    public static final String DB_PG_HOST_PROPERTY = "medimanage.db.pg.host";
    public static final String DB_PG_HOST_ENV = "MEDIMANAGE_DB_PG_HOST";
    public static final String DB_PG_PORT_PROPERTY = "medimanage.db.pg.port";
    public static final String DB_PG_PORT_ENV = "MEDIMANAGE_DB_PG_PORT";
    public static final String DB_PG_DATABASE_PROPERTY = "medimanage.db.pg.database";
    public static final String DB_PG_DATABASE_ENV = "MEDIMANAGE_DB_PG_DATABASE";
    public static final String DB_PG_USER_PROPERTY = "medimanage.db.pg.user";
    public static final String DB_PG_USER_ENV = "MEDIMANAGE_DB_PG_USER";
    public static final String DB_PG_PASSWORD_PROPERTY = "medimanage.db.pg.password";
    public static final String DB_PG_PASSWORD_ENV = "MEDIMANAGE_DB_PG_PASSWORD";

    private static final String PROP_DB_BACKEND = "db.backend";
    private static final String PROP_DB_PATH = "db.path";
    private static final String PROP_PG_HOST = "db.postgres.host";
    private static final String PROP_PG_PORT = "db.postgres.port";
    private static final String PROP_PG_DATABASE = "db.postgres.database";
    private static final String PROP_PG_USER = "db.postgres.user";
    private static final String PROP_PG_PASSWORD = "db.postgres.password";

    private static final String DEFAULT_PG_HOST = "localhost";
    private static final int DEFAULT_PG_PORT = 5432;
    private static final String DEFAULT_PG_DATABASE = "medimanage";
    private static final String DEFAULT_PG_USER = "postgres";
    private static final Preferences PREFERENCES = initializePreferences();

    static {
        try (InputStream input = DatabaseConfig.class.getClassLoader().getResourceAsStream("db_config.properties")) {
            if (input == null) {
                System.out.println("⚠️ db_config.properties not found! Using defaults.");
            } else {
                properties.load(input);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        try {
            ConnectionSettings settings = getCurrentSettings();
            return getConnection(settings);
        } catch (Exception e) {
            throw new SQLException("Failed to configure database connection: " + e.getMessage(), e);
        }
    }

    public static Connection getConnection(ConnectionSettings settings) throws SQLException {
        return openConnection(settings);
    }

    public static ConnectionSettings getCurrentSettings() {
        Backend backend = Backend.from(firstNonBlank(
                System.getProperty(DB_BACKEND_PROPERTY),
                System.getenv(DB_BACKEND_ENV),
                preferenceValue(PREFERENCES, PREF_DB_BACKEND),
                properties.getProperty(PROP_DB_BACKEND)));

        String sqlitePath = firstNonBlank(
                System.getProperty(DB_PATH_PROPERTY),
                System.getenv(DB_PATH_ENV),
                preferenceValue(PREFERENCES, PREF_DB_PATH),
                properties.getProperty(PROP_DB_PATH));

        String pgHost = firstNonBlank(
                System.getProperty(DB_PG_HOST_PROPERTY),
                System.getenv(DB_PG_HOST_ENV),
                preferenceValue(PREFERENCES, PREF_PG_HOST),
                properties.getProperty(PROP_PG_HOST),
                DEFAULT_PG_HOST);

        int pgPort = parsePort(firstNonBlank(
                System.getProperty(DB_PG_PORT_PROPERTY),
                System.getenv(DB_PG_PORT_ENV),
                preferenceValue(PREFERENCES, PREF_PG_PORT),
                properties.getProperty(PROP_PG_PORT)));

        String pgDatabase = firstNonBlank(
                System.getProperty(DB_PG_DATABASE_PROPERTY),
                System.getenv(DB_PG_DATABASE_ENV),
                preferenceValue(PREFERENCES, PREF_PG_DATABASE),
                properties.getProperty(PROP_PG_DATABASE),
                DEFAULT_PG_DATABASE);

        String pgUser = firstNonBlank(
                System.getProperty(DB_PG_USER_PROPERTY),
                System.getenv(DB_PG_USER_ENV),
                preferenceValue(PREFERENCES, PREF_PG_USER),
                properties.getProperty(PROP_PG_USER),
                DEFAULT_PG_USER);

        String pgPassword = firstNonBlank(
                System.getProperty(DB_PG_PASSWORD_PROPERTY),
                System.getenv(DB_PG_PASSWORD_ENV),
                preferenceValue(PREFERENCES, PREF_PG_PASSWORD),
                properties.getProperty(PROP_PG_PASSWORD),
                "");

        return new ConnectionSettings(backend, sqlitePath, pgHost, pgPort, pgDatabase, pgUser, pgPassword);
    }

    public static void testConnection(ConnectionSettings settings) throws SQLException {
        try (Connection ignored = openConnection(settings)) {
            // Successful open/close means config is valid.
        }
    }

    public static boolean isPostgreSql(Connection conn) throws SQLException {
        String productName = conn.getMetaData().getDatabaseProductName();
        return productName != null && productName.toLowerCase().contains("postgres");
    }

    public static boolean isSqlite(Connection conn) throws SQLException {
        String productName = conn.getMetaData().getDatabaseProductName();
        return productName != null && productName.toLowerCase().contains("sqlite");
    }

    private static Connection openConnection(ConnectionSettings settings) throws SQLException {
        if (settings.backend() == Backend.POSTGRESQL) {
            return createPostgresConnection(settings);
        }
        return createSqliteConnection(settings);
    }

    private static Connection createSqliteConnection(ConnectionSettings settings) throws SQLException {
        java.io.File dbFile = resolveDatabaseFile(settings.sqlitePath());
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        Connection conn = DriverManager.getConnection(url);

        try (java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode = WAL;");
            stmt.execute("PRAGMA synchronous = NORMAL;");
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
        return conn;
    }

    private static Connection createPostgresConnection(ConnectionSettings settings) throws SQLException {
        String host = requireNonBlank(settings.postgresHost(), "PostgreSQL host is required.");
        String database = requireNonBlank(settings.postgresDatabase(), "PostgreSQL database name is required.");
        String user = requireNonBlank(settings.postgresUser(), "PostgreSQL username is required.");

        // Auto-create the database if it doesn't exist
        ensureDatabaseExists(host, settings.postgresPort(), database, user,
                settings.postgresPassword() == null ? "" : settings.postgresPassword());

        String url = "jdbc:postgresql://" + host + ":" + settings.postgresPort() + "/" + database;
        java.util.Properties pgProperties = new java.util.Properties();
        pgProperties.setProperty("user", user);
        pgProperties.setProperty("password", settings.postgresPassword() == null ? "" : settings.postgresPassword());
        pgProperties.setProperty("sslmode", "disable");
        return DriverManager.getConnection(url, pgProperties);
    }

    /**
     * Connects to the default 'postgres' database and creates the target database
     * if it doesn't exist.
     */
    private static void ensureDatabaseExists(String host, int port, String database, String user, String password) {
        String adminUrl = "jdbc:postgresql://" + host + ":" + port + "/postgres";
        java.util.Properties adminProps = new java.util.Properties();
        adminProps.setProperty("user", user);
        adminProps.setProperty("password", password);
        adminProps.setProperty("sslmode", "disable");

        try (Connection adminConn = DriverManager.getConnection(adminUrl, adminProps)) {
            // Check if the database exists
            try (java.sql.PreparedStatement ps = adminConn.prepareStatement(
                    "SELECT 1 FROM pg_database WHERE datname = ?")) {
                ps.setString(1, database);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return; // Database already exists
                    }
                }
            }

            // Create the database
            try (Statement stmt = adminConn.createStatement()) {
                stmt.execute("CREATE DATABASE \"" + database.replace("\"", "\"\"") + "\"");
                System.out.println("✅ Created PostgreSQL database: " + database);
            }
        } catch (SQLException e) {
            // Log but don't throw — the main connection attempt will provide a clear error
            System.err.println("⚠️ Could not auto-create database '" + database + "': " + e.getMessage());
        }
    }

    private static java.io.File resolveDatabaseFile(String configuredPath) {
        if (configuredPath != null) {
            java.io.File configuredFile = new java.io.File(configuredPath.trim()).getAbsoluteFile();
            java.io.File parent = configuredFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
                System.err.println("❌ Failed to create configured DB dir: " + parent.getAbsolutePath());
            }
            return configuredFile;
        }

        String userDir = System.getProperty("user.dir");

        // Logic: If installed in Program Files, use 'runtime/db' subfolder.
        // Otherwise (Dev Mode), use project root.
        if (userDir.contains("Program Files") || userDir.contains("Program Files (x86)")) {
            java.io.File dbFolder = new java.io.File(userDir, "runtime/db");
            if (!dbFolder.exists()) {
                boolean created = dbFolder.mkdirs();
                if (!created && !dbFolder.exists()) {
                    System.err.println("❌ Failed to create DB dir: " + dbFolder.getAbsolutePath());
                }
            }
            return new java.io.File(dbFolder, "medimanage.db");
        } else {
            // Dev Mode
            return new java.io.File(userDir, "medimanage.db");
        }
    }

    private static Preferences initializePreferences() {
        if (System.getProperty("surefire.test.class.path") != null) {
            return null;
        }
        try {
            return Preferences.userRoot().node(PREF_NODE);
        } catch (Exception e) {
            return null;
        }
    }

    private static String preferenceValue(Preferences prefs, String key) {
        if (prefs == null) {
            return null;
        }
        String value = prefs.get(key, null);
        return value == null || value.trim().isEmpty() ? null : value;
    }

    private static int parsePort(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_PG_PORT;
        }
        try {
            int port = Integer.parseInt(raw.trim());
            if (port <= 0 || port > 65535) {
                return DEFAULT_PG_PORT;
            }
            return port;
        } catch (NumberFormatException e) {
            return DEFAULT_PG_PORT;
        }
    }

    private static String requireNonBlank(String value, String errorMessage) throws SQLException {
        if (value == null || value.isBlank()) {
            throw new SQLException(errorMessage);
        }
        return value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

}
