package org.example.MediManage;

import org.example.MediManage.config.DatabaseConfig;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DBUtil {

    public static Connection getConnection() throws SQLException {
        return DatabaseConfig.getConnection();
    }

    public static void initDB() throws SQLException {
        try (Connection conn = getConnection()) {
            // Enable foreign keys for SQLite
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON;");
            }

            System.out.println("✅ Connected to Database successfully");

            // Initialize Schema if tables don't exist
            runSchema(conn);
        }
    }

    private static void runSchema(Connection conn) throws SQLException {
        System.out.println("⚙️ Initializing Database Schema...");

        try (InputStream is = DBUtil.class.getResourceAsStream("/db/schema.sql")) {
            if (is == null) {
                System.err.println("❌ Critical: /db/schema.sql not found!");
                return;
            }
            // Use Scanner to read the file and split by semicolon
            try (java.util.Scanner scanner = new java.util.Scanner(is, StandardCharsets.UTF_8.name())) {
                scanner.useDelimiter(";");
                try (Statement stmt = conn.createStatement()) {
                    while (scanner.hasNext()) {
                        String sql = scanner.next().trim();
                        if (!sql.isEmpty()) {
                            try {
                                stmt.execute(sql);
                                System.out.println("✅ Executed schema statement: "
                                        + sql.substring(0, Math.min(50, sql.length())) + "...");
                            } catch (SQLException e) {
                                // Log but don't fail, as some statements (like duplicate columns) are expected
                                // on existing DBs
                                System.err.println("Database Initialization Note: " + e.getMessage() + " [Statement: "
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
}