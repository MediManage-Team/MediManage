package org.example.MediManage.dao;

import org.example.MediManage.DatabaseUtil;
import org.example.MediManage.model.Medicine;
import org.example.MediManage.security.Permission;
import org.example.MediManage.security.RbacPolicy;

import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MedicineDAO {
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;

    private void checkManagerPermission() {
        RbacPolicy.requireCurrentUser(Permission.MANAGE_MEDICINES);
    }

    public List<Medicine> getAllMedicines() {
        List<Medicine> list = new ArrayList<>();
        // Try with purchase_price first; fall back if column hasn't been migrated yet
        String sqlFull = "SELECT m.medicine_id, m.name, m.generic_name, m.company, m.expiry_date, m.price, " +
                "COALESCE(m.purchase_price, 0.0) AS purchase_price, s.quantity " +
                "FROM medicines m " +
                "LEFT JOIN stock s ON m.medicine_id = s.medicine_id " +
                "WHERE m.active = 1 " +
                "ORDER BY m.name ASC";
        String sqlFallback = "SELECT m.medicine_id, m.name, m.generic_name, m.company, m.expiry_date, m.price, " +
                "s.quantity " +
                "FROM medicines m " +
                "LEFT JOIN stock s ON m.medicine_id = s.medicine_id " +
                "WHERE m.active = 1 " +
                "ORDER BY m.name ASC";

        boolean hasPurchasePrice = true;
        try (Connection conn = DatabaseUtil.getConnection();
                Statement stmt = conn.createStatement()) {
            ResultSet rs;
            try {
                rs = stmt.executeQuery(sqlFull);
            } catch (SQLException columnMissing) {
                // purchase_price column not yet migrated — use fallback
                hasPurchasePrice = false;
                rs = stmt.executeQuery(sqlFallback);
            }
            while (rs.next()) {
                list.add(new Medicine(
                        rs.getInt("medicine_id"),
                        rs.getString("name"),
                        rs.getString("generic_name"),
                        rs.getString("company"),
                        rs.getString("expiry_date"),
                        rs.getInt("quantity"),
                        rs.getDouble("price"),
                        hasPurchasePrice ? rs.getDouble("purchase_price") : 0.0));
            }
            rs.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public Medicine getMedicineById(int medicineId) {
        if (medicineId <= 0) {
            return null;
        }
        String sql = "SELECT m.medicine_id, m.name, m.generic_name, m.company, m.expiry_date, m.price, s.quantity " +
                "FROM medicines m " +
                "LEFT JOIN stock s ON m.medicine_id = s.medicine_id " +
                "WHERE m.active = 1 AND m.medicine_id = ? " +
                "LIMIT 1";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, medicineId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new Medicine(
                        rs.getInt("medicine_id"),
                        rs.getString("name"),
                        rs.getString("generic_name"),
                        rs.getString("company"),
                        rs.getString("expiry_date"),
                        rs.getInt("quantity"),
                        rs.getDouble("price"));
            }
        } catch (SQLException e) {
            System.err.println("MedicineDAO.getMedicineById: " + e.getMessage());
            return null;
        }
    }

    /**
     * Paginated active medicines list.
     */
    public List<Medicine> getMedicinesPage(int offset, int limit) {
        List<Medicine> list = new ArrayList<>();
        int safeOffset = Math.max(0, offset);
        int safeLimit = normalizeLimit(limit);

        String sql = "SELECT m.medicine_id, m.name, m.generic_name, m.company, m.expiry_date, m.price, s.quantity " +
                "FROM medicines m " +
                "LEFT JOIN stock s ON m.medicine_id = s.medicine_id " +
                "WHERE m.active = 1 " +
                "ORDER BY m.name ASC LIMIT ? OFFSET ?";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, safeLimit);
            pstmt.setInt(2, safeOffset);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new Medicine(
                            rs.getInt("medicine_id"),
                            rs.getString("name"),
                            rs.getString("generic_name"),
                            rs.getString("company"),
                            rs.getString("expiry_date"),
                            rs.getInt("quantity"),
                            rs.getDouble("price")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public int countActiveMedicines() {
        String sql = "SELECT COUNT(*) FROM medicines WHERE active = 1";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql);
                ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public void addMedicine(String name, String genericName, String company, String expiry, double price,
            int initialStock, double purchasePrice, int reorderThreshold) {
        checkManagerPermission();
        String insertMed = "INSERT INTO medicines(name, generic_name, company, expiry_date, price, purchase_price, reorder_threshold) VALUES(?, ?, ?, ?, ?, ?, ?)";
        String insertStock = "INSERT INTO stock(medicine_id, quantity) VALUES(?, ?)";

        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement psMed = conn.prepareStatement(insertMed, Statement.RETURN_GENERATED_KEYS)) {
                psMed.setString(1, name);
                psMed.setString(2, genericName);
                psMed.setString(3, company);
                psMed.setString(4, expiry);
                psMed.setDouble(5, price);
                psMed.setDouble(6, purchasePrice);
                psMed.setInt(7, reorderThreshold);
                psMed.executeUpdate();

                ResultSet rs = psMed.getGeneratedKeys();
                int medId = 0;
                if (rs.next()) {
                    medId = rs.getInt(1);
                }

                if (medId > 0) {
                    try (PreparedStatement psStock = conn.prepareStatement(insertStock)) {
                        psStock.setInt(1, medId);
                        psStock.setInt(2, initialStock);
                        psStock.executeUpdate();
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Update Medicine (Name, Generic, Company, Price, Expiry, PurchasePrice,
    // ReorderThreshold)
    public void updateMedicine(Medicine medicine, int reorderThreshold) {
        checkManagerPermission();
        String sql = "UPDATE medicines SET name=?, generic_name=?, company=?, price=?, expiry_date=?, purchase_price=?, reorder_threshold=? WHERE medicine_id=?";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, medicine.getName());
            pstmt.setString(2, medicine.getGenericName());
            pstmt.setString(3, medicine.getCompany());
            pstmt.setDouble(4, medicine.getPrice());
            pstmt.setString(5, medicine.getExpiry());
            pstmt.setDouble(6, medicine.getPurchasePrice());
            pstmt.setInt(7, reorderThreshold);
            pstmt.setInt(8, medicine.getId());

            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Direct Stock Update
    // Direct Stock Update
    public void updateStock(int medicineId, int newQuantity) {
        checkManagerPermission();
        String updateSql = "UPDATE stock SET quantity=? WHERE medicine_id=?";
        String insertSql = "INSERT INTO stock (medicine_id, quantity) VALUES (?, ?)";

        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement startStmt = conn.prepareStatement(updateSql)) {
                startStmt.setInt(1, newQuantity);
                startStmt.setInt(2, medicineId);
                int rows = startStmt.executeUpdate();

                if (rows == 0) {
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        insertStmt.setInt(1, medicineId);
                        insertStmt.setInt(2, newQuantity);
                        insertStmt.executeUpdate();
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Soft-delete a medicine by setting active = 0.
     * This preserves referential integrity with bill_items and avoids FK constraint
     * errors.
     */
    public void deleteMedicine(int medicineId) {
        checkManagerPermission();
        String sql = "UPDATE medicines SET active = 0 WHERE medicine_id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, medicineId);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                System.out.println("✅ Medicine " + medicineId + " soft-deleted.");
            } else {
                System.err.println("⚠️ Medicine " + medicineId + " not found.");
            }
        } catch (SQLException e) {
            System.err.println("Delete failed: " + e.getMessage());
        }
    }
    // --- Barcode Methods ---

    /**
     * Look up a medicine by its barcode string.
     */
    public Medicine findByBarcode(String barcode) {
        if (barcode == null || barcode.isBlank())
            return null;
        String sql = "SELECT m.medicine_id, m.name, m.generic_name, m.company, m.expiry_date, m.price, s.quantity " +
                "FROM medicines m " +
                "LEFT JOIN stock s ON m.medicine_id = s.medicine_id " +
                "WHERE m.active = 1 AND m.barcode = ? " +
                "LIMIT 1";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, barcode.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Medicine(
                            rs.getInt("medicine_id"),
                            rs.getString("name"),
                            rs.getString("generic_name"),
                            rs.getString("company"),
                            rs.getString("expiry_date"),
                            rs.getInt("quantity"),
                            rs.getDouble("price"));
                }
            }
        } catch (SQLException e) {
            System.err.println("MedicineDAO.findByBarcode: " + e.getMessage());
        }
        return null;
    }

    /**
     * Assign or update a barcode for a medicine.
     */
    public void updateBarcode(int medicineId, String barcode) throws SQLException {
        checkManagerPermission();
        String sql = "UPDATE medicines SET barcode = ? WHERE medicine_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, barcode);
            ps.setInt(2, medicineId);
            ps.executeUpdate();
        }
    }

    // --- Business Intelligence Methods ---

    public List<Medicine> searchByGeneric(String keyword) {
        List<Medicine> list = new ArrayList<>();
        String sql = "SELECT m.medicine_id, m.name, m.generic_name, m.company, m.expiry_date, m.price, s.quantity " +
                "FROM medicines m " +
                "LEFT JOIN stock s ON m.medicine_id = s.medicine_id " +
                "WHERE m.active = 1 AND (m.generic_name LIKE ? OR m.name LIKE ?)";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            String searchPattern = "%" + keyword + "%";
            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new Medicine(
                            rs.getInt("medicine_id"),
                            rs.getString("name"),
                            rs.getString("generic_name"),
                            rs.getString("company"),
                            rs.getString("expiry_date"),
                            rs.getInt("quantity"),
                            rs.getDouble("price")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Paginated search for medicines by keyword. Searches name, generic_name, and
     * company.
     * Returns at most `limit` results starting from `offset`.
     */
    public List<Medicine> searchMedicines(String keyword, int offset, int limit) {
        List<Medicine> list = new ArrayList<>();
        int safeOffset = Math.max(0, offset);
        int safeLimit = normalizeLimit(limit);
        String safeKeyword = keyword == null ? "" : keyword.trim();
        if (safeKeyword.isEmpty()) {
            return getMedicinesPage(safeOffset, safeLimit);
        }

        String sql = "SELECT m.medicine_id, m.name, m.generic_name, m.company, m.expiry_date, m.price, s.quantity " +
                "FROM medicines m " +
                "LEFT JOIN stock s ON m.medicine_id = s.medicine_id " +
                "WHERE m.active = 1 AND (m.name LIKE ? OR m.generic_name LIKE ? OR m.company LIKE ?) " +
                "ORDER BY m.name ASC LIMIT ? OFFSET ?";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            String searchPattern = "%" + safeKeyword + "%";
            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);
            pstmt.setString(3, searchPattern);
            pstmt.setInt(4, safeLimit);
            pstmt.setInt(5, safeOffset);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new Medicine(
                            rs.getInt("medicine_id"),
                            rs.getString("name"),
                            rs.getString("generic_name"),
                            rs.getString("company"),
                            rs.getString("expiry_date"),
                            rs.getInt("quantity"),
                            rs.getDouble("price")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Count total active medicines matching a keyword.
     */
    public int countMedicines(String keyword) {
        String safeKeyword = keyword == null ? "" : keyword.trim();
        if (safeKeyword.isEmpty()) {
            return countActiveMedicines();
        }

        String sql = "SELECT COUNT(*) FROM medicines m " +
                "WHERE m.active = 1 AND (m.name LIKE ? OR m.generic_name LIKE ? OR m.company LIKE ?)";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            String searchPattern = "%" + safeKeyword + "%";
            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);
            pstmt.setString(3, searchPattern);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public List<Medicine> getExpiringMedicines(int days) {
        List<Medicine> list = new ArrayList<>();
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(days);
        String sql = "SELECT m.medicine_id, m.name, m.generic_name, m.company, m.expiry_date, m.price, s.quantity " +
                "FROM medicines m " +
                "LEFT JOIN stock s ON m.medicine_id = s.medicine_id " +
                "WHERE m.active = 1 " +
                "AND m.expiry_date >= ? " +
                "AND m.expiry_date <= ? " +
                "ORDER BY m.expiry_date ASC";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, startDate.toString());
            pstmt.setString(2, endDate.toString());

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new Medicine(
                            rs.getInt("medicine_id"),
                            rs.getString("name"),
                            rs.getString("generic_name"),
                            rs.getString("company"),
                            rs.getString("expiry_date"),
                            rs.getInt("quantity"),
                            rs.getDouble("price")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<OutOfStockInsightRow> getOutOfStockInsights(int lookbackDays, int limit) {
        int safeLookbackDays = Math.max(1, lookbackDays);
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(safeLookbackDays - 1);
        return getOutOfStockInsights(startDate, endDate, null, null, limit);
    }

    public List<OutOfStockInsightRow> getOutOfStockInsights(
            LocalDate rangeStartDate,
            LocalDate rangeEndDate,
            String supplierFilter,
            String categoryFilter,
            int limit) {
        List<OutOfStockInsightRow> rows = new ArrayList<>();
        int safeLimit = normalizeLimit(limit);
        LocalDate safeEndDate = rangeEndDate == null ? LocalDate.now() : rangeEndDate;
        LocalDate safeStartDate = rangeStartDate == null ? safeEndDate.minusDays(29) : rangeStartDate;
        if (safeStartDate.isAfter(safeEndDate)) {
            LocalDate temp = safeStartDate;
            safeStartDate = safeEndDate;
            safeEndDate = temp;
        }
        long safeLookbackDays = Math.max(1L, ChronoUnit.DAYS.between(safeStartDate, safeEndDate) + 1L);
        String safeSupplierFilter = normalizeFilterValue(supplierFilter);
        String safeCategoryFilter = normalizeFilterValue(categoryFilter);
        String rangeStart = safeStartDate + " 00:00:00";
        String rangeEndExclusive = safeEndDate.plusDays(1) + " 00:00:00";

        String sql = "SELECT m.medicine_id, m.name, m.company, COALESCE(s.quantity, 0) AS current_stock, " +
                "(SELECT MAX(b.bill_date) " +
                " FROM bill_items bi " +
                " JOIN bills b ON b.bill_id = bi.bill_id " +
                " WHERE bi.medicine_id = m.medicine_id) AS last_sale_at, " +
                "(SELECT COALESCE(SUM(bi.total), 0) " +
                " FROM bill_items bi " +
                " JOIN bills b ON b.bill_id = bi.bill_id " +
                " WHERE bi.medicine_id = m.medicine_id " +
                " AND b.bill_date >= ? " +
                " AND b.bill_date < ?) AS lookback_revenue " +
                "FROM medicines m " +
                "LEFT JOIN stock s ON s.medicine_id = m.medicine_id " +
                "WHERE m.active = 1 " +
                "AND COALESCE(s.quantity, 0) <= 0 " +
                "AND (? IS NULL OR LOWER(COALESCE(m.company, '')) = LOWER(?)) " +
                "AND (? IS NULL OR LOWER(COALESCE(m.generic_name, '')) = LOWER(?)) " +
                "ORDER BY lookback_revenue DESC, m.name ASC " +
                "LIMIT ?";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, rangeStart);
            pstmt.setString(2, rangeEndExclusive);
            pstmt.setString(3, safeSupplierFilter);
            pstmt.setString(4, safeSupplierFilter);
            pstmt.setString(5, safeCategoryFilter);
            pstmt.setString(6, safeCategoryFilter);
            pstmt.setInt(7, safeLimit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String lastSaleAt = rs.getString("last_sale_at");
                    LocalDate lastSaleDate = parseDateFromTimestamp(lastSaleAt);
                    long daysOutOfStock = lastSaleDate == null
                            ? safeLookbackDays
                            : Math.max(1L, ChronoUnit.DAYS.between(lastSaleDate, safeEndDate));
                    double lookbackRevenue = round2(rs.getDouble("lookback_revenue"));
                    double averageDailyRevenue = round2(lookbackRevenue / safeLookbackDays);
                    long impactDays = Math.max(1L, Math.min(daysOutOfStock, safeLookbackDays));
                    double estimatedImpact = round2(averageDailyRevenue * impactDays);

                    rows.add(new OutOfStockInsightRow(
                            rs.getInt("medicine_id"),
                            rs.getString("name") == null ? "" : rs.getString("name"),
                            rs.getString("company") == null ? "" : rs.getString("company"),
                            rs.getInt("current_stock"),
                            lastSaleAt,
                            daysOutOfStock,
                            lookbackRevenue,
                            averageDailyRevenue,
                            estimatedImpact));
                }
            }
        } catch (SQLException e) {
            System.err.println("MedicineDAO.getOutOfStockInsights: " + e.getMessage());
        }
        return rows;
    }

    public List<NearStockOutInsightRow> getNearStockOutInsights(int lookbackDays, int reorderCoverageDays, int limit) {
        int safeLookbackDays = Math.max(1, lookbackDays);
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(safeLookbackDays - 1);
        return getNearStockOutInsights(startDate, endDate, reorderCoverageDays, null, null, limit);
    }

    public List<NearStockOutInsightRow> getNearStockOutInsights(
            LocalDate rangeStartDate,
            LocalDate rangeEndDate,
            int reorderCoverageDays,
            String supplierFilter,
            String categoryFilter,
            int limit) {
        List<NearStockOutInsightRow> rows = new ArrayList<>();
        int safeReorderCoverageDays = Math.max(1, reorderCoverageDays);
        int safeLimit = normalizeLimit(limit);
        LocalDate safeEndDate = rangeEndDate == null ? LocalDate.now() : rangeEndDate;
        LocalDate safeStartDate = rangeStartDate == null ? safeEndDate.minusDays(29) : rangeStartDate;
        if (safeStartDate.isAfter(safeEndDate)) {
            LocalDate temp = safeStartDate;
            safeStartDate = safeEndDate;
            safeEndDate = temp;
        }
        int safeLookbackDays = (int) Math.max(1L, ChronoUnit.DAYS.between(safeStartDate, safeEndDate) + 1L);
        String safeSupplierFilter = normalizeFilterValue(supplierFilter);
        String safeCategoryFilter = normalizeFilterValue(categoryFilter);
        String rangeStart = safeStartDate + " 00:00:00";
        String rangeEndExclusive = safeEndDate.plusDays(1) + " 00:00:00";

        String sql = "SELECT m.medicine_id, m.name, m.company, COALESCE(s.quantity, 0) AS current_stock, " +
                "COALESCE(SUM(CASE WHEN b.bill_date >= ? AND b.bill_date < ? THEN bi.quantity ELSE 0 END), 0) AS lookback_units_sold, "
                +
                "COALESCE(SUM(CASE WHEN b.bill_date >= ? AND b.bill_date < ? THEN bi.total ELSE 0 END), 0) AS lookback_revenue, "
                +
                "MAX(b.bill_date) AS last_sale_at " +
                "FROM medicines m " +
                "LEFT JOIN stock s ON s.medicine_id = m.medicine_id " +
                "LEFT JOIN bill_items bi ON bi.medicine_id = m.medicine_id " +
                "LEFT JOIN bills b ON b.bill_id = bi.bill_id " +
                "WHERE m.active = 1 " +
                "AND COALESCE(s.quantity, 0) > 0 " +
                "AND (? IS NULL OR LOWER(COALESCE(m.company, '')) = LOWER(?)) " +
                "AND (? IS NULL OR LOWER(COALESCE(m.generic_name, '')) = LOWER(?)) " +
                "GROUP BY m.medicine_id, m.name, m.company, s.quantity";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, rangeStart);
            pstmt.setString(2, rangeEndExclusive);
            pstmt.setString(3, rangeStart);
            pstmt.setString(4, rangeEndExclusive);
            pstmt.setString(5, safeSupplierFilter);
            pstmt.setString(6, safeSupplierFilter);
            pstmt.setString(7, safeCategoryFilter);
            pstmt.setString(8, safeCategoryFilter);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int currentStock = rs.getInt("current_stock");
                    double lookbackUnitsSold = rs.getDouble("lookback_units_sold");
                    if (lookbackUnitsSold <= 0.0) {
                        continue;
                    }

                    double averageDailyConsumptionRaw = lookbackUnitsSold / safeLookbackDays;
                    if (averageDailyConsumptionRaw <= 0.0) {
                        continue;
                    }

                    int reorderThresholdQty = Math.max(1,
                            (int) Math.ceil(averageDailyConsumptionRaw * safeReorderCoverageDays));
                    if (currentStock > reorderThresholdQty) {
                        continue;
                    }

                    double lookbackRevenue = round2(rs.getDouble("lookback_revenue"));
                    double averageDailyConsumption = round2(averageDailyConsumptionRaw);
                    double daysToStockOut = round2(currentStock / averageDailyConsumptionRaw);
                    double averageUnitRevenue = lookbackUnitsSold <= 0.0 ? 0.0 : lookbackRevenue / lookbackUnitsSold;
                    int atRiskUnits = Math.max(0, reorderThresholdQty - currentStock);
                    double estimatedRevenueAtRisk = round2(atRiskUnits * averageUnitRevenue);

                    rows.add(new NearStockOutInsightRow(
                            rs.getInt("medicine_id"),
                            rs.getString("name") == null ? "" : rs.getString("name"),
                            rs.getString("company") == null ? "" : rs.getString("company"),
                            currentStock,
                            round2(lookbackUnitsSold),
                            lookbackRevenue,
                            averageDailyConsumption,
                            reorderThresholdQty,
                            daysToStockOut,
                            rs.getString("last_sale_at"),
                            estimatedRevenueAtRisk));
                }
            }
        } catch (SQLException e) {
            System.err.println("MedicineDAO.getNearStockOutInsights: " + e.getMessage());
        }

        rows.sort((a, b) -> {
            int riskCompare = Double.compare(b.estimatedRevenueAtRisk(), a.estimatedRevenueAtRisk());
            if (riskCompare != 0) {
                return riskCompare;
            }
            int daysCompare = Double.compare(a.daysToStockOut(), b.daysToStockOut());
            if (daysCompare != 0) {
                return daysCompare;
            }
            return a.medicineName().compareToIgnoreCase(b.medicineName());
        });

        if (rows.size() > safeLimit) {
            return new ArrayList<>(rows.subList(0, safeLimit));
        }
        return rows;
    }

    public List<DeadStockInsightRow> getDeadStockInsights(int noMovementDays, int limit) {
        return getDeadStockInsights(LocalDate.now(), noMovementDays, null, null, limit);
    }

    public List<DeadStockInsightRow> getDeadStockInsights(
            LocalDate referenceDate,
            int noMovementDays,
            String supplierFilter,
            String categoryFilter,
            int limit) {
        List<DeadStockInsightRow> rows = new ArrayList<>();
        int safeNoMovementDays = Math.max(1, noMovementDays);
        int safeLimit = normalizeLimit(limit);
        LocalDate safeReferenceDate = referenceDate == null ? LocalDate.now() : referenceDate;
        String safeSupplierFilter = normalizeFilterValue(supplierFilter);
        String safeCategoryFilter = normalizeFilterValue(categoryFilter);

        String sql = "SELECT m.medicine_id, m.name, m.company, m.price, COALESCE(s.quantity, 0) AS current_stock, " +
                "(SELECT MAX(b.bill_date) " +
                " FROM bill_items bi " +
                " JOIN bills b ON b.bill_id = bi.bill_id " +
                " WHERE bi.medicine_id = m.medicine_id) AS last_sale_at " +
                "FROM medicines m " +
                "LEFT JOIN stock s ON s.medicine_id = m.medicine_id " +
                "WHERE m.active = 1 " +
                "AND COALESCE(s.quantity, 0) > 0 " +
                "AND (? IS NULL OR LOWER(COALESCE(m.company, '')) = LOWER(?)) " +
                "AND (? IS NULL OR LOWER(COALESCE(m.generic_name, '')) = LOWER(?))";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, safeSupplierFilter);
            pstmt.setString(2, safeSupplierFilter);
            pstmt.setString(3, safeCategoryFilter);
            pstmt.setString(4, safeCategoryFilter);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int currentStock = rs.getInt("current_stock");
                    String lastSaleAt = rs.getString("last_sale_at");
                    LocalDate lastSaleDate = parseDateFromTimestamp(lastSaleAt);
                    long daysSinceLastMovement = lastSaleDate == null
                            ? safeNoMovementDays
                            : Math.max(0L, ChronoUnit.DAYS.between(lastSaleDate, safeReferenceDate));
                    if (daysSinceLastMovement < safeNoMovementDays) {
                        continue;
                    }

                    double unitPrice = round2(rs.getDouble("price"));
                    double deadStockValue = round2(currentStock * unitPrice);
                    rows.add(new DeadStockInsightRow(
                            rs.getInt("medicine_id"),
                            rs.getString("name") == null ? "" : rs.getString("name"),
                            rs.getString("company") == null ? "" : rs.getString("company"),
                            currentStock,
                            unitPrice,
                            lastSaleAt,
                            daysSinceLastMovement,
                            deadStockValue));
                }
            }
        } catch (SQLException e) {
            System.err.println("MedicineDAO.getDeadStockInsights: " + e.getMessage());
        }

        rows.sort((a, b) -> {
            int valueCompare = Double.compare(b.deadStockValue(), a.deadStockValue());
            if (valueCompare != 0) {
                return valueCompare;
            }
            int daysCompare = Long.compare(b.daysSinceLastMovement(), a.daysSinceLastMovement());
            if (daysCompare != 0) {
                return daysCompare;
            }
            return a.medicineName().compareToIgnoreCase(b.medicineName());
        });

        if (rows.size() > safeLimit) {
            return new ArrayList<>(rows.subList(0, safeLimit));
        }
        return rows;
    }

    public List<FastMovingInsightRow> getFastMovingInsights(int lookbackDays, int limit) {
        int safeLookbackDays = Math.max(1, lookbackDays);
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(safeLookbackDays - 1);
        return getFastMovingInsights(startDate, endDate, null, null, limit);
    }

    public List<FastMovingInsightRow> getFastMovingInsights(
            LocalDate rangeStartDate,
            LocalDate rangeEndDate,
            String supplierFilter,
            String categoryFilter,
            int limit) {
        List<FastMovingInsightRow> rows = new ArrayList<>();
        int safeLimit = normalizeLimit(limit);
        LocalDate safeEndDate = rangeEndDate == null ? LocalDate.now() : rangeEndDate;
        LocalDate safeStartDate = rangeStartDate == null ? safeEndDate.minusDays(29) : rangeStartDate;
        if (safeStartDate.isAfter(safeEndDate)) {
            LocalDate temp = safeStartDate;
            safeStartDate = safeEndDate;
            safeEndDate = temp;
        }
        long safeLookbackDays = Math.max(1L, ChronoUnit.DAYS.between(safeStartDate, safeEndDate) + 1L);
        String safeSupplierFilter = normalizeFilterValue(supplierFilter);
        String safeCategoryFilter = normalizeFilterValue(categoryFilter);
        String rangeStart = safeStartDate + " 00:00:00";
        String rangeEndExclusive = safeEndDate.plusDays(1) + " 00:00:00";

        String sql = "SELECT m.medicine_id, m.name, m.company, " +
                "COALESCE(SUM(bi.quantity), 0) AS lookback_units_sold, " +
                "COALESCE(SUM(bi.total), 0) AS lookback_revenue, " +
                "MAX(b.bill_date) AS last_sale_at " +
                "FROM medicines m " +
                "JOIN bill_items bi ON bi.medicine_id = m.medicine_id " +
                "JOIN bills b ON b.bill_id = bi.bill_id " +
                "WHERE m.active = 1 " +
                "AND b.bill_date >= ? " +
                "AND b.bill_date < ? " +
                "AND (? IS NULL OR LOWER(COALESCE(m.company, '')) = LOWER(?)) " +
                "AND (? IS NULL OR LOWER(COALESCE(m.generic_name, '')) = LOWER(?)) " +
                "GROUP BY m.medicine_id, m.name, m.company " +
                "ORDER BY lookback_units_sold DESC, lookback_revenue DESC, m.name ASC " +
                "LIMIT ?";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, rangeStart);
            pstmt.setString(2, rangeEndExclusive);
            pstmt.setString(3, safeSupplierFilter);
            pstmt.setString(4, safeSupplierFilter);
            pstmt.setString(5, safeCategoryFilter);
            pstmt.setString(6, safeCategoryFilter);
            pstmt.setInt(7, safeLimit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    double lookbackUnitsSold = round2(rs.getDouble("lookback_units_sold"));
                    double lookbackRevenue = round2(rs.getDouble("lookback_revenue"));
                    rows.add(new FastMovingInsightRow(
                            rs.getInt("medicine_id"),
                            rs.getString("name") == null ? "" : rs.getString("name"),
                            rs.getString("company") == null ? "" : rs.getString("company"),
                            lookbackUnitsSold,
                            lookbackRevenue,
                            round2(lookbackUnitsSold / safeLookbackDays),
                            round2(lookbackRevenue / safeLookbackDays),
                            rs.getString("last_sale_at")));
                }
            }
        } catch (SQLException e) {
            System.err.println("MedicineDAO.getFastMovingInsights: " + e.getMessage());
        }

        return rows;
    }

    public List<ReturnDamagedInsightRow> getReturnDamagedInsights(int lookbackDays, int limit) {
        int safeLookbackDays = Math.max(1, lookbackDays);
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(safeLookbackDays - 1);
        return getReturnDamagedInsights(startDate, endDate, null, null, limit);
    }

    public List<ReturnDamagedInsightRow> getReturnDamagedInsights(
            LocalDate rangeStartDate,
            LocalDate rangeEndDate,
            String supplierFilter,
            String categoryFilter,
            int limit) {
        List<ReturnDamagedInsightRow> rows = new ArrayList<>();
        int safeLimit = normalizeLimit(limit);
        LocalDate safeEndDate = rangeEndDate == null ? LocalDate.now() : rangeEndDate;
        LocalDate safeStartDate = rangeStartDate == null ? safeEndDate.minusDays(29) : rangeStartDate;
        if (safeStartDate.isAfter(safeEndDate)) {
            LocalDate temp = safeStartDate;
            safeStartDate = safeEndDate;
            safeEndDate = temp;
        }
        String safeSupplierFilter = normalizeFilterValue(supplierFilter);
        String safeCategoryFilter = normalizeFilterValue(categoryFilter);
        String rangeStart = safeStartDate + " 00:00:00";
        String rangeEndExclusive = safeEndDate.plusDays(1) + " 00:00:00";

        String sql = "SELECT m.medicine_id, m.name, m.company, " +
                "COALESCE(SUM(CASE WHEN ia.adjustment_type = 'RETURN' THEN ia.quantity ELSE 0 END), 0) AS returned_qty, "
                +
                "COALESCE(SUM(CASE WHEN ia.adjustment_type = 'DAMAGED' THEN ia.quantity ELSE 0 END), 0) AS damaged_qty, "
                +
                "COALESCE(SUM(CASE WHEN ia.adjustment_type = 'RETURN' THEN ia.quantity * ia.unit_price ELSE 0 END), 0) AS return_value, "
                +
                "COALESCE(SUM(CASE WHEN ia.adjustment_type = 'DAMAGED' THEN ia.quantity * ia.unit_price ELSE 0 END), 0) AS damaged_value "
                +
                "FROM inventory_adjustments ia " +
                "JOIN medicines m ON m.medicine_id = ia.medicine_id " +
                "WHERE m.active = 1 " +
                "AND ia.occurred_at >= ? " +
                "AND ia.occurred_at < ? " +
                "AND ia.adjustment_type IN ('RETURN', 'DAMAGED') " +
                "AND (? IS NULL OR LOWER(COALESCE(m.company, '')) = LOWER(?)) " +
                "AND (? IS NULL OR LOWER(COALESCE(m.generic_name, '')) = LOWER(?)) " +
                "GROUP BY m.medicine_id, m.name, m.company " +
                "ORDER BY " +
                "(COALESCE(SUM(CASE WHEN ia.adjustment_type = 'RETURN' THEN ia.quantity * ia.unit_price ELSE 0 END), 0) + "
                +
                " COALESCE(SUM(CASE WHEN ia.adjustment_type = 'DAMAGED' THEN ia.quantity * ia.unit_price ELSE 0 END), 0)) DESC, "
                +
                "(COALESCE(SUM(CASE WHEN ia.adjustment_type = 'RETURN' THEN ia.quantity ELSE 0 END), 0) + " +
                " COALESCE(SUM(CASE WHEN ia.adjustment_type = 'DAMAGED' THEN ia.quantity ELSE 0 END), 0)) DESC, " +
                "m.name ASC " +
                "LIMIT ?";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, rangeStart);
            pstmt.setString(2, rangeEndExclusive);
            pstmt.setString(3, safeSupplierFilter);
            pstmt.setString(4, safeSupplierFilter);
            pstmt.setString(5, safeCategoryFilter);
            pstmt.setString(6, safeCategoryFilter);
            pstmt.setInt(7, safeLimit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    long returnedQuantity = rs.getLong("returned_qty");
                    long damagedQuantity = rs.getLong("damaged_qty");
                    long totalQuantity = returnedQuantity + damagedQuantity;
                    double returnValue = round2(rs.getDouble("return_value"));
                    double damagedValue = round2(rs.getDouble("damaged_value"));
                    double totalValue = round2(returnValue + damagedValue);
                    int medicineId = rs.getInt("medicine_id");
                    String rootCauseTags = fetchRootCauseTags(conn, medicineId, rangeStart, rangeEndExclusive);

                    rows.add(new ReturnDamagedInsightRow(
                            medicineId,
                            rs.getString("name") == null ? "" : rs.getString("name"),
                            rs.getString("company") == null ? "" : rs.getString("company"),
                            returnedQuantity,
                            damagedQuantity,
                            totalQuantity,
                            returnValue,
                            damagedValue,
                            totalValue,
                            rootCauseTags));
                }
            }
        } catch (SQLException e) {
            System.err.println("MedicineDAO.getReturnDamagedInsights: " + e.getMessage());
        }

        return rows;
    }

    private String normalizeFilterValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || "all".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(limit, MAX_PAGE_SIZE);
    }

    private LocalDate parseDateFromTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 10) {
            normalized = normalized.substring(0, 10);
        }
        try {
            return LocalDate.parse(normalized);
        } catch (Exception ignored) {
            return null;
        }
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String fetchRootCauseTags(Connection conn, int medicineId, String rangeStart, String rangeEndExclusive) {
        String sql = "SELECT DISTINCT root_cause_tag " +
                "FROM inventory_adjustments " +
                "WHERE medicine_id = ? " +
                "AND occurred_at >= ? " +
                "AND occurred_at < ? " +
                "AND adjustment_type IN ('RETURN', 'DAMAGED') " +
                "AND root_cause_tag IS NOT NULL " +
                "AND TRIM(root_cause_tag) <> '' " +
                "ORDER BY root_cause_tag ASC";
        Set<String> tags = new LinkedHashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, medicineId);
            ps.setString(2, rangeStart);
            ps.setString(3, rangeEndExclusive);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tag = rs.getString("root_cause_tag");
                    if (tag != null && !tag.isBlank()) {
                        tags.add(tag.trim());
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("MedicineDAO.fetchRootCauseTags: " + e.getMessage());
        }
        if (tags.isEmpty()) {
            return "-";
        }
        return String.join(", ", tags);
    }

    /**
     * Returns medicines where stock is at or below their reorder_threshold.
     * Each medicine can have its own threshold (default 10).
     */
    public List<ReorderNeededRow> getReorderNeeded() {
        List<ReorderNeededRow> rows = new ArrayList<>();
        String sqlFull = "SELECT m.medicine_id, m.name, m.company, " +
                "COALESCE(s.quantity, 0) AS current_stock, " +
                "COALESCE(m.reorder_threshold, 10) AS reorder_threshold, " +
                "m.supplier_id " +
                "FROM medicines m " +
                "LEFT JOIN stock s ON s.medicine_id = m.medicine_id " +
                "WHERE m.active = 1 " +
                "AND COALESCE(s.quantity, 0) <= COALESCE(m.reorder_threshold, 10) " +
                "ORDER BY COALESCE(s.quantity, 0) ASC, m.name ASC";
        String sqlFallback = "SELECT m.medicine_id, m.name, m.company, " +
                "COALESCE(s.quantity, 0) AS current_stock, " +
                "10 AS reorder_threshold " +
                "FROM medicines m " +
                "LEFT JOIN stock s ON s.medicine_id = m.medicine_id " +
                "WHERE m.active = 1 " +
                "AND COALESCE(s.quantity, 0) <= 10 " +
                "ORDER BY COALESCE(s.quantity, 0) ASC, m.name ASC";

        try (Connection conn = DatabaseUtil.getConnection();
                Statement stmt = conn.createStatement()) {
            ResultSet rs;
            boolean hasFull = true;
            try {
                rs = stmt.executeQuery(sqlFull);
            } catch (SQLException columnMissing) {
                hasFull = false;
                rs = stmt.executeQuery(sqlFallback);
            }
            while (rs.next()) {
                rows.add(new ReorderNeededRow(
                        rs.getInt("medicine_id"),
                        rs.getString("name") == null ? "" : rs.getString("name"),
                        rs.getString("company") == null ? "" : rs.getString("company"),
                        rs.getInt("current_stock"),
                        rs.getInt("reorder_threshold"),
                        hasFull && rs.getObject("supplier_id") != null ? rs.getInt("supplier_id") : 0));
            }
            rs.close();
        } catch (SQLException e) {
            System.err.println("MedicineDAO.getReorderNeeded: " + e.getMessage());
        }
        return rows;
    }

    public record ReorderNeededRow(
            int medicineId,
            String medicineName,
            String company,
            int currentStock,
            int reorderThreshold,
            int supplierId) {
    }

    public record OutOfStockInsightRow(
            int medicineId,
            String medicineName,
            String company,
            int currentStock,
            String lastSaleAt,
            long daysOutOfStock,
            double lookbackRevenue,
            double averageDailyRevenue,
            double estimatedRevenueImpact) {
    }

    public record NearStockOutInsightRow(
            int medicineId,
            String medicineName,
            String company,
            int currentStock,
            double lookbackUnitsSold,
            double lookbackRevenue,
            double averageDailyConsumption,
            int reorderThresholdQty,
            double daysToStockOut,
            String lastSaleAt,
            double estimatedRevenueAtRisk) {
    }

    public record DeadStockInsightRow(
            int medicineId,
            String medicineName,
            String company,
            int currentStock,
            double unitPrice,
            String lastSaleAt,
            long daysSinceLastMovement,
            double deadStockValue) {
    }

    public record FastMovingInsightRow(
            int medicineId,
            String medicineName,
            String company,
            double lookbackUnitsSold,
            double lookbackRevenue,
            double averageDailyUnits,
            double averageDailyRevenue,
            String lastSaleAt) {
    }

    public record ReturnDamagedInsightRow(
            int medicineId,
            String medicineName,
            String company,
            long returnedQuantity,
            long damagedQuantity,
            long totalQuantity,
            double returnValue,
            double damagedValue,
            double totalValue,
            String rootCauseTags) {
    }
}
