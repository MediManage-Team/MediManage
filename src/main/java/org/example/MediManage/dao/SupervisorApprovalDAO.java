package org.example.MediManage.dao;

import org.example.MediManage.model.SupervisorApproval;
import org.example.MediManage.util.DatabaseUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SupervisorApprovalDAO {
    public int recordApproval(
            Connection conn,
            Integer requestedByUserId,
            int approvedByUserId,
            String actionType,
            String entityType,
            Integer entityId,
            String justification,
            String approvalNotes) throws SQLException {
        String sql = """
                INSERT INTO supervisor_approvals (
                    requested_by_user_id,
                    approved_by_user_id,
                    action_type,
                    entity_type,
                    entity_id,
                    justification,
                    approval_notes
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            if (requestedByUserId != null && requestedByUserId > 0) {
                ps.setInt(1, requestedByUserId);
            } else {
                ps.setNull(1, java.sql.Types.INTEGER);
            }
            ps.setInt(2, approvedByUserId);
            ps.setString(3, safe(actionType));
            ps.setString(4, safe(entityType));
            if (entityId != null && entityId > 0) {
                ps.setInt(5, entityId);
            } else {
                ps.setNull(5, java.sql.Types.INTEGER);
            }
            ps.setString(6, justification == null ? "" : justification.trim());
            ps.setString(7, approvalNotes == null ? "" : approvalNotes.trim());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public List<SupervisorApproval> getRecentApprovals(int limit) throws SQLException {
        List<SupervisorApproval> rows = new ArrayList<>();
        String sql = """
                SELECT sa.approval_id,
                       sa.requested_by_user_id,
                       sa.approved_by_user_id,
                       COALESCE(u.username, '') AS approved_by_username,
                       sa.action_type,
                       sa.entity_type,
                       sa.entity_id,
                       COALESCE(sa.justification, '') AS justification,
                       COALESCE(sa.approval_notes, '') AS approval_notes,
                       sa.approved_at
                FROM supervisor_approvals sa
                LEFT JOIN users u ON u.user_id = sa.approved_by_user_id
                ORDER BY sa.approved_at DESC, sa.approval_id DESC
                LIMIT ?
                """;
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer requestedBy = rs.getObject("requested_by_user_id") == null
                            ? null
                            : rs.getInt("requested_by_user_id");
                    Integer entityId = rs.getObject("entity_id") == null ? null : rs.getInt("entity_id");
                    rows.add(new SupervisorApproval(
                            rs.getInt("approval_id"),
                            requestedBy,
                            rs.getInt("approved_by_user_id"),
                            rs.getString("approved_by_username"),
                            rs.getString("action_type"),
                            rs.getString("entity_type"),
                            entityId,
                            rs.getString("justification"),
                            rs.getString("approval_notes"),
                            rs.getString("approved_at")));
                }
            }
        }
        return rows;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
