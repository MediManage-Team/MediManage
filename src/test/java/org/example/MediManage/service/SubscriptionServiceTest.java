package org.example.MediManage.service;

import org.example.MediManage.dao.SubscriptionDAO;
import org.example.MediManage.model.CustomerSubscription;
import org.example.MediManage.model.SubscriptionEnrollmentStatus;
import org.example.MediManage.model.SubscriptionPlanMedicineRule;
import org.example.MediManage.model.SubscriptionPlan;
import org.example.MediManage.model.SubscriptionPlanStatus;
import org.example.MediManage.model.User;
import org.example.MediManage.model.UserRole;
import org.example.MediManage.service.subscription.SubscriptionEligibilityCode;
import org.example.MediManage.service.subscription.SubscriptionEligibilityResult;
import org.example.MediManage.util.UserSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionServiceTest {

    private FakeSubscriptionDAO dao;
    private SubscriptionService service;

    @BeforeEach
    void setup() {
        dao = new FakeSubscriptionDAO();
        service = new SubscriptionService(dao);
        UserSession.getInstance().logout();
    }

    @AfterEach
    void cleanup() {
        UserSession.getInstance().logout();
    }

    @Test
    void managerCanCreatePlanButCashierCannot() throws Exception {
        login(1, UserRole.MANAGER);
        int planId = service.createPlan(samplePlan(0, "PRO-MONTHLY", SubscriptionPlanStatus.DRAFT));
        assertTrue(planId > 0);

        login(2, UserRole.CASHIER);
        assertThrows(SecurityException.class,
                () -> service.createPlan(samplePlan(0, "PRO-CASH", SubscriptionPlanStatus.DRAFT)));
    }

    @Test
    void cashierCanEnrollCustomerButStaffCannot() throws Exception {
        dao.createPlan(samplePlan(0, "ACTIVE-1", SubscriptionPlanStatus.ACTIVE), 1);

        login(10, UserRole.CASHIER);
        assertDoesNotThrow(() -> service.enrollCustomer(100, 1, LocalDate.now(), "POS", null, null));
        assertEquals(1, dao.getCustomerEnrollments(100).size());

        login(11, UserRole.STAFF);
        assertThrows(SecurityException.class,
                () -> service.enrollCustomer(101, 1, LocalDate.now(), "POS", null, null));
    }

    @Test
    void freezeAndUnfreezeEnrollmentTransitionsWork() throws Exception {
        dao.createPlan(samplePlan(0, "ACTIVE-2", SubscriptionPlanStatus.ACTIVE), 1);
        login(20, UserRole.MANAGER);
        int enrollmentId = service.enrollCustomer(200, 1, LocalDate.now(), "POS", null, null);

        service.freezeEnrollment(enrollmentId, "Travel", null, null);
        Optional<CustomerSubscription> frozen = dao.findEnrollmentById(enrollmentId);
        assertTrue(frozen.isPresent());
        assertEquals(SubscriptionEnrollmentStatus.FROZEN, frozen.get().status());

        service.unfreezeEnrollment(enrollmentId, null, null);
        Optional<CustomerSubscription> activeAgain = dao.findEnrollmentById(enrollmentId);
        assertTrue(activeAgain.isPresent());
        assertEquals(SubscriptionEnrollmentStatus.ACTIVE, activeAgain.get().status());
    }

    @Test
    void evaluateEligibilityReturnsNoEnrollmentForUnknownCustomer() {
        SubscriptionEligibilityResult result = service.evaluateEligibility(9999);
        assertEquals(SubscriptionEligibilityCode.NO_ENROLLMENT, result.code());
    }

    @Test
    void evaluateEligibilityReturnsFrozenWhenEnrollmentIsFrozen() throws Exception {
        dao.createPlan(samplePlan(0, "ACTIVE-3", SubscriptionPlanStatus.ACTIVE), 1);
        login(30, UserRole.MANAGER);
        int enrollmentId = service.enrollCustomer(300, 1, LocalDate.now(), "POS", null, null);
        service.freezeEnrollment(enrollmentId, "Hold", null, null);

        SubscriptionEligibilityResult result = service.evaluateEligibility(300);
        assertEquals(SubscriptionEligibilityCode.ENROLLMENT_FROZEN, result.code());
    }

    @Test
    void managerCanManagePlanMedicineRulesButCashierCannot() throws Exception {
        dao.createPlan(samplePlan(0, "ACTIVE-4", SubscriptionPlanStatus.ACTIVE), 1);
        SubscriptionPlanMedicineRule rule = new SubscriptionPlanMedicineRule(
                0,
                1,
                101,
                "Paracetamol",
                true,
                12.5,
                50.0,
                6.0,
                true,
                null,
                null);

        login(40, UserRole.MANAGER);
        assertDoesNotThrow(() -> service.upsertPlanMedicineRule(rule));
        assertEquals(1, service.getPlanMedicineRules(1).size());

        login(41, UserRole.CASHIER);
        assertThrows(SecurityException.class, () -> service.getPlanMedicineRules(1));
        assertThrows(SecurityException.class, () -> service.upsertPlanMedicineRule(rule));
    }

    @Test
    void planMedicineRuleValidationRejectsInvalidDiscount() {
        login(42, UserRole.MANAGER);
        SubscriptionPlanMedicineRule invalidRule = new SubscriptionPlanMedicineRule(
                0,
                1,
                101,
                "Test",
                true,
                150.0,
                null,
                null,
                true,
                null,
                null);
        assertThrows(IllegalArgumentException.class, () -> service.upsertPlanMedicineRule(invalidRule));
    }

    @Test
    void policyChangesWriteChainedAuditRows() throws Exception {
        login(50, UserRole.MANAGER);
        int planId = service.createPlan(samplePlan(0, "AUDIT-1", SubscriptionPlanStatus.DRAFT));
        service.activatePlan(planId);
        service.upsertPlanMedicineRule(new SubscriptionPlanMedicineRule(
                0,
                planId,
                1001,
                "Audit Medicine",
                true,
                10.0,
                20.0,
                5.0,
                true,
                null,
                null));

        assertTrue(dao.auditRows.size() >= 3);
        FakeSubscriptionDAO.AuditEntry second = dao.auditRows.get(1);
        FakeSubscriptionDAO.AuditEntry third = dao.auditRows.get(2);
        assertNotNull(second.previousChecksum());
        assertNotNull(third.previousChecksum());
        assertEquals(dao.auditRows.get(0).checksum(), second.previousChecksum());
        assertEquals(second.checksum(), third.previousChecksum());
    }

    private SubscriptionPlan samplePlan(int id, String code, SubscriptionPlanStatus status) {
        return new SubscriptionPlan(
                id,
                code,
                "Plan " + code,
                "test",
                499.0,
                30,
                7,
                10.0,
                20.0,
                5.0,
                status,
                false,
                true,
                null,
                null,
                null,
                null);
    }

    private void login(int userId, UserRole role) {
        UserSession.getInstance().login(new User(userId, role.name().toLowerCase(), "", role));
    }

    private static class FakeSubscriptionDAO extends SubscriptionDAO {
        private int planSeq = 1;
        private int enrollmentSeq = 1;
        private int ruleSeq = 1;
        private final Map<Integer, SubscriptionPlan> plans = new HashMap<>();
        private final Map<Integer, CustomerSubscription> enrollments = new HashMap<>();
        private final Map<Integer, SubscriptionPlanMedicineRule> planMedicineRules = new HashMap<>();
        private final List<AuditEntry> auditRows = new ArrayList<>();
        private String latestChecksum;

        @Override
        public int createPlan(SubscriptionPlan plan, int actorUserId) {
            int id = planSeq++;
            SubscriptionPlan stored = new SubscriptionPlan(
                    id,
                    plan.planCode(),
                    plan.planName(),
                    plan.description(),
                    plan.price(),
                    plan.durationDays(),
                    plan.graceDays(),
                    plan.defaultDiscountPercent(),
                    plan.maxDiscountPercent(),
                    plan.minimumMarginPercent(),
                    plan.status(),
                    plan.autoRenew(),
                    plan.requiresApproval(),
                    actorUserId,
                    actorUserId,
                    "2026-02-23 00:00:00",
                    "2026-02-23 00:00:00");
            plans.put(id, stored);
            return id;
        }

        @Override
        public void updatePlanStatus(int planId, SubscriptionPlanStatus status, int actorUserId) {
            SubscriptionPlan current = plans.get(planId);
            if (current == null) {
                return;
            }
            plans.put(planId, current.withStatus(status, actorUserId));
        }

        @Override
        public void updatePlan(SubscriptionPlan plan, int actorUserId) {
            SubscriptionPlan current = plans.get(plan.planId());
            if (current == null) {
                return;
            }
            plans.put(plan.planId(), new SubscriptionPlan(
                    current.planId(),
                    plan.planCode(),
                    plan.planName(),
                    plan.description(),
                    plan.price(),
                    plan.durationDays(),
                    plan.graceDays(),
                    plan.defaultDiscountPercent(),
                    plan.maxDiscountPercent(),
                    plan.minimumMarginPercent(),
                    plan.status(),
                    plan.autoRenew(),
                    plan.requiresApproval(),
                    current.createdByUserId(),
                    actorUserId,
                    current.createdAt(),
                    "2026-02-24 00:00:00"));
        }

        @Override
        public Optional<SubscriptionPlan> findPlanById(int planId) {
            return Optional.ofNullable(plans.get(planId));
        }

        @Override
        public Optional<EligibilityContext> findLatestEligibilityContext(int customerId) {
            CustomerSubscription latest = null;
            for (CustomerSubscription enrollment : enrollments.values()) {
                if (enrollment.customerId() != customerId) {
                    continue;
                }
                if (latest == null || enrollment.enrollmentId() > latest.enrollmentId()) {
                    latest = enrollment;
                }
            }
            if (latest == null) {
                return Optional.empty();
            }
            SubscriptionPlan plan = plans.get(latest.planId());
            SubscriptionPlanStatus planStatus = plan == null ? null : plan.status();
            String planName = plan == null ? null : plan.planName();
            return Optional.of(new EligibilityContext(latest, planStatus, planName));
        }

        @Override
        public int createEnrollment(CustomerSubscription enrollment, int actorUserId) {
            int id = enrollmentSeq++;
            CustomerSubscription stored = new CustomerSubscription(
                    id,
                    enrollment.customerId(),
                    enrollment.planId(),
                    enrollment.status(),
                    enrollment.startDate(),
                    enrollment.endDate(),
                    enrollment.graceEndDate(),
                    enrollment.enrollmentChannel(),
                    actorUserId,
                    enrollment.approvedByUserId(),
                    enrollment.approvalReference(),
                    enrollment.cancellationReason(),
                    enrollment.frozenReason(),
                    "2026-02-23 00:00:00",
                    "2026-02-23 00:00:00");
            enrollments.put(id, stored);
            return id;
        }

        @Override
        public Optional<CustomerSubscription> findEnrollmentById(int enrollmentId) {
            return Optional.ofNullable(enrollments.get(enrollmentId));
        }

        @Override
        public List<SubscriptionPlanMedicineRule> getPlanMedicineRules(int planId) {
            List<SubscriptionPlanMedicineRule> result = new ArrayList<>();
            for (SubscriptionPlanMedicineRule rule : planMedicineRules.values()) {
                if (rule.planId() == planId) {
                    result.add(rule);
                }
            }
            return result;
        }

        @Override
        public Optional<SubscriptionPlanMedicineRule> findPlanMedicineRuleByPlanAndMedicine(int planId, int medicineId) {
            for (SubscriptionPlanMedicineRule rule : planMedicineRules.values()) {
                if (rule.planId() == planId && rule.medicineId() == medicineId) {
                    return Optional.of(rule);
                }
            }
            return Optional.empty();
        }

        @Override
        public Optional<SubscriptionPlanMedicineRule> findPlanMedicineRuleById(int ruleId) {
            return Optional.ofNullable(planMedicineRules.get(ruleId));
        }

        @Override
        public void upsertPlanMedicineRule(
                int planId,
                int medicineId,
                boolean includeRule,
                double discountPercent,
                Double maxDiscountAmount,
                Double minMarginPercent,
                boolean active) {
            SubscriptionPlanMedicineRule existing = null;
            for (SubscriptionPlanMedicineRule row : planMedicineRules.values()) {
                if (row.planId() == planId && row.medicineId() == medicineId) {
                    existing = row;
                    break;
                }
            }

            if (existing != null) {
                planMedicineRules.put(existing.ruleId(), new SubscriptionPlanMedicineRule(
                        existing.ruleId(),
                        planId,
                        medicineId,
                        existing.medicineName(),
                        includeRule,
                        discountPercent,
                        maxDiscountAmount,
                        minMarginPercent,
                        active,
                        existing.createdAt(),
                        "2026-02-24 00:00:00"));
                return;
            }

            int newRuleId = ruleSeq++;
            planMedicineRules.put(newRuleId, new SubscriptionPlanMedicineRule(
                    newRuleId,
                    planId,
                    medicineId,
                    "Medicine-" + medicineId,
                    includeRule,
                    discountPercent,
                    maxDiscountAmount,
                    minMarginPercent,
                    active,
                    "2026-02-24 00:00:00",
                    "2026-02-24 00:00:00"));
        }

        @Override
        public void deletePlanMedicineRule(int ruleId) {
            planMedicineRules.remove(ruleId);
        }

        @Override
        public void appendSubscriptionAuditLog(
                String eventType,
                String entityType,
                String entityId,
                Integer actorUserId,
                Integer approvalId,
                String reason,
                String beforeJson,
                String afterJson,
                String previousChecksum,
                String checksum) {
            auditRows.add(new AuditEntry(
                    eventType,
                    entityType,
                    entityId,
                    previousChecksum,
                    checksum));
            latestChecksum = checksum;
        }

        @Override
        public Optional<String> latestAuditChecksum() {
            return Optional.ofNullable(latestChecksum);
        }

        @Override
        public void updateEnrollmentStatus(
                int enrollmentId,
                SubscriptionEnrollmentStatus status,
                String eventType,
                String reason,
                int actorUserId,
                Integer approverUserId,
                String approvalReference) {
            CustomerSubscription current = enrollments.get(enrollmentId);
            if (current == null) {
                return;
            }
            String cancellationReason = status == SubscriptionEnrollmentStatus.CANCELLED ? reason : null;
            String frozenReason = status == SubscriptionEnrollmentStatus.FROZEN ? reason : null;
            enrollments.put(enrollmentId, new CustomerSubscription(
                    current.enrollmentId(),
                    current.customerId(),
                    current.planId(),
                    status,
                    current.startDate(),
                    current.endDate(),
                    current.graceEndDate(),
                    current.enrollmentChannel(),
                    current.enrolledByUserId(),
                    approverUserId,
                    approvalReference,
                    cancellationReason,
                    frozenReason,
                    current.createdAt(),
                    "2026-02-24 00:00:00"));
        }

        @Override
        public List<CustomerSubscription> getCustomerEnrollments(int customerId) {
            List<CustomerSubscription> result = new ArrayList<>();
            for (CustomerSubscription enrollment : enrollments.values()) {
                if (enrollment.customerId() == customerId) {
                    result.add(enrollment);
                }
            }
            return result;
        }

        @Override
        public void renewEnrollment(
                int enrollmentId,
                String newStartDate,
                String newEndDate,
                String newGraceEndDate,
                int actorUserId,
                Integer approverUserId,
                String approvalReference) throws SQLException {
            CustomerSubscription current = enrollments.get(enrollmentId);
            if (current == null) {
                throw new SQLException("Enrollment not found");
            }
            enrollments.put(enrollmentId, new CustomerSubscription(
                    current.enrollmentId(),
                    current.customerId(),
                    current.planId(),
                    SubscriptionEnrollmentStatus.ACTIVE,
                    newStartDate,
                    newEndDate,
                    newGraceEndDate,
                    current.enrollmentChannel(),
                    current.enrolledByUserId(),
                    approverUserId,
                    approvalReference,
                    null,
                    null,
                    current.createdAt(),
                    "2026-02-24 00:00:00"));
        }

        private record AuditEntry(
                String eventType,
                String entityType,
                String entityId,
                String previousChecksum,
                String checksum) {
        }
    }
}
