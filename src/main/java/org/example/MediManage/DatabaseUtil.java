package org.example.MediManage;

import org.example.MediManage.config.DatabaseConfig;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DatabaseUtil {
    private static final Pattern ALTER_ADD_COLUMN_PATTERN = Pattern.compile(
            "(?is)^ALTER\\s+TABLE\\s+([`\"\\[]?[A-Za-z_][A-Za-z0-9_]*[`\"\\]]?)\\s+ADD\\s+COLUMN\\s+([`\"\\[]?[A-Za-z_][A-Za-z0-9_]*[`\"\\]]?).*");

    public static Connection getConnection() throws SQLException {
        return DatabaseConfig.getConnection();
    }

    public static void initDB() throws SQLException {
        try (Connection conn = getConnection()) {
            // Ensure SQLite FK checks are on for each new connection.
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON;");
            }

            System.out.println("✅ Connected to Database successfully");

            // Initialize Schema if tables don't exist
            runSchema(conn);

            // Auto-seed sample data if DB is fresh (idempotent)
            runSeedDataIfNeeded(conn);
        }
    }

    public static void initDB(DatabaseConfig.ConnectionSettings settings) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection(settings)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON;");
            }

            System.out.println("✅ Connected to Database successfully");
            runSchema(conn);
        }
    }

    private static void runSchema(Connection conn) throws SQLException {
        System.out.println("⚙️ Initializing Database Schema...");
        String schemaResource = resolveSchemaResource(conn);

        try (InputStream is = DatabaseUtil.class.getResourceAsStream(schemaResource)) {
            if (is == null) {
                System.err.println("❌ Critical: " + schemaResource + " not found!");
                return;
            }
            // Use Scanner to read the file and split by semicolon
            try (java.util.Scanner scanner = new java.util.Scanner(is, StandardCharsets.UTF_8.name())) {
                scanner.useDelimiter(";");
                try (Statement stmt = conn.createStatement()) {
                    while (scanner.hasNext()) {
                        String sql = scanner.next().trim();
                        if (!sql.isEmpty()) {
                            if (shouldSkipAlterAddColumn(conn, sql)) {
                                continue;
                            }

                            try {
                                stmt.execute(sql);
                                System.out.println("✅ Executed schema statement: "
                                        + sql.substring(0, Math.min(50, sql.length())) + "...");
                            } catch (SQLException e) {
                                // Log but don't fail so existing DBs can still boot.
                                System.err
                                        .println("Database Initialization Warning: " + e.getMessage() + " [Statement: "
                                                + sql.substring(0, Math.min(50, sql.length())) + "...]");
                            }
                        }
                    }
                }
            }
        } catch (java.io.IOException e) {
            throw new SQLException("Failed to read schema file", e);
        }

        System.out.println("✅ Schema initialized successfully.");
    }

    /**
     * Automatically seeds sample data if the database is fresh.
     * Uses suppliers table as the idempotent guard — if it has data, seeding is
     * skipped.
     */
    private static void runSeedDataIfNeeded(Connection conn) {
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM suppliers")) {
            if (rs.next() && rs.getInt(1) > 0) {
                System.out.println("ℹ️ Seed data already exists — skipping.");
                return;
            }
        } catch (SQLException e) {
            // Table might not exist yet, that's okay
            System.out.println("ℹ️ Skipping seed check: " + e.getMessage());
            return;
        }

        System.out.println("🌱 Seeding sample data...");
        String[] seedFiles = { "/db/seed_data.sql", "/db/seed_dashboard.sql" };
        for (String seedFile : seedFiles) {
            runSqlResource(conn, seedFile);
        }
        System.out.println("✅ Sample data seeded successfully.");
    }

    /**
     * Executes all SQL statements from a classpath resource file.
     */
    private static void runSqlResource(Connection conn, String resourcePath) {
        try (InputStream is = DatabaseUtil.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                System.err.println("⚠️ Seed file not found: " + resourcePath);
                return;
            }
            try (java.util.Scanner scanner = new java.util.Scanner(is, StandardCharsets.UTF_8.name())) {
                scanner.useDelimiter(";");
                try (Statement stmt = conn.createStatement()) {
                    while (scanner.hasNext()) {
                        String sql = scanner.next().trim();
                        if (!sql.isEmpty() && !sql.startsWith("--")) {
                            try {
                                stmt.execute(sql);
                            } catch (SQLException e) {
                                // Log but continue — some INSERT OR IGNORE may warn
                                System.err.println("Seed warning: " + e.getMessage());
                            }
                        }
                    }
                }
            }
        } catch (java.io.IOException | SQLException e) {
            System.err.println("⚠️ Failed to read seed file: " + resourcePath + " — " + e.getMessage());
        }
    }

    private static String resolveSchemaResource(Connection conn) throws SQLException {
        return "/db/schema.sql";
    }

    private static boolean shouldSkipAlterAddColumn(Connection conn, String sql) throws SQLException {
        String normalized = stripLeadingSqlComments(sql).trim();
        Matcher matcher = ALTER_ADD_COLUMN_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return false;
        }

        String tableName = sanitizeIdentifier(matcher.group(1));
        String columnName = sanitizeIdentifier(matcher.group(2));
        if (tableName.isEmpty() || columnName.isEmpty()) {
            return false;
        }

        if (columnExists(conn, tableName, columnName)) {
            System.out.println(
                    "ℹ️ Skipped migration: column '" + columnName + "' already exists on '" + tableName + "'.");
            return true;
        }
        return false;
    }

    private static boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet rs = metaData.getColumns(conn.getCatalog(), null, tableName, null)) {
            while (rs.next()) {
                String existing = rs.getString("COLUMN_NAME");
                if (columnName.equalsIgnoreCase(existing)) {
                    return true;
                }
            }
        }
        try (ResultSet rs = metaData.getColumns(conn.getCatalog(), "public", tableName, null)) {
            while (rs.next()) {
                String existing = rs.getString("COLUMN_NAME");
                if (columnName.equalsIgnoreCase(existing)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String stripLeadingSqlComments(String sql) {
        StringBuilder cleaned = new StringBuilder(sql.length());
        boolean started = false;

        for (String line : sql.split("\\R")) {
            String trimmed = line.trim();
            if (!started && (trimmed.isEmpty() || trimmed.startsWith("--"))) {
                continue;
            }
            started = true;
            cleaned.append(line).append('\n');
        }
        return cleaned.toString();
    }

    private static String sanitizeIdentifier(String identifier) {
        if (identifier == null) {
            return "";
        }
        String value = identifier.trim();
        if (value.length() >= 2) {
            if ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("`") && value.endsWith("`"))
                    || (value.startsWith("[") && value.endsWith("]"))) {
                value = value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
