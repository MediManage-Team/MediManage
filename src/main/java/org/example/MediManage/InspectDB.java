package org.example.MediManage;

import java.sql.*;

public class InspectDB {
    public static void main(String[] args) {
        String dbPath = "src/main/resources/db/medimanage.db";
        String url = "jdbc:sqlite:" + dbPath;

        try (Connection conn = DriverManager.getConnection(url);
                Statement stmt = conn.createStatement()) {

            System.out.println("Connected to " + dbPath);

            // Check Views
            System.out.println("\n--- VIEWS ---");
            try (ResultSet rs = stmt.executeQuery("SELECT name, sql FROM sqlite_master WHERE type='view'")) {
                while (rs.next()) {
                    System.out.println("View: " + rs.getString("name"));
                    System.out.println("SQL: " + rs.getString("sql"));
                    System.out.println("--------------------------------------------------");
                }
            }

            // Check Triggers
            System.out.println("\n--- TRIGGERS ---");
            try (ResultSet rs = stmt.executeQuery("SELECT name, sql FROM sqlite_master WHERE type='trigger'")) {
                while (rs.next()) {
                    System.out.println("Trigger: " + rs.getString("name"));
                    System.out.println("SQL: " + rs.getString("sql"));
                    System.out.println("--------------------------------------------------");
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
