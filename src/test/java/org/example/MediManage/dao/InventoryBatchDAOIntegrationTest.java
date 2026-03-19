package org.example.MediManage.dao;

import org.example.MediManage.model.InventoryBatch;
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
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryBatchDAOIntegrationTest {
    private static final String DB_PATH_PROPERTY = "medimanage.db.path";
    private Path tempDir;
    private Path testDbPath;

    @BeforeEach
    void setupDb() throws Exception {
        tempDir = Files.createTempDirectory("medimanage-inventory-batch-tests-");
        testDbPath = tempDir.resolve("medimanage-inventory-batch.db");
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
    void availableBatchesExposeLinearExpirySequenceAndBatchBarcode() throws Exception {
        InventoryBatchDAO dao = new InventoryBatchDAO();
        int medicineId = insertMedicine(
                "BatchSeq-" + System.nanoTime(),
                "Cefixime",
                "Acme Labs",
                LocalDate.now().plusDays(120).toString(),
                12.0,
                7.25,
                "MED-" + System.nanoTime());

        try (Connection conn = DatabaseUtil.getConnection()) {
            dao.recordPurchaseBatch(
                    conn,
                    medicineId,
                    null,
                    "LOT-LATE",
                    LocalDate.now().plusDays(60).toString(),
                    LocalDate.now().toString(),
                    7.25,
                    12.0,
                    8,
                    null);
            dao.recordPurchaseBatch(
                    conn,
                    medicineId,
                    null,
                    "LOT-EARLY",
                    LocalDate.now().plusDays(15).toString(),
                    LocalDate.now().minusDays(1).toString(),
                    7.10,
                    11.75,
                    5,
                    null);
        }

        List<InventoryBatch> rows = dao.getAvailableBatchesForMedicine(medicineId);
        assertEquals(2, rows.size());
        assertEquals(13, queryInt("SELECT quantity FROM stock WHERE medicine_id = " + medicineId));

        InventoryBatch first = rows.get(0);
        InventoryBatch second = rows.get(1);

        assertEquals("LOT-EARLY", first.batchNumber());
        assertEquals(1, first.expirySequence());
        assertFalse(first.batchBarcode().isBlank());
        assertNotNull(first.daysToExpiry());

        assertEquals("LOT-LATE", second.batchNumber());
        assertEquals(2, second.expirySequence());
        assertFalse(second.batchBarcode().isBlank());
        assertNotNull(second.daysToExpiry());
    }

    @Test
    void managementOverviewSummarizesStockExposureAndDumpedUnits() throws Exception {
        InventoryBatchDAO batchDAO = new InventoryBatchDAO();
        InventoryAdjustmentDAO adjustmentDAO = new InventoryAdjustmentDAO();
        String medicineBarcode = "MED-" + System.nanoTime();
        LocalDate expiryDate = LocalDate.now().plusDays(5);
        int medicineId = insertMedicine(
                "Mgmt-" + System.nanoTime(),
                "Azithromycin",
                "Zen Pharma",
                expiryDate.plusDays(20).toString(),
                10.0,
                6.0,
                medicineBarcode);

        try (Connection conn = DatabaseUtil.getConnection()) {
            batchDAO.recordPurchaseBatch(
                    conn,
                    medicineId,
                    null,
                    "LOT-MGMT",
                    expiryDate.toString(),
                    LocalDate.now().toString(),
                    6.0,
                    10.0,
                    20,
                    null);
        }

        adjustmentDAO.recordAdjustment(medicineId, "DUMP", 4, 6.0, "Expired Dump", "Disposed expired stock", null);

        InventoryBatchDAO.MedicineManagementOverviewRow row = batchDAO.getManagementOverview(20).stream()
                .filter(candidate -> candidate.medicineId() == medicineId)
                .findFirst()
                .orElseThrow();

        assertEquals(medicineBarcode, row.medicineBarcode());
        assertEquals(16, row.currentStock());
        assertEquals(16, row.trackedBatchUnits());
        assertEquals(0, row.stockGapUnits());
        assertEquals(1, row.activeBatchCount());
        assertEquals(expiryDate.toString(), row.earliestBatchExpiry());
        assertEquals(0, row.expiredUnits());
        assertEquals(16, row.expiring30dUnits());
        assertEquals(96.0, row.expiryExposureCost(), 0.0001);
        assertEquals(4, row.dumpedUnits());
        assertTrue(queryString("SELECT adjustment_type FROM inventory_adjustments LIMIT 1").equals("DAMAGED"));
    }

    private static int insertMedicine(
            String name,
            String genericName,
            String company,
            String expiryDate,
            double sellingPrice,
            double purchasePrice,
            String barcode) throws Exception {
        String medicineSql = """
                INSERT INTO medicines (name, generic_name, company, expiry_date, price, purchase_price, reorder_threshold, barcode, active)
                VALUES (?, ?, ?, ?, ?, ?, 10, ?, 1)
                """;
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(medicineSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, genericName);
            ps.setString(3, company);
            ps.setString(4, expiryDate);
            ps.setDouble(5, sellingPrice);
            ps.setDouble(6, purchasePrice);
            ps.setString(7, barcode);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Failed to insert medicine");
                }
                return rs.getInt(1);
            }
        }
    }

    private static int queryInt(String sql) throws Exception {
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
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
