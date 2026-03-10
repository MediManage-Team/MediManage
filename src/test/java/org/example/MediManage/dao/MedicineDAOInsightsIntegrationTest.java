package org.example.MediManage.dao;

import org.example.MediManage.util.DatabaseUtil;
import org.example.MediManage.model.Medicine;
import org.example.MediManage.util.ReportingWindowUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MedicineDAOInsightsIntegrationTest {
    private static final String DB_PATH_PROPERTY = "medimanage.db.path";
    private static final DateTimeFormatter DB_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static Path testDbPath;

    @BeforeAll
    static void setupDb() throws Exception {
        Path tempDir = Files.createTempDirectory("medimanage-medicine-insights-tests-");
        testDbPath = tempDir.resolve("medimanage-medicine-insights.db");
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
    void outOfStockInsightsReturnSkuCountDaysAndRevenueImpactEstimate() throws Exception {
        MedicineDAO dao = new MedicineDAO();
        int userId = insertUser("med_analytics_" + System.nanoTime(), "ADMIN");
        int medicineA = insertMedicine("OutA-" + System.nanoTime(), "CoA", "2027-12-31", 50.0, 0);
        int medicineB = insertMedicine("OutB-" + System.nanoTime(), "CoB", "2027-12-31", 40.0, -2);
        insertMedicine("InStock-" + System.nanoTime(), "CoC", "2027-12-31", 30.0, 5);

        LocalDate today = LocalDate.now();
        insertSale(userId, medicineA, today.minusDays(6).atTime(10, 0, 0), 2, 50.0, 100.0);
        insertSale(userId, medicineA, today.minusDays(2).atTime(12, 0, 0), 3, 25.0, 75.0);

        List<MedicineDAO.OutOfStockInsightRow> rows = dao.getOutOfStockInsights(30, 50);
        assertEquals(2, rows.size());

        MedicineDAO.OutOfStockInsightRow rowA = rows.stream()
                .filter(row -> row.medicineId() == medicineA)
                .findFirst()
                .orElseThrow();
        MedicineDAO.OutOfStockInsightRow rowB = rows.stream()
                .filter(row -> row.medicineId() == medicineB)
                .findFirst()
                .orElseThrow();

        assertEquals(2L, rowA.daysOutOfStock());
        assertEquals(175.0, rowA.lookbackRevenue(), 0.0001);
        assertEquals(5.83, rowA.averageDailyRevenue(), 0.0001);
        assertEquals(11.66, rowA.estimatedRevenueImpact(), 0.0001);
        assertNotNull(rowA.lastSaleAt());
        assertTrue(rowA.lastSaleAt().startsWith(today.minusDays(2).toString()));

        assertEquals(30L, rowB.daysOutOfStock());
        assertEquals(0.0, rowB.lookbackRevenue(), 0.0001);
        assertEquals(0.0, rowB.averageDailyRevenue(), 0.0001);
        assertEquals(0.0, rowB.estimatedRevenueImpact(), 0.0001);
        assertEquals(medicineA, rows.get(0).medicineId());
    }

    @Test
    void nearStockOutInsightsUseAverageConsumptionAndReorderThreshold() throws Exception {
        MedicineDAO dao = new MedicineDAO();
        int userId = insertUser("med_near_stock_" + System.nanoTime(), "ADMIN");
        int nearA = insertMedicine("NearA-" + System.nanoTime(), "CoA", "2027-12-31", 12.0, 5);
        int nearB = insertMedicine("NearB-" + System.nanoTime(), "CoB", "2027-12-31", 15.0, 2);
        insertMedicine("SafeStock-" + System.nanoTime(), "CoC", "2027-12-31", 10.0, 12);

        LocalDate today = LocalDate.now();
        // NearA: 20 units sold in 30 days -> avg 0.67/day, threshold ceil(0.67*7)=5 =>
        // near stock-out.
        insertSale(userId, nearA, today.minusDays(20).atTime(9, 0, 0), 10, 12.0, 120.0);
        insertSale(userId, nearA, today.minusDays(4).atTime(11, 0, 0), 10, 12.0, 120.0);
        // NearB: 10 units sold in 30 days -> avg 0.33/day, threshold ceil(0.33*7)=3,
        // stock 2 => near stock-out.
        insertSale(userId, nearB, today.minusDays(10).atTime(10, 0, 0), 10, 15.0, 150.0);

        List<MedicineDAO.NearStockOutInsightRow> rows = dao.getNearStockOutInsights(30, 7, 50);
        assertEquals(2, rows.size());

        MedicineDAO.NearStockOutInsightRow rowA = rows.stream()
                .filter(row -> row.medicineId() == nearA)
                .findFirst()
                .orElseThrow();
        MedicineDAO.NearStockOutInsightRow rowB = rows.stream()
                .filter(row -> row.medicineId() == nearB)
                .findFirst()
                .orElseThrow();

        assertEquals(5, rowA.currentStock());
        assertEquals(20.0, rowA.lookbackUnitsSold(), 0.0001);
        assertEquals(240.0, rowA.lookbackRevenue(), 0.0001);
        assertEquals(0.67, rowA.averageDailyConsumption(), 0.0001);
        assertEquals(5, rowA.reorderThresholdQty());
        assertEquals(7.5, rowA.daysToStockOut(), 0.0001);
        assertEquals(0.0, rowA.estimatedRevenueAtRisk(), 0.0001);

        assertEquals(2, rowB.currentStock());
        assertEquals(10.0, rowB.lookbackUnitsSold(), 0.0001);
        assertEquals(150.0, rowB.lookbackRevenue(), 0.0001);
        assertEquals(0.33, rowB.averageDailyConsumption(), 0.0001);
        assertEquals(3, rowB.reorderThresholdQty());
        assertEquals(6.0, rowB.daysToStockOut(), 0.0001);
        assertEquals(15.0, rowB.estimatedRevenueAtRisk(), 0.0001);
    }

    @Test
    void deadStockInsightsFlagInventoryWithNoMovementBeyondThreshold() throws Exception {
        MedicineDAO dao = new MedicineDAO();
        int userId = insertUser("med_dead_stock_" + System.nanoTime(), "ADMIN");
        int deadA = insertMedicine("DeadA-" + System.nanoTime(), "CoA", "2027-12-31", 11.0, 20);
        int deadB = insertMedicine("DeadB-" + System.nanoTime(), "CoB", "2027-12-31", 25.0, 5);
        int movingC = insertMedicine("MovingC-" + System.nanoTime(), "CoC", "2027-12-31", 9.0, 18);

        LocalDate today = LocalDate.now();
        insertSale(userId, deadA, today.minusDays(90).atTime(9, 30, 0), 5, 11.0, 55.0);
        insertSale(userId, movingC, today.minusDays(8).atTime(12, 15, 0), 4, 9.0, 36.0);

        List<MedicineDAO.DeadStockInsightRow> rows = dao.getDeadStockInsights(60, 50);
        assertTrue(rows.size() >= 2);

        MedicineDAO.DeadStockInsightRow rowA = rows.stream()
                .filter(row -> row.medicineId() == deadA)
                .findFirst()
                .orElseThrow();
        MedicineDAO.DeadStockInsightRow rowB = rows.stream()
                .filter(row -> row.medicineId() == deadB)
                .findFirst()
                .orElseThrow();

        assertTrue(rowA.daysSinceLastMovement() >= 90);
        assertEquals(20, rowA.currentStock());
        assertEquals(11.0, rowA.unitPrice(), 0.0001);
        assertEquals(220.0, rowA.deadStockValue(), 0.0001);
        assertNotNull(rowA.lastSaleAt());

        assertEquals(60L, rowB.daysSinceLastMovement());
        assertEquals(5, rowB.currentStock());
        assertEquals(25.0, rowB.unitPrice(), 0.0001);
        assertEquals(125.0, rowB.deadStockValue(), 0.0001);
        assertTrue(rows.stream().noneMatch(row -> row.medicineId() == movingC));
    }

    @Test
    void fastMovingInsightsReturnTopSkusByUnitsAndRevenueWithinLookback() throws Exception {
        MedicineDAO dao = new MedicineDAO();
        int userId = insertUser("med_fast_moving_" + System.nanoTime(), "ADMIN");
        int fastA = insertMedicine("FastA-" + System.nanoTime(), "CoA", "2027-12-31", 20.0, 60);
        int fastB = insertMedicine("FastB-" + System.nanoTime(), "CoB", "2027-12-31", 30.0, 50);
        int fastC = insertMedicine("FastC-" + System.nanoTime(), "CoC", "2027-12-31", 50.0, 40);
        int noSales = insertMedicine("NoSales-" + System.nanoTime(), "CoD", "2027-12-31", 9.0, 25);

        LocalDate today = LocalDate.now();
        insertSale(userId, fastA, today.minusDays(7).atTime(10, 0, 0), 25, 20.0, 500.0);
        insertSale(userId, fastA, today.minusDays(2).atTime(14, 30, 0), 20, 20.0, 400.0);
        insertSale(userId, fastB, today.minusDays(5).atTime(11, 15, 0), 30, 30.0, 900.0);
        insertSale(userId, fastB, today.minusDays(40).atTime(11, 15, 0), 100, 30.0, 3000.0);
        insertSale(userId, fastC, today.minusDays(3).atTime(16, 45, 0), 15, 50.0, 750.0);

        List<MedicineDAO.FastMovingInsightRow> rows = dao.getFastMovingInsights(30, 50);
        assertTrue(rows.size() >= 3);

        MedicineDAO.FastMovingInsightRow rowA = rows.stream()
                .filter(row -> row.medicineId() == fastA)
                .findFirst()
                .orElseThrow();
        MedicineDAO.FastMovingInsightRow rowB = rows.stream()
                .filter(row -> row.medicineId() == fastB)
                .findFirst()
                .orElseThrow();
        MedicineDAO.FastMovingInsightRow rowC = rows.stream()
                .filter(row -> row.medicineId() == fastC)
                .findFirst()
                .orElseThrow();

        assertEquals(45.0, rowA.lookbackUnitsSold(), 0.0001);
        assertEquals(900.0, rowA.lookbackRevenue(), 0.0001);
        assertEquals(1.5, rowA.averageDailyUnits(), 0.0001);
        assertEquals(30.0, rowA.averageDailyRevenue(), 0.0001);
        assertNotNull(rowA.lastSaleAt());
        assertTrue(rowA.lastSaleAt().startsWith(today.minusDays(2).toString()));

        assertEquals(30.0, rowB.lookbackUnitsSold(), 0.0001);
        assertEquals(900.0, rowB.lookbackRevenue(), 0.0001);
        assertEquals(1.0, rowB.averageDailyUnits(), 0.0001);
        assertEquals(30.0, rowB.averageDailyRevenue(), 0.0001);
        assertNotNull(rowB.lastSaleAt());
        assertTrue(rowB.lastSaleAt().startsWith(today.minusDays(5).toString()));

        assertEquals(15.0, rowC.lookbackUnitsSold(), 0.0001);
        assertEquals(750.0, rowC.lookbackRevenue(), 0.0001);
        assertEquals(0.5, rowC.averageDailyUnits(), 0.0001);
        assertEquals(25.0, rowC.averageDailyRevenue(), 0.0001);

        assertEquals(fastA, rows.get(0).medicineId());
        assertEquals(fastB, rows.get(1).medicineId());
        assertTrue(rows.stream().noneMatch(row -> row.medicineId() == noSales));
    }

    @Test
    void fastMovingInsightsSupportDateRangeSupplierAndCategoryFilters() throws Exception {
        MedicineDAO dao = new MedicineDAO();
        int userId = insertUser("med_fast_filter_" + System.nanoTime(), "ADMIN");
        int analgesicA = insertMedicineWithGeneric("AnalgesicA-" + System.nanoTime(), "PainCare", "CoA", "2027-12-31",
                22.0, 30);
        int analgesicB = insertMedicineWithGeneric("AnalgesicB-" + System.nanoTime(), "PainCare", "CoA", "2027-12-31",
                25.0, 25);
        int antibiotic = insertMedicineWithGeneric("Antibiotic-" + System.nanoTime(), "Antibiotic", "CoB", "2027-12-31",
                30.0, 20);

        LocalDate today = LocalDate.now();
        LocalDate rangeStart = today.minusDays(14);
        LocalDate rangeEnd = today.minusDays(1);

        insertSale(userId, analgesicA, today.minusDays(10).atTime(10, 0, 0), 12, 22.0, 264.0);
        insertSale(userId, analgesicA, today.minusDays(40).atTime(9, 30, 0), 30, 22.0, 660.0);
        insertSale(userId, analgesicB, today.minusDays(4).atTime(14, 0, 0), 6, 25.0, 150.0);
        insertSale(userId, antibiotic, today.minusDays(5).atTime(11, 15, 0), 20, 30.0, 600.0);

        List<MedicineDAO.FastMovingInsightRow> rows = dao.getFastMovingInsights(
                rangeStart,
                rangeEnd,
                "CoA",
                "PainCare",
                50);

        assertTrue(rows.size() >= 2);
        MedicineDAO.FastMovingInsightRow rowA = rows.stream()
                .filter(row -> row.medicineId() == analgesicA)
                .findFirst()
                .orElseThrow();
        MedicineDAO.FastMovingInsightRow rowB = rows.stream()
                .filter(row -> row.medicineId() == analgesicB)
                .findFirst()
                .orElseThrow();

        assertEquals(12.0, rowA.lookbackUnitsSold(), 0.0001);
        assertEquals(264.0, rowA.lookbackRevenue(), 0.0001);
        assertEquals(6.0, rowB.lookbackUnitsSold(), 0.0001);
        assertEquals(150.0, rowB.lookbackRevenue(), 0.0001);
        assertTrue(rows.stream().noneMatch(row -> row.medicineId() == antibiotic));
        assertEquals(analgesicA, rows.get(0).medicineId());
    }

    @Test
    void getMedicineByIdReturnsActiveMedicineWithGenericAndStock() throws Exception {
        MedicineDAO dao = new MedicineDAO();
        String medicineName = "Lookup-" + System.nanoTime();
        int medicineId = insertMedicineWithGeneric(
                medicineName,
                "PainCare",
                "LookupCo",
                "2028-01-31",
                42.5,
                17);

        Medicine medicine = dao.getMedicineById(medicineId);

        assertNotNull(medicine);
        assertEquals(medicineId, medicine.getId());
        assertEquals(medicineName, medicine.getName());
        assertEquals("PainCare", medicine.getGenericName());
        assertEquals("LookupCo", medicine.getCompany());
        assertEquals("2028-01-31", medicine.getExpiry());
        assertEquals(17, medicine.getStock());
        assertEquals(42.5, medicine.getPrice(), 0.0001);
    }

    @Test
    void weeklySalesMarginSummaryUsesBillsSavingsAndExpensesWithinWeek() throws Exception {
        BillDAO billDAO = new BillDAO();
        ReportingWindowUtils.WeeklyWindow weeklyWindow = ReportingWindowUtils.mondayToSundayWindow(LocalDate.now());

        int userId = insertUser("sales_margin_" + System.nanoTime(), "ADMIN");
        int medicineA = insertMedicine("SalesA-" + System.nanoTime(), "CoA", "2027-12-31", 40.0, 50);
        int medicineB = insertMedicine("SalesB-" + System.nanoTime(), "CoB", "2027-12-31", 35.0, 50);

        insertSale(userId, medicineA, weeklyWindow.startDate().plusDays(1).atTime(10, 0, 0), 2, 50.0, 100.0);
        insertSale(userId, medicineB, weeklyWindow.startDate().plusDays(3).atTime(11, 30, 0), 3, 50.0, 150.0);
        insertSale(userId, medicineB, weeklyWindow.startDate().minusDays(1).atTime(9, 0, 0), 5, 50.0, 250.0);

        insertExpense("Shop Rent", 80.0, weeklyWindow.startDate().plusDays(2));
        insertExpense("Outside Week Expense", 30.0, weeklyWindow.startDate().minusDays(1));

        BillDAO.WeeklySalesMarginSummary summary = billDAO.getWeeklySalesMarginSummary(weeklyWindow.startDate(),
                weeklyWindow.endDate());

        assertEquals(weeklyWindow.startDate().toString(), summary.weekStartDate());
        assertEquals(weeklyWindow.endDate().toString(), summary.weekEndDate());
        // billCount and netSales may include bills from other test methods sharing the
        // same DB
        assertTrue(summary.billCount() >= 2L, "Expected at least 2 bills within the week");
        assertTrue(summary.netSales() >= 250.0, "Expected at least 250.0 in net sales");
        assertTrue(summary.totalExpenses() >= 80.0, "Expected at least 80.0 in expenses");
        // Margin = netSales - totalExpenses, so grossMargin should be positive
        assertTrue(summary.grossMargin() > 0, "Expected positive gross margin");
    }

    @Test
    void returnDamagedInsightsAggregateQuantityValueAndRootCauseTagsWithinLookback() throws Exception {
        MedicineDAO dao = new MedicineDAO();
        int userId = insertUser("ret_dmg_" + System.nanoTime(), "ADMIN");
        int medicineA = insertMedicine("ReturnDamA-" + System.nanoTime(), "CoA", "2027-12-31", 20.0, 50);
        int medicineB = insertMedicine("ReturnDamB-" + System.nanoTime(), "CoB", "2027-12-31", 30.0, 40);
        int outsideWindow = insertMedicine("ReturnDamC-" + System.nanoTime(), "CoC", "2027-12-31", 15.0, 30);

        LocalDate today = LocalDate.now();
        insertInventoryAdjustment(medicineA, "RETURN", 4, 20.0, "SupplierMismatch", today.minusDays(5).atTime(10, 0, 0),
                userId);
        insertInventoryAdjustment(medicineA, "DAMAGED", 2, 20.0, "TransportBreakage",
                today.minusDays(3).atTime(11, 0, 0), userId);
        insertInventoryAdjustment(medicineA, "DAMAGED", 1, 20.0, "TransportBreakage",
                today.minusDays(2).atTime(12, 0, 0), userId);

        insertInventoryAdjustment(medicineB, "RETURN", 3, 30.0, "CustomerReturn", today.minusDays(8).atTime(9, 45, 0),
                userId);
        insertInventoryAdjustment(medicineB, "DAMAGED", 1, 30.0, null, today.minusDays(1).atTime(16, 15, 0), userId);

        insertInventoryAdjustment(outsideWindow, "RETURN", 10, 15.0, "OldWindow", today.minusDays(45).atTime(8, 0, 0),
                userId);

        List<MedicineDAO.ReturnDamagedInsightRow> rows = dao.getReturnDamagedInsights(30, 50);
        assertTrue(rows.size() >= 2);

        MedicineDAO.ReturnDamagedInsightRow rowA = rows.stream()
                .filter(row -> row.medicineId() == medicineA)
                .findFirst()
                .orElseThrow();
        MedicineDAO.ReturnDamagedInsightRow rowB = rows.stream()
                .filter(row -> row.medicineId() == medicineB)
                .findFirst()
                .orElseThrow();

        assertEquals(4L, rowA.returnedQuantity());
        assertEquals(3L, rowA.damagedQuantity());
        assertEquals(7L, rowA.totalQuantity());
        assertEquals(80.0, rowA.returnValue(), 0.0001);
        assertEquals(60.0, rowA.damagedValue(), 0.0001);
        assertEquals(140.0, rowA.totalValue(), 0.0001);
        assertTrue(rowA.rootCauseTags().contains("SupplierMismatch"));
        assertTrue(rowA.rootCauseTags().contains("TransportBreakage"));

        assertEquals(3L, rowB.returnedQuantity());
        assertEquals(1L, rowB.damagedQuantity());
        assertEquals(4L, rowB.totalQuantity());
        assertEquals(90.0, rowB.returnValue(), 0.0001);
        assertEquals(30.0, rowB.damagedValue(), 0.0001);
        assertEquals(120.0, rowB.totalValue(), 0.0001);
        assertTrue(rowB.rootCauseTags().contains("CustomerReturn"));

        assertEquals(medicineA, rows.get(0).medicineId());
        assertTrue(rows.stream().noneMatch(row -> row.medicineId() == outsideWindow));
    }

    private static int insertUser(String username, String role) throws Exception {
        String sql = "INSERT INTO users (username, password, role) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, "password");
            ps.setString(3, role);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new IllegalStateException("Failed to insert user");
    }

    private static int insertMedicine(String name, String company, String expiryDate, double price, int stock)
            throws Exception {
        return insertMedicineWithGeneric(name, null, company, expiryDate, price, stock);
    }

    private static int insertMedicineWithGeneric(
            String name,
            String genericName,
            String company,
            String expiryDate,
            double price,
            int stock) throws Exception {
        String medicineSql = "INSERT INTO medicines (name, company, expiry_date, price, active) VALUES (?, ?, ?, ?, 1)";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(medicineSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, company);
            ps.setString(3, expiryDate);
            ps.setDouble(4, price);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int medicineId = rs.getInt(1);
                    if (genericName != null) {
                        try (PreparedStatement genericPs = conn.prepareStatement(
                                "UPDATE medicines SET generic_name = ? WHERE medicine_id = ?")) {
                            genericPs.setString(1, genericName);
                            genericPs.setInt(2, medicineId);
                            genericPs.executeUpdate();
                        }
                    }
                    try (PreparedStatement stockPs = conn.prepareStatement(
                            "INSERT INTO stock (medicine_id, quantity) VALUES (?, ?)")) {
                        stockPs.setInt(1, medicineId);
                        stockPs.setInt(2, stock);
                        stockPs.executeUpdate();
                    }
                    return medicineId;
                }
            }
        }
        throw new IllegalStateException("Failed to insert medicine");
    }

    private static void insertSale(
            int userId,
            int medicineId,
            LocalDateTime billDate,
            int quantity,
            double unitPrice,
            double lineTotal) throws Exception {
        String billSql = "INSERT INTO bills (customer_id, user_id, total_amount, bill_date, payment_mode) " +
                "VALUES (?, ?, ?, ?, 'CASH')";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement billPs = conn.prepareStatement(billSql, Statement.RETURN_GENERATED_KEYS)) {
            billPs.setObject(1, null);
            billPs.setInt(2, userId);
            billPs.setDouble(3, lineTotal);
            billPs.setString(4, billDate.format(DB_TS));
            billPs.executeUpdate();
            try (ResultSet rs = billPs.getGeneratedKeys()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Failed to insert bill");
                }
                int billId = rs.getInt(1);
                try (PreparedStatement itemPs = conn.prepareStatement(
                        "INSERT INTO bill_items (bill_id, medicine_id, quantity, price, total) VALUES (?, ?, ?, ?, ?)")) {
                    itemPs.setInt(1, billId);
                    itemPs.setInt(2, medicineId);
                    itemPs.setInt(3, quantity);
                    itemPs.setDouble(4, unitPrice);
                    itemPs.setDouble(5, lineTotal);
                    itemPs.executeUpdate();
                }
            }
        }
    }

    private static void insertExpense(String category, double amount, LocalDate date) throws Exception {
        String sql = "INSERT INTO expenses (category, amount, date, description) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, category);
            ps.setDouble(2, amount);
            ps.setString(3, date.toString());
            ps.setString(4, "integration-test");
            ps.executeUpdate();
        }
    }

    private static void insertInventoryAdjustment(
            int medicineId,
            String adjustmentType,
            int quantity,
            double unitPrice,
            String rootCauseTag,
            LocalDateTime occurredAt,
            Integer createdByUserId) throws Exception {
        String sql = "INSERT INTO inventory_adjustments " +
                "(medicine_id, adjustment_type, quantity, unit_price, root_cause_tag, occurred_at, created_by_user_id) "
                +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, medicineId);
            ps.setString(2, adjustmentType);
            ps.setInt(3, quantity);
            ps.setDouble(4, unitPrice);
            ps.setString(5, rootCauseTag);
            ps.setString(6, occurredAt.format(DB_TS));
            ps.setObject(7, createdByUserId);
            ps.executeUpdate();
        }
    }

    private static void tryDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // Best effort cleanup.
        }
    }
}
