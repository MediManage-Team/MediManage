package org.example.MediManage.util;

import org.example.MediManage.service.AdminBootstrapService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseInitializationRegressionTest {
    private static final String DB_PATH_PROPERTY = "medimanage.db.path";

    private static final String LEGACY_CORE_SCHEMA = """
            CREATE TABLE users (
                user_id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT UNIQUE NOT NULL,
                password TEXT NOT NULL,
                role TEXT DEFAULT 'STAFF'
            );
            CREATE TABLE medicines (
                medicine_id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                company TEXT,
                price REAL NOT NULL,
                expiry_date TEXT
            );
            CREATE TABLE stock (
                stock_id INTEGER PRIMARY KEY AUTOINCREMENT,
                medicine_id INTEGER,
                quantity INTEGER NOT NULL
            );
            CREATE TABLE customers (
                customer_id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL
            );
            CREATE TABLE bills (
                bill_id INTEGER PRIMARY KEY AUTOINCREMENT,
                customer_id INTEGER,
                user_id INTEGER,
                total_amount REAL,
                bill_date TEXT DEFAULT CURRENT_TIMESTAMP
            );
            CREATE TABLE bill_items (
                item_id INTEGER PRIMARY KEY AUTOINCREMENT,
                bill_id INTEGER,
                medicine_id INTEGER,
                quantity INTEGER,
                price REAL,
                total REAL
            );
            CREATE TABLE expenses (
                expense_id INTEGER PRIMARY KEY AUTOINCREMENT,
                category TEXT NOT NULL,
                amount REAL NOT NULL,
                date TEXT NOT NULL,
                description TEXT
            );
            """;

    private Path tempDir;
    private Path dbPath;

    @AfterEach
    void tearDown() throws Exception {
        System.clearProperty(DB_PATH_PROPERTY);
        if (dbPath != null) {
            tryDelete(dbPath.resolveSibling(dbPath.getFileName() + "-wal"));
            tryDelete(dbPath.resolveSibling(dbPath.getFileName() + "-shm"));
            tryDelete(dbPath);
        }
        if (tempDir != null) {
            tryDelete(tempDir);
        }
    }

    @Test
    void upgradesLegacyCoreSchemaToCurrentSchema() throws Exception {
        useFreshDatabase();
        executeSql(LEGACY_CORE_SCHEMA);

        DatabaseUtil.initDB();

        assertTrue(tableExists("suppliers"));
        assertTrue(tableExists("message_templates"));
        assertTrue(tableExists("receipt_settings"));
        assertTrue(tableExists("analytics_report_dispatch_schedules"));
        assertTrue(tableExists("bill_item_prescription_directions"));
        assertFalse(tableExists("prescriptions"));
        assertFalse(tableExists("locations"));
        assertTrue(columnExists("medicines", "generic_name"));
        assertTrue(columnExists("bills", "payment_mode"));
        assertTrue(columnExists("bills", "prescription_highlights"));
        assertFalse(columnExists("bills", "location_id"));
        assertTrue(columnExists("bill_items", "price_override"));
        assertTrue(columnExists("receipt_settings", "invoice_template_path"));
        assertTrue(columnExists("receipt_settings", "receipt_template_path"));
    }

    @Test
    void schemaInitializationIsIdempotentAcrossRepeatRuns() throws Exception {
        useFreshDatabase();
        executeSql(LEGACY_CORE_SCHEMA);

        DatabaseUtil.initDB();
        DatabaseUtil.initDB();

        assertEquals(3, countRows("message_templates"));
        assertTrue(tableExists("subscription_plans"));
        assertTrue(tableExists("subscription_enrollments"));
        assertTrue(columnExists("stock", "quantity"));
    }

    @Test
    void upgradedLegacyBillsSchemaKeepsInvoiceInsertsWorking() throws Exception {
        useFreshDatabase();
        executeSql(LEGACY_CORE_SCHEMA);
        executeSql("ALTER TABLE bills ADD COLUMN location_id INTEGER");

        DatabaseUtil.initDB();

        assertTrue(tableExists("subscription_enrollments"));
        assertFalse(columnExists("bills", "location_id"));

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO bills (total_amount, bill_date, payment_mode) VALUES (?, ?, ?)")) {
            stmt.setDouble(1, 118.0);
            stmt.setString(2, "2026-03-20 19:45:00");
            stmt.setString(3, "CASH");
            assertEquals(1, stmt.executeUpdate());
        }

        assertEquals(1, countRows("bills"));
    }

    @Test
    void freshInstallDoesNotSeedDemoUsersOrBusinessData() throws Exception {
        useFreshDatabase();

        DatabaseUtil.initDB();

        assertEquals(0, countRows("users"));
        assertEquals(0, countRows("suppliers"));
        assertTrue(DatabaseUtil.requiresAdminBootstrap());
    }

    @Test
    void removesLegacyDemoAdminOnStartup() throws Exception {
        useFreshDatabase();
        executeSql(LEGACY_CORE_SCHEMA);
        executeSql("INSERT INTO users (username, password, role) VALUES ('1', '1', 'ADMIN');");

        DatabaseUtil.initDB();

        assertFalse(DatabaseUtil.hasLegacyDemoAdminCredential());
        assertTrue(DatabaseUtil.requiresAdminBootstrap());
    }

    @Test
    void autoSeedsDefaultAdminCredentialsWithHashedPassword() throws Exception {
        useFreshDatabase();
        DatabaseUtil.initDB();
        AdminBootstrapService bootstrapService = new AdminBootstrapService();

        assertTrue(bootstrapService.createDefaultAdminIfMissing());
        assertFalse(bootstrapService.createDefaultAdminIfMissing());

        assertEquals(1, countRows("users"));
        assertEquals(AdminBootstrapService.DEFAULT_USERNAME,
                querySingleString("SELECT username FROM users WHERE UPPER(role) = 'ADMIN'"));
        String storedPassword = querySingleString("SELECT password FROM users WHERE username = 'admin'");
        assertNotEquals(AdminBootstrapService.DEFAULT_PASSWORD, storedPassword);
        assertFalse(storedPassword.isBlank());
        assertFalse(DatabaseUtil.requiresAdminBootstrap());
    }

    @Test
    void bootstrapAdminRequiresNonTrivialPasswordAndStoresHash() throws Exception {
        useFreshDatabase();
        DatabaseUtil.initDB();
        AdminBootstrapService bootstrapService = new AdminBootstrapService();

        assertThrows(IllegalArgumentException.class,
                () -> bootstrapService.createInitialAdmin("owner", "short1"));

        bootstrapService.createInitialAdmin("owner", "StrongPass123");

        assertEquals(1, countRows("users"));
        String storedPassword = querySingleString("SELECT password FROM users WHERE username = 'owner'");
        assertNotEquals("StrongPass123", storedPassword);
        assertFalse(storedPassword.isBlank());
        assertFalse(DatabaseUtil.requiresAdminBootstrap());
    }

    private void useFreshDatabase() throws Exception {
        tempDir = Files.createTempDirectory("medimanage-db-regression-");
        dbPath = tempDir.resolve("medimanage.db");
        System.setProperty(DB_PATH_PROPERTY, dbPath.toString());
    }

    private void executeSql(String sql) throws Exception {
        try (Connection conn = DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String statement : sql.split(";")) {
                String normalized = statement.trim();
                if (!normalized.isEmpty()) {
                    stmt.execute(normalized);
                }
            }
        }
    }

    private boolean tableExists(String tableName) throws Exception {
        String sql = "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tableName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private boolean columnExists(String tableName, String columnName) throws Exception {
        try (Connection conn = DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private int countRows(String tableName) throws Exception {
        try (Connection conn = DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private String querySingleString(String sql) throws Exception {
        try (Connection conn = DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : "";
        }
    }

    private void tryDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }
}
