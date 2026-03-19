package org.example.MediManage.dao;

import org.example.MediManage.util.DatabaseUtil;
import org.example.MediManage.model.PurchaseOrder;
import org.example.MediManage.model.PurchaseOrderItem;
import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PurchaseOrderDAO {
    private final InventoryBatchDAO inventoryBatchDAO = new InventoryBatchDAO();
    private final AuditLogDAO auditLogDAO = new AuditLogDAO();

    public List<PurchaseOrder> getAllPurchaseOrders() throws SQLException {
        List<PurchaseOrder> list = new ArrayList<>();
        String sql = "SELECT po.*, s.name as supplier_name " +
                     "FROM purchase_orders po " +
                     "JOIN suppliers s ON po.supplier_id = s.supplier_id " +
                     "ORDER BY po.order_date DESC";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public List<PurchaseOrderItem> getPurchaseOrderItems(int poId) throws SQLException {
        List<PurchaseOrderItem> list = new ArrayList<>();
        String sql = "SELECT poi.*, " +
                     "COALESCE(NULLIF(poi.medicine_name_snapshot, ''), m.name, 'Unknown Medicine') AS medicine_name, " +
                     "COALESCE(NULLIF(poi.generic_name_snapshot, ''), m.generic_name, '') AS generic_name, " +
                     "COALESCE(NULLIF(poi.company_snapshot, ''), m.company, '') AS company " +
                     "FROM purchase_order_items poi " +
                     "LEFT JOIN medicines m ON poi.medicine_id = m.medicine_id " +
                     "WHERE poi.po_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, poId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PurchaseOrderItem item = new PurchaseOrderItem();
                    item.setPoiId(rs.getInt("poi_id"));
                    item.setPoId(rs.getInt("po_id"));
                    item.setMedicineId(rs.getInt("medicine_id"));
                    item.setMedicineName(rs.getString("medicine_name"));
                    item.setGenericName(rs.getString("generic_name"));
                    item.setCompany(rs.getString("company"));
                    item.setBatchNumber(rs.getString("batch_number"));
                    item.setExpiryDate(rs.getString("expiry_date"));
                    item.setPurchaseDate(rs.getString("purchase_date"));
                    item.setOrderedQty(rs.getInt("ordered_qty"));
                    item.setReceivedQty(rs.getInt("received_qty"));
                    item.setUnitCost(rs.getDouble("unit_cost"));
                    item.setSellingPrice(rs.getDouble("selling_price"));
                    item.setReorderThreshold(rs.getInt("reorder_threshold"));
                    list.add(item);
                }
            }
        }
        return list;
    }

    public Map<String, Double> getPurchaseSpendBetweenDates(LocalDate start, LocalDate end) {
        Map<String, Double> spend = new LinkedHashMap<>();
        LocalDate safeStart = start == null ? LocalDate.now().minusDays(6) : start;
        LocalDate safeEnd = end == null ? safeStart.plusDays(6) : end;
        if (safeEnd.isBefore(safeStart)) {
            safeEnd = safeStart;
        }

        String sql = """
                SELECT DATE(order_date) AS day,
                       COALESCE(SUM(total_amount), 0) AS total_spend
                FROM purchase_orders
                WHERE order_date >= ?
                  AND order_date < ?
                GROUP BY DATE(order_date)
                ORDER BY day ASC
                """;
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, safeStart + " 00:00:00");
            ps.setString(2, safeEnd.plusDays(1) + " 00:00:00");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    spend.put(rs.getString("day"), rs.getDouble("total_spend"));
                }
            }
        } catch (SQLException e) {
            System.err.println("PurchaseOrderDAO.getPurchaseSpendBetweenDates: " + e.getMessage());
        }
        return spend;
    }

    public List<SupplierPerformanceRow> getSupplierPerformance(LocalDate start, LocalDate end, int limit) {
        List<SupplierPerformanceRow> rows = new ArrayList<>();
        LocalDate safeStart = start == null ? LocalDate.now().minusDays(29) : start;
        LocalDate safeEnd = end == null ? safeStart.plusDays(29) : end;
        if (safeEnd.isBefore(safeStart)) {
            safeEnd = safeStart;
        }
        int safeLimit = Math.max(1, limit);

        String sql = """
                SELECT s.supplier_id,
                       COALESCE(s.name, 'Unknown Supplier') AS supplier_name,
                       COUNT(DISTINCT po.po_id) AS purchase_orders,
                       COUNT(DISTINCT poi.medicine_id) AS distinct_skus,
                       COALESCE(SUM(poi.received_qty), 0) AS total_units,
                       COALESCE(SUM(poi.received_qty * poi.unit_cost), 0) AS total_spend,
                       COALESCE(SUM(poi.received_qty * poi.unit_cost) / NULLIF(SUM(poi.received_qty), 0), 0) AS avg_unit_cost,
                       MAX(po.order_date) AS last_order_date
                FROM purchase_orders po
                JOIN suppliers s ON s.supplier_id = po.supplier_id
                LEFT JOIN purchase_order_items poi ON poi.po_id = po.po_id
                WHERE po.order_date >= ?
                  AND po.order_date < ?
                GROUP BY s.supplier_id, s.name
                ORDER BY total_spend DESC, purchase_orders DESC, supplier_name ASC
                LIMIT ?
                """;

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, safeStart + " 00:00:00");
            ps.setString(2, safeEnd.plusDays(1) + " 00:00:00");
            ps.setInt(3, safeLimit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new SupplierPerformanceRow(
                            rs.getInt("supplier_id"),
                            rs.getString("supplier_name"),
                            rs.getLong("purchase_orders"),
                            rs.getLong("distinct_skus"),
                            rs.getLong("total_units"),
                            round2(rs.getDouble("total_spend")),
                            round2(rs.getDouble("avg_unit_cost")),
                            rs.getString("last_order_date")));
                }
            }
        } catch (SQLException e) {
            System.err.println("PurchaseOrderDAO.getSupplierPerformance: " + e.getMessage());
        }
        return rows;
    }

    /**
     * Creates a purchase order, inserts its items, and immediately updates inventory.
     * Transactional.
     */
    public void receivePurchaseOrder(PurchaseOrder po, List<PurchaseOrderItem> items) throws SQLException {
        if (po == null) {
            throw new SQLException("Purchase order details are required.");
        }
        if (items == null || items.isEmpty()) {
            throw new SQLException("Purchase order must contain at least one item.");
        }

        Connection conn = DatabaseUtil.getConnection();
        String insertPoSql = "INSERT INTO purchase_orders (supplier_id, status, total_amount, notes, created_by_user_id) " +
                             "VALUES (?, 'RECEIVED', ?, ?, ?)";
        String insertItemSql = """
                INSERT INTO purchase_order_items (
                    po_id,
                    medicine_id,
                    medicine_name_snapshot,
                    generic_name_snapshot,
                    company_snapshot,
                    batch_number,
                    expiry_date,
                    purchase_date,
                    ordered_qty,
                    received_qty,
                    unit_cost,
                    selling_price,
                    reorder_threshold
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        conn.setAutoCommit(false);
        try (PreparedStatement psPo = conn.prepareStatement(insertPoSql, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement psItem = conn.prepareStatement(insertItemSql, Statement.RETURN_GENERATED_KEYS)) {

            psPo.setInt(1, po.getSupplierId());
            psPo.setDouble(2, po.getTotalAmount());
            psPo.setString(3, po.getNotes());
            if (po.getCreatedByUserId() > 0) {
                psPo.setInt(4, po.getCreatedByUserId());
            } else {
                psPo.setNull(4, Types.INTEGER);
            }
            psPo.executeUpdate();

            int poId;
            try (ResultSet rsKeys = psPo.getGeneratedKeys()) {
                if (!rsKeys.next()) {
                    throw new SQLException("Failed to retrieve generated PO ID.");
                }
                poId = rsKeys.getInt(1);
                po.setPoId(poId);
            }

            for (PurchaseOrderItem item : items) {
                validateItem(item);
                int medicineId = resolveMedicineId(conn, po.getSupplierId(), item);
                item.setMedicineId(medicineId);
                inventoryBatchDAO.ensureBatchCoverage(conn, medicineId);
                updateMedicineCommercialSnapshot(conn, po.getSupplierId(), item);

                psItem.setInt(1, poId);
                psItem.setInt(2, medicineId);
                psItem.setString(3, safe(item.getMedicineName()));
                psItem.setString(4, safe(item.getGenericName()));
                psItem.setString(5, safe(item.getCompany()));
                psItem.setString(6, safe(item.getBatchNumber()));
                psItem.setString(7, safe(item.getExpiryDate()));
                psItem.setString(8, safe(item.getPurchaseDate()));
                psItem.setInt(9, item.getOrderedQty());
                psItem.setInt(10, item.getReceivedQty());
                psItem.setDouble(11, item.getUnitCost());
                psItem.setDouble(12, item.getSellingPrice());
                psItem.setInt(13, Math.max(1, item.getReorderThreshold()));
                psItem.executeUpdate();
                int poiId;
                try (ResultSet itemKeys = psItem.getGeneratedKeys()) {
                    if (!itemKeys.next()) {
                        throw new SQLException("Failed to retrieve purchase order line ID.");
                    }
                    poiId = itemKeys.getInt(1);
                    item.setPoiId(poiId);
                }

                inventoryBatchDAO.recordPurchaseBatch(
                        conn,
                        medicineId,
                        poiId,
                        item.getBatchNumber(),
                        item.getExpiryDate(),
                        item.getPurchaseDate(),
                        item.getUnitCost(),
                        item.getSellingPrice(),
                        item.getReceivedQty(),
                        po.getSupplierId() > 0 ? po.getSupplierId() : null);
            }

            conn.commit();
            logPurchaseAudit(po, items);
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
            conn.close();
        }
    }

    private PurchaseOrder mapRow(ResultSet rs) throws SQLException {
        PurchaseOrder po = new PurchaseOrder();
        po.setPoId(rs.getInt("po_id"));
        po.setSupplierId(rs.getInt("supplier_id"));
        po.setSupplierName(rs.getString("supplier_name"));
        po.setOrderDate(rs.getString("order_date"));
        po.setExpectedDelivery(rs.getString("expected_delivery"));
        po.setStatus(rs.getString("status"));
        po.setTotalAmount(rs.getDouble("total_amount"));
        po.setNotes(rs.getString("notes"));
        po.setCreatedByUserId(rs.getInt("created_by_user_id"));
        po.setUpdatedAt(rs.getString("updated_at"));
        return po;
    }

    private void validateItem(PurchaseOrderItem item) throws SQLException {
        if (item == null) {
            throw new SQLException("Purchase order line item is missing.");
        }
        if (safe(item.getMedicineName()).isBlank()) {
            throw new SQLException("Medicine name is required for every purchase line.");
        }
        if (safe(item.getCompany()).isBlank()) {
            throw new SQLException("Company / manufacturer is required for every purchase line.");
        }
        if (safe(item.getBatchNumber()).isBlank()) {
            throw new SQLException("Batch / lot number is required for every purchase line.");
        }
        if (safe(item.getPurchaseDate()).isBlank()) {
            throw new SQLException("Buying date is required for every purchase line.");
        }
        if (safe(item.getExpiryDate()).isBlank()) {
            throw new SQLException("Expiry date is required for every purchase line.");
        }
        if (item.getReceivedQty() <= 0 || item.getOrderedQty() <= 0) {
            throw new SQLException("Received quantity must be greater than zero.");
        }
        if (item.getUnitCost() < 0) {
            throw new SQLException("Unit cost cannot be negative.");
        }
        if (item.getSellingPrice() <= 0) {
            throw new SQLException("Selling price must be greater than zero.");
        }
        if (safe(item.getExpiryDate()).compareTo(safe(item.getPurchaseDate())) < 0) {
            throw new SQLException("Expiry date cannot be earlier than buying date.");
        }
    }

    private int resolveMedicineId(Connection conn, int supplierId, PurchaseOrderItem item) throws SQLException {
        if (item.getMedicineId() > 0) {
            return item.getMedicineId();
        }

        int existingId = findExactMedicineId(conn, item.getMedicineName(), item.getCompany());
        if (existingId > 0) {
            return existingId;
        }

        String sql = """
                INSERT INTO medicines (
                    name,
                    generic_name,
                    company,
                    expiry_date,
                    price,
                    purchase_price,
                    reorder_threshold,
                    supplier_id,
                    active
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, safe(item.getMedicineName()));
            ps.setString(2, safe(item.getGenericName()));
            ps.setString(3, safe(item.getCompany()));
            ps.setString(4, safe(item.getExpiryDate()));
            ps.setDouble(5, item.getSellingPrice());
            ps.setDouble(6, item.getUnitCost());
            ps.setInt(7, Math.max(1, item.getReorderThreshold()));
            if (supplierId > 0) {
                ps.setInt(8, supplierId);
            } else {
                ps.setNull(8, Types.INTEGER);
            }
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) {
                    throw new SQLException("Failed to create new medicine from purchase entry.");
                }
                return rs.getInt(1);
            }
        }
    }

    private int findExactMedicineId(Connection conn, String medicineName, String company) throws SQLException {
        String sql = """
                SELECT medicine_id
                FROM medicines
                WHERE LOWER(name) = LOWER(?)
                  AND LOWER(COALESCE(company, '')) = LOWER(?)
                ORDER BY active DESC, medicine_id ASC
                LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, safe(medicineName));
            ps.setString(2, safe(company));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int medicineId = rs.getInt(1);
                    try (PreparedStatement reactivate = conn.prepareStatement(
                            "UPDATE medicines SET active = 1 WHERE medicine_id = ?")) {
                        reactivate.setInt(1, medicineId);
                        reactivate.executeUpdate();
                    }
                    return medicineId;
                }
            }
        }
        return 0;
    }

    private void updateMedicineCommercialSnapshot(Connection conn, int supplierId, PurchaseOrderItem item) throws SQLException {
        String sql = """
                UPDATE medicines
                SET purchase_price = ?,
                    price = ?,
                    expiry_date = CASE
                        WHEN ? = '' THEN expiry_date
                        WHEN expiry_date IS NULL OR TRIM(expiry_date) = '' THEN ?
                        WHEN expiry_date > ? THEN ?
                        ELSE expiry_date
                    END,
                    reorder_threshold = CASE
                        WHEN ? > 0 THEN ?
                        ELSE COALESCE(reorder_threshold, 10)
                    END,
                    generic_name = CASE
                        WHEN (generic_name IS NULL OR TRIM(generic_name) = '') AND ? <> '' THEN ?
                        ELSE generic_name
                    END,
                    supplier_id = CASE
                        WHEN ? > 0 THEN ?
                        ELSE supplier_id
                    END
                WHERE medicine_id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            String expiryDate = safe(item.getExpiryDate());
            String genericName = safe(item.getGenericName());
            int reorderThreshold = Math.max(1, item.getReorderThreshold());

            ps.setDouble(1, item.getUnitCost());
            ps.setDouble(2, item.getSellingPrice());
            ps.setString(3, expiryDate);
            ps.setString(4, expiryDate);
            ps.setString(5, expiryDate);
            ps.setString(6, expiryDate);
            ps.setInt(7, reorderThreshold);
            ps.setInt(8, reorderThreshold);
            ps.setString(9, genericName);
            ps.setString(10, genericName);
            ps.setInt(11, supplierId);
            ps.setInt(12, supplierId);
            ps.setInt(13, item.getMedicineId());
            ps.executeUpdate();
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private void logPurchaseAudit(PurchaseOrder po, List<PurchaseOrderItem> items) {
        try {
            JSONArray lines = new JSONArray();
            for (PurchaseOrderItem item : items) {
                lines.put(new JSONObject()
                        .put("medicineId", item.getMedicineId())
                        .put("medicineName", safe(item.getMedicineName()))
                        .put("batchNumber", safe(item.getBatchNumber()))
                        .put("expiryDate", safe(item.getExpiryDate()))
                        .put("purchaseDate", safe(item.getPurchaseDate()))
                        .put("receivedQty", item.getReceivedQty())
                        .put("unitCost", item.getUnitCost())
                        .put("sellingPrice", item.getSellingPrice()));
            }
            auditLogDAO.logEvent(
                    po.getCreatedByUserId() > 0 ? po.getCreatedByUserId() : null,
                    "PURCHASE_RECEIVED",
                    "PURCHASE_ORDER",
                    po.getPoId(),
                    "Received purchase order #" + po.getPoId(),
                    new JSONObject()
                            .put("supplierId", po.getSupplierId())
                            .put("totalAmount", po.getTotalAmount())
                            .put("notes", safe(po.getNotes()))
                            .put("items", lines)
                            .toString());
        } catch (Exception ignored) {
        }
    }

    public record SupplierPerformanceRow(
            int supplierId,
            String supplierName,
            long purchaseOrders,
            long distinctSkus,
            long totalUnits,
            double totalSpend,
            double averageUnitCost,
            String lastOrderDate) {
    }
}
