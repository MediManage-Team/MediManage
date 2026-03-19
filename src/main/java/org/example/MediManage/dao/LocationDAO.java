package org.example.MediManage.dao;

import org.example.MediManage.util.DatabaseUtil;
import org.example.MediManage.model.Location;
import org.example.MediManage.model.LocationStockRow;
import org.example.MediManage.model.LocationTransferRow;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for multi-location management — locations, stock by location, and
 * transfers.
 */
public class LocationDAO {

    // ────── Location CRUD ──────

    public List<Location> getActiveLocations() throws SQLException {
        List<Location> list = new ArrayList<>();
        String sql = "SELECT * FROM locations WHERE active = 1 ORDER BY name";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next())
                list.add(mapLocation(rs));
        }
        return list;
    }

    public int addLocation(Location loc) throws SQLException {
        String sql = "INSERT INTO locations (name, address, phone, location_type) VALUES (?,?,?,?)";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, loc.getName());
            ps.setString(2, loc.getAddress());
            ps.setString(3, loc.getPhone());
            ps.setString(4, loc.getLocationType());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    public void updateLocation(Location loc) throws SQLException {
        String sql = "UPDATE locations SET name=?, address=?, phone=?, location_type=? WHERE location_id=?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, loc.getName());
            ps.setString(2, loc.getAddress());
            ps.setString(3, loc.getPhone());
            ps.setString(4, loc.getLocationType());
            ps.setInt(5, loc.getLocationId());
            ps.executeUpdate();
        }
    }

    // ────── Location Stock ──────

    public int getStockAtLocation(int locationId, int medicineId) throws SQLException {
        String sql = "SELECT quantity FROM location_stock WHERE location_id=? AND medicine_id=?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, locationId);
            ps.setInt(2, medicineId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("quantity") : 0;
            }
        }
    }

    public void setStockAtLocation(int locationId, int medicineId, int quantity) throws SQLException {
        String sql = "INSERT INTO location_stock (location_id, medicine_id, quantity) VALUES (?, ?, ?) " +
                "ON CONFLICT(location_id, medicine_id) DO UPDATE SET quantity = ?, updated_at = CURRENT_TIMESTAMP";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, locationId);
            ps.setInt(2, medicineId);
            ps.setInt(3, quantity);
            ps.setInt(4, quantity);
            ps.executeUpdate();
        }
    }

    public List<LocationStockRow> getStockRowsForLocation(int locationId) throws SQLException {
        List<LocationStockRow> rows = new ArrayList<>();
        String sql = "SELECT ls.location_stock_id, ls.medicine_id, ls.quantity, ls.min_stock, ls.updated_at, " +
                "m.name AS medicine_name, COALESCE(m.generic_name, '') AS generic_name, COALESCE(m.company, '') AS company " +
                "FROM location_stock ls " +
                "JOIN medicines m ON m.medicine_id = ls.medicine_id " +
                "WHERE ls.location_id = ? AND m.active = 1 " +
                "ORDER BY m.name ASC";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new LocationStockRow(
                            rs.getInt("location_stock_id"),
                            rs.getInt("medicine_id"),
                            rs.getString("medicine_name"),
                            rs.getString("generic_name"),
                            rs.getString("company"),
                            rs.getInt("quantity"),
                            rs.getInt("min_stock"),
                            rs.getString("updated_at")));
                }
            }
        }
        return rows;
    }

    public int sumAllocatedStock(int medicineId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(quantity), 0) FROM location_stock WHERE medicine_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, medicineId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // ────── Stock Transfers ──────

    public int createTransfer(int fromLocationId, int toLocationId, int medicineId, int quantity, int requestedBy)
            throws SQLException {
        String sql = "INSERT INTO stock_transfers (from_location_id, to_location_id, medicine_id, quantity, requested_by) VALUES (?,?,?,?,?)";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, fromLocationId);
            ps.setInt(2, toLocationId);
            ps.setInt(3, medicineId);
            ps.setInt(4, quantity);
            ps.setInt(5, requestedBy);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    public boolean completeTransfer(int transferId) throws SQLException {
        Connection conn = DatabaseUtil.getConnection();
        try {
            conn.setAutoCommit(false);

            // Get transfer details
            String selectSql = "SELECT * FROM stock_transfers WHERE transfer_id=? AND status='PENDING'";
            int fromId, toId, medId, qty;
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setInt(1, transferId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return false;
                    }
                    fromId = rs.getInt("from_location_id");
                    toId = rs.getInt("to_location_id");
                    medId = rs.getInt("medicine_id");
                    qty = rs.getInt("quantity");
                }
            }

            // Decrease stock at source
            String decSql = "UPDATE location_stock SET quantity = quantity - ?, updated_at = CURRENT_TIMESTAMP " +
                    "WHERE location_id=? AND medicine_id=? AND quantity >= ?";
            try (PreparedStatement ps = conn.prepareStatement(decSql)) {
                ps.setInt(1, qty);
                ps.setInt(2, fromId);
                ps.setInt(3, medId);
                ps.setInt(4, qty);
                if (ps.executeUpdate() == 0) {
                    conn.rollback();
                    return false;
                }
            }

            // Increase stock at destination (upsert)
            String incSql = "INSERT INTO location_stock (location_id, medicine_id, quantity) VALUES (?, ?, ?) " +
                    "ON CONFLICT(location_id, medicine_id) DO UPDATE SET quantity = quantity + ?, updated_at = CURRENT_TIMESTAMP";
            try (PreparedStatement ps = conn.prepareStatement(incSql)) {
                ps.setInt(1, toId);
                ps.setInt(2, medId);
                ps.setInt(3, qty);
                ps.setInt(4, qty);
                ps.executeUpdate();
            }

            // Mark transfer completed
            String updateSql = "UPDATE stock_transfers SET status='COMPLETED', completed_at=CURRENT_TIMESTAMP WHERE transfer_id=?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setInt(1, transferId);
                ps.executeUpdate();
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
            conn.close();
        }
    }

    public List<LocationTransferRow> getRecentTransfers(int limit) throws SQLException {
        List<LocationTransferRow> rows = new ArrayList<>();
        String sql = "SELECT st.transfer_id, st.medicine_id, st.quantity, st.status, st.requested_at, st.completed_at, " +
                "COALESCE(m.name, '') AS medicine_name, " +
                "COALESCE(fl.name, '') AS from_location_name, " +
                "COALESCE(tl.name, '') AS to_location_name, " +
                "COALESCE(u.username, '') AS requested_by_username " +
                "FROM stock_transfers st " +
                "JOIN medicines m ON m.medicine_id = st.medicine_id " +
                "JOIN locations fl ON fl.location_id = st.from_location_id " +
                "JOIN locations tl ON tl.location_id = st.to_location_id " +
                "LEFT JOIN users u ON u.user_id = st.requested_by " +
                "ORDER BY st.requested_at DESC LIMIT ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new LocationTransferRow(
                            rs.getInt("transfer_id"),
                            rs.getInt("medicine_id"),
                            rs.getString("medicine_name"),
                            rs.getString("from_location_name"),
                            rs.getString("to_location_name"),
                            rs.getInt("quantity"),
                            rs.getString("status"),
                            rs.getString("requested_at"),
                            rs.getString("completed_at"),
                            rs.getString("requested_by_username")));
                }
            }
        }
        return rows;
    }

    private Location mapLocation(ResultSet rs) throws SQLException {
        Location loc = new Location();
        loc.setLocationId(rs.getInt("location_id"));
        loc.setName(rs.getString("name"));
        loc.setAddress(rs.getString("address"));
        loc.setPhone(rs.getString("phone"));
        loc.setLocationType(rs.getString("location_type"));
        loc.setActive(rs.getInt("active") == 1);
        return loc;
    }
}
