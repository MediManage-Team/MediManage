package org.example.MediManage.dao;

import org.example.MediManage.DatabaseUtil;
import org.example.MediManage.model.Customer;
import org.example.MediManage.util.ValidationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Customer entities.
 * Handles all database operations related to customers with proper validation
 * and error handling.
 */
public class CustomerDAO {

    private static final Logger logger = LoggerFactory.getLogger(CustomerDAO.class);

    /**
     * Adds a new customer to the database.
     * 
     * @param customer the customer object to add
     * @throws IllegalArgumentException if validation fails
     * @throws SQLException             if database operation fails
     */
    public void addCustomer(Customer customer) throws SQLException {
        if (customer == null) {
            throw new IllegalArgumentException("Customer object cannot be null");
        }

        // Validate required fields
        ValidationUtil.requireNonEmpty(customer.getName(), "Customer name");
        ValidationUtil.requireNonEmpty(customer.getPhoneNumber(), "Phone number");

        // Validate phone format
        if (!ValidationUtil.isValidPhone(customer.getPhoneNumber())) {
            throw new IllegalArgumentException("Invalid phone number format");
        }

        // Validate email if provided
        if (customer.getEmail() != null && !customer.getEmail().trim().isEmpty()) {
            if (!ValidationUtil.isValidEmail(customer.getEmail())) {
                throw new IllegalArgumentException("Invalid email format");
            }
        }

        String sql = "INSERT INTO customers (name, email, phone, address, nominee_name, nominee_relation, " +
                "insurance_provider, insurance_policy_no, diseases, photo_id_path) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, customer.getName());
            pstmt.setString(2, customer.getEmail());
            pstmt.setString(3, customer.getPhoneNumber());
            pstmt.setString(4, customer.getAddress());
            pstmt.setString(5, customer.getNomineeName());
            pstmt.setString(6, customer.getNomineeRelation());
            pstmt.setString(7, customer.getInsuranceProvider());
            pstmt.setString(8, customer.getInsurancePolicyNo());
            pstmt.setString(9, customer.getDiseases());
            pstmt.setString(10, customer.getPhotoIdPath());

            pstmt.executeUpdate();
            logger.info("Added new customer: {} (Phone: {})", customer.getName(), customer.getPhoneNumber());
        } catch (SQLException e) {
            logger.error("Failed to add customer: {}", customer.getName(), e);
            throw e;
        }
    }

    /**
     * Searches for customers by name or phone number.
     * 
     * @param query the search query
     * @return list of matching customers
     */
    public List<Customer> searchCustomer(String query) {
        List<Customer> customers = new ArrayList<>();

        if (ValidationUtil.isNullOrEmpty(query)) {
            logger.warn("Empty search query provided");
            return customers;
        }

        String sql = "SELECT * FROM customers WHERE name LIKE ? OR phone LIKE ?";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            String searchPattern = "%" + query + "%";
            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);

            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Customer customer = new Customer();
                customer.setCustomerId(rs.getInt("customer_id"));
                customer.setName(rs.getString("name"));
                customer.setEmail(rs.getString("email"));
                customer.setPhoneNumber(rs.getString("phone"));
                customer.setAddress(rs.getString("address"));
                customer.setNomineeName(rs.getString("nominee_name"));
                customer.setNomineeRelation(rs.getString("nominee_relation"));
                customer.setInsuranceProvider(rs.getString("insurance_provider"));
                customer.setInsurancePolicyNo(rs.getString("insurance_policy_no"));
                customer.setDiseases(rs.getString("diseases"));
                customer.setPhotoIdPath(rs.getString("photo_id_path"));
                customer.setCurrentBalance(rs.getDouble("current_balance"));
                customers.add(customer);
            }
            logger.debug("Found {} customers matching query: {}", customers.size(), query);

        } catch (SQLException e) {
            logger.error("Failed to search customers with query: {}", query, e);
            throw new RuntimeException("Failed to search customers", e);
        }
        return customers;
    }

    /**
     * Updates a customer's balance (for credit transactions).
     * 
     * @param customerId the customer ID
     * @param amount     the amount to add (positive for debt, negative for payment)
     * @throws IllegalArgumentException if validation fails
     * @throws SQLException             if database operation fails
     */
    public void updateBalance(int customerId, double amount) throws SQLException {
        ValidationUtil.requirePositive(customerId, "Customer ID");

        String sql = "UPDATE customers SET current_balance = current_balance + ? WHERE customer_id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setInt(2, customerId);

            int rowsUpdated = ps.executeUpdate();
            if (rowsUpdated > 0) {
                logger.info("Updated balance for customer ID: {} by amount: {}", customerId, amount);
            } else {
                logger.warn("No customer found with ID: {}", customerId);
                throw new SQLException("Customer ID " + customerId + " not found");
            }
        } catch (SQLException e) {
            logger.error("Failed to update balance for customer ID: {}", customerId, e);
            throw e;
        }
    }

    /**
     * Gets a customer's current balance.
     * 
     * @param customerId the customer ID
     * @return the current balance
     * @throws SQLException if database operation fails
     */
    public double getBalance(int customerId) throws SQLException {
        ValidationUtil.requirePositive(customerId, "Customer ID");

        String sql = "SELECT current_balance FROM customers WHERE customer_id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getDouble("current_balance");
            } else {
                throw new SQLException("Customer ID " + customerId + " not found");
            }
        } catch (SQLException e) {
            logger.error("Failed to get balance for customer ID: {}", customerId, e);
            throw e;
        }
    }
}
