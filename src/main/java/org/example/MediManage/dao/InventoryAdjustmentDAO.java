package org.example.MediManage.dao;

import org.example.MediManage.model.InventoryAdjustment;
import org.example.MediManage.util.DatabaseUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class InventoryAdjustmentDAO {
    private final InventoryBatchDAO inventoryBatchDAO = new InventoryBatchDAO();

    public void recordAdjustment(
            int medicineId,
            String adjustmentType,
            int quantity,
            double unitPrice,
            String rootCauseTag,
            String notes,
            Integer createdByUserId) throws SQLException {
        String normalizedType = normalizeType(adjustmentType);
        if (medicineId <= 0) {
            throw new SQLException("A valid medicine must be selected.");
        }
        if (quantity <= 0) {
            throw new SQLException("Adjustment quantity must be greater than zero.");
        }
        if (unitPrice < 0) {
            throw new SQLException("Unit price cannot be negative.");
        }

        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int currentStock = getCurrentStock(conn, medicineId);
                if (currentStock < quantity) {
                    throw new SQLException("Only " + currentStock + " unit(s) are currently in stock.");
                }

                int adjustmentId;
                try (PreparedStatement insert = conn.prepareStatement("""
                        INSERT INTO inventory_adjustments (
                            medicine_id,
                            adjustment_type,
                            quantity,
                            unit_price,
                            root_cause_tag,
                            notes,
                            created_by_user_id
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, PreparedStatement.RETURN_GENERATED_KEYS)) {
                    insert.setInt(1, medicineId);
                    insert.setString(2, normalizedType);
                    insert.setInt(3, quantity);
                    insert.setDouble(4, unitPrice);
                    insert.setString(5, normalizeNullable(rootCauseTag));
                    insert.setString(6, normalizeNullable(notes));
                    if (createdByUserId != null && createdByUserId > 0) {
                        insert.setInt(7, createdByUserId);
                    } else {
                        insert.setNull(7, Types.INTEGER);
                    }
                    insert.executeUpdate();
                    try (ResultSet generatedKeys = insert.getGeneratedKeys()) {
                        if (!generatedKeys.next()) {
                            throw new SQLException("Failed to create stock adjustment record.");
                        }
                        adjustmentId = generatedKeys.getInt(1);
                    }
                }

                java.util.List<InventoryBatchDAO.BatchAllocation> allocations =
                        inventoryBatchDAO.consumeForStockReduction(conn, medicineId, quantity);
                if (allocations.size() == 1 && allocations.get(0).batchId() != null) {
                    try (PreparedStatement updateAdjustment = conn.prepareStatement(
                            "UPDATE inventory_adjustments SET batch_id = ? WHERE adjustment_id = ?")) {
                        updateAdjustment.setInt(1, allocations.get(0).batchId());
                        updateAdjustment.setInt(2, adjustmentId);
                        updateAdjustment.executeUpdate();
                    }
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public List<InventoryAdjustment> getRecentAdjustments(int limit) throws SQLException {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        List<InventoryAdjustment> adjustments = new ArrayList<>();
        String sql = """
                SELECT ia.adjustment_id,
                       ia.medicine_id,
                       ia.adjustment_type,
                       ia.quantity,
                       ia.unit_price,
                       ia.root_cause_tag,
                       ia.notes,
                       ia.occurred_at,
                       COALESCE(m.name, 'Unknown Medicine') AS medicine_name,
                       COALESCE(m.company, '') AS company,
                       COALESCE(u.username, 'System') AS created_by_username
                FROM inventory_adjustments ia
                LEFT JOIN medicines m ON m.medicine_id = ia.medicine_id
                LEFT JOIN users u ON u.user_id = ia.created_by_user_id
                ORDER BY ia.occurred_at DESC, ia.adjustment_id DESC
                LIMIT ?
                """;
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, safeLimit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    InventoryAdjustment adjustment = new InventoryAdjustment();
                    adjustment.setAdjustmentId(rs.getInt("adjustment_id"));
                    adjustment.setMedicineId(rs.getInt("medicine_id"));
                    adjustment.setAdjustmentType(rs.getString("adjustment_type"));
                    adjustment.setQuantity(rs.getInt("quantity"));
                    adjustment.setUnitPrice(rs.getDouble("unit_price"));
                    adjustment.setRootCauseTag(rs.getString("root_cause_tag"));
                    adjustment.setNotes(rs.getString("notes"));
                    adjustment.setOccurredAt(rs.getString("occurred_at"));
                    adjustment.setMedicineName(rs.getString("medicine_name"));
                    adjustment.setCompany(rs.getString("company"));
                    adjustment.setCreatedByUsername(rs.getString("created_by_username"));
                    adjustments.add(adjustment);
                }
            }
        }
        return adjustments;
    }

    private int getCurrentStock(Connection conn, int medicineId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT quantity FROM stock WHERE medicine_id = ?")) {
            ps.setInt(1, medicineId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private String normalizeType(String adjustmentType) throws SQLException {
        String normalized = adjustmentType == null ? "" : adjustmentType.trim().toUpperCase(java.util.Locale.ROOT);
        if ("DUMP".equals(normalized) || "EXPIRED_DUMP".equals(normalized) || "WASTE".equals(normalized)) {
            return "DAMAGED";
        }
        if (!"RETURN".equals(normalized) && !"DAMAGED".equals(normalized)) {
            throw new SQLException("Adjustment type must be RETURN, DAMAGED, or DUMP.");
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
