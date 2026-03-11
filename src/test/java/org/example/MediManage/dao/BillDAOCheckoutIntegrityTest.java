package org.example.MediManage.dao;

import org.example.MediManage.model.BillItem;
import org.example.MediManage.model.PaymentSplit;
import org.example.MediManage.util.DatabaseUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BillDAOCheckoutIntegrityTest {
    private static final String DB_PATH_PROPERTY = "medimanage.db.path";
    private static Path testDbPath;

    @BeforeAll
    static void setupDb() throws Exception {
        Path tempDir = Files.createTempDirectory("medimanage-billdao-tests-");
        testDbPath = tempDir.resolve("medimanage-billdao.db");
        System.setProperty(DB_PATH_PROPERTY, testDbPath.toString());
        DatabaseUtil.initDB();
    }

    @AfterAll
    static void cleanupDb() {
        System.clearProperty(DB_PATH_PROPERTY);
        if (testDbPath == null) {
            return;
        }
        String baseName = testDbPath.getFileName().toString();
        tryDelete(testDbPath.resolveSibling(baseName + "-shm"));
        tryDelete(testDbPath.resolveSibling(baseName + "-wal"));
        tryDelete(testDbPath);
        Path parent = testDbPath.getParent();
        if (parent != null) {
            tryDelete(parent);
        }
    }

    @Test
    void generateInvoiceRejectsInsufficientStockAndRollsBack() throws Exception {
        BillDAO dao = new BillDAO();
        int userId = insertUser("stock_guard_" + System.nanoTime());
        int medicineId = insertMedicine("GuardMed-" + System.nanoTime(), 1);
        List<BillItem> items = List.of(new BillItem(medicineId, "GuardMed", "2030-12-31", 2, 100.0, 36.0));
        int baselineBills = countRows("bills");
        int baselineBillItems = countRows("bill_items");

        assertThrows(SQLException.class, () ->
                dao.generateInvoice(236.0, items, null, userId, List.of(), "CASH", 0, 0));

        assertEquals(baselineBills, countRows("bills"));
        assertEquals(baselineBillItems, countRows("bill_items"));
        assertEquals(1, getStock(medicineId));
    }

    @Test
    void generateInvoiceRejectsUnavailableLoyaltyRedemptionAndRollsBack() throws Exception {
        BillDAO dao = new BillDAO();
        int userId = insertUser("loyalty_guard_" + System.nanoTime());
        int customerId = insertCustomer("Loyalty Guard", 50);
        int medicineId = insertMedicine("LoyaltyMed-" + System.nanoTime(), 5);
        List<BillItem> items = List.of(new BillItem(medicineId, "LoyaltyMed", "2030-12-31", 1, 100.0, 18.0));
        int baselineBills = countRows("bills");
        int baselineBillItems = countRows("bill_items");

        assertThrows(SQLException.class, () ->
                dao.generateInvoice(118.0, items, customerId, userId, List.of(), "CASH", 100, 1));

        assertEquals(baselineBills, countRows("bills"));
        assertEquals(baselineBillItems, countRows("bill_items"));
        assertEquals(5, getStock(medicineId));
        assertEquals(50, getCustomerPoints(customerId));
    }

    @Test
    void generateInvoiceStoresSplitPaymentsAndCreditsOnlyCreditPortion() throws Exception {
        BillDAO dao = new BillDAO();
        int userId = insertUser("split_credit_" + System.nanoTime());
        int customerId = insertCustomer("Split Credit", 0);
        int medicineId = insertMedicine("SplitMed-" + System.nanoTime(), 5);
        List<BillItem> items = List.of(new BillItem(medicineId, "SplitMed", "2030-12-31", 1, 100.0, 18.0));
        List<PaymentSplit> splits = List.of(
                new PaymentSplit("Cash", 50.0),
                new PaymentSplit("Credit", 68.0, "ledger-001"));
        int baselineBills = countRows("bills");
        int baselineBillItems = countRows("bill_items");
        int baselineSplits = countRows("payment_splits");

        int billId = dao.generateInvoice(118.0, items, customerId, userId, splits, "CASH+CREDIT", 0, 0);

        assertEquals(baselineBills + 1, countRows("bills"));
        assertEquals(baselineBillItems + 1, countRows("bill_items"));
        assertEquals(baselineSplits + 2, countRows("payment_splits"));
        assertEquals(4, getStock(medicineId));
        assertEquals(68.0, getCustomerBalance(customerId), 0.0001);
        assertEquals(2, countPaymentSplitsForBill(billId));
    }

    private static int insertUser(String username) throws Exception {
        String sql = "INSERT INTO users (username, password, role) VALUES (?, ?, 'ADMIN')";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, "$2a$10$abcdefghijklmnopqrstuuGJQZ0fD7FQYJYqj5H4Qx7lW7N6m");
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Failed to insert user");
                }
                return rs.getInt(1);
            }
        }
    }

    private static int insertCustomer(String name, int loyaltyPoints) throws Exception {
        String sql = "INSERT INTO customers (name, phone, loyalty_points) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, "9999999999");
            ps.setInt(3, loyaltyPoints);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Failed to insert customer");
                }
                return rs.getInt(1);
            }
        }
    }

    private static int insertMedicine(String name, int stockQuantity) throws Exception {
        String medicineSql = "INSERT INTO medicines (name, generic_name, company, expiry_date, price) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement medicinePs = conn.prepareStatement(medicineSql, Statement.RETURN_GENERATED_KEYS)) {
            medicinePs.setString(1, name);
            medicinePs.setString(2, name);
            medicinePs.setString(3, "Acme");
            medicinePs.setString(4, "2030-12-31");
            medicinePs.setDouble(5, 100.0);
            medicinePs.executeUpdate();

            try (ResultSet rs = medicinePs.getGeneratedKeys()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Failed to insert medicine");
                }
                int medicineId = rs.getInt(1);
                try (PreparedStatement stockPs = conn.prepareStatement(
                        "INSERT INTO stock (medicine_id, quantity) VALUES (?, ?)")) {
                    stockPs.setInt(1, medicineId);
                    stockPs.setInt(2, stockQuantity);
                    stockPs.executeUpdate();
                }
                return medicineId;
            }
        }
    }

    private static int countRows(String tableName) throws Exception {
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + tableName);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static int getStock(int medicineId) throws Exception {
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT quantity FROM stock WHERE medicine_id = ?")) {
            ps.setInt(1, medicineId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static int getCustomerPoints(int customerId) throws Exception {
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT loyalty_points FROM customers WHERE customer_id = ?")) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static double getCustomerBalance(int customerId) throws Exception {
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT current_balance FROM customers WHERE customer_id = ?")) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getDouble(1);
            }
        }
    }

    private static int countPaymentSplitsForBill(int billId) throws Exception {
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM payment_splits WHERE bill_id = ?")) {
            ps.setInt(1, billId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static void tryDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // Best-effort cleanup.
        }
    }
}
