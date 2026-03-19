package org.example.MediManage.dao;

import org.example.MediManage.model.PurchaseOrder;
import org.example.MediManage.model.PurchaseOrderItem;
import org.example.MediManage.util.DatabaseUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

class PurchaseOrderDAOIntegrationTest {
    private static final String DB_PATH_PROPERTY = "medimanage.db.path";
    private static Path testDbPath;

    @BeforeAll
    static void setupDb() throws Exception {
        Path tempDir = Files.createTempDirectory("medimanage-purchase-order-tests-");
        testDbPath = tempDir.resolve("medimanage-purchase-order.db");
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
    void receivePurchaseOrderCreatesNewMedicineAndStoresBatchMetadata() throws Exception {
        PurchaseOrderDAO dao = new PurchaseOrderDAO();
        int supplierId = insertSupplier("Supplier-New-" + System.nanoTime());

        PurchaseOrder po = new PurchaseOrder();
        po.setSupplierId(supplierId);
        po.setTotalAmount(300.0);
        po.setNotes("INV-NEW-001");

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setMedicineName("NewMed-" + System.nanoTime());
        item.setGenericName("Paracetamol");
        item.setCompany("Acme Labs");
        item.setBatchNumber("LOT-NEW-01");
        item.setPurchaseDate("2026-03-18");
        item.setExpiryDate("2027-03-31");
        item.setOrderedQty(25);
        item.setReceivedQty(25);
        item.setUnitCost(8.50);
        item.setSellingPrice(12.00);
        item.setReorderThreshold(15);

        dao.receivePurchaseOrder(po, List.of(item));

        int medicineId = findMedicineId(item.getMedicineName(), item.getCompany());
        assertFalse(medicineId <= 0);
        assertEquals(25, getStock(medicineId));
        assertEquals(12.00, queryDouble("SELECT price FROM medicines WHERE medicine_id = " + medicineId), 0.0001);
        assertEquals(8.50, queryDouble("SELECT purchase_price FROM medicines WHERE medicine_id = " + medicineId), 0.0001);
        assertEquals("2027-03-31", queryString("SELECT expiry_date FROM medicines WHERE medicine_id = " + medicineId));
        assertEquals(supplierId, queryInt("SELECT supplier_id FROM medicines WHERE medicine_id = " + medicineId));
        assertEquals("LOT-NEW-01", queryString("SELECT batch_number FROM purchase_order_items WHERE po_id = " + po.getPoId()));
        assertEquals("2026-03-18", queryString("SELECT purchase_date FROM purchase_order_items WHERE po_id = " + po.getPoId()));
        assertEquals(12.00, queryDouble("SELECT selling_price FROM purchase_order_items WHERE po_id = " + po.getPoId()), 0.0001);
        assertFalse(queryString("SELECT batch_barcode FROM inventory_batches WHERE medicine_id = " + medicineId + " LIMIT 1").isBlank());
    }

    @Test
    void receivePurchaseOrderForExistingMedicineUpdatesStockAndEarliestExpirySnapshot() throws Exception {
        PurchaseOrderDAO dao = new PurchaseOrderDAO();
        int supplierId = insertSupplier("Supplier-Existing-" + System.nanoTime());
        int medicineId = insertMedicine(
                "ExistingMed-" + System.nanoTime(),
                "Ibuprofen",
                "HealWell",
                "2027-12-31",
                18.0,
                10,
                11.0);

        PurchaseOrder po = new PurchaseOrder();
        po.setSupplierId(supplierId);
        po.setTotalAmount(180.0);
        po.setNotes("INV-EXIST-001");

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setMedicineId(medicineId);
        item.setMedicineName(queryString("SELECT name FROM medicines WHERE medicine_id = " + medicineId));
        item.setGenericName("Ibuprofen");
        item.setCompany("HealWell");
        item.setBatchNumber("LOT-EX-09");
        item.setPurchaseDate("2026-03-18");
        item.setExpiryDate("2026-09-30");
        item.setOrderedQty(15);
        item.setReceivedQty(15);
        item.setUnitCost(9.25);
        item.setSellingPrice(14.50);
        item.setReorderThreshold(12);

        dao.receivePurchaseOrder(po, List.of(item));

        assertEquals(25, getStock(medicineId));
        assertEquals(14.50, queryDouble("SELECT price FROM medicines WHERE medicine_id = " + medicineId), 0.0001);
        assertEquals(9.25, queryDouble("SELECT purchase_price FROM medicines WHERE medicine_id = " + medicineId), 0.0001);
        assertEquals("2026-09-30", queryString("SELECT expiry_date FROM medicines WHERE medicine_id = " + medicineId));
        assertEquals("LOT-EX-09", queryString("SELECT batch_number FROM purchase_order_items WHERE po_id = " + po.getPoId()));
        assertEquals("HealWell", queryString("SELECT company_snapshot FROM purchase_order_items WHERE po_id = " + po.getPoId()));
        assertEquals(12, queryInt("SELECT reorder_threshold FROM purchase_order_items WHERE po_id = " + po.getPoId()));
        assertFalse(queryString("SELECT batch_barcode FROM inventory_batches WHERE medicine_id = " + medicineId + " ORDER BY batch_id DESC LIMIT 1").isBlank());
    }

    private static int insertSupplier(String name) throws Exception {
        String sql = "INSERT INTO suppliers (name, active) VALUES (?, 1)";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Failed to insert supplier");
                }
                return rs.getInt(1);
            }
        }
    }

    private static int insertMedicine(String name, String genericName, String company, String expiryDate,
                                      double sellingPrice, int initialStock, double purchasePrice) throws Exception {
        String medicineSql = """
                INSERT INTO medicines (name, generic_name, company, expiry_date, price, purchase_price, reorder_threshold, active)
                VALUES (?, ?, ?, ?, ?, ?, 10, 1)
                """;
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement medicinePs = conn.prepareStatement(medicineSql, Statement.RETURN_GENERATED_KEYS)) {
            medicinePs.setString(1, name);
            medicinePs.setString(2, genericName);
            medicinePs.setString(3, company);
            medicinePs.setString(4, expiryDate);
            medicinePs.setDouble(5, sellingPrice);
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
                    stockPs.setInt(2, initialStock);
                    stockPs.executeUpdate();
                }
                return medicineId;
            }
        }
    }

    private static int findMedicineId(String name, String company) throws Exception {
        String sql = "SELECT medicine_id FROM medicines WHERE name = ? AND company = ? LIMIT 1";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, company);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static int getStock(int medicineId) throws Exception {
        return queryInt("SELECT quantity FROM stock WHERE medicine_id = " + medicineId);
    }

    private static int queryInt(String sql) throws Exception {
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static double queryDouble(String sql) throws Exception {
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getDouble(1);
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
