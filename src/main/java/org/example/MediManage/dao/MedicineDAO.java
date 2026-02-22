package org.example.MediManage.dao;

import org.example.MediManage.DatabaseUtil;
import org.example.MediManage.model.Medicine;
import org.example.MediManage.model.UserRole;
import org.example.MediManage.util.UserSession;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class MedicineDAO {
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;

    private void checkManagerPermission() {
        if (!UserSession.getInstance().isLoggedIn() ||
                !(UserSession.getInstance().getUser().getRole() == UserRole.ADMIN ||
                        UserSession.getInstance().getUser().getRole() == UserRole.MANAGER)) {
            throw new SecurityException("Access Denied: Only ADMIN or MANAGER can perform this action.");
        }
    }

    public List<Medicine> getAllMedicines() {
        List<Medicine> list = new ArrayList<>();
        String sql = "SELECT m.medicine_id, m.name, m.generic_name, m.company, m.expiry_date, m.price, s.quantity " +
                "FROM medicines m " +
                "LEFT JOIN stock s ON m.medicine_id = s.medicine_id " +
                "WHERE m.active = 1 " +
                "ORDER BY m.name ASC";

        try (Connection conn = DatabaseUtil.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                list.add(new Medicine(
                        rs.getInt("medicine_id"),
                        rs.getString("name"),
                        rs.getString("generic_name"),
                        rs.getString("company"),
                        rs.getString("expiry_date"),
                        rs.getInt("quantity"),
                        rs.getDouble("price")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Paginated active medicines list.
     */
    public List<Medicine> getMedicinesPage(int offset, int limit) {
        List<Medicine> list = new ArrayList<>();
        int safeOffset = Math.max(0, offset);
        int safeLimit = normalizeLimit(limit);

        String sql = "SELECT m.medicine_id, m.name, m.generic_name, m.company, m.expiry_date, m.price, s.quantity " +
                "FROM medicines m " +
                "LEFT JOIN stock s ON m.medicine_id = s.medicine_id " +
                "WHERE m.active = 1 " +
                "ORDER BY m.name ASC LIMIT ? OFFSET ?";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, safeLimit);
            pstmt.setInt(2, safeOffset);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new Medicine(
                            rs.getInt("medicine_id"),
                            rs.getString("name"),
                            rs.getString("generic_name"),
                            rs.getString("company"),
                            rs.getString("expiry_date"),
                            rs.getInt("quantity"),
                            rs.getDouble("price")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public int countActiveMedicines() {
        String sql = "SELECT COUNT(*) FROM medicines WHERE active = 1";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql);
                ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public void addMedicine(String name, String genericName, String company, String expiry, double price,
            int initialStock) {
        checkManagerPermission();
        String insertMed = "INSERT INTO medicines(name, generic_name, company, expiry_date, price) VALUES(?, ?, ?, ?, ?)";
        String insertStock = "INSERT INTO stock(medicine_id, quantity) VALUES(?, ?)";

        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement psMed = conn.prepareStatement(insertMed, Statement.RETURN_GENERATED_KEYS)) {
                psMed.setString(1, name);
                psMed.setString(2, genericName);
                psMed.setString(3, company);
                psMed.setString(4, expiry);
                psMed.setDouble(5, price);
                psMed.executeUpdate();

                ResultSet rs = psMed.getGeneratedKeys();
                int medId = 0;
                if (rs.next()) {
                    medId = rs.getInt(1);
                }

                if (medId > 0) {
                    try (PreparedStatement psStock = conn.prepareStatement(insertStock)) {
                        psStock.setInt(1, medId);
                        psStock.setInt(2, initialStock);
                        psStock.executeUpdate();
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Update Medicine (Name, Generic, Company, Price, Expiry)
    public void updateMedicine(Medicine medicine) {
        checkManagerPermission();
        String sql = "UPDATE medicines SET name=?, generic_name=?, company=?, price=?, expiry_date=? WHERE medicine_id=?";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, medicine.getName());
            pstmt.setString(2, medicine.getGenericName());
            pstmt.setString(3, medicine.getCompany());
            pstmt.setDouble(4, medicine.getPrice());
            pstmt.setString(5, medicine.getExpiry());
            pstmt.setInt(6, medicine.getId());

            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Direct Stock Update
    // Direct Stock Update
    public void updateStock(int medicineId, int newQuantity) {
        checkManagerPermission();
        String updateSql = "UPDATE stock SET quantity=? WHERE medicine_id=?";
        String insertSql = "INSERT INTO stock (medicine_id, quantity) VALUES (?, ?)";

        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement startStmt = conn.prepareStatement(updateSql)) {
                startStmt.setInt(1, newQuantity);
                startStmt.setInt(2, medicineId);
                int rows = startStmt.executeUpdate();

                if (rows == 0) {
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        insertStmt.setInt(1, medicineId);
                        insertStmt.setInt(2, newQuantity);
                        insertStmt.executeUpdate();
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Soft-delete a medicine by setting active = 0.
     * This preserves referential integrity with bill_items and avoids FK constraint
     * errors.
     */
    public void deleteMedicine(int medicineId) {
        checkManagerPermission();
        String sql = "UPDATE medicines SET active = 0 WHERE medicine_id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, medicineId);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                System.out.println("✅ Medicine " + medicineId + " soft-deleted.");
            } else {
                System.err.println("⚠️ Medicine " + medicineId + " not found.");
            }
        } catch (SQLException e) {
            System.err.println("Delete failed: " + e.getMessage());
        }
    }
    // --- Business Intelligence Methods ---

    public List<Medicine> searchByGeneric(String keyword) {
        List<Medicine> list = new ArrayList<>();
        String sql = "SELECT m.medicine_id, m.name, m.generic_name, m.company, m.expiry_date, m.price, s.quantity " +
                "FROM medicines m " +
                "LEFT JOIN stock s ON m.medicine_id = s.medicine_id " +
                "WHERE m.active = 1 AND (m.generic_name LIKE ? OR m.name LIKE ?)";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            String searchPattern = "%" + keyword + "%";
            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new Medicine(
                            rs.getInt("medicine_id"),
                            rs.getString("name"),
                            rs.getString("generic_name"),
                            rs.getString("company"),
                            rs.getString("expiry_date"),
                            rs.getInt("quantity"),
                            rs.getDouble("price")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Paginated search for medicines by keyword. Searches name, generic_name, and
     * company.
     * Returns at most `limit` results starting from `offset`.
     */
    public List<Medicine> searchMedicines(String keyword, int offset, int limit) {
        List<Medicine> list = new ArrayList<>();
        int safeOffset = Math.max(0, offset);
        int safeLimit = normalizeLimit(limit);
        String safeKeyword = keyword == null ? "" : keyword.trim();
        if (safeKeyword.isEmpty()) {
            return getMedicinesPage(safeOffset, safeLimit);
        }

        String sql = "SELECT m.medicine_id, m.name, m.generic_name, m.company, m.expiry_date, m.price, s.quantity " +
                "FROM medicines m " +
                "LEFT JOIN stock s ON m.medicine_id = s.medicine_id " +
                "WHERE m.active = 1 AND (m.name LIKE ? OR m.generic_name LIKE ? OR m.company LIKE ?) " +
                "ORDER BY m.name ASC LIMIT ? OFFSET ?";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            String searchPattern = "%" + safeKeyword + "%";
            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);
            pstmt.setString(3, searchPattern);
            pstmt.setInt(4, safeLimit);
            pstmt.setInt(5, safeOffset);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new Medicine(
                            rs.getInt("medicine_id"),
                            rs.getString("name"),
                            rs.getString("generic_name"),
                            rs.getString("company"),
                            rs.getString("expiry_date"),
                            rs.getInt("quantity"),
                            rs.getDouble("price")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Count total active medicines matching a keyword.
     */
    public int countMedicines(String keyword) {
        String safeKeyword = keyword == null ? "" : keyword.trim();
        if (safeKeyword.isEmpty()) {
            return countActiveMedicines();
        }

        String sql = "SELECT COUNT(*) FROM medicines m " +
                "WHERE m.active = 1 AND (m.name LIKE ? OR m.generic_name LIKE ? OR m.company LIKE ?)";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            String searchPattern = "%" + safeKeyword + "%";
            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);
            pstmt.setString(3, searchPattern);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public List<Medicine> getExpiringMedicines(int days) {
        List<Medicine> list = new ArrayList<>();
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(days);
        String sql = "SELECT m.medicine_id, m.name, m.generic_name, m.company, m.expiry_date, m.price, s.quantity " +
                "FROM medicines m " +
                "LEFT JOIN stock s ON m.medicine_id = s.medicine_id " +
                "WHERE m.active = 1 " +
                "AND m.expiry_date >= ? " +
                "AND m.expiry_date <= ? " +
                "ORDER BY m.expiry_date ASC";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, startDate.toString());
            pstmt.setString(2, endDate.toString());

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new Medicine(
                            rs.getInt("medicine_id"),
                            rs.getString("name"),
                            rs.getString("generic_name"),
                            rs.getString("company"),
                            rs.getString("expiry_date"),
                            rs.getInt("quantity"),
                            rs.getDouble("price")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(limit, MAX_PAGE_SIZE);
    }
}
