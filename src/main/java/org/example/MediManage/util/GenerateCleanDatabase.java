package org.example.MediManage.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class GenerateCleanDatabase {
    public static void main(String[] args) {
        String dbPath = "base_medimanage.db";
        String jdbcUrl = "jdbc:sqlite:" + dbPath;
        
        System.out.println("Generating clean database at: " + dbPath);
        
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement()) {
             
            // Enable foreign keys
            stmt.execute("PRAGMA foreign_keys = ON;");
            
            // Re-run the core statements from DatabaseUtil.initDB() manually
            // to ensure no sample data gets triggered if we just called initDB.
            
            // 1. Users
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "username TEXT NOT NULL UNIQUE," +
                    "password TEXT NOT NULL," +
                    "role TEXT NOT NULL," +
                    "active INTEGER DEFAULT 1)");
                    
            // Insert default admin
            stmt.execute("INSERT OR IGNORE INTO users (username, password, role) VALUES ('admin', 'admin', 'ADMIN')");

            // 2. Customers
            stmt.execute("CREATE TABLE IF NOT EXISTS customers (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT NOT NULL," +
                    "phone TEXT NOT NULL UNIQUE," +
                    "email TEXT," +
                    "address TEXT," +
                    "loyalty_points INTEGER DEFAULT 0," +
                    "total_visits INTEGER DEFAULT 0," +
                    "total_spent REAL DEFAULT 0.0)");

            // 3. Medicines
            stmt.execute("CREATE TABLE IF NOT EXISTS medicines (" +
                    "medicine_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT NOT NULL," +
                    "generic_name TEXT," +
                    "company TEXT NOT NULL," +
                    "expiry_date TEXT NOT NULL," +
                    "price REAL NOT NULL," +
                    "active INTEGER DEFAULT 1)");
                    
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_stock_medicine ON medicines(name, company, expiry_date)");

            // 4. Stock
            stmt.execute("CREATE TABLE IF NOT EXISTS stock (" +
                    "medicine_id INTEGER PRIMARY KEY," +
                    "quantity INTEGER NOT NULL," +
                    "FOREIGN KEY(medicine_id) REFERENCES medicines(medicine_id))");

            // 5. Suppliers
            stmt.execute("CREATE TABLE IF NOT EXISTS suppliers (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT NOT NULL," +
                    "contact_person TEXT," +
                    "phone TEXT," +
                    "email TEXT," +
                    "address TEXT)");

            // 6. Bills
            stmt.execute("CREATE TABLE IF NOT EXISTS bills (" +
                    "bill_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "customer_id INTEGER," +
                    "user_id INTEGER NOT NULL," +
                    "total_amount REAL NOT NULL," +
                    "bill_date TEXT NOT NULL," +
                    "payment_mode TEXT NOT NULL," +
                    "loyalty_points_earned INTEGER DEFAULT 0," +
                    "loyalty_points_redeemed INTEGER DEFAULT 0," +
                    "FOREIGN KEY(customer_id) REFERENCES customers(id)," +
                    "FOREIGN KEY(user_id) REFERENCES users(id))");

            // 7. Bill Items
            stmt.execute("CREATE TABLE IF NOT EXISTS bill_items (" +
                    "bill_item_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "bill_id INTEGER NOT NULL," +
                    "medicine_id INTEGER NOT NULL," +
                    "quantity INTEGER NOT NULL," +
                    "price REAL NOT NULL," +
                    "total REAL NOT NULL," +
                    "FOREIGN KEY(bill_id) REFERENCES bills(bill_id)," +
                    "FOREIGN KEY(medicine_id) REFERENCES medicines(medicine_id))");

            // 8. Orders (for Restocking)
            stmt.execute("CREATE TABLE IF NOT EXISTS orders (" +
                    "order_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "supplier_id INTEGER NOT NULL," +
                    "order_date TEXT NOT NULL," +
                    "status TEXT NOT NULL," +
                    "total_amount REAL NOT NULL," +
                    "FOREIGN KEY(supplier_id) REFERENCES suppliers(id))");

            // 9. Order Items
            stmt.execute("CREATE TABLE IF NOT EXISTS order_items (" +
                    "order_item_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "order_id INTEGER NOT NULL," +
                    "medicine_id INTEGER NOT NULL," +
                    "quantity INTEGER NOT NULL," +
                    "price REAL NOT NULL," +
                    "FOREIGN KEY(order_id) REFERENCES orders(order_id)," +
                    "FOREIGN KEY(medicine_id) REFERENCES medicines(medicine_id))");

            // 10. Held Orders
            stmt.execute("CREATE TABLE IF NOT EXISTS held_orders (" +
                    "held_order_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "customer_name TEXT," +
                    "customer_phone TEXT," +
                    "held_at TEXT NOT NULL)");

            // 11. Held Order Items
            stmt.execute("CREATE TABLE IF NOT EXISTS held_order_items (" +
                    "item_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "held_order_id INTEGER NOT NULL," +
                    "medicine_id INTEGER NOT NULL," +
                    "quantity INTEGER NOT NULL," +
                    "price REAL NOT NULL," +
                    "FOREIGN KEY(held_order_id) REFERENCES held_orders(held_order_id))");

            // 12. Expenses
            stmt.execute("CREATE TABLE IF NOT EXISTS expenses (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "category TEXT NOT NULL," +
                    "amount REAL NOT NULL," +
                    "date TEXT NOT NULL," +
                    "description TEXT)");

            // 13. Inventory Adjustments
            stmt.execute("CREATE TABLE IF NOT EXISTS inventory_adjustments (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "medicine_id INTEGER NOT NULL," +
                    "adjustment_type TEXT NOT NULL," +
                    "quantity INTEGER NOT NULL," +
                    "unit_price REAL NOT NULL," +
                    "root_cause_tag TEXT," +
                    "occurred_at TEXT NOT NULL," +
                    "created_by_user_id INTEGER," +
                    "FOREIGN KEY(medicine_id) REFERENCES medicines(medicine_id)," +
                    "FOREIGN KEY(created_by_user_id) REFERENCES users(id))");

            // 14. Message Templates
            stmt.execute("CREATE TABLE IF NOT EXISTS message_templates (" +
                    "key_name TEXT PRIMARY KEY," +
                    "template_text TEXT NOT NULL)");

            // Seed Default Templates
            stmt.execute("INSERT OR REPLACE INTO message_templates (key_name, template_text) VALUES " +
                    "('default_invoice', 'Thank you for your purchase at MediManage! Your invoice total is $%.2f.'), " +
                    "('default_restock', 'Reminder: Time to restock your commonly used medicines.'), " +
                    "('default_promotion', 'Exclusive offer for our valued customers! Get 10% off your next purchase.')");

            System.out.println("Clean database initialization complete!");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
