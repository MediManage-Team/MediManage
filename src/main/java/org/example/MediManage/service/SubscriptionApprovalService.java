package org.example.MediManage.service;

import org.example.MediManage.dao.SubscriptionDAO;
import org.example.MediManage.model.SubscriptionApprovalStatus;
import org.example.MediManage.model.SubscriptionDiscountOverride;
import org.example.MediManage.model.SubscriptionDiscountOverrideStatus;
import org.example.MediManage.security.Permission;
import org.example.MediManage.security.RbacPolicy;
import org.example.MediManage.service.subscription.SubscriptionAuditChain;
import org.example.MediManage.util.UserSession;

import java.sql.SQLException;
import java.util.List;

public class SubscriptionApprovalService {
    private static final String APPROVAL_TYPE_MANUAL_OVERRIDE = "MANUAL_OVERRIDE";
    private static final String REF_TYPE_DISCOUNT_OVERRIDE = "DISCOUNT_OVERRIDE";

    private final SubscriptionDAO subscriptionDAO;

    public SubscriptionApprovalService() {
        this(new SubscriptionDAO());
    }

    SubscriptionApprovalService(SubscriptionDAO subscriptionDAO) {
        this.subscriptionDAO = subscriptionDAO;
    }

    public OverrideRequestResult requestManualOverride(
            Integer billId,
            Integer billItemId,
            Integer customerId,
            Integer enrollmentId,
            double requestedDiscountPercent,
            String reason) throws SQLException {
        RbacPolicy.requireCurrentUser(Permission.MANAGE_SUBSCRIPTION_ENROLLMENTS);
        String safeReason = requireReason(reason);
        validatePercent(requestedDiscountPercent, "Requested discount percent");

        int actorUserId = currentUserId();
        int overrideId = subscriptionDAO.createDiscountOverride(
                billId,
                billItemId,
                customerId,
                enrollmentId,
                requestedDiscountPercent,
                safeReason,
                actorUserId,
                null);

        int approvalId = subscriptionDAO.createApproval(
                APPROVAL_TYPE_MANUAL_OVERRIDE,
                REF_TYPE_DISCOUNT_OVERRIDE,
                overrideId,
                actorUserId,
                safeReason);

        subscriptionDAO.attachApprovalToOverride(overrideId, approvalId);

        appendAuditLog(
                "OVERRIDE_REQUESTED",
                "subscription_discount_overrides",
                String.valueOf(overrideId),
                actorUserId,
                approvalId,
                safeReason,
                null,
                "{\"status\":\"PENDING\",\"requested_discount_percent\":" + requestedDiscountPercent + "}");

        return new OverrideRequestResult(overrideId, approvalId, SubscriptionDiscountOverrideStatus.PENDING, safeReason);
    }

    public OverrideDecisionResult approveManualOverride(
            int overrideId,
            double approvedDiscountPercent,
            String decisionReason) throws SQLException {
        RbacPolicy.requireCurrentUser(Permission.APPROVE_SUBSCRIPTION_OVERRIDES);
        validatePercent(approvedDiscountPercent, "Approved discount percent");
        String safeReason = requireReason(decisionReason);

        SubscriptionDiscountOverride existing = requirePendingOverride(overrideId);
        int actorUserId = currentUserId();
        int approvalId = resolveApprovalId(existing, safeReason);

        subscriptionDAO.updateApprovalStatus(approvalId, SubscriptionApprovalStatus.APPROVED, actorUserId);
        subscriptionDAO.updateDiscountOverrideStatus(
                overrideId,
                SubscriptionDiscountOverrideStatus.APPROVED,
                approvedDiscountPercent,
                actorUserId,
                approvalId);

        appendAuditLog(
                "OVERRIDE_APPROVED",
                "subscription_discount_overrides",
                String.valueOf(overrideId),
                actorUserId,
                approvalId,
                safeReason,
                "{\"status\":\"PENDING\",\"requested_discount_percent\":" + existing.requestedDiscountPercent() + "}",
                "{\"status\":\"APPROVED\",\"approved_discount_percent\":" + approvedDiscountPercent + "}");

        return new OverrideDecisionResult(
                overrideId,
                approvalId,
                SubscriptionDiscountOverrideStatus.APPROVED,
                approvedDiscountPercent,
                safeReason);
    }

