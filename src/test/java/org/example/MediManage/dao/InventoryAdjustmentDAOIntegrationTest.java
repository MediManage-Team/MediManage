package org.example.MediManage.dao;

import org.example.MediManage.model.InventoryAdjustment;
import org.example.MediManage.util.DatabaseUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InventoryAdjustmentDAOIntegrationTest {
    private static final String DB_PATH_PROPERTY = "medimanage.db.path";
    private Path testDbPath;
    private Path tempDir;

    @BeforeEach
    void setupDb() throws Exception {
        tempDir = Files.createTempDirectory("medimanage-inventory-adjustment-tests-");
        testDbPath = tempDir.resolve("medimanage-inventory-adjustment.db");
        System.setProperty(DB_PATH_PROPERTY, testDbPath.toString());
        DatabaseUtil.initDB();
    }

    @AfterEach
    void cleanupDb() {
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
        if (tempDir != null) {
            tryDelete(tempDir);
        }
    }

    @Test
    void recordDamageAdjustmentReducesStockAndAppearsInRecentFeed() throws Exception {
        InventoryAdjustmentDAO dao = new InventoryAdjustmentDAO();
        int userId = insertUser("adjust_user_" + System.nanoTime());
        int medicineId = insertMedicine("AdjustMed-" + System.nanoTime(), 20, 8.5, 12.0);
        String expectedUsername = queryString("SELECT username FROM users WHERE user_id = " + userId);

        dao.recordAdjustment(medicineId, "DAMAGED", 4, 8.5, "Breakage", "Dropped carton", userId);

        assertEquals(16, getStock(medicineId));
        assertEquals(1, countRows("inventory_adjustments"));

        List<InventoryAdjustment> recent = dao.getRecentAdjustments(5);
        InventoryAdjustment adjustment = recent.stream()
                .filter(row -> row.getMedicineId() == medicineId)
                .findFirst()
                .orElseThrow();
        assertEquals("DAMAGED", adjustment.getAdjustmentType());
        assertEquals(4, adjustment.getQuantity());
        assertEquals("Breakage", adjustment.getRootCauseTag());
        assertEquals("Dropped carton", adjustment.getNotes());
        assertEquals(expectedUsername, adjustment.getCreatedByUsername());
    }

    @Test
    void insufficientStockAdjustmentRollsBack() throws Exception {
        InventoryAdjustmentDAO dao = new InventoryAdjustmentDAO();
        int medicineId = insertMedicine("LowStockMed-" + System.nanoTime(), 2, 5.0, 9.0);

        assertThrows(Exception.class,
                () -> dao.recordAdjustment(medicineId, "RETURN", 5, 5.0, "Supplier Return", "", null));

        assertEquals(2, getStock(medicineId));
        assertEquals(0, countRows("inventory_adjustments"));
    }

    @Test
    void dumpAdjustmentAliasStoresDamageRecordAndReducesStock() throws Exception {
        InventoryAdjustmentDAO dao = new InventoryAdjustmentDAO();
        int medicineId = insertMedicine("DumpMed-" + System.nanoTime(), 12, 6.5, 11.0);

        dao.recordAdjustment(medicineId, "DUMP", 3, 6.5, "Expired Dump", "Disposed expired batch", null);

        assertEquals(9, getStock(medicineId));
        assertEquals("DAMAGED", queryString("SELECT adjustment_type FROM inventory_adjustments LIMIT 1"));
        assertEquals("Expired Dump", queryString("SELECT root_cause_tag FROM inventory_adjustments LIMIT 1"));

        List<InventoryAdjustment> recent = dao.getRecentAdjustments(5);
        assertFalse(recent.isEmpty());
        assertEquals("DAMAGED", recent.getFirst().getAdjustmentType());
        assertEquals(3, recent.getFirst().getQuantity());
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

    private static int insertMedicine(String name, int stockQuantity, double purchasePrice, double price) throws Exception {
        String medicineSql = """
                INSERT INTO medicines (name, generic_name, company, expiry_date, price, purchase_price, reorder_threshold, active)
                VALUES (?, ?, ?, ?, ?, ?, 10, 1)
                """;
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement medicinePs = conn.prepareStatement(medicineSql, Statement.RETURN_GENERATED_KEYS)) {
            medicinePs.setString(1, name);
            medicinePs.setString(2, name);
            medicinePs.setString(3, "Acme");
            medicinePs.setString(4, "2030-12-31");
            medicinePs.setDouble(5, price);
            medicinePs.setDouble(6, purchasePrice);
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

    private static int countRows(String tableName) throws Exception {
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + tableName);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static String queryString(String sql) throws Exception {
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getString(1);
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
