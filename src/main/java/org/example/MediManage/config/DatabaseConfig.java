package org.example.MediManage.config;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.prefs.Preferences;

public class DatabaseConfig {
    public enum Backend {
        SQLITE;

        public static Backend from(String raw) {
            return SQLITE;
        }
    }

    public record ConnectionSettings(
            Backend backend,
            String sqlitePath) {
    }

    private static Properties properties = new Properties();
    private static final String PREF_NODE = "/org/example/MediManage";
    public static final String PREF_DB_BACKEND = "db_backend";
    public static final String PREF_DB_PATH = "db_path";

    public static final String DB_BACKEND_PROPERTY = "medimanage.db.backend";
    public static final String DB_BACKEND_ENV = "MEDIMANAGE_DB_BACKEND";
    public static final String DB_PATH_PROPERTY = "medimanage.db.path";
    public static final String DB_PATH_ENV = "MEDIMANAGE_DB_PATH";

    private static final String PROP_DB_PATH = "db.path";

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
        return createSqliteConnection(settings);
    }

    public static ConnectionSettings getCurrentSettings() {
        Backend backend = Backend.SQLITE;

        String sqlitePath = firstNonBlank(
                System.getProperty(DB_PATH_PROPERTY),
                System.getenv(DB_PATH_ENV),
                preferenceValue(PREFERENCES, PREF_DB_PATH),
                properties.getProperty(PROP_DB_PATH));

        return new ConnectionSettings(backend, sqlitePath);
    }

    public static void testConnection(ConnectionSettings settings) throws SQLException {
        try (Connection ignored = createSqliteConnection(settings)) {
            // Successful open/close means config is valid.
        }
    }

    public static boolean isSqlite(Connection conn) throws SQLException {
        String productName = conn.getMetaData().getDatabaseProductName();
        return productName != null && productName.toLowerCase().contains("sqlite");
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

    private static java.io.File resolveDatabaseFile(String configuredPath) {
        // Reliably determine the actual installation directory, not just the working directory
        String installPath = System.getProperty("user.dir");
        try {
            String path = DatabaseConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            // Handle Windows paths properly if they start with "/" like "/C:/..."
            if (path.startsWith("/") && path.contains(":")) {
                path = path.substring(1);
            }
            java.io.File appFile = new java.io.File(path);
            
            if (path.endsWith(".jar")) {
                java.io.File appDir = appFile.getParentFile();
                if (appDir != null && (appDir.getName().equals("app") || appDir.getName().equals("lib"))) {
                    installPath = appDir.getParentFile().getAbsolutePath();
                } else if (appDir != null) {
                    installPath = appDir.getAbsolutePath();
                }
            } else {
                installPath = appFile.getParentFile().getParentFile().getAbsolutePath(); 
            }
        } catch (Exception e) {
            System.err.println("⚠️ Could not determine code source location, falling back to user.dir: " + e.getMessage());
        }

        boolean isInstalled = installPath.contains("Program Files") || installPath.contains("Program Files (x86)") || new java.io.File(installPath, "runtime").exists();

        if (configuredPath != null && !configuredPath.trim().isEmpty()) {
            java.io.File configuredFile = new java.io.File(configuredPath.trim());
            
            // If the configured path is relative, resolve it against the calculated installPath rather than System32
            if (!configuredFile.isAbsolute()) {
                if (isInstalled && configuredPath.trim().equals("medimanage.db")) {
                    // For the installed app, force the default "medimanage.db" into runtime/db
                    java.io.File dbFolder = new java.io.File(installPath, "runtime/db");
                    dbFolder.mkdirs();
                    configuredFile = new java.io.File(dbFolder, "medimanage.db");
                } else {
                    configuredFile = new java.io.File(installPath, configuredPath.trim());
                }
            }
            
            java.io.File parent = configuredFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
                System.err.println("❌ Failed to create configured DB dir: " + parent.getAbsolutePath());
            }
            return configuredFile.getAbsoluteFile();
        }

        // Default logic if configuredPath is completely empty
        if (isInstalled) {
            java.io.File dbFolder = new java.io.File(installPath, "runtime/db");
            if (!dbFolder.exists()) {
                dbFolder.mkdirs();
            }
            return new java.io.File(dbFolder, "medimanage.db");
        } else {
            return new java.io.File(installPath, "medimanage.db");
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

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }
}
