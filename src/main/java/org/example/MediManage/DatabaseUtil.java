package org.example.MediManage;

import org.example.MediManage.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Utility class for database initialization and connection management.
 * Provides centralized database access through DatabaseConfig.
 */
public class DatabaseUtil {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseUtil.class);

    /**
     * Gets a database connection from the connection pool.
     * 
     * @return a database connection
     * @throws SQLException if connection cannot be obtained
     */
    public static Connection getConnection() throws SQLException {
        return DatabaseConfig.getConnection();
    }

    /**
     * Initializes the database schema and ensures proper configuration.
     * 
     * @throws SQLException if initialization fails
     */
    public static void initDB() throws SQLException {
        try (Connection conn = getConnection()) {
            // Enable foreign keys for SQLite
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON;");
            }

            logger.info("Connected to Database successfully");

            // Initialize Schema if tables don't exist
            runSchema(conn);
        }
    }

    /**
     * Executes the schema SQL file to create/update database tables.
     * 
     * @param conn the database connection
     * @throws SQLException if schema execution fails
     */
    private static void runSchema(Connection conn) throws SQLException {
        logger.info("Initializing Database Schema...");

        try (InputStream is = DatabaseUtil.class.getResourceAsStream("/db/schema.sql")) {
            if (is == null) {
                logger.error("Critical: /db/schema.sql not found!");
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
                                logger.debug("Executed schema statement: {}",
                                        sql.substring(0, Math.min(50, sql.length())) + "...");
                            } catch (SQLException e) {
                                // Log but don't fail, as some statements (like duplicate columns) are expected
                                // on existing DBs
                                logger.warn("Database Initialization Note: {} [Statement: {}...]",
                                        e.getMessage(), sql.substring(0, Math.min(50, sql.length())));
                            }
                        }
                    }
                }
            }
        } catch (java.io.IOException e) {
            logger.error("Failed to read schema file", e);
            throw new SQLException("Failed to read schema file", e);
        }

        logger.info("Schema initialized successfully");
    }
}