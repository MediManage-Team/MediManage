package org.example.MediManage.service;

import org.example.MediManage.dao.SubscriptionDAO;
import org.example.MediManage.dao.SubscriptionAIDecisionLogDAO;
import org.example.MediManage.model.CustomerSubscription;
import org.example.MediManage.model.SubscriptionAIDecisionLog;
import org.example.MediManage.model.SubscriptionEnrollmentStatus;
import org.example.MediManage.model.SubscriptionPlanMedicineRule;
import org.example.MediManage.model.SubscriptionPlan;
import org.example.MediManage.model.SubscriptionPlanStatus;
import org.example.MediManage.model.User;
import org.example.MediManage.model.UserRole;
import org.example.MediManage.service.subscription.SubscriptionAIDecisionLogService;
import org.example.MediManage.service.subscription.SubscriptionDiscountAbuseDetectionEngine;
import org.example.MediManage.service.subscription.SubscriptionDynamicOfferSuggestionEngine;
import org.example.MediManage.service.subscription.SubscriptionEligibilityCode;
import org.example.MediManage.service.subscription.SubscriptionEligibilityResult;
import org.example.MediManage.service.subscription.SubscriptionPlanRecommendationEngine;
import org.example.MediManage.service.subscription.SubscriptionRenewalPropensityEngine;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionServiceTest {

    private FakeSubscriptionDAO dao;
    private FakeSubscriptionAIDecisionLogDAO logDAO;
    private SubscriptionService service;

    @BeforeEach
    void setup() {
        dao = new FakeSubscriptionDAO();
        logDAO = new FakeSubscriptionAIDecisionLogDAO();
        service = new SubscriptionService(
                dao,
                new SubscriptionPlanRecommendationEngine(),
                new SubscriptionRenewalPropensityEngine(),
                new SubscriptionDiscountAbuseDetectionEngine(),
                new SubscriptionDynamicOfferSuggestionEngine(),
                new SubscriptionAIDecisionLogService(logDAO));
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
    void planMedicineRuleCannotExceedPlanMaxDiscountPercent() throws Exception {
        dao.createPlan(samplePlan(0, "ACTIVE-6", SubscriptionPlanStatus.ACTIVE), 1);
        login(43, UserRole.MANAGER);
        SubscriptionPlanMedicineRule invalidRule = new SubscriptionPlanMedicineRule(
                0,
                1,
                101,
                "Test",
                true,
                25.0,
                null,
                5.0,
                true,
                null,
                null);
        assertThrows(IllegalArgumentException.class, () -> service.upsertPlanMedicineRule(invalidRule));
    }

    @Test
    void planMedicineRuleCannotUndercutPlanMinimumMarginFloor() throws Exception {
        dao.createPlan(samplePlan(0, "ACTIVE-7", SubscriptionPlanStatus.ACTIVE), 1);
        login(44, UserRole.MANAGER);
        SubscriptionPlanMedicineRule invalidRule = new SubscriptionPlanMedicineRule(
                0,
                1,
                101,
                "Test",
                true,
                10.0,
                null,
                2.0,
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

    @Test
    void createPlanRejectsDurationBelowPolicyMinimum() {
        login(60, UserRole.MANAGER);
        SubscriptionPlan invalid = new SubscriptionPlan(
                0,
                "RULE-DUR",
                "Rule Duration",
                "invalid duration",
                499.0,
                5,
                3,
                10.0,
                20.0,
                5.0,
                SubscriptionPlanStatus.DRAFT,
                false,
                true,
                null,
                null,
                null,
                null);
        assertThrows(IllegalArgumentException.class, () -> service.createPlan(invalid));
    }

    @Test
    void createPlanRejectsGraceDaysAbovePolicyMaximum() {
        login(61, UserRole.MANAGER);
        SubscriptionPlan invalid = new SubscriptionPlan(
                0,
                "RULE-GRACE",
                "Rule Grace",
                "invalid grace",
                499.0,
                30,
                31,
                10.0,
                20.0,
                5.0,
                SubscriptionPlanStatus.DRAFT,
                false,
                true,
                null,
                null,
                null,
                null);
        assertThrows(IllegalArgumentException.class, () -> service.createPlan(invalid));
    }

    @Test
    void cancelEnrollmentRequiresReason() throws Exception {
        dao.createPlan(samplePlan(0, "ACTIVE-5", SubscriptionPlanStatus.ACTIVE), 1);
        login(62, UserRole.MANAGER);
        int enrollmentId = service.enrollCustomer(620, 1, LocalDate.now(), "POS", null, null);

        assertThrows(IllegalArgumentException.class,
                () -> service.cancelEnrollment(enrollmentId, "   ", null, null));
        assertEquals(SubscriptionEnrollmentStatus.ACTIVE, dao.findEnrollmentById(enrollmentId).orElseThrow().status());
    }

    @Test
    void recommendPlansForCustomerReturnsRankedRecommendationsFromHistory() {
        dao.createPlan(new SubscriptionPlan(
                0,
                "REC-PREMIUM",
                "Premium Care",
                "premium recommendation plan",
                1500.0,
                30,
                7,
                25.0,
                25.0,
                5.0,
                SubscriptionPlanStatus.ACTIVE,
                false,
                true,
                null,
                null,
                null,
                null), 1);
        dao.createPlan(new SubscriptionPlan(
                0,
                "REC-SAVER",
                "Saver Care",
                "saver recommendation plan",
                199.0,
                30,
                7,
                10.0,
                15.0,
                5.0,
                SubscriptionPlanStatus.ACTIVE,
                false,
                true,
                null,
                null,
                null,
                null), 1);

        dao.seedPurchaseEvents(710, List.of(
                new SubscriptionDAO.CustomerPurchaseEvent("2026-01-01 10:00:00", 820.0),
                new SubscriptionDAO.CustomerPurchaseEvent("2026-01-11 10:00:00", 910.0),
                new SubscriptionDAO.CustomerPurchaseEvent("2026-01-21 10:00:00", 780.0),
                new SubscriptionDAO.CustomerPurchaseEvent("2026-02-01 10:00:00", 860.0),
                new SubscriptionDAO.CustomerPurchaseEvent("2026-02-11 10:00:00", 940.0),
                new SubscriptionDAO.CustomerPurchaseEvent("2026-02-21 10:00:00", 890.0),
                new SubscriptionDAO.CustomerPurchaseEvent("2026-03-03 10:00:00", 930.0),
                new SubscriptionDAO.CustomerPurchaseEvent("2026-03-13 10:00:00", 960.0),
                new SubscriptionDAO.CustomerPurchaseEvent("2026-03-23 10:00:00", 900.0)));
        dao.seedRefillEvents(710, List.of(
                new SubscriptionDAO.CustomerRefillEvent(101, "2026-01-01 10:00:00", 1, 240.0),
                new SubscriptionDAO.CustomerRefillEvent(101, "2026-01-11 10:00:00", 1, 240.0),
                new SubscriptionDAO.CustomerRefillEvent(101, "2026-01-21 10:00:00", 1, 240.0),
                new SubscriptionDAO.CustomerRefillEvent(101, "2026-02-01 10:00:00", 1, 240.0),
                new SubscriptionDAO.CustomerRefillEvent(101, "2026-02-11 10:00:00", 1, 240.0),
                new SubscriptionDAO.CustomerRefillEvent(101, "2026-02-21 10:00:00", 1, 240.0),
                new SubscriptionDAO.CustomerRefillEvent(101, "2026-03-03 10:00:00", 1, 240.0),
                new SubscriptionDAO.CustomerRefillEvent(101, "2026-03-13 10:00:00", 1, 240.0),
                new SubscriptionDAO.CustomerRefillEvent(101, "2026-03-23 10:00:00", 1, 240.0)));

        login(70, UserRole.CASHIER);
        var result = service.recommendPlansForCustomer(710);

        assertFalse(result.recommendations().isEmpty());
        assertEquals("REC-SAVER", result.recommendations().get(0).planCode());
        assertTrue(result.behavior().billCount() > 0);
        assertTrue(result.recommendations().get(0).expectedNetMonthlyBenefit() > 0.0);
    }

    @Test
    void recommendPlansForCustomerRequiresEnrollmentPermission() {
        login(71, UserRole.STAFF);
        assertThrows(SecurityException.class, () -> service.recommendPlansForCustomer(710));
    }

    @Test
    void recommendPlansForCustomerLogsNoHistoryReasonCode() {
        dao.createPlan(samplePlan(0, "REC-EMPTY", SubscriptionPlanStatus.ACTIVE), 1);
        FakeSubscriptionAIDecisionLogDAO logDAO = new FakeSubscriptionAIDecisionLogDAO();
        SubscriptionService loggingService = new SubscriptionService(
                dao,
                new SubscriptionPlanRecommendationEngine(),
                new SubscriptionRenewalPropensityEngine(),
                new SubscriptionDiscountAbuseDetectionEngine(),
                new SubscriptionDynamicOfferSuggestionEngine(),
                new SubscriptionAIDecisionLogService(logDAO));

        login(78, UserRole.CASHIER);
        var result = loggingService.recommendPlansForCustomer(7777);

        assertTrue(result.recommendations().isEmpty());
        assertEquals(1, logDAO.rows.size());
        SubscriptionAIDecisionLog logged = logDAO.rows.get(0);
        assertEquals("PLAN_RECOMMENDATION", logged.decisionType());
        assertEquals("PLAN_RECO_NO_HISTORY", logged.reasonCode());
        assertEquals("7777", logged.subjectRef());
    }

    @Test
    void scoreRenewalChurnRiskReturnsRankedRiskRows() {
        dao.seedRenewalDueCandidates(List.of(
                new SubscriptionDAO.RenewalDueCandidate(
                        901,
                        8101,
                        11,
                        "RISK-HI",
                        "High Risk Candidate",
                        "2025-12-01 00:00:00",
                        "2026-03-05 00:00:00",
                        700.0,
                        30),
                new SubscriptionDAO.RenewalDueCandidate(
                        902,
                        8102,
                        12,
                        "RISK-LO",
                        "Low Risk Candidate",
                        "2025-12-01 00:00:00",
                        "2026-03-20 00:00:00",
                        200.0,
                        30)));

        dao.seedPurchaseEvents(8101, List.of(
                new SubscriptionDAO.CustomerPurchaseEvent("2026-01-01 10:00:00", 120.0)));
        dao.seedRefillEvents(8101, List.of());
        dao.seedEnrollmentRenewalCount(901, 0);

        dao.seedPurchaseEvents(8102, List.of(
                new SubscriptionDAO.CustomerPurchaseEvent("2026-03-20 10:00:00", 350.0),
                new SubscriptionDAO.CustomerPurchaseEvent("2026-03-22 10:00:00", 300.0),
                new SubscriptionDAO.CustomerPurchaseEvent("2026-03-24 10:00:00", 280.0),
                new SubscriptionDAO.CustomerPurchaseEvent("2026-03-26 10:00:00", 320.0),
                new SubscriptionDAO.CustomerPurchaseEvent("2026-03-28 10:00:00", 310.0)));
        dao.seedRefillEvents(8102, List.of(
                new SubscriptionDAO.CustomerRefillEvent(9011, "2026-03-20 10:00:00", 1, 200.0),
                new SubscriptionDAO.CustomerRefillEvent(9011, "2026-03-24 10:00:00", 1, 210.0),
                new SubscriptionDAO.CustomerRefillEvent(9011, "2026-03-28 10:00:00", 1, 220.0)));
        dao.seedEnrollmentRenewalCount(902, 3);

        login(72, UserRole.CASHIER);
        var scores = service.scoreRenewalChurnRisk(21, 10);

        assertEquals(2, scores.size());
        assertEquals(901, scores.get(0).enrollmentId());
        assertTrue(scores.get(0).churnRiskScore() >= scores.get(1).churnRiskScore());
        assertEquals("HIGH", scores.get(0).riskBand());
    }

    @Test
    void scoreRenewalChurnRiskRequiresEnrollmentPermission() {
        login(73, UserRole.STAFF);
        assertThrows(SecurityException.class, () -> service.scoreRenewalChurnRisk(21, 10));
    }

    @Test
    void detectDiscountAbuseMergesEnrollmentOverrideAndBillingSignals() {
        dao.seedEnrollmentAbuseSignals(List.of(
                new SubscriptionDAO.EnrollmentAbuseSignalRow(
                        501,
                        9,
                        4,
                        3,
                        2,
                        2,
                        3,
                        "2026-02-01 10:00:00",
                        "2026-02-23 10:00:00")));
        dao.seedOverrideAbuseSignals(List.of(
                new SubscriptionDAO.OverrideAbuseSignalRow(
                        88,
                        "cashier-x",
                        8,
                        2,
                        5,
                        1,
                        25.0,
                        62.5,
                        29.0,
                        40.0,
                        "2026-02-02 10:00:00",
                        "2026-02-23 10:00:00",
                        "HIGH")));
        dao.seedPricingIntegrityAlerts(List.of(
                new SubscriptionDAO.PricingIntegrityAlertRow(
                        7001,
                        "2026-02-20 10:00:00",
                        91,
                        "PLAN-91",
                        "Plan 91",
                        100.0,
                        80.0,
                        20.0,
                        20.0,
                        20.0,
                        "SAVINGS_EXCEED_GROSS",
                        "HIGH"),
                new SubscriptionDAO.PricingIntegrityAlertRow(
                        7002,
                        "2026-02-21 10:00:00",
                        91,
                        "PLAN-91",
                        "Plan 91",
                        120.0,
                        90.0,
                        30.0,
                        25.0,
                        25.0,
                        "NEGATIVE_SAVINGS",
                        "HIGH")));

        login(74, UserRole.MANAGER);
        List<SubscriptionDiscountAbuseDetectionEngine.AbuseFinding> findings = service.detectDiscountAbuse(
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 23),
                20);

        assertEquals(3, findings.size());
        assertTrue(findings.stream().anyMatch(row -> "ENROLLMENT_PATTERN".equals(row.signalType())));
        assertTrue(findings.stream().anyMatch(row -> "OVERRIDE_PATTERN".equals(row.signalType())));
        assertTrue(findings.stream().anyMatch(row -> "BILLING_PATTERN".equals(row.signalType())));
    }

    @Test
    void detectDiscountAbuseRequiresOverrideApprovalPermission() {
        login(75, UserRole.CASHIER);
        assertThrows(SecurityException.class,
                () -> service.detectDiscountAbuse(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 23), 20));
    }

    @Test
    void suggestDynamicOffersForCustomerReturnsGuardrailedSuggestions() {
        dao.createPlan(new SubscriptionPlan(
                0,
                "OFFER-PLAN",
                "Offer Plan",
                "dynamic offer plan",
                299.0,
                30,
                7,
                8.0,
                15.0,
                90.0,
                SubscriptionPlanStatus.ACTIVE,
                false,
                true,
                null,
                null,
                null,
                null), 1);

        dao.seedPurchaseEvents(9201, List.of(
                new SubscriptionDAO.CustomerPurchaseEvent("2026-01-01 10:00:00", 300.0),
                new SubscriptionDAO.CustomerPurchaseEvent("2026-02-01 10:00:00", 260.0),
                new SubscriptionDAO.CustomerPurchaseEvent("2026-03-01 10:00:00", 240.0)));
        dao.seedRefillEvents(9201, List.of(
                new SubscriptionDAO.CustomerRefillEvent(111, "2026-01-01 10:00:00", 1, 120.0),
                new SubscriptionDAO.CustomerRefillEvent(111, "2026-02-01 10:00:00", 1, 110.0),
                new SubscriptionDAO.CustomerRefillEvent(111, "2026-03-01 10:00:00", 1, 100.0)));
        dao.seedRenewalDueCandidates(List.of(
                new SubscriptionDAO.RenewalDueCandidate(
                        9901,
                        9201,
                        1,
                        "OFFER-PLAN",
                        "Offer Plan",
                        "2025-12-01 00:00:00",
                        "2026-03-05 00:00:00",
                        299.0,
                        30)));
        dao.seedEnrollmentRenewalCount(9901, 0);

        login(76, UserRole.CASHIER);
        List<SubscriptionDynamicOfferSuggestionEngine.DynamicOfferSuggestion> offers = service
                .suggestDynamicOffersForCustomer(9201, 3);

        assertFalse(offers.isEmpty());
        SubscriptionDynamicOfferSuggestionEngine.DynamicOfferSuggestion offer = offers.get(0);
        assertEquals("OFFER-PLAN", offer.planCode());
        assertTrue(offer.offerDiscountPercent() <= offer.guardrailMaxByPlanCapPercent() + 0.0001);
        assertTrue(offer.offerDiscountPercent() <= offer.guardrailMaxByMarginPercent() + 0.0001);
    }

    @Test
    void suggestDynamicOffersForCustomerRequiresEnrollmentPermission() {
        login(77, UserRole.STAFF);
        assertThrows(SecurityException.class, () -> service.suggestDynamicOffersForCustomer(9201, 3));
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

    private static class FakeSubscriptionAIDecisionLogDAO extends SubscriptionAIDecisionLogDAO {
        private final List<SubscriptionAIDecisionLog> rows = new ArrayList<>();

        @Override
        public long appendDecisionLog(SubscriptionAIDecisionLog decisionLog) {
            rows.add(decisionLog);
            return rows.size();
        }
    }

    private static class FakeSubscriptionDAO extends SubscriptionDAO {
        private int planSeq = 1;
        private int enrollmentSeq = 1;
        private int ruleSeq = 1;
        private final Map<Integer, SubscriptionPlan> plans = new HashMap<>();
        private final Map<Integer, CustomerSubscription> enrollments = new HashMap<>();
        private final Map<Integer, SubscriptionPlanMedicineRule> planMedicineRules = new HashMap<>();
        private final Map<Integer, List<SubscriptionDAO.CustomerPurchaseEvent>> purchaseEventsByCustomerId = new HashMap<>();
        private final Map<Integer, List<SubscriptionDAO.CustomerRefillEvent>> refillEventsByCustomerId = new HashMap<>();
        private final Map<Integer, Integer> renewalCountByEnrollmentId = new HashMap<>();
        private List<SubscriptionDAO.RenewalDueCandidate> renewalDueCandidates = new ArrayList<>();
        private List<SubscriptionDAO.EnrollmentAbuseSignalRow> enrollmentAbuseSignals = new ArrayList<>();
        private List<SubscriptionDAO.OverrideAbuseSignalRow> overrideAbuseSignals = new ArrayList<>();
        private List<SubscriptionDAO.PricingIntegrityAlertRow> pricingIntegrityAlerts = new ArrayList<>();
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
        public List<SubscriptionPlan> getAllPlans() {
            return new ArrayList<>(plans.values());
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
        public List<SubscriptionDAO.CustomerPurchaseEvent> getCustomerPurchaseEvents(int customerId, int lookbackDays) {
            return purchaseEventsByCustomerId.getOrDefault(customerId, List.of());
        }

        @Override
        public List<SubscriptionDAO.CustomerRefillEvent> getCustomerRefillEvents(int customerId, int lookbackDays) {
            return refillEventsByCustomerId.getOrDefault(customerId, List.of());
        }

        @Override
        public List<SubscriptionDAO.RenewalDueCandidate> getRenewalDueCandidates(int renewalWindowDays) {
            return renewalDueCandidates;
        }

        @Override
        public int getEnrollmentRenewalCount(int enrollmentId) {
            return renewalCountByEnrollmentId.getOrDefault(enrollmentId, 0);
        }

        @Override
        public List<SubscriptionDAO.EnrollmentAbuseSignalRow> getEnrollmentAbuseSignals(
                LocalDate start,
                LocalDate end,
                int minEvents) {
            return enrollmentAbuseSignals;
        }

        @Override
        public List<SubscriptionDAO.OverrideAbuseSignalRow> getOverrideAbuseSignals(
                LocalDate start,
                LocalDate end,
                int minRequests) {
            return overrideAbuseSignals;
        }

        @Override
        public List<SubscriptionDAO.PricingIntegrityAlertRow> getPricingIntegrityAlerts(
                LocalDate start,
                LocalDate end,
                int limit) {
            return pricingIntegrityAlerts;
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

        void seedPurchaseEvents(int customerId, List<SubscriptionDAO.CustomerPurchaseEvent> events) {
            purchaseEventsByCustomerId.put(customerId, events == null ? List.of() : List.copyOf(events));
        }

        void seedRefillEvents(int customerId, List<SubscriptionDAO.CustomerRefillEvent> events) {
            refillEventsByCustomerId.put(customerId, events == null ? List.of() : List.copyOf(events));
        }

        void seedRenewalDueCandidates(List<SubscriptionDAO.RenewalDueCandidate> candidates) {
            renewalDueCandidates = candidates == null ? List.of() : List.copyOf(candidates);
        }

        void seedEnrollmentRenewalCount(int enrollmentId, int count) {
            renewalCountByEnrollmentId.put(enrollmentId, Math.max(0, count));
        }

        void seedEnrollmentAbuseSignals(List<SubscriptionDAO.EnrollmentAbuseSignalRow> rows) {
            enrollmentAbuseSignals = rows == null ? List.of() : List.copyOf(rows);
        }

        void seedOverrideAbuseSignals(List<SubscriptionDAO.OverrideAbuseSignalRow> rows) {
            overrideAbuseSignals = rows == null ? List.of() : List.copyOf(rows);
        }

        void seedPricingIntegrityAlerts(List<SubscriptionDAO.PricingIntegrityAlertRow> rows) {
            pricingIntegrityAlerts = rows == null ? List.of() : List.copyOf(rows);
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
