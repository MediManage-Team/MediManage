package org.example.MediManage.dao;

import org.example.MediManage.DatabaseUtil;
import org.example.MediManage.model.CustomerSubscription;
import org.example.MediManage.model.SubscriptionEnrollmentStatus;
import org.example.MediManage.model.SubscriptionPlanMedicineRule;
import org.example.MediManage.model.SubscriptionPlan;
import org.example.MediManage.model.SubscriptionPlanStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionDAOIntegrationTest {
    private static final String DB_PATH_PROPERTY = "medimanage.db.path";
    private static Path testDbPath;

    @BeforeAll
    static void setupDb() throws Exception {
        Path tempDir = Files.createTempDirectory("medimanage-subscription-dao-tests-");
        testDbPath = tempDir.resolve("medimanage-subscription-dao.db");
        System.setProperty(DB_PATH_PROPERTY, testDbPath.toString());
        DatabaseUtil.initDB();
    }

    @AfterAll
    static void cleanupDb() {
        System.clearProperty(DB_PATH_PROPERTY);
        if (testDbPath == null) {
            return;
        }
        String baseName = testDbPath.getFileName().toString();
        tryDelete(testDbPath.resolveSibling(baseName + "-shm"));
        tryDelete(testDbPath.resolveSibling(baseName + "-wal"));
        tryDelete(testDbPath);
        Path parent = testDbPath.getParent();
        if (parent != null) {
            tryDelete(parent);
        }
    }

    @Test
    void createAndLifecycleEnrollmentFlow() throws Exception {
        SubscriptionDAO dao = new SubscriptionDAO();
        int actorId = insertUser("sub_admin_" + System.nanoTime(), "ADMIN");
        int customerId = insertCustomer("cust_" + System.nanoTime(), "9999999999");

        SubscriptionPlan plan = new SubscriptionPlan(
                0,
                "PLAN-" + System.nanoTime(),
                "Gold Care",
                "Gold plan for discounts",
                999.0,
                30,
                7,
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

        int planId = dao.createPlan(plan, actorId);
        assertTrue(planId > 0);

        dao.updatePlanStatus(planId, SubscriptionPlanStatus.ACTIVE, actorId);
        Optional<SubscriptionPlan> storedPlan = dao.findPlanById(planId);
        assertTrue(storedPlan.isPresent());
        assertEquals(SubscriptionPlanStatus.ACTIVE, storedPlan.get().status());

        CustomerSubscription enrollment = new CustomerSubscription(
                0,
                customerId,
                planId,
                SubscriptionEnrollmentStatus.ACTIVE,
                "2026-02-01 00:00:00",
                "2026-03-03 00:00:00",
                "2026-03-10 00:00:00",
                "POS",
                actorId,
                actorId,
                "APR-101",
                null,
                null,
                null,
                null);

        int enrollmentId = dao.createEnrollment(enrollment, actorId);
        assertTrue(enrollmentId > 0);

        Optional<CustomerSubscription> storedEnrollment = dao.findEnrollmentById(enrollmentId);
        assertTrue(storedEnrollment.isPresent());
        assertEquals(SubscriptionEnrollmentStatus.ACTIVE, storedEnrollment.get().status());

        dao.updateEnrollmentStatus(
                enrollmentId,
                SubscriptionEnrollmentStatus.FROZEN,
                "FREEZE",
                "Customer requested hold",
                actorId,
                actorId,
                "APR-102");

        Optional<CustomerSubscription> frozenEnrollment = dao.findEnrollmentById(enrollmentId);
        assertTrue(frozenEnrollment.isPresent());
        assertEquals(SubscriptionEnrollmentStatus.FROZEN, frozenEnrollment.get().status());
        assertEquals("Customer requested hold", frozenEnrollment.get().frozenReason());

        assertFalse(dao.getCustomerEnrollments(customerId).isEmpty());
        assertTrue(dao.findApplicableSubscription(customerId).isEmpty());
    }

    @Test
    void upsertAndDeletePlanMedicineRuleFlow() throws Exception {
        SubscriptionDAO dao = new SubscriptionDAO();
        int actorId = insertUser("sub_rule_admin_" + System.nanoTime(), "ADMIN");
        int planId = dao.createPlan(new SubscriptionPlan(
                0,
                "RULE-" + System.nanoTime(),
                "Rule Plan",
                "Plan with medicine rules",
                899.0,
                30,
                5,
                8.0,
                15.0,
                5.0,
                SubscriptionPlanStatus.ACTIVE,
                false,
                true,
                null,
                null,
                null,
                null), actorId);

        int medicineId = insertMedicine("RuleMed-" + System.nanoTime(), 100.0);

        dao.upsertPlanMedicineRule(planId, medicineId, true, 11.0, 25.0, 4.0, true);
        dao.upsertPlanMedicineRule(planId, medicineId, true, 13.5, 30.0, 5.0, true);

        java.util.List<SubscriptionPlanMedicineRule> rules = dao.getPlanMedicineRules(planId);
        assertEquals(1, rules.size());
        SubscriptionPlanMedicineRule stored = rules.get(0);
        assertEquals(13.5, stored.discountPercent(), 0.0001);
        assertEquals(30.0, stored.maxDiscountAmount(), 0.0001);

        dao.deletePlanMedicineRule(stored.ruleId());
        assertTrue(dao.getPlanMedicineRules(planId).isEmpty());
    }

    @Test
    void planRevenueImpactAndDiscountLeakageReportsUseSubscriptionBillData() throws Exception {
        SubscriptionDAO dao = new SubscriptionDAO();
        int actorId = insertUser("sub_report_admin_" + System.nanoTime(), "ADMIN");

        int planIdA = dao.createPlan(new SubscriptionPlan(
                0,
                "RPT-A-" + System.nanoTime(),
                "Plan A",
                "Plan A report",
                499.0,
                30,
                7,
                10.0,
                20.0,
                5.0,
                SubscriptionPlanStatus.ACTIVE,
                false,
                true,
                null,
                null,
                null,
                null), actorId);

        int planIdB = dao.createPlan(new SubscriptionPlan(
                0,
                "RPT-B-" + System.nanoTime(),
                "Plan B",
                "Plan B report",
                699.0,
                30,
                7,
                10.0,
                20.0,
                5.0,
                SubscriptionPlanStatus.ACTIVE,
                false,
                true,
                null,
                null,
                null,
                null), actorId);

        insertBill(actorId, "2026-02-10 10:00:00", 90.0, planIdA, 10.0);
        insertBill(actorId, "2026-02-10 11:00:00", 180.0, planIdA, 20.0);
        insertBill(actorId, "2026-02-11 12:00:00", 95.0, planIdB, 5.0);
        insertBill(actorId, "2026-02-11 13:00:00", 150.0, null, 0.0); // non-subscription bill should be excluded

        List<SubscriptionDAO.PlanRevenueImpactRow> planRows = dao.getPlanRevenueImpact(
                LocalDate.of(2026, 2, 10),
                LocalDate.of(2026, 2, 11));
        assertEquals(2, planRows.size());

        SubscriptionDAO.PlanRevenueImpactRow rowA = planRows.stream()
                .filter(row -> row.planId() == planIdA)
                .findFirst()
                .orElseThrow();
        assertEquals(2L, rowA.billCount());
        assertEquals(300.0, rowA.grossAmountBeforeDiscount(), 0.0001);
        assertEquals(270.0, rowA.netBilledAmount(), 0.0001);
        assertEquals(30.0, rowA.totalSavings(), 0.0001);
        assertEquals(10.0, rowA.leakagePercent(), 0.0001);

        SubscriptionDAO.PlanRevenueImpactRow rowB = planRows.stream()
                .filter(row -> row.planId() == planIdB)
                .findFirst()
                .orElseThrow();
        assertEquals(1L, rowB.billCount());
        assertEquals(100.0, rowB.grossAmountBeforeDiscount(), 0.0001);
        assertEquals(95.0, rowB.netBilledAmount(), 0.0001);
        assertEquals(5.0, rowB.totalSavings(), 0.0001);

        List<SubscriptionDAO.DiscountLeakageRow> leakageRows = dao.getDiscountLeakageByDay(
                LocalDate.of(2026, 2, 10),
                LocalDate.of(2026, 2, 11));
        assertEquals(2, leakageRows.size());

        SubscriptionDAO.DiscountLeakageRow dayOne = leakageRows.get(0);
        assertNotNull(dayOne.billDay());
        assertEquals(2L, dayOne.billCount());
        assertEquals(300.0, dayOne.grossAmountBeforeDiscount(), 0.0001);
        assertEquals(30.0, dayOne.totalSavings(), 0.0001);
        assertEquals(10.0, dayOne.leakagePercent(), 0.0001);
    }

    @Test
    void rejectedOverrideAttemptsReportReturnsRejectedRowsOnlyWithinRange() throws Exception {
        SubscriptionDAO dao = new SubscriptionDAO();
        int requester = insertUser("sub_req_" + System.nanoTime(), "CASHIER");
        int reviewer = insertUser("sub_rej_" + System.nanoTime(), "MANAGER");

        insertOverrideRow(
                requester,
                reviewer,
                "REJECTED",
                22.0,
                "Need special discount",
                "2026-02-10 09:00:00",
                "2026-02-10 09:30:00");
        insertOverrideRow(
                requester,
                reviewer,
                "REJECTED",
                18.0,
                "Old request outside window",
                "2026-01-01 09:00:00",
                "2026-01-01 09:30:00");
        insertOverrideRow(
                requester,
                reviewer,
                "APPROVED",
                12.0,
                "Approved should not appear",
                "2026-02-10 10:00:00",
                "2026-02-10 10:20:00");

        List<SubscriptionDAO.RejectedOverrideReportRow> rows = dao.getRejectedOverrideAttempts(
                LocalDate.of(2026, 2, 10),
                LocalDate.of(2026, 2, 11),
                50);

        assertEquals(1, rows.size());
        SubscriptionDAO.RejectedOverrideReportRow row = rows.get(0);
        assertEquals(22.0, row.requestedDiscountPercent(), 0.0001);
        assertEquals("Need special discount", row.requestReason());
        assertEquals(requester, row.requestedByUserId());
        assertEquals(reviewer, row.rejectedByUserId());
        assertNotNull(row.requestedByUsername());
    }

    @Test
    void pricingIntegrityAlertsReportFlagsSuspiciousBillsOnly() throws Exception {
        SubscriptionDAO dao = new SubscriptionDAO();
        int actorId = insertUser("sub_price_alert_admin_" + System.nanoTime(), "ADMIN");
        int planId = dao.createPlan(new SubscriptionPlan(
                0,
                "PRICE-" + System.nanoTime(),
                "Price Monitor Plan",
                "Plan for pricing monitor tests",
                799.0,
                30,
                7,
                10.0,
                25.0,
                5.0,
                SubscriptionPlanStatus.ACTIVE,
                false,
                true,
                null,
                null,
                null,
                null), actorId);

        int cleanBillId = insertBillWithMetrics(actorId, "2026-02-12 08:00:00", 90.0, planId, 10.0, 10.0);
        insertBillWithMetrics(actorId, "2026-02-12 09:00:00", 90.0, planId, 10.0, 42.0);
        insertBillWithMetrics(actorId, "2026-02-12 10:00:00", -5.0, planId, 20.0, 66.0);
        insertBillWithMetrics(actorId, "2026-02-12 11:00:00", 100.0, planId, -5.0, -5.0);
        insertBillWithMetrics(actorId, "2026-02-12 12:00:00", 90.0, planId, 10.0, 150.0);

        List<SubscriptionDAO.PricingIntegrityAlertRow> rows = dao.getPricingIntegrityAlerts(
                LocalDate.of(2026, 2, 12),
                LocalDate.of(2026, 2, 12),
                50);

        assertEquals(4, rows.size());
        assertFalse(rows.stream().anyMatch(row -> row.billId() == cleanBillId));
        assertTrue(rows.stream().anyMatch(row -> "DISCOUNT_PERCENT_MISMATCH".equals(row.alertCode())
                && "MEDIUM".equals(row.severity())));
        assertTrue(rows.stream().anyMatch(row -> "SAVINGS_EXCEED_GROSS".equals(row.alertCode())
                && "HIGH".equals(row.severity())));
        assertTrue(rows.stream().anyMatch(row -> "NEGATIVE_SAVINGS".equals(row.alertCode())
                && "HIGH".equals(row.severity())));
        assertTrue(rows.stream().anyMatch(row -> "DISCOUNT_PERCENT_OUT_OF_RANGE".equals(row.alertCode())
                && "HIGH".equals(row.severity())));
    }

    @Test
    void overrideAbuseSignalsReportClassifiesRequesterRisk() throws Exception {
        SubscriptionDAO dao = new SubscriptionDAO();
        int requesterHighRisk = insertUser("sub_abuse_hi_" + System.nanoTime(), "CASHIER");
        int requesterLowRisk = insertUser("sub_abuse_lo_" + System.nanoTime(), "CASHIER");
        int reviewer = insertUser("sub_abuse_reviewer_" + System.nanoTime(), "MANAGER");

        insertOverrideRow(requesterHighRisk, reviewer, "REJECTED", 35.0, "Need override", "2026-02-12 09:00:00", "2026-02-12 09:20:00");
        insertOverrideRow(requesterHighRisk, reviewer, "REJECTED", 40.0, "Need override", "2026-02-12 10:00:00", "2026-02-12 10:20:00");
        insertOverrideRow(requesterHighRisk, reviewer, "REJECTED", 30.0, "Need override", "2026-02-12 11:00:00", "2026-02-12 11:20:00");
        insertOverrideRow(requesterHighRisk, reviewer, "APPROVED", 28.0, "Need override", "2026-02-12 12:00:00", "2026-02-12 12:10:00");
        insertOverrideRow(requesterHighRisk, reviewer, "APPROVED", 24.0, "Need override", "2026-02-12 13:00:00", "2026-02-12 13:10:00");

        insertOverrideRow(requesterLowRisk, reviewer, "APPROVED", 12.0, "Routine override", "2026-02-12 09:30:00", "2026-02-12 09:40:00");
        insertOverrideRow(requesterLowRisk, reviewer, "APPROVED", 18.0, "Routine override", "2026-02-12 10:30:00", "2026-02-12 10:40:00");
        insertOverrideRow(requesterLowRisk, reviewer, "REJECTED", 20.0, "Routine override", "2026-02-12 11:30:00", "2026-02-12 11:45:00");

        List<SubscriptionDAO.OverrideAbuseSignalRow> rows = dao.getOverrideAbuseSignals(
                LocalDate.of(2026, 2, 12),
                LocalDate.of(2026, 2, 12),
                3);

        assertEquals(2, rows.size());

        SubscriptionDAO.OverrideAbuseSignalRow highRiskRow = rows.stream()
                .filter(row -> row.requestedByUserId() == requesterHighRisk)
                .findFirst()
                .orElseThrow();
        assertEquals(5, highRiskRow.totalRequests());
        assertEquals(3, highRiskRow.rejectedCount());
        assertEquals(60.0, highRiskRow.rejectionRatePercent(), 0.0001);
        assertEquals("HIGH", highRiskRow.severity());

        SubscriptionDAO.OverrideAbuseSignalRow lowRiskRow = rows.stream()
                .filter(row -> row.requestedByUserId() == requesterLowRisk)
                .findFirst()
                .orElseThrow();
        assertEquals(3, lowRiskRow.totalRequests());
        assertEquals("LOW", lowRiskRow.severity());
    }

    @Test
    void pilotFeedbackLifecycleSupportsCreateTrackAndResolve() throws Exception {
        SubscriptionDAO dao = new SubscriptionDAO();
        int reporterId = insertUser("sub_feedback_reporter_" + System.nanoTime(), "MANAGER");
        int ownerId = insertUser("sub_feedback_owner_" + System.nanoTime(), "ADMIN");

        int feedbackId = dao.createPilotFeedback(
                "PILOT",
                "HIGH",
                "Unexpected discount mismatch at checkout",
                "Observed mismatch between displayed and configured discount.",
                reporterId,
                null,
                null,
                null);
        assertTrue(feedbackId > 0);

        List<SubscriptionDAO.PilotFeedbackRow> openRows = dao.getPilotFeedback(
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 28),
                "OPEN",
                100);
        SubscriptionDAO.PilotFeedbackRow createdRow = openRows.stream()
                .filter(row -> row.feedbackId() == feedbackId)
                .findFirst()
                .orElseThrow();
        assertEquals("HIGH", createdRow.severity());
        assertEquals("OPEN", createdRow.status());
        assertEquals(reporterId, createdRow.reportedByUserId());

        dao.updatePilotFeedbackStatus(feedbackId, "IN_PROGRESS", ownerId, null);
        List<SubscriptionDAO.PilotFeedbackRow> inProgressRows = dao.getPilotFeedback(
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 28),
                "IN_PROGRESS",
                100);
        SubscriptionDAO.PilotFeedbackRow inProgressRow = inProgressRows.stream()
                .filter(row -> row.feedbackId() == feedbackId)
                .findFirst()
                .orElseThrow();
        assertEquals(ownerId, inProgressRow.ownerUserId());
        assertEquals("IN_PROGRESS", inProgressRow.status());

        dao.updatePilotFeedbackStatus(feedbackId, "RESOLVED", ownerId, "Adjusted discount rule and reran pilot validation.");
        List<SubscriptionDAO.PilotFeedbackRow> resolvedRows = dao.getPilotFeedback(
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 28),
                "RESOLVED",
                100);
        SubscriptionDAO.PilotFeedbackRow resolvedRow = resolvedRows.stream()
                .filter(row -> row.feedbackId() == feedbackId)
                .findFirst()
                .orElseThrow();
        assertEquals("RESOLVED", resolvedRow.status());
        assertNotNull(resolvedRow.resolvedAt());
        assertEquals("Adjusted discount rule and reran pilot validation.", resolvedRow.resolutionNotes());
    }

    @Test
    void weeklyAnalyticsAggregationRefreshesMaterializedSummaryTables() throws Exception {
        SubscriptionDAO dao = new SubscriptionDAO();
        int actorId = insertUser("sub_weekly_admin_" + System.nanoTime(), "ADMIN");
        int reviewerId = insertUser("sub_weekly_reviewer_" + System.nanoTime(), "MANAGER");
        int highRiskRequester = insertUser("sub_weekly_hi_" + System.nanoTime(), "CASHIER");
        int lowRiskRequester = insertUser("sub_weekly_lo_" + System.nanoTime(), "CASHIER");

        int planIdA = dao.createPlan(new SubscriptionPlan(
                0,
                "WEEK-A-" + System.nanoTime(),
                "Weekly Plan A",
                "Weekly materialization test plan A",
                499.0,
                30,
                7,
                10.0,
                20.0,
                5.0,
                SubscriptionPlanStatus.ACTIVE,
                false,
                true,
                null,
                null,
                null,
                null), actorId);
        int planIdB = dao.createPlan(new SubscriptionPlan(
                0,
                "WEEK-B-" + System.nanoTime(),
                "Weekly Plan B",
                "Weekly materialization test plan B",
                699.0,
                30,
                7,
                10.0,
                25.0,
                5.0,
                SubscriptionPlanStatus.ACTIVE,
                false,
                true,
                null,
                null,
                null,
                null), actorId);

        insertBill(actorId, "2025-11-04 09:00:00", 100.0, planIdA, 10.0);
        insertBill(actorId, "2025-11-05 09:00:00", 50.0, planIdA, 5.0);
        insertBill(actorId, "2025-11-06 09:00:00", 90.0, planIdB, 10.0);
        insertBillWithMetrics(actorId, "2025-11-07 09:00:00", 80.0, planIdB, -5.0, -5.0); // high-severity pricing alert
        insertBill(actorId, "2025-11-07 10:00:00", 120.0, null, 0.0); // non-subscription bill

        insertOverrideRow(highRiskRequester, reviewerId, "REJECTED", 35.0, "High risk signal", "2025-11-04 10:00:00", "2025-11-04 10:20:00");
        insertOverrideRow(highRiskRequester, reviewerId, "REJECTED", 30.0, "High risk signal", "2025-11-05 10:00:00", "2025-11-05 10:20:00");
        insertOverrideRow(highRiskRequester, reviewerId, "REJECTED", 28.0, "High risk signal", "2025-11-06 10:00:00", "2025-11-06 10:20:00");
        insertOverrideRow(highRiskRequester, reviewerId, "APPROVED", 26.0, "High risk signal", "2025-11-07 10:00:00", "2025-11-07 10:10:00");
        insertOverrideRow(highRiskRequester, reviewerId, "APPROVED", 25.0, "High risk signal", "2025-11-08 10:00:00", "2025-11-08 10:10:00");

        insertOverrideRow(lowRiskRequester, reviewerId, "APPROVED", 12.0, "Low risk signal", "2025-11-04 11:00:00", "2025-11-04 11:05:00");
        insertOverrideRow(lowRiskRequester, reviewerId, "APPROVED", 15.0, "Low risk signal", "2025-11-05 11:00:00", "2025-11-05 11:05:00");
        insertOverrideRow(lowRiskRequester, reviewerId, "REJECTED", 16.0, "Low risk signal", "2025-11-06 11:00:00", "2025-11-06 11:05:00");

        Optional<SubscriptionDAO.WeeklyAnalyticsSummaryRow> summary = dao.refreshWeeklyAnalyticsSummary(
                LocalDate.of(2025, 11, 6),
                "Asia/Kolkata");
        assertTrue(summary.isPresent());

        SubscriptionDAO.WeeklyAnalyticsSummaryRow summaryRow = summary.get();
        assertEquals("2025-11-03", summaryRow.weekStartDate());
        assertEquals("2025-11-09", summaryRow.weekEndDate());
        assertEquals("Asia/Kolkata", summaryRow.timezoneName());
        assertEquals(5L, summaryRow.totalBillCount());
        assertEquals(4L, summaryRow.subscriptionBillCount());
        assertEquals(340.0, summaryRow.grossAmountBeforeDiscount(), 0.0001);
        assertEquals(320.0, summaryRow.netBilledAmount(), 0.0001);
        assertEquals(20.0, summaryRow.totalSavings(), 0.0001);
        assertEquals(5.8824, summaryRow.leakagePercent(), 0.0001);
        assertEquals(0L, summaryRow.activeSubscribersSnapshot());
        assertEquals(0L, summaryRow.renewalsDueNext7Days());
        assertEquals(0L, summaryRow.pendingOverrideCount());
        assertEquals(1L, summaryRow.highPricingAlertCount());
        assertEquals(1L, summaryRow.highOverrideAbuseSignalCount());
        assertEquals(0L, summaryRow.openHighCriticalFeedbackCount());
        assertNotNull(summaryRow.generatedAt());

        List<SubscriptionDAO.WeeklyPlanRevenueSummaryRow> planSummaryRows = dao.getWeeklyPlanRevenueSummary(
                LocalDate.of(2025, 11, 6),
                "Asia/Kolkata");
        assertEquals(2, planSummaryRows.size());

        SubscriptionDAO.WeeklyPlanRevenueSummaryRow planAWeekly = planSummaryRows.stream()
                .filter(row -> row.planId() == planIdA)
                .findFirst()
                .orElseThrow();
        assertEquals(2L, planAWeekly.billCount());
        assertEquals(165.0, planAWeekly.grossAmountBeforeDiscount(), 0.0001);
        assertEquals(150.0, planAWeekly.netBilledAmount(), 0.0001);
        assertEquals(15.0, planAWeekly.totalSavings(), 0.0001);

        SubscriptionDAO.WeeklyPlanRevenueSummaryRow planBWeekly = planSummaryRows.stream()
                .filter(row -> row.planId() == planIdB)
                .findFirst()
                .orElseThrow();
        assertEquals(2L, planBWeekly.billCount());
        assertEquals(175.0, planBWeekly.grossAmountBeforeDiscount(), 0.0001);
        assertEquals(170.0, planBWeekly.netBilledAmount(), 0.0001);
        assertEquals(5.0, planBWeekly.totalSavings(), 0.0001);

        dao.refreshWeeklyAnalyticsSummary(LocalDate.of(2025, 11, 9), "Asia/Kolkata");
        assertEquals(1L, countWeeklySummaryRows("2025-11-03", "Asia/Kolkata"));
    }

    @Test
    void recentWeeklyLeakageHistoryReturnsPriorWeeksOnly() throws Exception {
        SubscriptionDAO dao = new SubscriptionDAO();
        int actorId = insertUser("sub_weekly_hist_admin_" + System.nanoTime(), "ADMIN");
        LocalDate priorWeekBillDate = LocalDate.of(2030, 4, 2);
        LocalDate currentWeekBillDate = priorWeekBillDate.plusWeeks(1);
        LocalDate referenceDate = currentWeekBillDate.plusDays(1);
        String priorWeekStartDate = priorWeekBillDate
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toString();

        int planId = dao.createPlan(new SubscriptionPlan(
                0,
                "HIST-" + System.nanoTime(),
                "History Plan",
                "Weekly leakage history plan",
                499.0,
                30,
                7,
                10.0,
                20.0,
                5.0,
                SubscriptionPlanStatus.ACTIVE,
                false,
                true,
                null,
                null,
                null,
                null), actorId);

        // Prior week
        insertBill(actorId, priorWeekBillDate + " 10:00:00", 90.0, planId, 10.0); // 10% leakage
        // Current week
        insertBill(actorId, currentWeekBillDate + " 10:00:00", 95.0, planId, 5.0); // 5% leakage

        dao.refreshWeeklyAnalyticsSummary(priorWeekBillDate, "Asia/Kolkata");
        dao.refreshWeeklyAnalyticsSummary(currentWeekBillDate, "Asia/Kolkata");

        List<SubscriptionDAO.WeeklyLeakageHistoryRow> rows = dao.getRecentWeeklyLeakageHistory(
                referenceDate,
                "Asia/Kolkata",
                1);

        assertEquals(1, rows.size());
        SubscriptionDAO.WeeklyLeakageHistoryRow historyRow = rows.get(0);
        assertEquals(priorWeekStartDate, historyRow.weekStartDate());
        assertEquals(10.0, historyRow.leakagePercent(), 0.0001);
        assertEquals("Asia/Kolkata", historyRow.timezoneName());
    }

    @Test
    void categoryRuleAndMedicineCategoryMappingsAreLoadedForEligibilityEvaluation() throws Exception {
        SubscriptionDAO dao = new SubscriptionDAO();
        int actorId = insertUser("sub_cat_rule_admin_" + System.nanoTime(), "ADMIN");
        int planId = dao.createPlan(new SubscriptionPlan(
                0,
                "CAT-" + System.nanoTime(),
                "Category Plan",
                "Category eligibility plan",
                499.0,
                30,
                7,
                10.0,
                20.0,
                5.0,
                SubscriptionPlanStatus.ACTIVE,
                false,
                true,
                null,
                null,
                null,
                null), actorId);
        int medicineId = insertMedicine("CategoryMed-" + System.nanoTime(), "Antibiotic", 100.0);
        insertCategoryRule(planId, "Antibiotic", false, 12.0, null, 6.0, true);

        var categoryRules = dao.loadCategoryRules(planId);
        assertEquals(1, categoryRules.size());
        assertTrue(categoryRules.containsKey("antibiotic"));
        assertFalse(categoryRules.get("antibiotic").includeRule());
        assertEquals(12.0, categoryRules.get("antibiotic").discountPercent(), 0.0001);

        var categoryMap = dao.loadMedicineCategoryById(List.of(medicineId));
        assertEquals("antibiotic", categoryMap.get(medicineId));
    }

    @Test
    void customerPurchaseAndRefillEventsAreLoadedForRecommendationEngine() throws Exception {
        SubscriptionDAO dao = new SubscriptionDAO();
        int actorId = insertUser("sub_reco_admin_" + System.nanoTime(), "ADMIN");
        int customerId = insertCustomer("reco_customer_" + System.nanoTime(), "9898989898");
        int medicineId = insertMedicine("RecoMed-" + System.nanoTime(), 120.0);

        int billOne = insertBillForCustomer(customerId, actorId, "2026-02-01 09:00:00", 360.0);
        int billTwo = insertBillForCustomer(customerId, actorId, "2026-02-11 09:00:00", 480.0);
        insertBillItem(billOne, medicineId, 3, 120.0, 360.0);
        insertBillItem(billTwo, medicineId, 4, 120.0, 480.0);

        List<SubscriptionDAO.CustomerPurchaseEvent> purchaseEvents = dao.getCustomerPurchaseEvents(customerId, 180);
        List<SubscriptionDAO.CustomerRefillEvent> refillEvents = dao.getCustomerRefillEvents(customerId, 180);

        assertEquals(2, purchaseEvents.size());
        assertEquals(2, refillEvents.size());
        assertEquals("2026-02-01 09:00:00", purchaseEvents.get(0).billDate());
        assertEquals(360.0, purchaseEvents.get(0).billedAmount(), 0.0001);
        assertEquals(medicineId, refillEvents.get(0).medicineId());
        assertEquals(3, refillEvents.get(0).quantity());
        assertEquals(360.0, refillEvents.get(0).lineAmount(), 0.0001);
    }

    @Test
    void renewalDueCandidatesAndRenewalCountAreLoadedForPropensityScoring() throws Exception {
        SubscriptionDAO dao = new SubscriptionDAO();
        int actorId = insertUser("sub_renewal_admin_" + System.nanoTime(), "ADMIN");
        int customerId = insertCustomer("renewal_customer_" + System.nanoTime(), "9797979797");

        SubscriptionPlan plan = new SubscriptionPlan(
                0,
                "REN-" + System.nanoTime(),
                "Renewal Plan",
                "renewal test plan",
                699.0,
                30,
                7,
                12.0,
                20.0,
                5.0,
                SubscriptionPlanStatus.ACTIVE,
                false,
                true,
                null,
                null,
                null,
                null);
        int planId = dao.createPlan(plan, actorId);

        LocalDate today = LocalDate.now();
        CustomerSubscription enrollment = new CustomerSubscription(
                0,
                customerId,
                planId,
                SubscriptionEnrollmentStatus.ACTIVE,
                today.minusDays(23) + " 00:00:00",
                today.plusDays(7) + " 00:00:00",
                today.plusDays(14) + " 00:00:00",
                "POS",
                actorId,
                actorId,
                "APR-RENEWAL",
                null,
                null,
                null,
                null);
        int enrollmentId = dao.createEnrollment(enrollment, actorId);
        insertSubscriptionEvent(enrollmentId, "RENEW", actorId);
        insertSubscriptionEvent(enrollmentId, "RENEW", actorId);

        int billId = insertBillForCustomer(customerId, actorId, today.minusDays(5) + " 10:00:00", 250.0);
        int medicineId = insertMedicine("RenewalMed-" + System.nanoTime(), 125.0);
        insertBillItem(billId, medicineId, 2, 125.0, 250.0);

        List<SubscriptionDAO.RenewalDueCandidate> candidates = dao.getRenewalDueCandidates(14);
        assertTrue(candidates.stream().anyMatch(row -> row.enrollmentId() == enrollmentId));
        assertEquals(2, dao.getEnrollmentRenewalCount(enrollmentId));
    }

    @Test
    void enrollmentAbuseSignalsDetectFrequentLifecycleAndBackdatedPatterns() throws Exception {
        SubscriptionDAO dao = new SubscriptionDAO();
        int actorId = insertUser("sub_enroll_abuse_admin_" + System.nanoTime(), "ADMIN");
        int customerId = insertCustomer("enroll_abuse_customer_" + System.nanoTime(), "9696969696");

        int planIdA = dao.createPlan(new SubscriptionPlan(
                0,
                "EA-A-" + System.nanoTime(),
                "Enrollment Abuse Plan A",
                "plan a",
                499.0,
                30,
                7,
                10.0,
                20.0,
                5.0,
                SubscriptionPlanStatus.ACTIVE,
                false,
                true,
                null,
                null,
                null,
                null), actorId);
        int planIdB = dao.createPlan(new SubscriptionPlan(
                0,
                "EA-B-" + System.nanoTime(),
                "Enrollment Abuse Plan B",
                "plan b",
                599.0,
                30,
                7,
                10.0,
                20.0,
                5.0,
                SubscriptionPlanStatus.ACTIVE,
                false,
                true,
                null,
                null,
                null,
                null), actorId);

        LocalDate today = LocalDate.now();
        CustomerSubscription enrollment = new CustomerSubscription(
                0,
                customerId,
                planIdA,
                SubscriptionEnrollmentStatus.ACTIVE,
                today.minusDays(10) + " 00:00:00",
                today.plusDays(20) + " 00:00:00",
                today.plusDays(27) + " 00:00:00",
                "POS",
                actorId,
                actorId,
                "APR-EA",
                null,
                null,
                null,
                null);
        int enrollmentId = dao.createEnrollment(enrollment, actorId);
        dao.changeEnrollmentPlan(enrollmentId, planIdB, "plan-switch", actorId, actorId, "APR-EA-2");
        dao.updateEnrollmentStatus(
                enrollmentId,
                SubscriptionEnrollmentStatus.FROZEN,
                "FREEZE",
                "temp hold",
                actorId,
                actorId,
                "APR-EA-3");
        dao.updateEnrollmentStatus(
                enrollmentId,
                SubscriptionEnrollmentStatus.CANCELLED,
                "CANCEL",
                "cancel test",
                actorId,
                actorId,
                "APR-EA-4");

        List<SubscriptionDAO.EnrollmentAbuseSignalRow> rows = dao.getEnrollmentAbuseSignals(
                today.minusDays(1),
                today.plusDays(1),
                3);
        SubscriptionDAO.EnrollmentAbuseSignalRow row = rows.stream()
                .filter(signal -> signal.customerId() == customerId)
                .findFirst()
                .orElseThrow();

        assertTrue(row.totalEvents() >= 4);
        assertTrue(row.planChangeCount() >= 1);
        assertTrue(row.cancellationCount() >= 1);
        assertTrue(row.backdatedEnrollmentCount() >= 1);
    }

    @Test
    void monitoringQueriesReturnConfirmedAbuseSubjectsAndEnrollmentDecisions() throws Exception {
        SubscriptionDAO dao = new SubscriptionDAO();
        int actorId = insertUser("sub_monitor_admin_" + System.nanoTime(), "ADMIN");
        int reviewerId = insertUser("sub_monitor_owner_" + System.nanoTime(), "MANAGER");
        String requesterUsername = "sub_monitor_cashier_" + System.nanoTime();
        int requesterId = insertUser(requesterUsername, "CASHIER");
        int customerId = insertCustomer("monitor_customer_" + System.nanoTime(), "9595959595");

        String planCode = "MON-" + System.nanoTime();
        int planId = dao.createPlan(new SubscriptionPlan(
                0,
                planCode,
                "Monitoring Plan",
                "plan for model monitoring queries",
                699.0,
                30,
                7,
                10.0,
                20.0,
                5.0,
                SubscriptionPlanStatus.ACTIVE,
                false,
                true,
                null,
                null,
                null,
                null), actorId);

        CustomerSubscription enrollment = new CustomerSubscription(
                0,
                customerId,
                planId,
                SubscriptionEnrollmentStatus.ACTIVE,
                LocalDate.now() + " 00:00:00",
                LocalDate.now().plusDays(30) + " 00:00:00",
                LocalDate.now().plusDays(37) + " 00:00:00",
                "POS",
                actorId,
                actorId,
                "APR-MON-1",
                null,
                null,
                null,
                null);
        int enrollmentId = dao.createEnrollment(enrollment, actorId);

        int overrideId = insertOverrideRow(
                requesterId,
                reviewerId,
                "REJECTED",
                32.0,
                "monitoring override",
                LocalDate.now() + " 10:00:00",
                LocalDate.now() + " 10:20:00");
        int billId = insertBill(actorId, LocalDate.now() + " 11:00:00", 90.0, planId, 10.0);

        int overrideFeedbackId = dao.createPilotFeedback(
                "PILOT",
                "HIGH",
                "Override abuse confirmation",
                "Confirmed suspicious override behavior.",
                reviewerId,
                reviewerId,
                null,
                overrideId);
        dao.updatePilotFeedbackStatus(
                overrideFeedbackId,
                "RESOLVED",
                reviewerId,
                "Validated and documented.");

        int billFeedbackId = dao.createPilotFeedback(
                "PILOT",
                "CRITICAL",
                "Billing abuse confirmation",
                "Confirmed pricing abuse pattern.",
                reviewerId,
                reviewerId,
                billId,
                null);
        dao.updatePilotFeedbackStatus(
                billFeedbackId,
                "RESOLVED",
                reviewerId,
                "Validated and documented.");

        LocalDate start = LocalDate.now().minusDays(1);
        LocalDate end = LocalDate.now().plusDays(1);
        List<String> subjects = dao.getConfirmedAbuseMonitoringSubjects(start, end);
        assertTrue(subjects.stream().anyMatch(s -> s.equalsIgnoreCase("user:" + requesterUsername.toLowerCase())));
        assertTrue(subjects.stream().anyMatch(s -> s.equalsIgnoreCase("plan:" + planCode.toLowerCase())));

        List<SubscriptionDAO.EnrollmentMonitoringDecisionRow> decisions = dao.getEnrollmentMonitoringDecisions(start, end, 50);
        SubscriptionDAO.EnrollmentMonitoringDecisionRow decision = decisions.stream()
                .filter(row -> row.enrollmentId() == enrollmentId)
                .findFirst()
                .orElseThrow();
        assertEquals(customerId, decision.customerId());
        assertEquals(planId, decision.enrolledPlanId());
    }

    private static int insertUser(String username, String role) throws Exception {
        String sql = "INSERT INTO users (username, password, role) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, "password");
            ps.setString(3, role);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new IllegalStateException("Failed to insert test user");
    }

    private static int insertCustomer(String name, String phone) throws Exception {
        String sql = "INSERT INTO customers (name, phone) VALUES (?, ?)";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, phone);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new IllegalStateException("Failed to insert test customer");
    }

    private static int insertMedicine(String name, double price) throws Exception {
        return insertMedicine(name, null, price);
    }

    private static int insertMedicine(String name, String genericName, double price) throws Exception {
        String insertMedicineSql = "INSERT INTO medicines (name, generic_name, company, price) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(insertMedicineSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, genericName);
            ps.setString(3, "Test Co");
            ps.setDouble(4, price);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int medicineId = rs.getInt(1);
                    try (PreparedStatement stockPs = conn.prepareStatement(
                            "INSERT INTO stock (medicine_id, quantity) VALUES (?, ?)")) {
                        stockPs.setInt(1, medicineId);
                        stockPs.setInt(2, 100);
                        stockPs.executeUpdate();
                    }
                    return medicineId;
                }
            }
        }
        throw new IllegalStateException("Failed to insert test medicine");
    }

    private static int insertCategoryRule(
            int planId,
            String categoryName,
            boolean includeRule,
            double discountPercent,
            Double maxDiscountAmount,
            Double minMarginPercent,
            boolean active) throws Exception {
        String sql = "INSERT INTO subscription_plan_category_rules (" +
                "plan_id, category_name, include_rule, discount_percent, max_discount_amount, min_margin_percent, active" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, planId);
            ps.setString(2, categoryName);
            ps.setBoolean(3, includeRule);
            ps.setDouble(4, discountPercent);
            ps.setObject(5, maxDiscountAmount);
            ps.setObject(6, minMarginPercent);
            ps.setBoolean(7, active);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new IllegalStateException("Failed to insert test category rule");
    }

    private static int insertBill(int userId, String billDate, double totalAmount, Integer planId, double savings)
            throws Exception {
        double discountPercent = totalAmount + savings <= 0 ? 0.0 : (savings / (totalAmount + savings)) * 100.0;
        return insertBillWithMetrics(userId, billDate, totalAmount, planId, savings, discountPercent);
    }

    private static int insertBillWithMetrics(
            int userId,
            String billDate,
            double totalAmount,
            Integer planId,
            double savings,
            double discountPercent) throws Exception {
        String sql = "INSERT INTO bills (" +
                "customer_id, user_id, total_amount, bill_date, payment_mode, " +
                "subscription_plan_id, subscription_discount_percent, subscription_savings_amount" +
                ") VALUES (?, ?, ?, ?, 'CASH', ?, ?, ?)";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setObject(1, null);
            ps.setInt(2, userId);
            ps.setDouble(3, totalAmount);
            ps.setString(4, billDate);
            ps.setObject(5, planId);
            ps.setDouble(6, discountPercent);
            ps.setDouble(7, savings);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new IllegalStateException("Failed to insert test bill");
    }

    private static int insertBillForCustomer(int customerId, int userId, String billDate, double totalAmount)
            throws Exception {
        String sql = "INSERT INTO bills (" +
                "customer_id, user_id, total_amount, bill_date, payment_mode, " +
                "subscription_plan_id, subscription_discount_percent, subscription_savings_amount" +
                ") VALUES (?, ?, ?, ?, 'CASH', ?, ?, ?)";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, customerId);
            ps.setInt(2, userId);
            ps.setDouble(3, totalAmount);
            ps.setString(4, billDate);
            ps.setObject(5, null);
            ps.setDouble(6, 0.0);
            ps.setDouble(7, 0.0);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new IllegalStateException("Failed to insert customer bill");
    }

    private static void insertBillItem(int billId, int medicineId, int quantity, double price, double total) throws Exception {
        String sql = "INSERT INTO bill_items (bill_id, medicine_id, quantity, price, total) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, billId);
            ps.setInt(2, medicineId);
            ps.setInt(3, quantity);
            ps.setDouble(4, price);
            ps.setDouble(5, total);
            ps.executeUpdate();
        }
    }

    private static void insertSubscriptionEvent(int enrollmentId, String eventType, int actorUserId) throws Exception {
        String sql = "INSERT INTO customer_subscription_events (" +
                "enrollment_id, event_type, old_plan_id, new_plan_id, event_note, effective_at, created_by_user_id" +
                ") VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?)";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, enrollmentId);
            ps.setString(2, eventType);
            ps.setObject(3, null);
            ps.setObject(4, null);
            ps.setString(5, "test event");
            ps.setInt(6, actorUserId);
            ps.executeUpdate();
        }
    }

    private static int insertOverrideRow(
            int requestedByUserId,
            Integer approvedByUserId,
            String status,
            double requestedPercent,
            String reason,
            String createdAt,
            String approvedAt) throws Exception {
        String sql = "INSERT INTO subscription_discount_overrides (" +
                "bill_id, bill_item_id, customer_id, enrollment_id, requested_discount_percent, approved_discount_percent, " +
                "status, reason, requested_by_user_id, approved_by_user_id, approval_id, created_at, approved_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setObject(1, null);
            ps.setObject(2, null);
            ps.setObject(3, null);
            ps.setObject(4, null);
            ps.setDouble(5, requestedPercent);
            ps.setObject(6, "APPROVED".equalsIgnoreCase(status) ? requestedPercent : null);
            ps.setString(7, status);
            ps.setString(8, reason);
            ps.setInt(9, requestedByUserId);
            ps.setObject(10, approvedByUserId);
            ps.setObject(11, null);
            ps.setString(12, createdAt);
            ps.setString(13, approvedAt);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new IllegalStateException("Failed to insert override report row");
    }

    private static long countWeeklySummaryRows(String weekStartDate, String timezoneName) throws Exception {
        String sql = "SELECT COUNT(*) AS cnt FROM subscription_weekly_summary WHERE week_start_date = ? AND timezone_name = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, weekStartDate);
            ps.setString(2, timezoneName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("cnt");
                }
            }
        }
        return 0L;
    }

    private static void tryDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // Best-effort cleanup only.
        }
    }
}
