package org.example.MediManage.config;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseConfig {

    private static final Properties properties = new Properties();

    // Load the settings when the class is first used
    static {
        try (InputStream input = DatabaseConfig.class.getClassLoader().getResourceAsStream("db_config.properties")) {
            if (input == null) {
                System.out.println("⚠️ Warning: db_config.properties not found! Make sure you created it.");
            } else {
                properties.load(input);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        // Read values from the hidden file
        String url = properties.getProperty("db.url");
        String user = properties.getProperty("db.user");
        String password = properties.getProperty("db.password");

        // Safety check
        if (url == null) {
            throw new RuntimeException("❌ Database Config Missing! Check src/main/resources/db_config.properties");
        }

        // Connect to MySQL
        return DriverManager.getConnection(url, user, password);
    }
}