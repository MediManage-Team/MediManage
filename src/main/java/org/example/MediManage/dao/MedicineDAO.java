package org.example.MediManage.dao;

import org.example.MediManage.DatabaseUtil;
import org.example.MediManage.model.Medicine;
import org.example.MediManage.model.UserRole;
import org.example.MediManage.util.UserSession;
import org.example.MediManage.util.ValidationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Medicine entities.
 * Handles all database operations related to medicines with proper validation
 * and error handling.
 */
public class MedicineDAO {

    private static final Logger logger = LoggerFactory.getLogger(MedicineDAO.class);

    /**
     * Checks if the current user has manager-level permissions.
     * 
     * @throws SecurityException if user doesn't have required permissions
     */
    private void checkManagerPermission() {
        if (!UserSession.getInstance().isLoggedIn() ||
                !(UserSession.getInstance().getUser().getRole() == UserRole.ADMIN ||
                        UserSession.getInstance().getUser().getRole() == UserRole.MANAGER)) {
            logger.warn("Unauthorized access attempt by user: {}",
                    UserSession.getInstance().isLoggedIn() ? UserSession.getInstance().getUser().getUsername()
                            : "anonymous");
            throw new SecurityException("Access Denied: Only ADMIN or MANAGER can perform this action.");
        }
    }

