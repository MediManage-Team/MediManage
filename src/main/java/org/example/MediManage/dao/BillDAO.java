package org.example.MediManage.dao;

import org.example.MediManage.DatabaseUtil;
import org.example.MediManage.model.BillItem;
import org.example.MediManage.model.BillHistoryRecord;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BillDAO {
    private static final int DEFAULT_HISTORY_LIMIT = 50;
    private static final int MAX_HISTORY_LIMIT = 500;

    public int generateInvoice(double totalAmount, List<BillItem> items, Integer customerId, Integer userId,
            String paymentMode)
            throws SQLException {
        return generateInvoice(totalAmount, items, customerId, userId, paymentMode, null);
    }

    public int generateInvoice(
            double totalAmount,
            List<BillItem> items,
            Integer customerId,
            Integer userId,
            String paymentMode,
            SubscriptionInvoiceContext subscriptionContext)
            throws SQLException {
        Connection conn = null;
        int billId = -1;
        try {
            conn = DatabaseUtil.getConnection();
            conn.setAutoCommit(false);

            String checkUser = "SELECT 1 FROM users WHERE user_id = ?";
            try (PreparedStatement psCheck = conn.prepareStatement(checkUser)) {
                psCheck.setInt(1, userId);
                try (ResultSet rsCheck = psCheck.executeQuery()) {
                    if (!rsCheck.next()) {
                        System.err.println("⚠️ Warning: User ID " + userId + " not found. Fallback to Admin (1).");
                        userId = 1;
                    }
                }
            }

            String billSql = "INSERT INTO bills (" +
                    "total_amount, bill_date, customer_id, user_id, payment_mode, " +
                    "subscription_enrollment_id, subscription_plan_id, subscription_discount_percent, " +
                    "subscription_savings_amount, subscription_approval_reference" +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement psBill = conn.prepareStatement(billSql, Statement.RETURN_GENERATED_KEYS)) {
                psBill.setDouble(1, totalAmount);
                String now = java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                psBill.setString(2, now);
                psBill.setObject(3, customerId); // setInt can't handle null, setObject can
                psBill.setObject(4, userId);
                psBill.setString(5, paymentMode != null ? paymentMode : "CASH");
                psBill.setObject(6, subscriptionContext != null ? subscriptionContext.subscriptionEnrollmentId() : null);
                psBill.setObject(7, subscriptionContext != null ? subscriptionContext.subscriptionPlanId() : null);
                psBill.setDouble(8, subscriptionContext != null ? subscriptionContext.subscriptionDiscountPercent() : 0.0);
                psBill.setDouble(9, subscriptionContext != null ? subscriptionContext.subscriptionSavingsAmount() : 0.0);
                psBill.setString(10, subscriptionContext != null ? subscriptionContext.subscriptionApprovalReference() : null);
                psBill.executeUpdate();
                ResultSet rs = psBill.getGeneratedKeys();
                if (rs.next()) {
                    billId = rs.getInt(1);
                }
            }

            String itemSql = "INSERT INTO bill_items (" +
                    "bill_id, medicine_id, quantity, price, total, " +
                    "subscription_discount_percent, subscription_discount_amount, subscription_rule_source" +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            String stockSql = "UPDATE stock SET quantity = quantity - ? WHERE medicine_id = ?";

            try (PreparedStatement psItem = conn.prepareStatement(itemSql);
                    PreparedStatement psStock = conn.prepareStatement(stockSql)) {

                for (BillItem item : items) {
                    psItem.setInt(1, billId);
                    psItem.setInt(2, item.getMedicineId());
                    psItem.setInt(3, item.getQty());
                    psItem.setDouble(4, item.getPrice());
                    psItem.setDouble(5, item.getTotal());
                    psItem.setDouble(6, item.getSubscriptionDiscountPercent());
                    psItem.setDouble(7, item.getSubscriptionDiscountAmount());
                    psItem.setString(8, item.getSubscriptionRuleSource());
                    psItem.addBatch();

                    psStock.setInt(1, item.getQty());
                    psStock.setInt(2, item.getMedicineId());
                    psStock.addBatch();
                }
                psItem.executeBatch();
                psStock.executeBatch();
            }

            if ("Credit".equalsIgnoreCase(paymentMode) && customerId != null) {
                // Update Customer Balance
                // Execute directly on 'conn' to avoid SQLITE_BUSY (Database Locked)
                String updateBalanceSql = "UPDATE customers SET current_balance = current_balance + ? WHERE customer_id = ?";
                try (PreparedStatement psUpdate = conn.prepareStatement(updateBalanceSql)) {
                    psUpdate.setDouble(1, totalAmount);
                    psUpdate.setInt(2, customerId);
                    psUpdate.executeUpdate();
                }
            }

            conn.commit();
        } catch (SQLException e) {
            if (conn != null)
                conn.rollback();
            String debugMsg = "Invoice Generation Failed.\n" +
                    "Debug Info:\n" +
                    "Customer ID: " + customerId + "\n" +
                    "User ID: " + userId + "\n" +
                    "Items: " + items.size() + "\n" +
                    "First Item Medicine ID: " + (items.isEmpty() ? "N/A" : items.get(0).getMedicineId());
            System.err.println(debugMsg);
            throw new SQLException(debugMsg + "\nOriginal Error: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
        return billId;
    }

    public record SubscriptionInvoiceContext(
            Integer subscriptionEnrollmentId,
            Integer subscriptionPlanId,
            double subscriptionDiscountPercent,
            double subscriptionSavingsAmount,
            String subscriptionApprovalReference) {
    }

    public double getDailySales() {
        LocalDate today = LocalDate.now();
        String start = today.atStartOfDay().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String end = today.plusDays(1).atStartOfDay()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String sql = "SELECT COALESCE(SUM(total_amount), 0) FROM bills " +
                "WHERE bill_date >= ? AND bill_date < ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, start);
            stmt.setString(2, end);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    public List<BillHistoryRecord> getBillHistory() {
        return getBillHistoryPage(0, DEFAULT_HISTORY_LIMIT);
    }

    public List<BillHistoryRecord> getBillHistoryPage(int offset, int limit) {
        List<BillHistoryRecord> history = new ArrayList<>();
        int safeOffset = Math.max(0, offset);
        int safeLimit = normalizeHistoryLimit(limit);
        String sql = "SELECT b.bill_id, b.bill_date, b.total_amount, c.name, c.phone, u.username " +
                "FROM bills b " +
                "LEFT JOIN customers c ON b.customer_id = c.customer_id " +
                "LEFT JOIN users u ON b.user_id = u.user_id " +
                "ORDER BY b.bill_date DESC LIMIT ? OFFSET ?";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, safeLimit);
            stmt.setInt(2, safeOffset);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    history.add(new BillHistoryRecord(
                            rs.getInt("bill_id"),
                            rs.getString("bill_date"),
                            rs.getDouble("total_amount"),
                            rs.getString("name"),
                            rs.getString("phone"),
                            rs.getString("username")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return history;
    }

    public int countBillHistory() {
        String sql = "SELECT COUNT(*) FROM bills";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // New Method for Reports
    public Map<String, Double> getSalesBetweenDates(LocalDate start, LocalDate end) {
        Map<String, Double> sales = new LinkedHashMap<>();
        // Filter via indexed bill_date range, then aggregate by day.
        String sql = "SELECT DATE(bill_date) as day, SUM(total_amount) as total " +
                "FROM bills WHERE bill_date >= ? AND bill_date < ? " +
                "GROUP BY day ORDER BY day ASC";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, start + " 00:00:00");
            ps.setString(2, end.plusDays(1) + " 00:00:00");

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sales.put(rs.getString("day"), rs.getDouble("total"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return sales;
    }

    public List<BillItem> getBillItemsExtended(int billId) {
        List<BillItem> items = new java.util.ArrayList<>();
        String sql = "SELECT bi.medicine_id, m.name, m.expiry_date, bi.quantity, bi.price, bi.total FROM bill_items bi LEFT JOIN medicines m ON bi.medicine_id = m.medicine_id WHERE bi.bill_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, billId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int mid = rs.getInt("medicine_id");
                    String name = rs.getString("name");
                    if (name == null)
                        name = "Deleted Item (" + mid + ")";
                    String expiry = rs.getString("expiry_date");
                    int qty = rs.getInt("quantity");
                    double price = rs.getDouble("price");
                    double total = rs.getDouble("total");
                    double gst = total - (price * qty);
                    items.add(new BillItem(mid, name, expiry, qty, price, gst));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return items;
    }

    // For Business Intelligence: Sales by Item (Medicine)
    public Map<String, Integer> getItemizedSales(LocalDate start, LocalDate end) {
        Map<String, Integer> sales = new LinkedHashMap<>();
        String sql = "SELECT m.name, SUM(bi.quantity) as total_qty " +
                "FROM bills b " +
                "JOIN bill_items bi ON bi.bill_id = b.bill_id " +
                "JOIN medicines m ON bi.medicine_id = m.medicine_id " +
                "WHERE b.bill_date >= ? AND b.bill_date < ? " +
                "GROUP BY m.name " +
                "ORDER BY total_qty DESC";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, start + " 00:00:00");
            ps.setString(2, end.plusDays(1) + " 00:00:00");

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sales.put(rs.getString("name"), rs.getInt("total_qty"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return sales;
    }

    // ======================== AI CARE PROTOCOL ========================

    /**
     * Save AI-generated care protocol for a bill.
     */
    public void saveAICareProtocol(int billId, String protocol) {
        String sql = "UPDATE bills SET ai_care_protocol = ? WHERE bill_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, protocol);
            ps.setInt(2, billId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to save AI care protocol for bill " + billId + ": " + e.getMessage());
        }
    }

    /**
     * Retrieve AI care protocol for a bill.
     */
    public String getAICareProtocol(int billId) {
        String sql = "SELECT ai_care_protocol FROM bills WHERE bill_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, billId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("ai_care_protocol");
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to load AI care protocol for bill " + billId + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Get payment mode for a bill.
     */
    public String getPaymentMode(int billId) {
        String sql = "SELECT payment_mode FROM bills WHERE bill_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, billId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("payment_mode");
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to get payment mode for bill " + billId + ": " + e.getMessage());
        }
        return "CASH";
    }

    private int normalizeHistoryLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_HISTORY_LIMIT;
        }
        return Math.min(limit, MAX_HISTORY_LIMIT);
    }
}
