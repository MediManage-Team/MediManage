package org.example.MediManage.dao;

import org.example.MediManage.util.DatabaseUtil;
import org.example.MediManage.model.ReceiptSettings;

import java.sql.*;

/**
 * DAO for pharmacy receipt settings (branding, address, GST, footer).
 */
public class ReceiptSettingsDAO {

    /**
     * Get the current receipt settings. Returns defaults if none exist yet.
     */
    public ReceiptSettings getSettings() throws SQLException {
        String sql = "SELECT * FROM receipt_settings ORDER BY setting_id LIMIT 1";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                ReceiptSettings s = new ReceiptSettings();
                s.setSettingId(rs.getInt("setting_id"));
                s.setPharmacyName(rs.getString("pharmacy_name"));
                s.setAddressLine1(rs.getString("address_line1"));
                s.setAddressLine2(rs.getString("address_line2"));
                s.setPhone(rs.getString("phone"));
                s.setEmail(rs.getString("email"));
                s.setGstNumber(rs.getString("gst_number"));
                s.setLogoPath(rs.getString("logo_path"));
                s.setFooterText(rs.getString("footer_text"));
                s.setShowBarcodeOnReceipt(rs.getInt("show_barcode_on_receipt") == 1);
                return s;
            }
        }
        return new ReceiptSettings(); // defaults
    }

    /**
     * Save or update receipt settings (upsert — one row only).
     */
    public void saveSettings(ReceiptSettings s) throws SQLException {
        if (s.getSettingId() > 0) {
            String sql = """
                    UPDATE receipt_settings SET pharmacy_name=?, address_line1=?, address_line2=?,
                    phone=?, email=?, gst_number=?, logo_path=?, footer_text=?,
                    show_barcode_on_receipt=?, updated_at=CURRENT_TIMESTAMP
                    WHERE setting_id=?
                    """;
            try (Connection conn = DatabaseUtil.getConnection();
                    PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, s.getPharmacyName());
                ps.setString(2, s.getAddressLine1());
                ps.setString(3, s.getAddressLine2());
                ps.setString(4, s.getPhone());
                ps.setString(5, s.getEmail());
                ps.setString(6, s.getGstNumber());
                ps.setString(7, s.getLogoPath());
                ps.setString(8, s.getFooterText());
                ps.setInt(9, s.isShowBarcodeOnReceipt() ? 1 : 0);
                ps.setInt(10, s.getSettingId());
                ps.executeUpdate();
            }
        } else {
            String sql = """
                    INSERT INTO receipt_settings (pharmacy_name, address_line1, address_line2,
                    phone, email, gst_number, logo_path, footer_text, show_barcode_on_receipt)
                    VALUES (?,?,?,?,?,?,?,?,?)
                    """;
            try (Connection conn = DatabaseUtil.getConnection();
                    PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, s.getPharmacyName());
                ps.setString(2, s.getAddressLine1());
                ps.setString(3, s.getAddressLine2());
                ps.setString(4, s.getPhone());
                ps.setString(5, s.getEmail());
                ps.setString(6, s.getGstNumber());
                ps.setString(7, s.getLogoPath());
                ps.setString(8, s.getFooterText());
                ps.setInt(9, s.isShowBarcodeOnReceipt() ? 1 : 0);
                ps.executeUpdate();
            }
        }
    }
}
