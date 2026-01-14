package org.example.MediManage.dao;

import org.example.MediManage.DBUtil;
import org.example.MediManage.DashboardController.Medicine; // Using inner class for now, ideally strictly separate POJO

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MedicineDAO {

    public List<Medicine> getAllMedicines() {
        List<Medicine> list = new ArrayList<>();
        // Query to get medicines data and current stock.
        // Assuming every medicine has an entry in 'medicines' and 'stock' (1:1
        // optional).
        // Since we are creating logic, let's join.
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

    // Method to add medicine + initial stock
    public void addMedicine(String name, String company, String expiry, double price, int initialStock) {
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
}
