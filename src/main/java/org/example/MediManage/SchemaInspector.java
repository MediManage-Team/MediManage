package org.example.MediManage;

import java.sql.*;

public class SchemaInspector {
    public static void main(String[] args) {
        String url = "jdbc:sqlite:C:/Users/ksvik/.medimanage/medimanage.db";
        System.out.println("🔍 Inspecting Database: " + url);

        try (Connection conn = DriverManager.getConnection(url)) {
            DatabaseMetaData meta = conn.getMetaData();

            System.out.println("\n--- TABLES & COLUMNS ---");
            try (ResultSet tables = meta.getTables(null, null, "%", new String[] { "TABLE" })) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    System.out.println("\nTABLE: " + tableName);

                    try (ResultSet columns = meta.getColumns(null, null, tableName, "%")) {
                        while (columns.next()) {
                            String colName = columns.getString("COLUMN_NAME");
                            String type = columns.getString("TYPE_NAME");
                            System.out.println("   - " + colName + " (" + type + ")");
                        }
                    }
                }
            }

            System.out.println("\n--- USER ROLES (Distinct) ---");
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT DISTINCT role FROM users")) {
                while (rs.next()) {
                    System.out.println("Found Role: '" + rs.getString("role") + "'");
                }
            } catch (SQLException e) {
                System.out.println("❌ Could not read roles: " + e.getMessage());
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
