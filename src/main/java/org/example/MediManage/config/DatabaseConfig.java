package org.example.MediManage.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Database configuration class with HikariCP connection pooling.
 * Provides optimized database connections with proper resource management.
 */
public class DatabaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);
    private static HikariDataSource dataSource;
    private static Properties properties = new Properties();

    static {
        try (InputStream input = DatabaseConfig.class.getClassLoader().getResourceAsStream("db_config.properties")) {
            if (input == null) {
                logger.warn("db_config.properties not found! Using defaults.");
            } else {
                properties.load(input);
            }
        } catch (IOException ex) {
            logger.error("Failed to load database configuration", ex);
        }

        // Initialize connection pool
        initializeConnectionPool();
    }

    /**
     * Initializes HikariCP connection pool for optimized database access.
     */
    private static void initializeConnectionPool() {
        try {
            File dbFile = resolveDatabaseFile();
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(url);
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);

            // SQLite specific configurations
            config.addDataSourceProperty("journal_mode", "WAL");
            config.addDataSourceProperty("synchronous", "NORMAL");

            // Connection test query
            config.setConnectionTestQuery("SELECT 1");

            dataSource = new HikariDataSource(config);
            logger.info("HikariCP connection pool initialized successfully for: {}", url);

        } catch (Exception e) {
            logger.error("Failed to initialize connection pool", e);
            throw new RuntimeException("Database connection pool initialization failed", e);
        }
    }

    /**
     * Gets a connection from the pool.
     * 
     * @return a database connection
     * @throws SQLException if connection cannot be obtained
     */
    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Connection pool not initialized");
        }

        Connection conn = dataSource.getConnection();

        // Enable foreign keys for SQLite
        try (java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        } catch (SQLException e) {
            logger.warn("Failed to enable foreign keys", e);
        }

        return conn;
    }

    /**
     * Resolves the database file path based on environment.
     * 
     * @return File object pointing to the database
     */
    private static File resolveDatabaseFile() {
        String userDir = System.getProperty("user.dir");

        // Logic: If installed in Program Files, use 'runtime/db' subfolder.
        // Otherwise (Dev Mode), use project root.
        if (userDir.contains("Program Files") || userDir.contains("Program Files (x86)")) {
            File dbFolder = new File(userDir, "runtime/db");
            if (!dbFolder.exists()) {
                boolean created = dbFolder.mkdirs();
                if (!created && !dbFolder.exists()) {
                    logger.error("Failed to create DB directory: {}", dbFolder.getAbsolutePath());
                }
            }
            return new File(dbFolder, "medimanage.db");
        } else {
            // Dev Mode
            return new File(userDir, "medimanage.db");
        }
    }

    /**
     * Checks if the database connection is healthy.
     * 
     * @return true if connection is healthy, false otherwise
     */
    public static boolean isHealthy() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            logger.error("Database health check failed", e);
            return false;
        }
    }

    /**
     * Closes the connection pool. Should be called on application shutdown.
     */
    public static void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Connection pool shut down successfully");
        }
    }
}