package org.example.MediManage.dao;

import org.example.MediManage.DatabaseUtil;
import org.example.MediManage.model.BillItem;
import org.example.MediManage.util.ValidationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object for Bill entities.
 * Handles all database operations related to bills, invoices, and sales
 * reports.
 */
public class BillDAO {

    private static final Logger logger = LoggerFactory.getLogger(BillDAO.class);

    /**
     * Generates an invoice and creates bill records in the database.
     * 
     * @param totalAmount the total bill amount
     * @param items       list of bill items
     * @param customerId  optional customer ID (null for walk-in customers)
     * @param userId      the user creating the bill
     * @param paymentMode payment mode (Cash/Credit/Card)
     * @return the generated bill ID
     * @throws SQLException             if operation fails
     * @throws IllegalArgumentException if validation fails
     */
    public int generateInvoice(double totalAmount, List<BillItem> items, Integer customerId, Integer userId,
            String paymentMode) throws SQLException {

        // Validation
        ValidationUtil.requirePositive(totalAmount, "Total amount");
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Bill must contain at least one item");
        }
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("User ID is required");
        }
        ValidationUtil.requireNonEmpty(paymentMode, "Payment mode");

        Connection conn = null;
        int billId = -1;

        try {
            conn = DatabaseUtil.getConnection();
            conn.setAutoCommit(false);

            // Validate user exists
            String checkUser = "SELECT 1 FROM users WHERE user_id = ?";
            try (PreparedStatement psCheck = conn.prepareStatement(checkUser)) {
                psCheck.setInt(1, userId);
                try (ResultSet rsCheck = psCheck.executeQuery()) {
                    if (!rsCheck.next()) {
                        logger.warn("User ID {} not found, falling back to admin (1)", userId);
                        userId = 1;
                    }
                }
            }

            // Insert bill record
            String billSql = "INSERT INTO bills (total_amount, bill_date, customer_id, user_id) VALUES (?, ?, ?, ?)";
            try (PreparedStatement psBill = conn.prepareStatement(billSql, Statement.RETURN_GENERATED_KEYS)) {
                psBill.setDouble(1, totalAmount);
                String now = java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                psBill.setString(2, now);
                psBill.setObject(3, customerId);
                psBill.setObject(4, userId);
                psBill.executeUpdate();

                ResultSet rs = psBill.getGeneratedKeys();
                if (rs.next()) {
                    billId = rs.getInt(1);
                }
            }

            if (billId == -1) {
                throw new SQLException("Failed to generate bill ID");
            }

            // Insert bill items and update stock
            String itemSql = "INSERT INTO bill_items (bill_id, medicine_id, quantity, price, total) VALUES (?, ?, ?, ?, ?)";
            String stockSql = "UPDATE stock SET quantity = quantity - ? WHERE medicine_id = ?";

            try (PreparedStatement psItem = conn.prepareStatement(itemSql);
                    PreparedStatement psStock = conn.prepareStatement(stockSql)) {

                for (BillItem item : items) {
                    // Validate each item
                    ValidationUtil.requirePositive(item.getMedicineId(), "Medicine ID");
                    ValidationUtil.requirePositive(item.getQty(), "Quantity");
                    ValidationUtil.requirePositive(item.getPrice(), "Price");

                    psItem.setInt(1, billId);
                    psItem.setInt(2, item.getMedicineId());
                    psItem.setInt(3, item.getQty());
                    psItem.setDouble(4, item.getPrice());
                    psItem.setDouble(5, item.getTotal());
                    psItem.addBatch();

                    psStock.setInt(1, item.getQty());
                    psStock.setInt(2, item.getMedicineId());
                    psStock.addBatch();
                }

                psItem.executeBatch();
                psStock.executeBatch();
            }

            // Update customer balance for credit purchases
            if ("Credit".equalsIgnoreCase(paymentMode) && customerId != null) {
                String updateBalanceSql = "UPDATE customers SET current_balance = current_balance + ? WHERE customer_id = ?";
                try (PreparedStatement psUpdate = conn.prepareStatement(updateBalanceSql)) {
                    psUpdate.setDouble(1, totalAmount);
                    psUpdate.setInt(2, customerId);
                    int updated = psUpdate.executeUpdate();
                    if (updated == 0) {
                        logger.warn("Customer ID {} not found for balance update", customerId);
                    }
                }
            }

            conn.commit();
            logger.info("Generated invoice #{} for amount {} (Customer: {}, Payment: {})",
                    billId, totalAmount, customerId != null ? customerId : "Walk-in", paymentMode);

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                    logger.error("Transaction rolled back for invoice generation");
                } catch (SQLException rollbackEx) {
                    logger.error("Failed to rollback transaction", rollbackEx);
                }
            }

            logger.error("Invoice generation failed - Customer: {}, User: {}, Items: {}",
                    customerId, userId, items.size(), e);
            throw new SQLException("Failed to generate invoice: " + e.getMessage(), e);

        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    logger.error("Failed to close connection", e);
                }
            }
        }

        return billId;
    }

    /**
     * Gets total sales for today.
     * 
     * @return today's sales amount
     */
    public double getDailySales() {
        String sql = "SELECT IFNULL(SUM(total_amount), 0) FROM bills WHERE date(bill_date) = date('now', 'localtime')";

        try (Connection conn = DatabaseUtil.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                double sales = rs.getDouble(1);
                logger.debug("Daily sales: {}", sales);
                return sales;
            }
        } catch (SQLException e) {
            logger.error("Failed to retrieve daily sales", e);
            throw new RuntimeException("Failed to retrieve daily sales", e);
        }
        return 0.0;
    }

    /**
     * Retrieves bill history with customer and user information.
     * 
     * @return list of bill history records
     */
    public List<org.example.MediManage.DashboardController.BillHistoryDTO> getBillHistory() {
        List<org.example.MediManage.DashboardController.BillHistoryDTO> history = new java.util.ArrayList<>();
        String sql = "SELECT b.bill_id, b.bill_date, b.total_amount, c.name, c.phone, u.username " +
                "FROM bills b " +
                "LEFT JOIN customers c ON b.customer_id = c.customer_id " +
                "LEFT JOIN users u ON b.user_id = u.user_id " +
                "ORDER BY b.bill_date DESC";

        try (Connection conn = DatabaseUtil.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                history.add(new org.example.MediManage.DashboardController.BillHistoryDTO(
                        rs.getInt("bill_id"),
                        rs.getString("bill_date"),
                        rs.getDouble("total_amount"),
                        rs.getString("name"),
                        rs.getString("phone"),
                        rs.getString("username")));
            }
            logger.debug("Retrieved {} bill history records", history.size());

        } catch (SQLException e) {
            logger.error("Failed to retrieve bill history", e);
            throw new RuntimeException("Failed to retrieve bill history", e);
        }
        return history;
    }

    /**
     * Gets sales data between two dates.
     * 
     * @param start start date
     * @param end   end date
     * @return map of date to sales amount
     */
    public Map<String, Double> getSalesBetweenDates(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Start and end dates are required");
        }
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("Start date must be before or equal to end date");
        }

        Map<String, Double> sales = new LinkedHashMap<>();
        String sql = "SELECT date(bill_date) as day, SUM(total_amount) as total " +
                "FROM bills WHERE date(bill_date) BETWEEN ? AND ? " +
                "GROUP BY day ORDER BY day ASC";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, start.toString());
            ps.setString(2, end.toString());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sales.put(rs.getString("day"), rs.getDouble("total"));
                }
            }
            logger.debug("Retrieved sales for {} days between {} and {}", sales.size(), start, end);

        } catch (SQLException e) {
            logger.error("Failed to retrieve sales between dates {} and {}", start, end, e);
            throw new RuntimeException("Failed to retrieve sales data", e);
        }
        return sales;
    }

    /**
     * Gets bill items with medicine details.
     * 
     * @param billId the bill ID
     * @return list of bill items
     */
    public List<BillItem> getBillItemsExtended(int billId) {
        ValidationUtil.requirePositive(billId, "Bill ID");

        List<BillItem> items = new java.util.ArrayList<>();
        String sql = "SELECT bi.medicine_id, m.name, m.expiry_date, bi.quantity, bi.price, bi.total " +
                "FROM bill_items bi " +
                "LEFT JOIN medicines m ON bi.medicine_id = m.medicine_id " +
                "WHERE bi.bill_id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, billId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int mid = rs.getInt("medicine_id");
                    String name = rs.getString("name");
                    if (name == null) {
                        name = "Deleted Item (" + mid + ")";
                    }
                    String expiry = rs.getString("expiry_date");
                    int qty = rs.getInt("quantity");
                    double price = rs.getDouble("price");
                    double total = rs.getDouble("total");
                    double gst = total - (price * qty);

                    items.add(new BillItem(mid, name, expiry, qty, price, gst));
                }
            }
            logger.debug("Retrieved {} items for bill #{}", items.size(), billId);

        } catch (SQLException e) {
            logger.error("Failed to retrieve bill items for bill #{}", billId, e);
            throw new RuntimeException("Failed to retrieve bill items", e);
        }
        return items;
    }
}
