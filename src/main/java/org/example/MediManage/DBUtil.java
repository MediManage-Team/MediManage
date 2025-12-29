package org.example.MediManage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DBUtil {

    // 1. UPDATE WITH YOUR SPECIFIC INFO
    // If you named your database "medimanage_db" in Workbench, keep it.
    private static final String DB_URL = "jdbc:mysql://localhost:3306/medimanage_db";

    // 2. USE THE PASSWORD YOU WROTE DOWN EARLIER
    private static final String DB_USER = "root";
    private static final String DB_PASS = "Password@123";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    public static void initDB() {
        try (Connection conn = getConnection()) {
            System.out.println("✅ Connected to MySQL Database successfully");

            // Optional: You can keep table creation logic here if you want to allow
            // the app to create tables automatically, but for now, rely on Workbench.

        } catch (SQLException e) {
            System.err.println("❌ Connection Failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}