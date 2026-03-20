package org.example.MediManage.config;

import org.example.MediManage.util.AppPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

    public static java.io.File getResolvedDatabaseFile() {
        return resolveDatabaseFile(getCurrentSettings().sqlitePath());
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
        Path installRoot = AppPaths.resolveInstallRoot();
        boolean isInstalled = AppPaths.isPackagedInstall(installRoot);
        Path relativeBase = isInstalled && !AppPaths.isWindows()
                ? AppPaths.appDataDir()
                : installRoot;

        if (configuredPath != null && !configuredPath.trim().isEmpty()) {
            String normalizedPath = configuredPath.trim();
            java.io.File configuredFile = new java.io.File(normalizedPath);

            if (!configuredFile.isAbsolute()) {
                if (isInstalled && normalizedPath.equals("medimanage.db")) {
                    configuredFile = defaultInstalledDatabasePath(installRoot).toFile();
                } else {
                    configuredFile = relativeBase.resolve(normalizedPath).toFile();
                }
            }

            java.io.File parent = configuredFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
                System.err.println("❌ Failed to create configured DB dir: " + parent.getAbsolutePath());
            }
            seedBundledDatabaseIfNeeded(configuredFile.toPath(), installRoot,
                    isInstalled && normalizedPath.equals("medimanage.db"));
            return configuredFile.getAbsoluteFile();
        }

        if (isInstalled) {
            Path defaultPath = defaultInstalledDatabasePath(installRoot);
            java.io.File dbFolder = defaultPath.getParent().toFile();
            if (!dbFolder.exists()) {
                dbFolder.mkdirs();
            }
            seedBundledDatabaseIfNeeded(defaultPath, installRoot, true);
            return defaultPath.toFile();
        } else {
            return installRoot.resolve("medimanage.db").toFile();
        }
    }

    private static Path defaultInstalledDatabasePath(Path installRoot) {
        if (AppPaths.isWindows()) {
            return installRoot.resolve("runtime").resolve("db").resolve("medimanage.db");
        }
        return AppPaths.appDataPath("runtime", "db", "medimanage.db");
    }

    private static void seedBundledDatabaseIfNeeded(Path target, Path installRoot, boolean isInstalled) {
        if (!isInstalled || target == null || Files.exists(target)) {
            return;
        }

        Path source = bundledDatabaseSource(installRoot);
        if (source == null || target.equals(source)) {
            return;
        }

        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (Exception e) {
            System.err.println("⚠️ Failed to seed packaged database from " + source + ": " + e.getMessage());
        }
    }

    private static Path bundledDatabaseSource(Path installRoot) {
        Path[] candidates = new Path[] {
                installRoot.resolve("runtime").resolve("db").resolve("medimanage.db"),
                installRoot.resolve("base_medimanage.db"),
                installRoot.resolve("app").resolve("base_medimanage.db"),
                installRoot.resolve("lib").resolve("app").resolve("base_medimanage.db")
        };

        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
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