    public OverrideDecisionResult rejectManualOverride(int overrideId, String decisionReason) throws SQLException {
        RbacPolicy.requireCurrentUser(Permission.APPROVE_SUBSCRIPTION_OVERRIDES);
        String safeReason = requireReason(decisionReason);

        SubscriptionDiscountOverride existing = requirePendingOverride(overrideId);
        int actorUserId = currentUserId();
        int approvalId = resolveApprovalId(existing, safeReason);

        subscriptionDAO.updateApprovalStatus(approvalId, SubscriptionApprovalStatus.REJECTED, actorUserId);
        subscriptionDAO.updateDiscountOverrideStatus(
                overrideId,
                SubscriptionDiscountOverrideStatus.REJECTED,
                null,
                actorUserId,
                approvalId);

        appendAuditLog(
                "OVERRIDE_REJECTED",
                "subscription_discount_overrides",
                String.valueOf(overrideId),
                actorUserId,
                approvalId,
                safeReason,
                "{\"status\":\"PENDING\",\"requested_discount_percent\":" + existing.requestedDiscountPercent() + "}",
                "{\"status\":\"REJECTED\"}");

        return new OverrideDecisionResult(
                overrideId,
                approvalId,
                SubscriptionDiscountOverrideStatus.REJECTED,
                0.0,
                safeReason);
    }

    public List<SubscriptionDiscountOverride> getPendingOverrides() {
        RbacPolicy.requireCurrentUser(Permission.APPROVE_SUBSCRIPTION_OVERRIDES);
        return subscriptionDAO.getPendingDiscountOverrides();
    }

    private SubscriptionDiscountOverride requirePendingOverride(int overrideId) {
        SubscriptionDiscountOverride existing = subscriptionDAO.findDiscountOverrideById(overrideId)
                .orElseThrow(() -> new IllegalArgumentException("Override not found: " + overrideId));
        if (existing.status() != SubscriptionDiscountOverrideStatus.PENDING) {
            throw new IllegalStateException("Override is not pending.");
        }
        return existing;
    }

    private int resolveApprovalId(SubscriptionDiscountOverride existing, String reason) throws SQLException {
        if (existing.approvalId() != null) {
            return existing.approvalId();
        }
        int approvalId = subscriptionDAO.createApproval(
                APPROVAL_TYPE_MANUAL_OVERRIDE,
                REF_TYPE_DISCOUNT_OVERRIDE,
                existing.overrideId(),
                existing.requestedByUserId(),
                reason);
        subscriptionDAO.attachApprovalToOverride(existing.overrideId(), approvalId);
        return approvalId;
    }

    private void appendAuditLog(
            String eventType,
            String entityType,
            String entityId,
            int actorUserId,
            Integer approvalId,
            String reason,
            String beforeJson,
            String afterJson) throws SQLException {
        String previousChecksum = subscriptionDAO.latestAuditChecksum().orElse("");
        String eventTimestamp = SubscriptionAuditChain.nowTimestamp();
        String checksum = SubscriptionAuditChain.computeChecksum(
                eventType,
                entityType,
                entityId,
                actorUserId,
                approvalId,
                reason,
                beforeJson,
                afterJson,
                previousChecksum,
                eventTimestamp);

        subscriptionDAO.appendSubscriptionAuditLog(
                eventType,
                entityType,
                entityId,
                actorUserId,
                approvalId,
                reason,
                beforeJson,
                afterJson,
                previousChecksum.isBlank() ? null : previousChecksum,
                checksum);
    }

    private String requireReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Reason is mandatory.");
        }
        return reason.trim();
    }

    private void validatePercent(double percent, String label) {
        if (percent <= 0.0 || percent > 100.0) {
            throw new IllegalArgumentException(label + " must be between 0 and 100.");
        }
    }

    private int currentUserId() {
        if (!UserSession.getInstance().isLoggedIn() || UserSession.getInstance().getUser() == null) {
            throw new SecurityException("Access denied: login required.");
        }
        return UserSession.getInstance().getUser().getId();
    }

    public record OverrideRequestResult(
            int overrideId,
            int approvalId,
            SubscriptionDiscountOverrideStatus status,
            String reason) {
    }

    public record OverrideDecisionResult(
            int overrideId,
            int approvalId,
            SubscriptionDiscountOverrideStatus status,
            double approvedDiscountPercent,
            String reason) {
    }
}
