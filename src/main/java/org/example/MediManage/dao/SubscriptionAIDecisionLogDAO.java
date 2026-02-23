package org.example.MediManage.dao;

import org.example.MediManage.DatabaseUtil;
import org.example.MediManage.model.SubscriptionAIDecisionLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SubscriptionAIDecisionLogDAO {

    public long appendDecisionLog(SubscriptionAIDecisionLog decisionLog) throws SQLException {
        if (decisionLog == null) {
            throw new IllegalArgumentException("Decision log is required.");
        }

        String sql = "INSERT INTO subscription_ai_decision_log (" +
                "decision_type, subject_type, subject_ref, reason_code, reason_message, decision_payload_json, " +
                "model_component, model_version, prompt_key, prompt_version, actor_user_id" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, decisionLog.decisionType());
            ps.setString(2, decisionLog.subjectType());
            ps.setString(3, decisionLog.subjectRef());
            ps.setString(4, decisionLog.reasonCode());
            ps.setString(5, decisionLog.reasonMessage());
            ps.setString(6, decisionLog.decisionPayloadJson());
            ps.setString(7, decisionLog.modelComponent());
            ps.setString(8, decisionLog.modelVersion());
            ps.setString(9, decisionLog.promptKey());
            ps.setObject(10, decisionLog.promptVersion());
            ps.setObject(11, decisionLog.actorUserId());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return 0L;
    }

    public List<SubscriptionAIDecisionLog> getRecentDecisionLogs(int limit) {
        int safeLimit = limit <= 0 ? 50 : Math.max(1, Math.min(500, limit));
        List<SubscriptionAIDecisionLog> rows = new ArrayList<>();
        String sql = "SELECT * FROM subscription_ai_decision_log ORDER BY decision_log_id DESC LIMIT ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, safeLimit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapDecisionLog(rs));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load subscription AI decision logs: " + e.getMessage());
        }
        return rows;
    }

    private SubscriptionAIDecisionLog mapDecisionLog(ResultSet rs) throws SQLException {
        return new SubscriptionAIDecisionLog(
                rs.getLong("decision_log_id"),
                rs.getString("decision_type"),
                rs.getString("subject_type"),
                rs.getString("subject_ref"),
                rs.getString("reason_code"),
                rs.getString("reason_message"),
                rs.getString("decision_payload_json"),
                rs.getString("model_component"),
                rs.getString("model_version"),
                rs.getString("prompt_key"),
                nullableInt(rs, "prompt_version"),
                nullableInt(rs, "actor_user_id"),
                rs.getString("created_at"));
    }

    private Integer nullableInt(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        if (rs.wasNull()) {
            return null;
        }
        return value;
    }
}
