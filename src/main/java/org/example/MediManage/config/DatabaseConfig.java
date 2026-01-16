package org.example.MediManage.config;

import java.io.IOException;
import java.io.InputStream;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseConfig {

    private static Properties properties = new Properties();

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
            java.io.File dbFile = resolveDatabaseFile();

            // Initial Setup if needed
            // if (!dbFile.exists() || dbFile.length() < 1024 * 1024) {
            // initializeDatabaseFile(dbFile);
            // }

            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            // System.out.println("🔌 Connecting to database at: " + url);
            Connection conn = DriverManager.getConnection(url);

            try (java.sql.Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA journal_mode = WAL;");
                stmt.execute("PRAGMA synchronous = NORMAL;");
                stmt.execute("PRAGMA foreign_keys = ON;");
            }

            return conn;

        } catch (Exception e) {
            throw new SQLException("Failed to configure database connection: " + e.getMessage(), e);
        }
    }

    private static java.io.File resolveDatabaseFile() {
        // Safe Guard: If running in "Program Files", force AppData usage.
        String userDir = System.getProperty("user.dir");
        boolean isInstalled = userDir.contains("Program Files") || userDir.contains("Program Files (x86)");

        // Priority 1: Local file in execution directory (Dev Mode / Portable)
        // Only if NOT installed in Program Files
        if (!isInstalled) {
            java.io.File localFile = new java.io.File("medimanage.db");
            if (localFile.exists()) {
                // System.out.println("📂 Dev Mode: Using local database file.");
                return localFile;
            }
        }

        // Priority 2: Production Mode (AppData)
        // Strict logic as requested: Use APPDATA/MediManage/medimanage.db
        String appData = System.getenv("APPDATA");
        if (appData == null) {
            // Fallback for non-Windows or if env var is missing
            appData = System.getProperty("user.home") + "/AppData/Roaming";
        }

        java.io.File dbFolder = new java.io.File(appData, "MediManage");
        if (!dbFolder.exists()) {
            boolean created = dbFolder.mkdirs();
            if (!created && !dbFolder.exists()) {
                System.err.println("❌ Failed to create database folder: " + dbFolder.getAbsolutePath());
            }
        }

        return new java.io.File(dbFolder, "medimanage.db");
    }

}