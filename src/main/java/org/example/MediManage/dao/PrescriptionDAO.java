package org.example.MediManage.dao;

import org.example.MediManage.DatabaseUtil;
import org.example.MediManage.model.Prescription;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PrescriptionDAO {

    /**
     * Add a new prescription.
     */
    public int addPrescription(Prescription p) throws SQLException {
        String sql = "INSERT INTO prescriptions (customer_id, customer_name, doctor_name, status, notes, medicines_text) "
                +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setObject(1, p.getCustomerId());
            ps.setString(2, p.getCustomerName());
            ps.setString(3, p.getDoctorName());
            ps.setString(4, p.getStatus() != null ? p.getStatus() : "PENDING");
            ps.setString(5, p.getNotes());
            ps.setString(6, p.getMedicinesText());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        return -1;
    }

    /**
     * Get all prescriptions ordered by most recent first.
     */
    public List<Prescription> getAllPrescriptions() {
        List<Prescription> list = new ArrayList<>();
        String sql = "SELECT * FROM prescriptions ORDER BY prescribed_date DESC";

        try (Connection conn = DatabaseUtil.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                list.add(mapPrescription(rs));
            }
        } catch (SQLException e) {
            // Prescriptions table may not exist yet — log quietly
            System.err.println("PrescriptionDAO.getAllPrescriptions: " + e.getMessage());
        }
        return list;
    }

    /**
     * Get prescriptions filtered by status.
     */
    public List<Prescription> getByStatus(String status) {
        List<Prescription> list = new ArrayList<>();
        String sql = "SELECT * FROM prescriptions WHERE status = ? ORDER BY prescribed_date DESC";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapPrescription(rs));
                }
            }
        } catch (SQLException e) {
            // Prescriptions table may not exist yet — log quietly
            System.err.println("PrescriptionDAO.getByStatus: " + e.getMessage());
        }
        return list;
    }

    /**
     * Update prescription status (PENDING → VERIFIED → DISPENSED).
     */
    public void updateStatus(int prescriptionId, String newStatus) throws SQLException {
        String sql = "UPDATE prescriptions SET status = ? WHERE prescription_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setInt(2, prescriptionId);
            ps.executeUpdate();
        }
    }

    /**
     * Save AI validation result for a prescription.
     */
    public void saveAIValidation(int prescriptionId, String validation) throws SQLException {
        String sql = "UPDATE prescriptions SET ai_validation = ? WHERE prescription_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, validation);
            ps.setInt(2, prescriptionId);
            ps.executeUpdate();
        }
    }

    /**
     * Delete a prescription.
     */
    public void deletePrescription(int prescriptionId) throws SQLException {
        String sql = "DELETE FROM prescriptions WHERE prescription_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, prescriptionId);
            ps.executeUpdate();
        }
    }

    // ======================== HELPERS ========================

    private Prescription mapPrescription(ResultSet rs) throws SQLException {
        Prescription p = new Prescription();
        p.setPrescriptionId(rs.getInt("prescription_id"));
        p.setCustomerId((Integer) rs.getObject("customer_id"));
        p.setCustomerName(rs.getString("customer_name"));
        p.setDoctorName(rs.getString("doctor_name"));
        p.setStatus(rs.getString("status"));
        p.setPrescribedDate(rs.getString("prescribed_date"));
        p.setNotes(rs.getString("notes"));
        p.setMedicinesText(rs.getString("medicines_text"));
        p.setAiValidation(rs.getString("ai_validation"));
        return p;
    }
}
