package org.example.MediManage.dao;

import org.example.MediManage.model.MessageTemplate;
import org.example.MediManage.util.DatabaseUtil;
import java.sql.*;

public class MessageTemplateDAO {
    private static final String LEGACY_WHATSAPP_INVOICE_TEMPLATE =
            "🟢 *{{pharmacy_name}}*\n─────────────────────\n\nHello *{{customer_name}}*,\n\nThank you for choosing us! Your invoice #*{{bill_id}}* for *₹{{total_amount}}* is attached below as a PDF document.\n\n{{care_note}}\nStay healthy & take care! 🙏";
    private static final String LEGACY_EMAIL_INVOICE_SUBJECT_TEMPLATE =
            "Your Invoice #{{bill_id}} from {{pharmacy_name}}";
    private static final String LEGACY_EMAIL_INVOICE_BODY_TEMPLATE =
            "Dear {{customer_name}},\n\nThank you for choosing {{pharmacy_name}}.\n\nPlease find attached your invoice #{{bill_id}} for the amount of ₹{{total_amount}}.\n\n{{care_note}}\n\nBest Regards,\n{{pharmacy_name}}";

    public static final String KEY_WHATSAPP_INVOICE = "whatsapp_invoice";
    public static final String KEY_EMAIL_INVOICE_SUBJECT = "email_invoice_subject";
    public static final String KEY_EMAIL_INVOICE_BODY = "email_invoice_body";

    public MessageTemplate getByKey(String keyName) {
        String sql = "SELECT * FROM message_templates WHERE template_key = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, keyName);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String subject = normalizeLegacyDefault(keyName, rs.getString("subject"));
                String body = normalizeLegacyDefault(keyName, rs.getString("body_template"));
                return new MessageTemplate(
                    rs.getInt("template_id"),
                    rs.getString("template_key"),
                    subject,
                    body
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void save(MessageTemplate template) {
        String sql = "INSERT INTO message_templates (template_key, subject, body_template) VALUES (?, ?, ?) " +
                     "ON CONFLICT(template_key) DO UPDATE SET subject = excluded.subject, body_template = excluded.body_template";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, template.getKeyName());
            pstmt.setString(2, template.getSubjectTemplate());
            pstmt.setString(3, template.getBodyTemplate());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void resetAllToDefaults() {
        String sql = "DELETE FROM message_templates";
        try (Connection conn = DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            
            // Re-seed defaults
            save(new MessageTemplate(0, KEY_WHATSAPP_INVOICE, null, getDefaultBody(KEY_WHATSAPP_INVOICE)));
            save(new MessageTemplate(0, KEY_EMAIL_INVOICE_SUBJECT, getDefaultBody(KEY_EMAIL_INVOICE_SUBJECT), getDefaultBody(KEY_EMAIL_INVOICE_SUBJECT)));
            save(new MessageTemplate(0, KEY_EMAIL_INVOICE_BODY, null, getDefaultBody(KEY_EMAIL_INVOICE_BODY)));
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getDefaultBody(String key) {
        switch (key) {
            case KEY_WHATSAPP_INVOICE:
                return "*{{pharmacy_name}}*\n\nHello {{customer_name}},\n\nYour invoice #{{bill_id}} for Rs. {{total_amount}} is attached as a PDF.\n\n{{care_note}}\nThank you for choosing {{pharmacy_name}}.";
            case KEY_EMAIL_INVOICE_SUBJECT:
                return "Your Invoice #{{bill_id}} from {{pharmacy_name}}";
            case KEY_EMAIL_INVOICE_BODY:
                return "Dear {{customer_name}},\n\nPlease find attached your invoice #{{bill_id}} from {{pharmacy_name}} for Rs. {{total_amount}}.\n\n{{care_note}}\n\nRegards,\n{{pharmacy_name}}";
            default:
                return "";
        }
    }

    private String normalizeLegacyDefault(String key, String value) {
        if (value == null) {
            return null;
        }
        String legacyDefault = switch (key) {
            case KEY_WHATSAPP_INVOICE -> LEGACY_WHATSAPP_INVOICE_TEMPLATE;
            case KEY_EMAIL_INVOICE_SUBJECT -> LEGACY_EMAIL_INVOICE_SUBJECT_TEMPLATE;
            case KEY_EMAIL_INVOICE_BODY -> LEGACY_EMAIL_INVOICE_BODY_TEMPLATE;
            default -> null;
        };
        if (legacyDefault != null && legacyDefault.equals(value)) {
            return getDefaultBody(key);
        }
        return value;
    }
}
