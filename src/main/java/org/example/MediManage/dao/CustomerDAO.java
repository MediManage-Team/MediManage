package org.example.MediManage.dao;

import org.example.MediManage.DatabaseUtil;
import org.example.MediManage.model.Customer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CustomerDAO {

    public void addCustomer(Customer customer) throws SQLException {
        String sql = "INSERT INTO customers (name, email, phone, address, nominee_name, nominee_relation, insurance_provider, insurance_policy_no, diseases, photo_id_path) "
                +
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
        }
    }

    public List<Customer> searchCustomer(String query) {
        List<Customer> customers = new ArrayList<>();
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
                customer.setCurrentBalance(rs.getDouble("current_balance")); // New Field
                customers.add(customer);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return customers;
    }

    public void updateBalance(int customerId, double amount) throws SQLException {
        // SQLite: Update balance. If payment is credit, amount is positive (debt
        // increases).
        // If customer pays, amount is negative (debt decreases).
        // Wait, user story says "payment mode 'Credit' -> add bill amount to balance".
        // I'll assume 'amount' is added.
        String sql = "UPDATE customers SET current_balance = current_balance + ? WHERE customer_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setInt(2, customerId);
            ps.executeUpdate();
        }
    }
}
