package org.example.MediManage.dao;

import org.example.MediManage.util.DatabaseUtil;
import org.example.MediManage.model.HeldOrder;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for held/layaway orders.
 */
public class HeldOrderDAO {

    /**
     * Save a new held order. Returns the generated hold_id.
     */
    public int holdOrder(Integer customerId, int userId, String itemsJson, double totalAmount, String notes)
            throws SQLException {
        String sql = "INSERT INTO held_orders (customer_id, user_id, items_json, total_amount, notes) VALUES (?,?,?,?,?)";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (customerId != null)
                ps.setInt(1, customerId);
            else
                ps.setNull(1, Types.INTEGER);
            ps.setInt(2, userId);
            ps.setString(3, itemsJson);
            ps.setDouble(4, totalAmount);
            ps.setString(5, notes);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    /**
     * Get all held orders that are still in HELD status.
     */
    public List<HeldOrder> getActiveHeldOrders() throws SQLException {
        String sql = """
                SELECT ho.hold_id, ho.customer_id, ho.user_id, ho.items_json, ho.total_amount,
                       ho.notes, ho.status, ho.held_at, ho.recalled_at,
                       COALESCE(c.name, 'Walk-in') AS customer_name,
                       COALESCE(u.username, '?') AS user_name
                FROM held_orders ho
                LEFT JOIN customers c ON ho.customer_id = c.customer_id
                LEFT JOIN users u ON ho.user_id = u.user_id
                WHERE ho.status = 'HELD'
                ORDER BY ho.held_at DESC
                """;
        List<HeldOrder> results = new ArrayList<>();
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                HeldOrder ho = new HeldOrder();
                ho.setHoldId(rs.getInt("hold_id"));
                ho.setCustomerId(rs.getInt("customer_id"));
                ho.setUserId(rs.getInt("user_id"));
                ho.setItemsJson(rs.getString("items_json"));
                ho.setTotalAmount(rs.getDouble("total_amount"));
                ho.setNotes(rs.getString("notes"));
                ho.setStatus(rs.getString("status"));
                ho.setHeldAt(rs.getString("held_at"));
                ho.setRecalledAt(rs.getString("recalled_at"));
                ho.setCustomerName(rs.getString("customer_name"));
                ho.setUserName(rs.getString("user_name"));
                results.add(ho);
            }
        }
        return results;
    }

    /**
     * Recall a held order — marks it as RECALLED with timestamp.
     */
    public boolean recallOrder(int holdId) throws SQLException {
        String sql = "UPDATE held_orders SET status = 'RECALLED', recalled_at = CURRENT_TIMESTAMP WHERE hold_id = ? AND status = 'HELD'";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, holdId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Cancel a held order.
     */
    public boolean cancelOrder(int holdId) throws SQLException {
        String sql = "UPDATE held_orders SET status = 'CANCELLED' WHERE hold_id = ? AND status = 'HELD'";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, holdId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Count active held orders.
     */
    public int countActiveHeldOrders() throws SQLException {
        String sql = "SELECT COUNT(*) FROM held_orders WHERE status = 'HELD'";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
