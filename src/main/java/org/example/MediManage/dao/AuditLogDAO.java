package org.example.MediManage.dao;

import org.example.MediManage.model.AuditEvent;
import org.example.MediManage.util.DatabaseUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AuditLogDAO {
    public void logEvent(
            Integer actorUserId,
            String eventType,
            String entityType,
            Integer entityId,
            String summary,
            String detailsJson) throws SQLException {
        try (Connection conn = DatabaseUtil.getConnection()) {
            logEvent(conn, actorUserId, eventType, entityType, entityId, summary, detailsJson);
        }
    }

    public void logEvent(
            Connection conn,
            Integer actorUserId,
            String eventType,
            String entityType,
            Integer entityId,
            String summary,
            String detailsJson) throws SQLException {
        String sql = """
                INSERT INTO audit_events (
                    actor_user_id,
                    event_type,
                    entity_type,
                    entity_id,
                    summary,
                    details_json
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (actorUserId != null && actorUserId > 0) {
                ps.setInt(1, actorUserId);
            } else {
                ps.setNull(1, java.sql.Types.INTEGER);
            }
            ps.setString(2, safe(eventType));
            ps.setString(3, safe(entityType));
            if (entityId != null && entityId > 0) {
                ps.setInt(4, entityId);
            } else {
                ps.setNull(4, java.sql.Types.INTEGER);
            }
            ps.setString(5, safe(summary));
            ps.setString(6, detailsJson == null ? "" : detailsJson.trim());
            ps.executeUpdate();
        }
    }

    public List<AuditEvent> getRecentEvents(int limit) throws SQLException {
        List<AuditEvent> events = new ArrayList<>();
        String sql = """
                SELECT ae.event_id,
                       ae.occurred_at,
                       ae.actor_user_id,
                       COALESCE(u.username, '') AS actor_username,
                       ae.event_type,
                       ae.entity_type,
                       ae.entity_id,
                       ae.summary,
                       COALESCE(ae.details_json, '') AS details_json
                FROM audit_events ae
                LEFT JOIN users u ON u.user_id = ae.actor_user_id
                ORDER BY ae.occurred_at DESC, ae.event_id DESC
                LIMIT ?
                """;
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer actorUserId = rs.getObject("actor_user_id") == null ? null : rs.getInt("actor_user_id");
                    Integer entityId = rs.getObject("entity_id") == null ? null : rs.getInt("entity_id");
                    events.add(new AuditEvent(
                            rs.getInt("event_id"),
                            rs.getString("occurred_at"),
                            actorUserId,
                            rs.getString("actor_username"),
                            rs.getString("event_type"),
                            rs.getString("entity_type"),
                            entityId,
                            rs.getString("summary"),
                            rs.getString("details_json")));
                }
            }
        }
        return events;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
