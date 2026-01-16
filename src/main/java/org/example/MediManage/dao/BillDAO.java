package org.example.MediManage.dao;

import org.example.MediManage.DBUtil;
import org.example.MediManage.model.BillItem;

import java.sql.*;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BillDAO {

    public int generateInvoice(double totalAmount, List<BillItem> items, Integer customerId, Integer userId,
            String paymentMode)
            throws SQLException {
        Connection conn = null;
        int billId = -1;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            String billSql = "INSERT INTO bills (total_amount, bill_date, customer_id, user_id) VALUES (?, ?, ?, ?)";
            try (PreparedStatement psBill = conn.prepareStatement(billSql, Statement.RETURN_GENERATED_KEYS)) {
                psBill.setDouble(1, totalAmount);
                String now = java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                psBill.setString(2, now);
                psBill.setObject(3, customerId); // setInt can't handle null, setObject can
                psBill.setObject(4, userId);
                psBill.executeUpdate();
                ResultSet rs = psBill.getGeneratedKeys();
                if (rs.next()) {
                    billId = rs.getInt(1);
                }
            }

            String itemSql = "INSERT INTO bill_items (bill_id, medicine_id, quantity, price, total) VALUES (?, ?, ?, ?, ?)";
            String stockSql = "UPDATE stock SET quantity = quantity - ? WHERE medicine_id = ?";

            try (PreparedStatement psItem = conn.prepareStatement(itemSql);
                    PreparedStatement psStock = conn.prepareStatement(stockSql)) {

                for (BillItem item : items) {
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

            if ("Credit".equalsIgnoreCase(paymentMode) && customerId != null) {
                // Update Customer Balance
                org.example.MediManage.dao.CustomerDAO customerDAO = new org.example.MediManage.dao.CustomerDAO(); // Create
                                                                                                                   // instance
                                                                                                                   // or
                                                                                                                   // dependency
                                                                                                                   // inject
                customerDAO.updateBalance(customerId, totalAmount);
            }

            conn.commit();
        } catch (SQLException e) {
            if (conn != null)
                conn.rollback();
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
        return billId;
    }

    public double getDailySales() {
        // SQLite: compare stored local date (string) with calculated local date of now
        // stored: "2023-10-27 14:00:00" -> date() -> "2023-10-27"
        // now: date('now', 'localtime') -> "2023-10-27" (if running locally)
        String sql = "SELECT IFNULL(SUM(total_amount), 0) FROM bills WHERE date(bill_date) = date('now', 'localtime')";
        try (Connection conn = DBUtil.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getDouble(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    public java.util.List<org.example.MediManage.DashboardController.BillHistoryDTO> getBillHistory() {
        java.util.List<org.example.MediManage.DashboardController.BillHistoryDTO> history = new java.util.ArrayList<>();
        String sql = "SELECT b.bill_id, b.bill_date, b.total_amount, c.name, c.phone, u.username " +
                "FROM bills b " +
                "LEFT JOIN customers c ON b.customer_id = c.customer_id " +
                "LEFT JOIN users u ON b.user_id = u.user_id " +
                "ORDER BY b.bill_date DESC";

        try (Connection conn = DBUtil.getConnection();
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
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return history;
    }

    // New Method for Reports
    public Map<String, Double> getSalesBetweenDates(LocalDate start, LocalDate end) {
        Map<String, Double> sales = new LinkedHashMap<>();
        // SQLite: group by the date part of the timestamp string
        // Assuming format yyyy-MM-dd HH:mm:ss, date() extracts yyyy-MM-dd
        String sql = "SELECT date(bill_date) as day, SUM(total_amount) as total " +
                "FROM bills WHERE date(bill_date) BETWEEN ? AND ? " +
                "GROUP BY day ORDER BY day ASC";

        try (Connection conn = DBUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, start.toString());
            ps.setString(2, end.toString());

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
        try (Connection conn = DBUtil.getConnection();
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
}
