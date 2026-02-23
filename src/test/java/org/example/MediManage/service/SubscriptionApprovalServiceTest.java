package org.example.MediManage.service;

import org.example.MediManage.dao.SubscriptionDAO;
import org.example.MediManage.dao.SubscriptionAIDecisionLogDAO;
import org.example.MediManage.model.SubscriptionApproval;
import org.example.MediManage.model.SubscriptionAIDecisionLog;
import org.example.MediManage.model.SubscriptionApprovalStatus;
import org.example.MediManage.model.SubscriptionDiscountOverride;
import org.example.MediManage.model.SubscriptionDiscountOverrideStatus;
import org.example.MediManage.model.User;
import org.example.MediManage.model.UserRole;
import org.example.MediManage.service.subscription.SubscriptionAIDecisionLogService;
import org.example.MediManage.service.subscription.SubscriptionOverrideRiskScoringEngine;
import org.example.MediManage.util.UserSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionApprovalServiceTest {
    private FakeSubscriptionDAO dao;
    private FakeSubscriptionAIDecisionLogDAO logDAO;
    private SubscriptionApprovalService service;

    @BeforeEach
    void setup() {
        dao = new FakeSubscriptionDAO();
        logDAO = new FakeSubscriptionAIDecisionLogDAO();
        service = new SubscriptionApprovalService(
                dao,
                new SubscriptionOverrideRiskScoringEngine(),
                new SubscriptionAIDecisionLogService(logDAO));
        UserSession.getInstance().logout();
    }

    @AfterEach
    void cleanup() {
        UserSession.getInstance().logout();
    }

    @Test
    void cashierCanRequestOverrideWithMandatoryReason() throws Exception {
        login(10, UserRole.CASHIER);

        SubscriptionApprovalService.OverrideRequestResult result = service.requestManualOverride(
                1001,
                2001,
                3001,
                4001,
                12.5,
                "Need discount for customer retention");

        assertTrue(result.overrideId() > 0);
        assertTrue(result.approvalId() > 0);
        assertEquals(SubscriptionDiscountOverrideStatus.PENDING, result.status());
        assertEquals(1, dao.getPendingDiscountOverrides().size());
    }

    @Test
    void overrideRequestWithoutReasonIsRejected() {
        login(10, UserRole.CASHIER);
        assertThrows(IllegalArgumentException.class,
                () -> service.requestManualOverride(1, 1, 1, 1, 10.0, "   "));
    }

    @Test
    void managerCanApprovePendingOverride() throws Exception {
        login(10, UserRole.CASHIER);
        SubscriptionApprovalService.OverrideRequestResult request = service.requestManualOverride(
                1002,
                2002,
                3002,
                4002,
                15.0,
                "Manual retention discount");

        login(20, UserRole.MANAGER);
        SubscriptionApprovalService.OverrideDecisionResult decision = service.approveManualOverride(
                request.overrideId(),
                12.0,
                "Approved after review");

        assertEquals(SubscriptionDiscountOverrideStatus.APPROVED, decision.status());
        SubscriptionDiscountOverride stored = dao.findDiscountOverrideById(request.overrideId()).orElseThrow();
        assertEquals(SubscriptionDiscountOverrideStatus.APPROVED, stored.status());
        assertEquals(12.0, stored.approvedDiscountPercent(), 0.0001);
        SubscriptionApproval approval = dao.findApprovalById(request.approvalId()).orElseThrow();
        assertEquals(SubscriptionApprovalStatus.APPROVED, approval.approvalStatus());
        assertFalse(dao.auditRows.isEmpty());
    }

    @Test
    void cashierCannotApproveOverride() throws Exception {
        login(10, UserRole.CASHIER);
        SubscriptionApprovalService.OverrideRequestResult request = service.requestManualOverride(
                1003,
                2003,
                3003,
                4003,
                9.0,
                "Special request");

        login(11, UserRole.CASHIER);
        assertThrows(SecurityException.class,
                () -> service.approveManualOverride(request.overrideId(), 8.0, "Not allowed"));
    }

    @Test
    void managerCannotApproveOwnOverrideRequest() throws Exception {
        login(20, UserRole.MANAGER);
        SubscriptionApprovalService.OverrideRequestResult request = service.requestManualOverride(
                1100,
                2100,
                3100,
                4100,
                10.0,
                "Manager requested exception");

        assertThrows(SecurityException.class,
                () -> service.approveManualOverride(request.overrideId(), 9.0, "Self approval attempt"));
        SubscriptionDiscountOverride stored = dao.findDiscountOverrideById(request.overrideId()).orElseThrow();
        assertEquals(SubscriptionDiscountOverrideStatus.PENDING, stored.status());
    }

    @Test
    void highRiskApprovalRequiresExplicitHumanReviewConfirmation() throws Exception {
        login(10, UserRole.CASHIER);
        SubscriptionApprovalService.OverrideRequestResult request = service.requestManualOverride(
                1200,
                2200,
                3200,
                4200,
                24.0,
                "Retention override for customer");

        dao.setOverrideAbuseSignals(List.of(
                new SubscriptionDAO.OverrideAbuseSignalRow(
                        10,
                        "cashier",
                        8,
                        2,
                        5,
                        1,
                        25.0,
                        62.5,
                        28.0,
                        35.0,
                        "2026-02-01 00:00:00",
                        "2026-02-23 00:00:00",
                        "HIGH")));

        login(20, UserRole.MANAGER);
        assertThrows(
                IllegalStateException.class,
                () -> service.approveManualOverride(request.overrideId(), 20.0, "Reviewed context"));

        SubscriptionDiscountOverride pending = dao.findDiscountOverrideById(request.overrideId()).orElseThrow();
        assertEquals(SubscriptionDiscountOverrideStatus.PENDING, pending.status());

        SubscriptionApprovalService.OverrideDecisionResult decision = service.approveManualOverride(
                request.overrideId(),
                20.0,
                "Reviewed context",
                true);
        assertEquals(SubscriptionDiscountOverrideStatus.APPROVED, decision.status());
    }

    @Test
    void approveManualOverrideLogsHighRiskEscalationReasonCode() throws Exception {
        FakeSubscriptionAIDecisionLogDAO logDAO = new FakeSubscriptionAIDecisionLogDAO();
        SubscriptionApprovalService loggingService = new SubscriptionApprovalService(
                dao,
                new SubscriptionOverrideRiskScoringEngine(),
                new SubscriptionAIDecisionLogService(logDAO));

        login(10, UserRole.CASHIER);
        SubscriptionApprovalService.OverrideRequestResult request = loggingService.requestManualOverride(
                1210,
                2210,
                3210,
                4210,
                24.0,
                "Retention override for customer");
        dao.setOverrideAbuseSignals(List.of(
                new SubscriptionDAO.OverrideAbuseSignalRow(
                        10,
                        "cashier",
                        8,
                        2,
                        5,
                        1,
                        25.0,
                        62.5,
                        28.0,
                        35.0,
                        "2026-02-01 00:00:00",
                        "2026-02-23 00:00:00",
                        "HIGH")));

        login(20, UserRole.MANAGER);
        assertThrows(
                IllegalStateException.class,
                () -> loggingService.approveManualOverride(request.overrideId(), 20.0, "Reviewed context"));

        assertFalse(logDAO.rows.isEmpty());
        SubscriptionAIDecisionLog logged = logDAO.rows.get(logDAO.rows.size() - 1);
        assertEquals("OVERRIDE_RISK_ASSESSMENT", logged.decisionType());
        assertEquals("OVERRIDE_RISK_MEDIUM_ESCALATE", logged.reasonCode());
        assertEquals(String.valueOf(request.overrideId()), logged.subjectRef());
    }

    @Test
    void managerCanReadOverrideFrequencyAlerts() {
        dao.setOverrideFrequencySnapshots(List.of(
                new SubscriptionDAO.OverrideFrequencySnapshot(
                        77,
                        8,
                        2,
                        3,
                        3,
                        "2026-02-23 00:00:00",
                        "2026-02-23 08:00:00")));

        login(20, UserRole.MANAGER);
        List<SubscriptionApprovalService.OverrideFrequencyAlert> alerts = service.getOverrideFrequencyAlerts(24, 5);

        assertEquals(1, alerts.size());
        SubscriptionApprovalService.OverrideFrequencyAlert alert = alerts.get(0);
        assertEquals(77, alert.requestedByUserId());
        assertEquals(8, alert.totalRequests());
        assertEquals("MEDIUM", alert.severity());
        assertTrue(alert.message().contains("User #77"));
    }

    @Test
    void cashierCannotReadOverrideFrequencyAlerts() {
        login(10, UserRole.CASHIER);
        assertThrows(SecurityException.class, () -> service.getOverrideFrequencyAlerts(24, 5));
    }

    @Test
    void managerCanReadOverrideRiskAssessment() throws Exception {
        login(10, UserRole.CASHIER);
        SubscriptionApprovalService.OverrideRequestResult request = service.requestManualOverride(
                1004,
                2004,
                3004,
                4004,
                28.0,
                "High-value retention request");

        dao.setOverrideAbuseSignals(List.of(
                new SubscriptionDAO.OverrideAbuseSignalRow(
                        10,
                        "cashier",
                        7,
                        2,
                        4,
                        1,
                        28.57,
                        57.14,
                        26.0,
                        35.0,
                        "2026-02-01 00:00:00",
                        "2026-02-23 00:00:00",
                        "HIGH")));
        dao.setEnrollmentAbuseSignals(List.of(
                new SubscriptionDAO.EnrollmentAbuseSignalRow(
                        3004,
                        6,
                        3,
                        1,
                        1,
                        1,
                        2,
                        "2026-02-01 00:00:00",
                        "2026-02-23 00:00:00")));

        login(20, UserRole.MANAGER);
        SubscriptionOverrideRiskScoringEngine.OverrideRiskAssessment assessment = service
                .getOverrideRiskAssessment(request.overrideId());

        assertEquals(request.overrideId(), assessment.overrideId());
        assertTrue(assessment.riskScore() >= 45);
        assertTrue(assessment.riskBand().equals("MEDIUM") || assessment.riskBand().equals("HIGH"));
        assertTrue(assessment.summary().contains("Risk"));
    }

    @Test
    void cashierCannotReadOverrideRiskAssessment() throws Exception {
        login(10, UserRole.CASHIER);
        SubscriptionApprovalService.OverrideRequestResult request = service.requestManualOverride(
                1005,
                2005,
                3005,
                4005,
                12.0,
                "Routine request");

        login(11, UserRole.CASHIER);
        assertThrows(SecurityException.class, () -> service.getOverrideRiskAssessment(request.overrideId()));
    }

    private void login(int userId, UserRole role) {
        UserSession.getInstance().login(new User(userId, role.name().toLowerCase(), "", role));
    }

    private static class FakeSubscriptionAIDecisionLogDAO extends SubscriptionAIDecisionLogDAO {
        private final List<SubscriptionAIDecisionLog> rows = new ArrayList<>();

        @Override
        public long appendDecisionLog(SubscriptionAIDecisionLog decisionLog) {
            rows.add(decisionLog);
            return rows.size();
        }
    }

    private static class FakeSubscriptionDAO extends SubscriptionDAO {
        private int approvalSeq = 1;
        private int overrideSeq = 1;
        private final Map<Integer, SubscriptionApproval> approvals = new HashMap<>();
        private final Map<Integer, SubscriptionDiscountOverride> overrides = new HashMap<>();
        private final List<String> auditRows = new ArrayList<>();
        private List<OverrideFrequencySnapshot> overrideFrequencySnapshots = new ArrayList<>();
        private List<OverrideAbuseSignalRow> overrideAbuseSignals = new ArrayList<>();
        private List<EnrollmentAbuseSignalRow> enrollmentAbuseSignals = new ArrayList<>();
        private String latestChecksum;

        @Override
        public int createApproval(String approvalType, String requestRefType, int requestRefId, int requestedByUserId,
                String reason) {
            int id = approvalSeq++;
            approvals.put(id, new SubscriptionApproval(
                    id,
                    approvalType,
                    requestRefType,
                    requestRefId,
                    requestedByUserId,
                    null,
                    SubscriptionApprovalStatus.PENDING,
                    reason,
                    null,
                    "2026-02-23 00:00:00"));
            return id;
        }

        @Override
        public void updateApprovalStatus(int approvalId, SubscriptionApprovalStatus status, Integer approverUserId) {
            SubscriptionApproval current = approvals.get(approvalId);
            if (current == null) {
                return;
            }
            approvals.put(approvalId, new SubscriptionApproval(
                    current.approvalId(),
                    current.approvalType(),
                    current.requestRefType(),
                    current.requestRefId(),
                    current.requestedByUserId(),
                    approverUserId,
                    status,
                    current.reason(),
                    "2026-02-24 00:00:00",
                    current.createdAt()));
        }

        @Override
        public Optional<SubscriptionApproval> findApprovalById(int approvalId) {
            return Optional.ofNullable(approvals.get(approvalId));
        }

        @Override
        public int createDiscountOverride(Integer billId, Integer billItemId, Integer customerId, Integer enrollmentId,
                double requestedDiscountPercent, String reason, int requestedByUserId, Integer approvalId) {
            int id = overrideSeq++;
            overrides.put(id, new SubscriptionDiscountOverride(
                    id,
                    billId,
                    billItemId,
                    customerId,
                    enrollmentId,
                    requestedDiscountPercent,
                    null,
                    SubscriptionDiscountOverrideStatus.PENDING,
                    reason,
                    requestedByUserId,
                    null,
                    approvalId,
                    "2026-02-23 00:00:00",
                    null));
            return id;
        }

        @Override
        public void attachApprovalToOverride(int overrideId, int approvalId) {
            SubscriptionDiscountOverride current = overrides.get(overrideId);
            if (current == null) {
                return;
            }
            overrides.put(overrideId, new SubscriptionDiscountOverride(
                    current.overrideId(),
                    current.billId(),
                    current.billItemId(),
                    current.customerId(),
                    current.enrollmentId(),
                    current.requestedDiscountPercent(),
                    current.approvedDiscountPercent(),
                    current.status(),
                    current.reason(),
                    current.requestedByUserId(),
                    current.approvedByUserId(),
                    approvalId,
                    current.createdAt(),
                    current.approvedAt()));
        }

        @Override
        public void updateDiscountOverrideStatus(int overrideId, SubscriptionDiscountOverrideStatus status,
                Double approvedDiscountPercent, Integer approvedByUserId, Integer approvalId) {
            SubscriptionDiscountOverride current = overrides.get(overrideId);
            if (current == null) {
                return;
            }
            overrides.put(overrideId, new SubscriptionDiscountOverride(
                    current.overrideId(),
                    current.billId(),
                    current.billItemId(),
                    current.customerId(),
                    current.enrollmentId(),
                    current.requestedDiscountPercent(),
                    approvedDiscountPercent,
                    status,
                    current.reason(),
                    current.requestedByUserId(),
                    approvedByUserId,
                    approvalId,
                    current.createdAt(),
                    "2026-02-24 00:00:00"));
        }

        @Override
        public Optional<SubscriptionDiscountOverride> findDiscountOverrideById(int overrideId) {
            return Optional.ofNullable(overrides.get(overrideId));
        }

        @Override
        public List<SubscriptionDiscountOverride> getPendingDiscountOverrides() {
            List<SubscriptionDiscountOverride> pending = new ArrayList<>();
            for (SubscriptionDiscountOverride row : overrides.values()) {
                if (row.status() == SubscriptionDiscountOverrideStatus.PENDING) {
                    pending.add(row);
                }
            }
            return pending;
        }

        @Override
        public List<OverrideFrequencySnapshot> getOverrideFrequencySnapshots(String sinceTimestamp, int minRequests) {
            return new ArrayList<>(overrideFrequencySnapshots);
        }

        @Override
        public List<OverrideAbuseSignalRow> getOverrideAbuseSignals(
                java.time.LocalDate start,
                java.time.LocalDate end,
                int minRequests) {
            return new ArrayList<>(overrideAbuseSignals);
        }

        @Override
        public List<EnrollmentAbuseSignalRow> getEnrollmentAbuseSignals(
                java.time.LocalDate start,
                java.time.LocalDate end,
                int minEvents) {
            return new ArrayList<>(enrollmentAbuseSignals);
        }

        @Override
        public void appendSubscriptionAuditLog(String eventType, String entityType, String entityId, Integer actorUserId,
                Integer approvalId, String reason, String beforeJson, String afterJson, String previousChecksum,
                String checksum) throws SQLException {
            auditRows.add(eventType + ":" + entityId);
            latestChecksum = checksum;
        }

        @Override
        public Optional<String> latestAuditChecksum() {
            return Optional.ofNullable(latestChecksum);
        }

        void setOverrideFrequencySnapshots(List<OverrideFrequencySnapshot> snapshots) {
            overrideFrequencySnapshots = new ArrayList<>(snapshots);
        }

        void setOverrideAbuseSignals(List<OverrideAbuseSignalRow> rows) {
            overrideAbuseSignals = new ArrayList<>(rows);
        }

        void setEnrollmentAbuseSignals(List<EnrollmentAbuseSignalRow> rows) {
            enrollmentAbuseSignals = new ArrayList<>(rows);
        }
    }
}
