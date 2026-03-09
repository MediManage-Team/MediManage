package org.example.MediManage.dao;

import org.example.MediManage.DatabaseUtil;
import org.example.MediManage.model.PurchaseOrder;
import org.example.MediManage.model.PurchaseOrderItem;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PurchaseOrderDAO {

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
        String sql = "SELECT poi.*, m.name as medicine_name, m.company " +
                     "FROM purchase_order_items poi " +
                     "JOIN medicines m ON poi.medicine_id = m.medicine_id " +
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
                    item.setCompany(rs.getString("company"));
                    item.setOrderedQty(rs.getInt("ordered_qty"));
                    item.setReceivedQty(rs.getInt("received_qty"));
                    item.setUnitCost(rs.getDouble("unit_cost"));
                    list.add(item);
                }
            }
        }
        return list;
    }

    /**
     * Creates a purchase order, inserts its items, and immediately updates inventory.
     * Transactional.
     */
    public void receivePurchaseOrder(PurchaseOrder po, List<PurchaseOrderItem> items) throws SQLException {
        Connection conn = DatabaseUtil.getConnection();
        conn.setAutoCommit(false); // Start transaction

        String insertPoSql = "INSERT INTO purchase_orders (supplier_id, status, total_amount, notes, created_by_user_id) " +
                             "VALUES (?, 'RECEIVED', ?, ?, ?)";
                             
        String insertItemSql = "INSERT INTO purchase_order_items (po_id, medicine_id, ordered_qty, received_qty, unit_cost) " +
                               "VALUES (?, ?, ?, ?, ?)";
                               
        String getStockSql = "SELECT stock_id, quantity FROM stock WHERE medicine_id = ?";
        String insertStockSql = "INSERT INTO stock (medicine_id, quantity) VALUES (?, ?)";
        String updateStockSql = "UPDATE stock SET quantity = quantity + ? WHERE medicine_id = ?";

        try (PreparedStatement psPo = conn.prepareStatement(insertPoSql, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement psItem = conn.prepareStatement(insertItemSql);
             PreparedStatement psGetStock = conn.prepareStatement(getStockSql);
             PreparedStatement psInsertStock = conn.prepareStatement(insertStockSql);
             PreparedStatement psUpdateStock = conn.prepareStatement(updateStockSql)) {

            // 1. Insert Purchase Order
            psPo.setInt(1, po.getSupplierId());
            psPo.setDouble(2, po.getTotalAmount());
            psPo.setString(3, po.getNotes());
            if (po.getCreatedByUserId() > 0) {
                psPo.setInt(4, po.getCreatedByUserId());
            } else {
                psPo.setNull(4, Types.INTEGER); // or whatever default
            }
            psPo.executeUpdate();

            int poId;
            try (ResultSet rsKeys = psPo.getGeneratedKeys()) {
                if (rsKeys.next()) {
                    poId = rsKeys.getInt(1);
                    po.setPoId(poId);
                } else {
                    throw new SQLException("Failed to retrieve generated PO ID.");
                }
            }

            // 2. Insert Items and Update Stock
            for (PurchaseOrderItem item : items) {
                // Insert PO Item
                psItem.setInt(1, poId);
                psItem.setInt(2, item.getMedicineId());
                psItem.setInt(3, item.getOrderedQty());
                psItem.setInt(4, item.getReceivedQty()); // Usually matches ordered in a direct receive
                psItem.setDouble(5, item.getUnitCost());
                psItem.addBatch();

                // Check Current Stock
                psGetStock.setInt(1, item.getMedicineId());
                boolean stockExists;
                try (ResultSet rsStock = psGetStock.executeQuery()) {
                    stockExists = rsStock.next();
                }

                if (stockExists) {
                    // Update Stock
                    psUpdateStock.setInt(1, item.getReceivedQty());
                    psUpdateStock.setInt(2, item.getMedicineId());
                    psUpdateStock.addBatch();
                } else {
                    // Insert Stock (should be rare if inventory already manages all meds)
                    psInsertStock.setInt(1, item.getMedicineId());
                    psInsertStock.setInt(2, item.getReceivedQty());
                    psInsertStock.addBatch();
                }
            }
            
            psItem.executeBatch();
            psUpdateStock.executeBatch();
            psInsertStock.executeBatch();

            conn.commit();
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
}