    /**
     * Retrieves all medicines from the database.
     * 
     * @return list of all medicines
     */
    public List<Medicine> getAllMedicines() {
        List<Medicine> list = new ArrayList<>();
        String sql = "SELECT m.medicine_id, m.name, m.generic_name, m.company, m.expiry_date, m.price, " +
                "COALESCE(s.quantity, 0) as quantity " +
                "FROM medicines m " +
                "LEFT JOIN stock s ON m.medicine_id = s.medicine_id";

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
            logger.debug("Retrieved {} medicines from database", list.size());
        } catch (SQLException e) {
            logger.error("Failed to retrieve medicines from database", e);
            throw new RuntimeException("Failed to retrieve medicines", e);
        }
        return list;
    }

    /**
     * Adds a new medicine to the database with initial stock.
     * 
     * @param name         medicine name
     * @param genericName  generic name
     * @param company      manufacturer company
     * @param expiry       expiry date (YYYY-MM-DD format)
     * @param price        unit price
     * @param initialStock initial stock quantity
     * @throws IllegalArgumentException if validation fails
     * @throws SQLException             if database operation fails
     */
    public void addMedicine(String name, String genericName, String company, String expiry,
            double price, int initialStock) throws SQLException {
        checkManagerPermission();

        // Validate inputs
        ValidationUtil.requireNonEmpty(name, "Medicine name");
        ValidationUtil.requireNonEmpty(genericName, "Generic name");
        ValidationUtil.requireNonEmpty(company, "Company name");
        ValidationUtil.requireNonEmpty(expiry, "Expiry date");
        ValidationUtil.requirePositive(price, "Price");
        ValidationUtil.requireNonNegative(initialStock, "Initial stock");

        if (!ValidationUtil.isValidDate(expiry)) {
            throw new IllegalArgumentException("Invalid expiry date format. Expected: YYYY-MM-DD");
        }

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
                logger.info("Added new medicine: {} (ID: {}) with stock: {}", name, medId, initialStock);
            } catch (SQLException e) {
                conn.rollback();
                logger.error("Failed to add medicine: {}", name, e);
                throw e;
            }
        }
    }

    /**
     * Updates an existing medicine's details.
     * 
     * @param medicine the medicine object with updated values
     * @throws IllegalArgumentException if validation fails
     * @throws SQLException             if database operation fails
     */
    public void updateMedicine(Medicine medicine) throws SQLException {
        checkManagerPermission();

        if (medicine == null) {
            throw new IllegalArgumentException("Medicine object cannot be null");
        }

        ValidationUtil.requireNonEmpty(medicine.getName(), "Medicine name");
        ValidationUtil.requireNonEmpty(medicine.getGenericName(), "Generic name");
        ValidationUtil.requireNonEmpty(medicine.getCompany(), "Company name");
        ValidationUtil.requirePositive(medicine.getPrice(), "Price");

        String sql = "UPDATE medicines SET name=?, generic_name=?, company=?, price=?, expiry_date=? WHERE medicine_id=?";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, medicine.getName());
            pstmt.setString(2, medicine.getGenericName());
            pstmt.setString(3, medicine.getCompany());
            pstmt.setDouble(4, medicine.getPrice());
            pstmt.setString(5, medicine.getExpiry());
            pstmt.setInt(6, medicine.getId());

            int rowsUpdated = pstmt.executeUpdate();
            if (rowsUpdated > 0) {
                logger.info("Updated medicine ID: {} - {}", medicine.getId(), medicine.getName());
            } else {
                logger.warn("No medicine found with ID: {}", medicine.getId());
            }
        } catch (SQLException e) {
            logger.error("Failed to update medicine ID: {}", medicine.getId(), e);
            throw e;
        }
    }

    /**
     * Updates the stock quantity for a medicine.
     * 
     * @param medicineId  the medicine ID
     * @param newQuantity the new quantity
     * @throws IllegalArgumentException if validation fails
     * @throws SQLException             if database operation fails
     */
    public void updateStock(int medicineId, int newQuantity) throws SQLException {
        checkManagerPermission();

        ValidationUtil.requirePositive(medicineId, "Medicine ID");
        ValidationUtil.requireNonNegative(newQuantity, "Stock quantity");

        String updateSql = "UPDATE stock SET quantity=? WHERE medicine_id=?";
        String insertSql = "INSERT INTO stock (medicine_id, quantity) VALUES (?, ?)";

        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                updateStmt.setInt(1, newQuantity);
                updateStmt.setInt(2, medicineId);
                int rows = updateStmt.executeUpdate();

                if (rows == 0) {
                    // Stock entry doesn't exist, insert new one
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        insertStmt.setInt(1, medicineId);
                        insertStmt.setInt(2, newQuantity);
                        insertStmt.executeUpdate();
                        logger.info("Created new stock entry for medicine ID: {} with quantity: {}",
                                medicineId, newQuantity);
                    }
                } else {
                    logger.info("Updated stock for medicine ID: {} to quantity: {}", medicineId, newQuantity);
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                logger.error("Failed to update stock for medicine ID: {}", medicineId, e);
                throw e;
            }
        }
    }

    /**
     * Deletes a medicine from the database.
     * Note: This will fail if the medicine is referenced in bills due to foreign
     * key constraints.
     * 
     * @param medicineId the medicine ID to delete
     * @throws SQLException if database operation fails or medicine is referenced in
     *                      bills
     */
    public void deleteMedicine(int medicineId) throws SQLException {
        checkManagerPermission();

        ValidationUtil.requirePositive(medicineId, "Medicine ID");

        String deleteStock = "DELETE FROM stock WHERE medicine_id=?";
        String deleteMed = "DELETE FROM medicines WHERE medicine_id=?";

        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Delete stock first
                try (PreparedStatement psStock = conn.prepareStatement(deleteStock)) {
                    psStock.setInt(1, medicineId);
                    psStock.executeUpdate();
                }

                // Delete medicine
                try (PreparedStatement psMed = conn.prepareStatement(deleteMed)) {
                    psMed.setInt(1, medicineId);
                    int rows = psMed.executeUpdate();
                    if (rows == 0) {
                        throw new SQLException("Medicine ID " + medicineId + " not found");
                    }
                }

                conn.commit();
                logger.info("Deleted medicine ID: {}", medicineId);
            } catch (SQLException e) {
                conn.rollback();
                if (e.getMessage().contains("FOREIGN KEY constraint failed")) {
                    logger.warn("Cannot delete medicine ID: {} - referenced in bills", medicineId);
                    throw new SQLException("Cannot delete medicine: It is referenced in existing bills. " +
                            "Consider marking it as inactive instead.");
                }
                logger.error("Failed to delete medicine ID: {}", medicineId, e);
                throw e;
            }
        }
    }
}
