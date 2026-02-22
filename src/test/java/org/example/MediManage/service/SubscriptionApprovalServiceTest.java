package org.example.MediManage.service;

import org.example.MediManage.dao.SubscriptionDAO;
import org.example.MediManage.model.SubscriptionApproval;
import org.example.MediManage.model.SubscriptionApprovalStatus;
import org.example.MediManage.model.SubscriptionDiscountOverride;
import org.example.MediManage.model.SubscriptionDiscountOverrideStatus;
import org.example.MediManage.model.User;
import org.example.MediManage.model.UserRole;
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
    private SubscriptionApprovalService service;

    @BeforeEach
    void setup() {
        dao = new FakeSubscriptionDAO();
        service = new SubscriptionApprovalService(dao);
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

    private void login(int userId, UserRole role) {
        UserSession.getInstance().login(new User(userId, role.name().toLowerCase(), "", role));
    }

    private static class FakeSubscriptionDAO extends SubscriptionDAO {
        private int approvalSeq = 1;
        private int overrideSeq = 1;
        private final Map<Integer, SubscriptionApproval> approvals = new HashMap<>();
        private final Map<Integer, SubscriptionDiscountOverride> overrides = new HashMap<>();
        private final List<String> auditRows = new ArrayList<>();
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
    }
}
