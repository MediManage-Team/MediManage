package org.example.MediManage.service;

import org.example.MediManage.dao.SubscriptionDAO;
import org.example.MediManage.model.SubscriptionApprovalStatus;
import org.example.MediManage.model.SubscriptionDiscountOverride;
import org.example.MediManage.model.SubscriptionDiscountOverrideStatus;
import org.example.MediManage.security.Permission;
import org.example.MediManage.security.RbacPolicy;
import org.example.MediManage.service.subscription.SubscriptionAIDecisionLogService;
import org.example.MediManage.service.subscription.SubscriptionAuditChain;
import org.example.MediManage.service.subscription.SubscriptionOverrideRiskScoringEngine;
import org.example.MediManage.util.UserSession;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SubscriptionApprovalService {
    private static final String APPROVAL_TYPE_MANUAL_OVERRIDE = "MANUAL_OVERRIDE";
    private static final String REF_TYPE_DISCOUNT_OVERRIDE = "DISCOUNT_OVERRIDE";
    private static final DateTimeFormatter DB_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DEFAULT_ALERT_WINDOW_HOURS = 24;
    private static final int DEFAULT_ALERT_THRESHOLD = 5;
    private static final int DEFAULT_RISK_LOOKBACK_DAYS = 30;
    private static final String DECISION_TYPE_OVERRIDE_RISK = "OVERRIDE_RISK_ASSESSMENT";
    private static final String SUBJECT_OVERRIDE = "OVERRIDE";
    private static final String MODEL_COMPONENT_OVERRIDE_RISK = "SubscriptionOverrideRiskScoringEngine";
    private static final String MODEL_VERSION_V1 = "v1";

    private final SubscriptionDAO subscriptionDAO;
    private final SubscriptionOverrideRiskScoringEngine overrideRiskScoringEngine;
    private final SubscriptionAIDecisionLogService aiDecisionLogService;

    public SubscriptionApprovalService() {
        this(
                new SubscriptionDAO(),
                new SubscriptionOverrideRiskScoringEngine(),
                SubscriptionAIDecisionLogService.getInstance());
    }

    SubscriptionApprovalService(SubscriptionDAO subscriptionDAO) {
        this(
                subscriptionDAO,
                new SubscriptionOverrideRiskScoringEngine(),
                SubscriptionAIDecisionLogService.getInstance());
    }

    SubscriptionApprovalService(
            SubscriptionDAO subscriptionDAO,
            SubscriptionOverrideRiskScoringEngine overrideRiskScoringEngine) {
        this(
                subscriptionDAO,
                overrideRiskScoringEngine,
                SubscriptionAIDecisionLogService.getInstance());
    }

    SubscriptionApprovalService(
            SubscriptionDAO subscriptionDAO,
            SubscriptionOverrideRiskScoringEngine overrideRiskScoringEngine,
            SubscriptionAIDecisionLogService aiDecisionLogService) {
        this.subscriptionDAO = subscriptionDAO;
        this.overrideRiskScoringEngine = overrideRiskScoringEngine;
        this.aiDecisionLogService = aiDecisionLogService;
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

        DashboardKpiService.invalidateSubscriptionMetrics();

        return new OverrideRequestResult(overrideId, approvalId, SubscriptionDiscountOverrideStatus.PENDING, safeReason);
    }

    public OverrideDecisionResult approveManualOverride(
            int overrideId,
            double approvedDiscountPercent,
            String decisionReason) throws SQLException {
        return approveManualOverride(overrideId, approvedDiscountPercent, decisionReason, false);
    }

    public OverrideDecisionResult approveManualOverride(
            int overrideId,
            double approvedDiscountPercent,
            String decisionReason,
            boolean highRiskHumanReviewConfirmed) throws SQLException {
        RbacPolicy.requireCurrentUser(Permission.APPROVE_SUBSCRIPTION_OVERRIDES);
        validatePercent(approvedDiscountPercent, "Approved discount percent");
        String safeReason = requireReason(decisionReason);

        SubscriptionDiscountOverride existing = requirePendingOverride(overrideId);
        int actorUserId = currentUserId();
        ensureNotSelfApproval(actorUserId, existing.requestedByUserId());
        SubscriptionOverrideRiskScoringEngine.OverrideRiskAssessment riskAssessment = buildOverrideRiskAssessment(
                existing,
                DEFAULT_RISK_LOOKBACK_DAYS);
        logOverrideRiskDecision(
                existing,
                riskAssessment,
                DEFAULT_RISK_LOOKBACK_DAYS,
                highRiskHumanReviewConfirmed,
                actorUserId);
        enforceHighRiskHumanReview(riskAssessment, highRiskHumanReviewConfirmed);
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
                buildApprovedAuditAfterJson(approvedDiscountPercent, riskAssessment, highRiskHumanReviewConfirmed));

        DashboardKpiService.invalidateSubscriptionMetrics();

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
        ensureNotSelfApproval(actorUserId, existing.requestedByUserId());
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

        DashboardKpiService.invalidateSubscriptionMetrics();

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

    public List<OverrideFrequencyAlert> getOverrideFrequencyAlerts() {
        return getOverrideFrequencyAlerts(DEFAULT_ALERT_WINDOW_HOURS, DEFAULT_ALERT_THRESHOLD);
    }

    public List<OverrideFrequencyAlert> getOverrideFrequencyAlerts(int windowHours, int minRequests) {
        RbacPolicy.requireCurrentUser(Permission.APPROVE_SUBSCRIPTION_OVERRIDES);
        int safeWindowHours = Math.max(1, windowHours);
        int safeMinRequests = Math.max(1, minRequests);
        String sinceTimestamp = LocalDateTime.now()
                .minusHours(safeWindowHours)
                .format(DB_TIMESTAMP);

        List<SubscriptionDAO.OverrideFrequencySnapshot> snapshots = subscriptionDAO
                .getOverrideFrequencySnapshots(sinceTimestamp, safeMinRequests);

        return snapshots.stream()
                .map(snapshot -> toAlert(snapshot, safeWindowHours, safeMinRequests))
                .toList();
    }

    public SubscriptionOverrideRiskScoringEngine.OverrideRiskAssessment getOverrideRiskAssessment(int overrideId) {
        RbacPolicy.requireCurrentUser(Permission.APPROVE_SUBSCRIPTION_OVERRIDES);
        if (overrideId <= 0) {
            throw new IllegalArgumentException("Override id must be valid.");
        }
        SubscriptionDiscountOverride override = subscriptionDAO.findDiscountOverrideById(overrideId)
                .orElseThrow(() -> new IllegalArgumentException("Override not found: " + overrideId));
        return buildOverrideRiskAssessment(override, DEFAULT_RISK_LOOKBACK_DAYS);
    }

    public List<SubscriptionOverrideRiskScoringEngine.OverrideRiskAssessment> getPendingOverrideRiskAssessments(
            int limit) {
        RbacPolicy.requireCurrentUser(Permission.APPROVE_SUBSCRIPTION_OVERRIDES);
        int safeLimit = normalizeRiskAssessmentLimit(limit);
        List<SubscriptionDiscountOverride> pending = subscriptionDAO.getPendingDiscountOverrides();
        if (pending.isEmpty()) {
            return List.of();
        }

        List<SubscriptionOverrideRiskScoringEngine.OverrideRiskAssessment> rows = new ArrayList<>();
        for (SubscriptionDiscountOverride override : pending) {
            if (override == null) {
                continue;
            }
            rows.add(buildOverrideRiskAssessment(override, DEFAULT_RISK_LOOKBACK_DAYS));
        }
        rows.sort((left, right) -> Integer.compare(right.riskScore(), left.riskScore()));
        if (rows.size() <= safeLimit) {
            return rows;
        }
        return rows.subList(0, safeLimit);
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

    private void ensureNotSelfApproval(int actorUserId, int requesterUserId) {
        if (actorUserId == requesterUserId) {
            throw new SecurityException("Self-approval is not allowed for subscription overrides.");
        }
    }

    private void enforceHighRiskHumanReview(
            SubscriptionOverrideRiskScoringEngine.OverrideRiskAssessment riskAssessment,
            boolean highRiskHumanReviewConfirmed) {
        if (riskAssessment == null || !riskAssessment.escalationRecommended()) {
            return;
        }
        if (!highRiskHumanReviewConfirmed) {
            throw new IllegalStateException(
                    "High-risk AI advisory requires explicit human review confirmation before approval.");
        }
    }

    private String buildApprovedAuditAfterJson(
            double approvedDiscountPercent,
            SubscriptionOverrideRiskScoringEngine.OverrideRiskAssessment riskAssessment,
            boolean highRiskHumanReviewConfirmed) {
        String riskBand = riskAssessment == null ? "UNKNOWN" : riskAssessment.riskBand();
        int riskScore = riskAssessment == null ? 0 : riskAssessment.riskScore();
        boolean escalationRecommended = riskAssessment != null && riskAssessment.escalationRecommended();
        return String.format(
                Locale.US,
                "{\"status\":\"APPROVED\",\"approved_discount_percent\":%.2f,\"ai_risk_band\":\"%s\",\"ai_risk_score\":%d,\"ai_escalation_recommended\":%s,\"high_risk_human_review_confirmed\":%s}",
                approvedDiscountPercent,
                riskBand,
                riskScore,
                escalationRecommended,
                highRiskHumanReviewConfirmed);
    }

    private void logOverrideRiskDecision(
            SubscriptionDiscountOverride override,
            SubscriptionOverrideRiskScoringEngine.OverrideRiskAssessment riskAssessment,
            int lookbackDays,
            boolean highRiskHumanReviewConfirmed,
            int actorUserId) {
        if (override == null || riskAssessment == null) {
            return;
        }
        String reasonCode = resolveOverrideRiskReasonCode(riskAssessment, highRiskHumanReviewConfirmed);
        String message = riskAssessment.summary() == null || riskAssessment.summary().isBlank()
                ? "Override risk assessment generated."
                : riskAssessment.summary();
        String payload = "{"
                + "\"override_id\":" + override.overrideId()
                + ",\"requested_discount_percent\":" + override.requestedDiscountPercent()
                + ",\"risk_score\":" + riskAssessment.riskScore()
                + ",\"risk_band\":\"" + json(riskAssessment.riskBand()) + "\""
                + ",\"escalation_recommended\":" + riskAssessment.escalationRecommended()
                + ",\"lookback_days\":" + lookbackDays
                + ",\"high_risk_human_review_confirmed\":" + highRiskHumanReviewConfirmed
                + ",\"requester_recent_request_count\":" + riskAssessment.requesterRecentRequestCount()
                + ",\"requester_recent_rejection_rate_percent\":" + riskAssessment.requesterRecentRejectionRatePercent()
                + ",\"customer_recent_lifecycle_events\":" + riskAssessment.customerRecentLifecycleEvents()
                + ",\"summary\":\"" + json(riskAssessment.summary()) + "\""
                + ",\"rationale\":\"" + json(riskAssessment.rationale()) + "\""
                + ",\"recommended_action\":\"" + json(riskAssessment.recommendedAction()) + "\""
                + "}";
        aiDecisionLogService.logDecision(
                DECISION_TYPE_OVERRIDE_RISK,
                SUBJECT_OVERRIDE,
                String.valueOf(override.overrideId()),
                reasonCode,
                message,
                payload,
                MODEL_COMPONENT_OVERRIDE_RISK,
                MODEL_VERSION_V1,
                null,
                null,
                actorUserId);
    }

    private String resolveOverrideRiskReasonCode(
            SubscriptionOverrideRiskScoringEngine.OverrideRiskAssessment riskAssessment,
            boolean highRiskHumanReviewConfirmed) {
        if (riskAssessment == null) {
            return "OVERRIDE_RISK_UNKNOWN";
        }
        String riskBand = riskAssessment.riskBand() == null ? "UNKNOWN" : riskAssessment.riskBand().trim().toUpperCase(Locale.US);
        if ("HIGH".equals(riskBand)) {
            return highRiskHumanReviewConfirmed
                    ? "OVERRIDE_RISK_HIGH_ESCALATE_CONFIRMED"
                    : "OVERRIDE_RISK_HIGH_ESCALATE_REQUIRED";
        }
        if ("MEDIUM".equals(riskBand) && riskAssessment.escalationRecommended()) {
            return "OVERRIDE_RISK_MEDIUM_ESCALATE";
        }
        if ("MEDIUM".equals(riskBand)) {
            return "OVERRIDE_RISK_MEDIUM_STANDARD";
        }
        if ("LOW".equals(riskBand)) {
            return "OVERRIDE_RISK_LOW_STANDARD";
        }
        return "OVERRIDE_RISK_UNKNOWN";
    }

    private OverrideFrequencyAlert toAlert(
            SubscriptionDAO.OverrideFrequencySnapshot snapshot,
            int windowHours,
            int minRequests) {
        String severity = snapshot.totalRequests() >= (minRequests * 2) ? "HIGH" : "MEDIUM";
        String message = "User #" + snapshot.requestedByUserId()
                + " submitted " + snapshot.totalRequests()
                + " override requests in the last " + windowHours + "h"
                + " (pending: " + snapshot.pendingCount()
                + ", approved: " + snapshot.approvedCount()
                + ", rejected: " + snapshot.rejectedCount() + ").";
        return new OverrideFrequencyAlert(
                snapshot.requestedByUserId(),
                snapshot.totalRequests(),
                snapshot.approvedCount(),
                snapshot.rejectedCount(),
                snapshot.pendingCount(),
                windowHours,
                severity,
                snapshot.firstRequestAt(),
                snapshot.latestRequestAt(),
                message);
    }

    private SubscriptionOverrideRiskScoringEngine.OverrideRiskAssessment buildOverrideRiskAssessment(
            SubscriptionDiscountOverride override,
            int lookbackDays) {
        int safeLookbackDays = Math.max(1, lookbackDays);
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(safeLookbackDays - 1L);

        SubscriptionDAO.OverrideAbuseSignalRow requesterSignal = subscriptionDAO
                .getOverrideAbuseSignals(startDate, endDate, 1)
                .stream()
                .filter(row -> row != null && row.requestedByUserId() == override.requestedByUserId())
                .findFirst()
                .orElse(null);

        Integer customerId = resolveCustomerId(override);
        SubscriptionDAO.EnrollmentAbuseSignalRow customerSignal = customerId == null
                ? null
                : subscriptionDAO.getEnrollmentAbuseSignals(startDate, endDate, 1)
                        .stream()
                        .filter(row -> row != null && row.customerId() == customerId)
                        .findFirst()
                        .orElse(null);

        return overrideRiskScoringEngine.score(
                override,
                requesterSignal,
                customerSignal,
                safeLookbackDays);
    }

    private Integer resolveCustomerId(SubscriptionDiscountOverride override) {
        if (override == null) {
            return null;
        }
        if (override.customerId() != null && override.customerId() > 0) {
            return override.customerId();
        }
        if (override.enrollmentId() == null || override.enrollmentId() <= 0) {
            return null;
        }
        return subscriptionDAO.findEnrollmentById(override.enrollmentId())
                .map(enrollment -> enrollment.customerId())
                .orElse(null);
    }

    private int normalizeRiskAssessmentLimit(int limit) {
        if (limit <= 0) {
            return 50;
        }
        return Math.max(1, Math.min(500, limit));
    }

    private String json(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
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

    public record OverrideFrequencyAlert(
            int requestedByUserId,
            int totalRequests,
            int approvedCount,
            int rejectedCount,
            int pendingCount,
            int windowHours,
            String severity,
            String firstRequestAt,
            String latestRequestAt,
            String message) {
    }
}
