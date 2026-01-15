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
            seedUsers(conn);
        } catch (SQLException e) {
            System.err.println("❌ Connection Failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void seedUsers(Connection conn) {
        try {
            // 1. Migrate Legacy Roles
            try (java.sql.Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("UPDATE users SET role='CASHIER' WHERE role='STAFF'");
            }

            // 2. Ensure Standard Users Exist
            // Check and Insert Admin
            ensureUser(conn, "admin", "admin123", "ADMIN");
            ensureUser(conn, "manager", "admin", "MANAGER");
            ensureUser(conn, "pharmacist", "admin", "PHARMACIST");
            ensureUser(conn, "cashier", "admin", "CASHIER");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void ensureUser(Connection conn, String username, String password, String role) throws SQLException {
        // Simple check-then-insert to avoid complex SQL dependency on constraints
        String checkSql = "SELECT user_id FROM users WHERE username = ?";
        try (java.sql.PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setString(1, username);
            try (java.sql.ResultSet rs = checkStmt.executeQuery()) {
                if (!rs.next()) {
                    // Doesn't exist, insert
                    String insertSql = "INSERT INTO users (username, password, role) VALUES (?, ?, ?)";
                    try (java.sql.PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        insertStmt.setString(1, username);
                        insertStmt.setString(2, password);
                        insertStmt.setString(3, role);
                        insertStmt.executeUpdate();
                        System.out.println("Created user: " + username);
                    }
                }
                // If exists, do nothing. Respect manual DB changes.
            }
        }
    }
}