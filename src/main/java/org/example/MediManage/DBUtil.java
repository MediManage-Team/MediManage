package org.example.MediManage;

import org.example.MediManage.config.DatabaseConfig;
import java.sql.Connection;
import java.sql.SQLException;

public class DBUtil {

    // Redirect requests to the Secure DatabaseConfig
    public static Connection getConnection() throws SQLException {
        return DatabaseConfig.getConnection();
    }

    public static void initDB() {
        try (Connection conn = getConnection()) {
            System.out.println("✅ Connected to MySQL Database successfully");
        } catch (SQLException e) {
            System.err.println("❌ Connection Failed: " + e.getMessage());
            // This print stack trace helps you see IF the password is wrong
            e.printStackTrace();
        }
    }
}