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

}