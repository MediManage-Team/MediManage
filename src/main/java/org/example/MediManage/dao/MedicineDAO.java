package org.example.MediManage.dao;

import org.example.MediManage.DBUtil;
import org.example.MediManage.model.Medicine;
import org.example.MediManage.model.UserRole;
import org.example.MediManage.util.UserSession;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MedicineDAO {

    private void checkManagerPermission() {
        if (!UserSession.getInstance().isLoggedIn() ||
                !(UserSession.getInstance().getUser().getRole() == UserRole.ADMIN ||
                        UserSession.getInstance().getUser().getRole() == UserRole.MANAGER)) {
            throw new SecurityException("Access Denied: Only ADMIN or MANAGER can perform this action.");
        }
    }

    public List<Medicine> getAllMedicines() {
        List<Medicine> list = new ArrayList<>();
        String sql = "SELECT m.medicine_id, m.name, m.company, m.expiry_date, m.price, s.quantity " +
                "FROM medicines m " +
                "LEFT JOIN stock s ON m.medicine_id = s.medicine_id";

        try (Connection conn = DBUtil.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                list.add(new Medicine(
                        rs.getInt("medicine_id"),
                        rs.getString("name"),
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

    public void addMedicine(String name, String company, String expiry, double price, int initialStock) {
        checkManagerPermission();
        String insertMed = "INSERT INTO medicines(name, company, expiry_date, price) VALUES(?, ?, ?, ?)";
        String insertStock = "INSERT INTO stock(medicine_id, quantity) VALUES(?, ?)";

        try (Connection conn = DBUtil.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement psMed = conn.prepareStatement(insertMed, Statement.RETURN_GENERATED_KEYS)) {
                psMed.setString(1, name);
                psMed.setString(2, company);
                psMed.setString(3, expiry);
                psMed.setDouble(4, price);
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

    // Update Medicine (Name, Company, Price, Expiry)
    public void updateMedicine(Medicine medicine) {
        checkManagerPermission();
        String sql = "UPDATE medicines SET name=?, company=?, price=?, expiry_date=? WHERE medicine_id=?";

        try (Connection conn = DBUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, medicine.getName());
            pstmt.setString(2, medicine.getCompany());
            pstmt.setDouble(3, medicine.getPrice());
            pstmt.setString(4, medicine.getExpiry());
            pstmt.setInt(5, medicine.getId());

            pstmt.executeUpdate();

            // Also ensure stock is updated if changed via this object?
            // The requirement says "updateStock" is separate, but if we pass a Medicine
            // object that has stock,
            // we might want to sync it. For now, strict separation as per requirement.
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

        try (Connection conn = DBUtil.getConnection()) {
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

    // Delete Medicine
    public void deleteMedicine(int medicineId) {
        checkManagerPermission();
        String deleteStock = "DELETE FROM stock WHERE medicine_id=?";
        String deleteMed = "DELETE FROM medicines WHERE medicine_id=?";
        // Note: bill_items also references medicine_id. Deleting a medicine used in
        // bills will fail due to FK.
        // For a real app, Soft Delete is better (active=0).
        // BUT strict requirement says "deleteMedicine".
        // I will try to delete stock first, then medicine. use try-catch to warn user
        // if FK constraint fails.

        try (Connection conn = DBUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement psStock = conn.prepareStatement(deleteStock)) {
                    psStock.setInt(1, medicineId);
                    psStock.executeUpdate();
                }

                try (PreparedStatement psMed = conn.prepareStatement(deleteMed)) {
                    psMed.setInt(1, medicineId);
                    psMed.executeUpdate();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                System.err.println("Cannot delete medicine: It might be used in bills. " + e.getMessage());
                // Rethrow or handle quietly? For now, print error.
                throw new SQLException("Cannot delete medicine used in bills.");
            }
        } catch (SQLException e) {
            // e.printStackTrace();
            // Better to let controller handle it or just log.
            System.err.println("Delete failed: " + e.getMessage());
        }
    }
}
