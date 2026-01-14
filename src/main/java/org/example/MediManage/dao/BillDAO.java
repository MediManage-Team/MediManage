package org.example.MediManage.dao;

import org.example.MediManage.DBUtil;
import org.example.MediManage.DashboardController.BillItem;

import java.sql.*;
import java.util.List;

public class BillDAO {

    public int generateInvoice(double totalAmount, List<BillItem> items, Integer customerId) throws SQLException {
        Connection conn = null;
        int billId = -1;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            // 1. Insert Bill
            String billSql = "INSERT INTO bills (total_amount, bill_date, customer_id) VALUES (?, CURRENT_TIMESTAMP, ?)";
            try (PreparedStatement psBill = conn.prepareStatement(billSql, Statement.RETURN_GENERATED_KEYS)) {
                psBill.setDouble(1, totalAmount);
                psBill.setObject(2, customerId);
                psBill.executeUpdate();
                ResultSet rs = psBill.getGeneratedKeys();
                if (rs.next()) {
                    billId = rs.getInt(1);
                }
            }

            // 2. Insert Items & Update Stock
            String itemSql = "INSERT INTO bill_items (bill_id, medicine_id, quantity, price, total) VALUES (?, ?, ?, ?, ?)";
            String stockSql = "UPDATE stock SET quantity = quantity - ? WHERE medicine_id = ?";

            try (PreparedStatement psItem = conn.prepareStatement(itemSql);
                    PreparedStatement psStock = conn.prepareStatement(stockSql)) {

                for (BillItem item : items) {
                    // Insert Item
                    psItem.setInt(1, billId);
                    psItem.setInt(2, item.getMedicineId());
                    psItem.setInt(3, item.getQty());
                    psItem.setDouble(4, item.getPrice());
                    psItem.setDouble(5, item.getTotal());
                    psItem.addBatch();

                    // Update Stock
                    psStock.setInt(1, item.getQty());
                    psStock.setInt(2, item.getMedicineId());
                    psStock.addBatch();
                }
                psItem.executeBatch();
                psStock.executeBatch();
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
        String sql = "SELECT b.bill_id, b.bill_date, b.total_amount, c.name, c.phone " +
                "FROM bills b " +
                "LEFT JOIN customers c ON b.customer_id = c.customer_id " +
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
                        rs.getString("phone")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return history;
    }
}
