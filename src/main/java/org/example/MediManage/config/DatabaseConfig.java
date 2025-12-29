package org.example.MediManage.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConfig {

    // 1. Point to your new MySQL Database
    private static final String DB_URL = "jdbc:mysql://localhost:3306/medimanage_db";

    // 2. Add your MySQL Credentials
    private static final String DB_USER = "root";
    private static final String DB_PASS = "Password@123"; // ⚠️ REPLACE THIS with the password you wrote down!

    static {
        try {
            // 3. Load the MySQL Driver instead of SQLite
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("MySQL JDBC Driver not found", e);
        }
    }

    public static Connection getConnection() {
        try {
            return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        } catch (Exception e) {
            throw new RuntimeException("MySQL connection failed", e);
        }
    }
}