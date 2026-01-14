package org.example.MediManage.service;

import org.example.MediManage.DBUtil;

import java.sql.Connection;
import java.sql.Statement;

public class DatabaseService {

        public static void initialize() {
                try (Connection conn = DBUtil.getConnection();
                                Statement stmt = conn.createStatement()) {

                        // Enable Foreign Keys
                        stmt.execute("PRAGMA foreign_keys = ON;");

                        // 1. Medicines Table
                        String createMedicines = "CREATE TABLE IF NOT EXISTS medicines (" +
                                        "medicine_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                        "name TEXT NOT NULL, " +
                                        "company TEXT, " +
                                        "expiry_date TEXT, " +
                                        "price REAL DEFAULT 0.0" +
                                        ");";
                        stmt.execute(createMedicines);

                        // 2. Stock Table
                        String createStock = "CREATE TABLE IF NOT EXISTS stock (" +
                                        "medicine_id INTEGER PRIMARY KEY, " +
                                        "quantity INTEGER DEFAULT 0, " +
                                        "FOREIGN KEY (medicine_id) REFERENCES medicines(medicine_id) ON DELETE CASCADE"
                                        +
                                        ");";
                        stmt.execute(createStock);

                        // 3. Customers Table
                        String createCustomers = "CREATE TABLE IF NOT EXISTS customers (" +
                                        "customer_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                        "name TEXT, " +
                                        "email TEXT, " +
                                        "phone TEXT, " +
                                        "address TEXT, " +
                                        "nominee_name TEXT, " +
                                        "nominee_relation TEXT, " +
                                        "insurance_provider TEXT, " +
                                        "insurance_policy_no TEXT, " +
                                        "diseases TEXT, " +
                                        "photo_id_path TEXT" +
                                        ");";
                        stmt.execute(createCustomers);

                        // Ensure phone exists (Migration fix)
                        try {
                                stmt.execute("ALTER TABLE customers ADD COLUMN phone TEXT;");
                        } catch (Exception ignored) {
                                // Column likely exists or other minor issue. Ignore for now as we want to
                                // proceed.
                        }

                        // 4. Bills Table
                        String createBills = "CREATE TABLE IF NOT EXISTS bills (" +
                                        "bill_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                        "customer_id INTEGER, " +
                                        "bill_date TEXT DEFAULT CURRENT_TIMESTAMP, " +
                                        "total_amount REAL, " +
                                        "FOREIGN KEY (customer_id) REFERENCES customers(customer_id)" +
                                        ");";
                        stmt.execute(createBills);

                        // 4. Bill Items Table
                        String createBillItems = "CREATE TABLE IF NOT EXISTS bill_items (" +
                                        "item_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                        "bill_id INTEGER, " +
                                        "medicine_id INTEGER, " +
                                        "quantity INTEGER, " +
                                        "price REAL, " +
                                        "total REAL, " +
                                        "FOREIGN KEY (bill_id) REFERENCES bills(bill_id), " +
                                        "FOREIGN KEY (medicine_id) REFERENCES medicines(medicine_id)" +
                                        ");";
                        stmt.execute(createBillItems);

                        // 5. Users Table
                        String createUsers = "CREATE TABLE IF NOT EXISTS users (" +
                                        "user_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                        "username TEXT UNIQUE, " +
                                        "password TEXT" +
                                        ");";
                        stmt.execute(createUsers);

                        // Seed Admin User
                        // Check if admin exists
                        var rs = stmt.executeQuery("SELECT count(*) FROM users WHERE username = 'admin'");
                        if (rs.next() && rs.getInt(1) == 0) {
                                stmt.execute("INSERT INTO users (username, password) VALUES ('admin', 'admin123')");
                                System.out.println("✅ Default Admin User Created");
                        }

                        System.out.println("✅ SQLite Database Initialized Successfully");

                } catch (Exception e) {
                        System.err.println("❌ Database Initialization Failed: " + e.getMessage());
                        e.printStackTrace();
                }
        }
}
