package org.example.MediManage.dao;

import org.example.MediManage.model.InventoryBatch;
import org.example.MediManage.util.DatabaseUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class InventoryBatchDAO {
    private static final DateTimeFormatter BATCH_STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public record BatchAllocation(
            Integer batchId,
            String batchNumber,
            String expiryDate,
            int quantity,
            double unitCost,
            double sellingPrice) {
    }

    public record ExpiryLossExposureRow(
            int medicineId,
            String medicineName,
            String company,
            String supplierName,
            String batchNumber,
            String batchBarcode,
            String expiryDate,
            Integer daysToExpiry,
            int expirySequence,
            int availableQuantity,
            double unitCost,
            double sellingPrice,
            double stockCostValue,
            double stockSalesValue) {
    }

    public record MedicineManagementOverviewRow(
            int medicineId,
            String medicineName,
            String genericName,
            String company,
            String medicineBarcode,
            int currentStock,
            int trackedBatchUnits,
            int stockGapUnits,
            int activeBatchCount,
            String earliestBatchExpiry,
            int expiredUnits,
            int expiring30dUnits,
            double expiryExposureCost,
            int dumpedUnits) {
    }

    public void recordPurchaseBatch(
            Connection conn,
            int medicineId,
            Integer sourcePoiId,
            String batchNumber,
            String expiryDate,
            String purchaseDate,
            double unitCost,
            double sellingPrice,
            int quantity,
            Integer supplierId) throws SQLException {
        if (quantity <= 0) {
            return;
        }
        String sql = """
                INSERT INTO inventory_batches (
                    medicine_id,
                    source_poi_id,
                    batch_number,
                    batch_barcode,
                    expiry_date,
                    purchase_date,
                    unit_cost,
                    selling_price,
                    initial_quantity,
                    available_quantity,
                    supplier_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, medicineId);
            if (sourcePoiId != null && sourcePoiId > 0) {
                ps.setInt(2, sourcePoiId);
            } else {
                ps.setNull(2, java.sql.Types.INTEGER);
            }
            ps.setString(3, normalizedBatchNumber(batchNumber, medicineId));
            ps.setString(4, generatedBatchBarcode(medicineId, batchNumber, expiryDate));
            ps.setString(5, blankToNull(expiryDate));
            ps.setString(6, blankToNull(purchaseDate));
            ps.setDouble(7, Math.max(0.0, unitCost));
            ps.setDouble(8, Math.max(0.0, sellingPrice));
            ps.setInt(9, quantity);
            ps.setInt(10, quantity);
            if (supplierId != null && supplierId > 0) {
                ps.setInt(11, supplierId);
            } else {
                ps.setNull(11, java.sql.Types.INTEGER);
            }
            ps.executeUpdate();
        }
        syncStockFromBatches(conn, medicineId);
    }

    public List<BatchAllocation> allocateFefo(Connection conn, int medicineId, int requestedQty) throws SQLException {
        if (requestedQty <= 0) {
            return List.of();
        }
        ensureBatchCoverage(conn, medicineId);

        List<BatchAllocation> allocations = new ArrayList<>();
        int remaining = requestedQty;
        String selectSql = """
                SELECT batch_id,
                       batch_number,
                       expiry_date,
                       available_quantity,
                       unit_cost,
                       selling_price
                FROM inventory_batches
                WHERE medicine_id = ?
                  AND available_quantity > 0
                ORDER BY CASE
                            WHEN expiry_date IS NULL OR TRIM(expiry_date) = '' THEN 1
                            ELSE 0
                         END,
                         expiry_date ASC,
                         CASE
                            WHEN purchase_date IS NULL OR TRIM(purchase_date) = '' THEN 1
                            ELSE 0
                         END,
                         purchase_date ASC,
                         batch_id ASC
                """;
        String updateSql = "UPDATE inventory_batches SET available_quantity = available_quantity - ?, updated_at = CURRENT_TIMESTAMP WHERE batch_id = ?";

        try (PreparedStatement selectPs = conn.prepareStatement(selectSql);
             PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
            selectPs.setInt(1, medicineId);
            try (ResultSet rs = selectPs.executeQuery()) {
                while (rs.next() && remaining > 0) {
                    int available = rs.getInt("available_quantity");
                    int allocate = Math.min(remaining, available);
                    if (allocate <= 0) {
                        continue;
                    }

                    int batchId = rs.getInt("batch_id");
                    updatePs.setInt(1, allocate);
                    updatePs.setInt(2, batchId);
                    if (updatePs.executeUpdate() != 1) {
                        throw new SQLException("Failed to reserve stock from batch " + batchId);
                    }

                    allocations.add(new BatchAllocation(
                            batchId,
                            rs.getString("batch_number"),
                            rs.getString("expiry_date"),
                            allocate,
                            rs.getDouble("unit_cost"),
                            rs.getDouble("selling_price")));
                    remaining -= allocate;
                }
            }
        }

        if (remaining > 0) {
            throw new SQLException("Insufficient FEFO batch stock for medicine_id=" + medicineId);
        }

        syncStockFromBatches(conn, medicineId);
        return allocations;
    }

    public List<BatchAllocation> consumeForStockReduction(Connection conn, int medicineId, int quantity) throws SQLException {
        return allocateFefo(conn, medicineId, quantity);
    }

    public void setTargetStock(
            Connection conn,
            int medicineId,
            int targetQuantity,
            double unitCost,
            double sellingPrice,
            String expiryDate,
            Integer supplierId) throws SQLException {
        int currentBatchQuantity = getCurrentBatchStock(conn, medicineId);
        if (targetQuantity == currentBatchQuantity) {
            syncStockFromBatches(conn, medicineId);
            return;
        }
        if (targetQuantity > currentBatchQuantity) {
            int delta = targetQuantity - currentBatchQuantity;
            recordPurchaseBatch(
                    conn,
                    medicineId,
                    null,
                    "MANUAL-" + medicineId + "-" + LocalDateTime.now().format(BATCH_STAMP),
                    expiryDate,
                    LocalDate.now().toString(),
                    unitCost,
                    sellingPrice,
                    delta,
                    supplierId);
            return;
        }

        int reduction = currentBatchQuantity - targetQuantity;
        allocateFefo(conn, medicineId, reduction);
        syncStockFromBatches(conn, medicineId);
    }

    public List<InventoryBatch> getAvailableBatchesForMedicine(int medicineId) throws SQLException {
        List<InventoryBatch> rows = new ArrayList<>();
        String sql = """
                SELECT *
                FROM v_inventory_batch_expiry_timeline
                WHERE medicine_id = ?
                ORDER BY expiry_sequence ASC
                """;
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, medicineId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapBatch(rs));
                }
            }
        }
        return rows;
    }

    public List<InventoryBatch> getExpiringBatches(LocalDate startDate, LocalDate endDate, int limit) throws SQLException {
        List<InventoryBatch> rows = new ArrayList<>();
        LocalDate safeStart = startDate == null ? LocalDate.now() : startDate;
        LocalDate safeEnd = endDate == null ? safeStart.plusDays(30) : endDate;
        int safeLimit = Math.max(1, limit);

        String sql = """
                SELECT *
                FROM v_inventory_batch_expiry_timeline
                WHERE available_quantity > 0
                  AND expiry_date <> ''
                  AND expiry_date >= ?
                  AND expiry_date <= ?
                ORDER BY expiry_sequence ASC, available_quantity DESC, medicine_name ASC
                LIMIT ?
                """;
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, safeStart.toString());
            ps.setString(2, safeEnd.toString());
            ps.setInt(3, safeLimit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapBatch(rs));
                }
            }
        }
        return rows;
    }

    public List<InventoryBatch> getExpiredBatches(LocalDate asOfDate, int limit) throws SQLException {
        List<InventoryBatch> rows = new ArrayList<>();
        LocalDate safeAsOf = asOfDate == null ? LocalDate.now() : asOfDate;
        int safeLimit = Math.max(1, limit);

        String sql = """
                SELECT *
                FROM v_inventory_batch_expiry_timeline
                WHERE available_quantity > 0
                  AND expiry_date <> ''
                  AND expiry_date < ?
                ORDER BY expiry_sequence ASC, available_quantity DESC, medicine_name ASC
                LIMIT ?
                """;
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, safeAsOf.toString());
            ps.setInt(2, safeLimit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapBatch(rs));
                }
            }
        }
        return rows;
    }

    public List<ExpiryLossExposureRow> getExpiryLossExposure(LocalDate untilDate, int limit) throws SQLException {
        List<ExpiryLossExposureRow> rows = new ArrayList<>();
        LocalDate safeUntil = untilDate == null ? LocalDate.now().plusDays(30) : untilDate;
        int safeLimit = Math.max(1, limit);

        String sql = """
                SELECT *
                FROM v_inventory_batch_expiry_timeline
                WHERE available_quantity > 0
                  AND expiry_date <> ''
                  AND expiry_date <= ?
                ORDER BY expiry_sequence ASC, available_quantity DESC, medicine_name ASC
                LIMIT ?
                """;
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, safeUntil.toString());
            ps.setInt(2, safeLimit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int availableQty = rs.getInt("available_quantity");
                    double unitCost = rs.getDouble("unit_cost");
                    double sellingPrice = rs.getDouble("selling_price");
                    rows.add(new ExpiryLossExposureRow(
                            rs.getInt("medicine_id"),
                            rs.getString("medicine_name"),
                            rs.getString("company"),
                            rs.getString("supplier_name"),
                            rs.getString("batch_number"),
                            rs.getString("batch_barcode"),
                            rs.getString("expiry_date"),
                            readNullableInt(rs, "days_to_expiry"),
                            rs.getInt("expiry_sequence"),
                            availableQty,
                            unitCost,
                            sellingPrice,
                            round2(rs.getDouble("stock_cost_value")),
                            round2(rs.getDouble("stock_sales_value"))));
                }
            }
        }
        return rows;
    }

    public List<MedicineManagementOverviewRow> getManagementOverview(int limit) throws SQLException {
        List<MedicineManagementOverviewRow> rows = new ArrayList<>();
        int safeLimit = Math.max(1, limit);
        String sql = """
                SELECT medicine_id,
                       name,
                       generic_name,
                       company,
                       medicine_barcode,
                       current_stock,
                       tracked_batch_units,
                       stock_gap_units,
                       active_batch_count,
                       earliest_batch_expiry,
                       expired_units,
                       expiring_30d_units,
                       expiry_exposure_cost,
                       dumped_units
                FROM v_medicine_management_overview
                ORDER BY expiry_exposure_cost DESC,
                         expired_units DESC,
                         expiring_30d_units DESC,
                         current_stock DESC,
                         name ASC
                LIMIT ?
                """;
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, safeLimit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new MedicineManagementOverviewRow(
                            rs.getInt("medicine_id"),
                            rs.getString("name"),
                            rs.getString("generic_name"),
                            rs.getString("company"),
                            rs.getString("medicine_barcode"),
                            rs.getInt("current_stock"),
                            rs.getInt("tracked_batch_units"),
                            rs.getInt("stock_gap_units"),
                            rs.getInt("active_batch_count"),
                            rs.getString("earliest_batch_expiry"),
                            rs.getInt("expired_units"),
                            rs.getInt("expiring_30d_units"),
                            round2(rs.getDouble("expiry_exposure_cost")),
                            rs.getInt("dumped_units")));
                }
            }
        }
        return rows;
    }

    public int countExpiredBatches(LocalDate asOfDate) throws SQLException {
        LocalDate safeAsOf = asOfDate == null ? LocalDate.now() : asOfDate;
        String sql = """
                SELECT COUNT(*)
                FROM inventory_batches
                WHERE available_quantity > 0
                  AND expiry_date IS NOT NULL
                  AND TRIM(expiry_date) <> ''
                  AND expiry_date < ?
                """;
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, safeAsOf.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public int countBatchesExpiringBetween(LocalDate startDate, LocalDate endDate) throws SQLException {
        LocalDate safeStart = startDate == null ? LocalDate.now() : startDate;
        LocalDate safeEnd = endDate == null ? safeStart.plusDays(30) : endDate;
        String sql = """
                SELECT COUNT(*)
                FROM inventory_batches
                WHERE available_quantity > 0
                  AND expiry_date IS NOT NULL
                  AND TRIM(expiry_date) <> ''
                  AND expiry_date >= ?
                  AND expiry_date <= ?
                """;
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, safeStart.toString());
            ps.setString(2, safeEnd.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public double getExpiryLossExposureTotal(LocalDate untilDate) throws SQLException {
        LocalDate safeUntil = untilDate == null ? LocalDate.now().plusDays(30) : untilDate;
        String sql = """
                SELECT COALESCE(SUM(available_quantity * unit_cost), 0)
                FROM inventory_batches
                WHERE available_quantity > 0
                  AND expiry_date IS NOT NULL
                  AND TRIM(expiry_date) <> ''
                  AND expiry_date <= ?
                """;
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, safeUntil.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? round2(rs.getDouble(1)) : 0.0;
            }
        }
    }

    public int getCurrentBatchStock(Connection conn, int medicineId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(available_quantity), 0) FROM inventory_batches WHERE medicine_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, medicineId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void syncStockFromBatches(Connection conn, int medicineId) throws SQLException {
        int quantity = getCurrentBatchStock(conn, medicineId);
        try (PreparedStatement update = conn.prepareStatement("UPDATE stock SET quantity = ? WHERE medicine_id = ?")) {
            update.setInt(1, quantity);
            update.setInt(2, medicineId);
            int rows = update.executeUpdate();
            if (rows == 0) {
                try (PreparedStatement insert = conn.prepareStatement("INSERT INTO stock (medicine_id, quantity) VALUES (?, ?)")) {
                    insert.setInt(1, medicineId);
                    insert.setInt(2, quantity);
                    insert.executeUpdate();
                }
            }
        }
    }

    public void ensureBatchCoverage(Connection conn, int medicineId) throws SQLException {
        int batchQty = getCurrentBatchStock(conn, medicineId);
        int stockQty = 0;
        double unitCost = 0.0;
        double sellingPrice = 0.0;
        String expiryDate = null;
        Integer supplierId = null;

        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT COALESCE(s.quantity, 0) AS stock_qty,
                       COALESCE(m.purchase_price, 0.0) AS purchase_price,
                       COALESCE(m.price, 0.0) AS selling_price,
                       COALESCE(m.expiry_date, '') AS expiry_date,
                       m.supplier_id
                FROM medicines m
                LEFT JOIN stock s ON s.medicine_id = m.medicine_id
                WHERE m.medicine_id = ?
                """)) {
            ps.setInt(1, medicineId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    stockQty = rs.getInt("stock_qty");
                    unitCost = rs.getDouble("purchase_price");
                    sellingPrice = rs.getDouble("selling_price");
                    expiryDate = rs.getString("expiry_date");
                    Object supplierValue = rs.getObject("supplier_id");
                    if (supplierValue != null) {
                        supplierId = rs.getInt("supplier_id");
                    }
                }
            }
        }

        int gap = stockQty - batchQty;
        if (gap <= 0) {
            return;
        }
        recordPurchaseBatch(
                conn,
                medicineId,
                null,
                "LEGACY-" + medicineId + "-" + LocalDateTime.now().format(BATCH_STAMP),
                expiryDate,
                LocalDate.now().toString(),
                unitCost,
                sellingPrice,
                gap,
                supplierId);
    }

    private InventoryBatch mapBatch(ResultSet rs) throws SQLException {
        return new InventoryBatch(
                rs.getInt("batch_id"),
                rs.getInt("medicine_id"),
                rs.getString("medicine_name"),
                rs.getString("company"),
                rs.getString("supplier_name"),
                rs.getString("batch_number"),
                rs.getString("batch_barcode"),
                rs.getString("expiry_date"),
                readNullableInt(rs, "days_to_expiry"),
                rs.getInt("expiry_sequence"),
                rs.getString("purchase_date"),
                rs.getDouble("unit_cost"),
                rs.getDouble("selling_price"),
                rs.getInt("initial_quantity"),
                rs.getInt("available_quantity"),
                rs.getString("created_at"));
    }

    private Integer readNullableInt(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : rs.getInt(column);
    }

    private String normalizedBatchNumber(String batchNumber, int medicineId) {
        if (batchNumber != null && !batchNumber.isBlank()) {
            return batchNumber.trim();
        }
        return "AUTO-" + medicineId + "-" + LocalDate.now().format(BATCH_STAMP);
    }

    public String generatedBatchBarcode(int medicineId, String batchNumber, String expiryDate) {
        String normalizedBatch = normalizedBatchNumber(batchNumber, medicineId)
                .replaceAll("[^A-Za-z0-9]+", "")
                .toUpperCase();
        if (normalizedBatch.length() > 18) {
            normalizedBatch = normalizedBatch.substring(0, 18);
        }
        String expiryToken = expiryDate == null ? "" : expiryDate.replaceAll("[^0-9]", "");
        if (expiryToken.length() > 6) {
            expiryToken = expiryToken.substring(0, 6);
        }
        StringBuilder barcode = new StringBuilder("BT-")
                .append(medicineId)
                .append('-')
                .append(normalizedBatch);
        if (!expiryToken.isBlank()) {
            barcode.append('-').append(expiryToken);
        }
        return barcode.toString();
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
