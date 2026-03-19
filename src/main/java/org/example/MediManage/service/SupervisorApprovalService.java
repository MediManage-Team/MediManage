package org.example.MediManage.service;

import org.example.MediManage.dao.AuditLogDAO;
import org.example.MediManage.dao.SupervisorApprovalDAO;
import org.example.MediManage.dao.UserDAO;
import org.example.MediManage.model.User;
import org.example.MediManage.model.UserRole;
import org.example.MediManage.util.DatabaseUtil;
import org.example.MediManage.util.UserSession;
import org.json.JSONObject;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

public class SupervisorApprovalService {
    public record ApprovalResult(
            boolean approved,
            int approvalId,
            int approverUserId,
            String approverUsername,
            String message) {
    }

    private final UserDAO userDAO;
    private final SupervisorApprovalDAO approvalDAO;
    private final AuditLogDAO auditLogDAO;

    public SupervisorApprovalService() {
        this(new UserDAO(), new SupervisorApprovalDAO(), new AuditLogDAO());
    }

    SupervisorApprovalService(UserDAO userDAO, SupervisorApprovalDAO approvalDAO, AuditLogDAO auditLogDAO) {
        this.userDAO = userDAO;
        this.approvalDAO = approvalDAO;
        this.auditLogDAO = auditLogDAO;
    }

    public boolean currentUserCanBypassApproval() {
        User current = UserSession.getInstance().getUser();
        return current != null && current.getRole() == UserRole.ADMIN;
    }

    public ApprovalResult approveAction(
            String supervisorUsername,
            String supervisorPassword,
            Set<UserRole> allowedApproverRoles,
            String actionType,
            String entityType,
            Integer entityId,
            String justification,
            String approvalNotes) throws SQLException {
        User currentUser = UserSession.getInstance().getUser();
        User approver = userDAO.authenticate(
                supervisorUsername == null ? "" : supervisorUsername.trim(),
                supervisorPassword == null ? "" : supervisorPassword);
        if (approver == null) {
            return new ApprovalResult(false, 0, 0, "", "Supervisor authentication failed.");
        }
        if (allowedApproverRoles != null && !allowedApproverRoles.isEmpty() && !allowedApproverRoles.contains(approver.getRole())) {
            return new ApprovalResult(false, 0, 0, approver.getUsername(),
                    "Supervisor role is not allowed to approve this action.");
        }
        if (currentUser != null && approver.getId() == currentUser.getId() && approver.getRole() != UserRole.ADMIN) {
            return new ApprovalResult(false, 0, approver.getId(), approver.getUsername(),
                    "A different supervisor must approve this action.");
        }

        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int approvalId = approvalDAO.recordApproval(
                        conn,
                        currentUser == null ? null : currentUser.getId(),
                        approver.getId(),
                        actionType,
                        entityType,
                        entityId,
                        justification,
                        approvalNotes);

                JSONObject details = new JSONObject();
                details.put("actionType", actionType == null ? "" : actionType);
                details.put("entityType", entityType == null ? "" : entityType);
                details.put("entityId", entityId == null ? JSONObject.NULL : entityId);
                details.put("requestedByUserId", currentUser == null ? JSONObject.NULL : currentUser.getId());
                details.put("approvedByUserId", approver.getId());
                details.put("approvedByUsername", approver.getUsername());
                details.put("justification", justification == null ? "" : justification);
                details.put("approvalNotes", approvalNotes == null ? "" : approvalNotes);
                auditLogDAO.logEvent(
                        conn,
                        approver.getId(),
                        "SUPERVISOR_APPROVAL",
                        entityType,
                        entityId,
                        "Supervisor approval granted for " + (actionType == null ? "ACTION" : actionType),
                        details.toString());

                conn.commit();
                return new ApprovalResult(true, approvalId, approver.getId(), approver.getUsername(),
                        "Approved by " + approver.getUsername() + ".");
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
}
