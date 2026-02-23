package org.example.MediManage.service.subscription;

import org.example.MediManage.dao.SubscriptionDAO;
import org.example.MediManage.model.SubscriptionPlan;
import org.example.MediManage.model.SubscriptionPlanStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SubscriptionModelMonitoringServiceTest {

    @Test
    void evaluateComputesAbusePrecisionAndRecallFromPredictedAndActualSubjects() {
        FakeMonitoringSubscriptionDAO dao = new FakeMonitoringSubscriptionDAO();
        dao.overrideSignals = List.of(
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
                        "HIGH"));
        dao.pricingAlerts = List.of(
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
                        "NEGATIVE_SAVINGS",
                        "HIGH"),
                new SubscriptionDAO.PricingIntegrityAlertRow(
                        7002,
                        "2026-02-21 10:00:00",
                        91,
                        "PLAN-91",
                        "Plan 91",
                        110.0,
                        90.0,
                        20.0,
                        18.0,
                        18.0,
                        "SAVINGS_EXCEED_GROSS",
                        "HIGH"),
                new SubscriptionDAO.PricingIntegrityAlertRow(
                        7003,
                        "2026-02-22 10:00:00",
                        91,
                        "PLAN-91",
                        "Plan 91",
                        120.0,
                        95.0,
                        25.0,
                        20.0,
                        20.0,
                        "NEGATIVE_GROSS_BEFORE_DISCOUNT",
                        "HIGH"));
        dao.confirmedSubjects = List.of("user:cashier-x");

        SubscriptionModelMonitoringService service = new SubscriptionModelMonitoringService(dao);
        SubscriptionModelMonitoringService.MonitoringSnapshot snapshot = service.evaluate(
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 23));

        SubscriptionModelMonitoringService.AbuseDetectionMonitoring abuse = snapshot.abuseDetection();
        assertEquals(2, abuse.predictedPositiveCount());
        assertEquals(1, abuse.actualPositiveCount());
        assertEquals(1, abuse.truePositiveCount());
        assertEquals(1, abuse.falsePositiveCount());
        assertEquals(0, abuse.falseNegativeCount());
        assertEquals(50.0, abuse.precisionPercent(), 0.0001);
        assertEquals(100.0, abuse.recallPercent(), 0.0001);
    }

    @Test
    void evaluateComputesRecommendationAcceptanceRate() {
        FakeMonitoringSubscriptionDAO dao = new FakeMonitoringSubscriptionDAO();
        dao.plans = List.of(
                new SubscriptionPlan(
                        1,
                        "REC-PREMIUM",
                        "Premium Care",
                        "premium plan",
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
                        null),
                new SubscriptionPlan(
                        2,
                        "REC-SAVER",
                        "Saver Care",
                        "saver plan",
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
                        null));

        List<SubscriptionDAO.CustomerPurchaseEvent> purchases = List.of(
                new SubscriptionDAO.CustomerPurchaseEvent("2026-01-01 10:00:00", 820.0),
                new SubscriptionDAO.CustomerPurchaseEvent("2026-01-11 10:00:00", 910.0),
                new SubscriptionDAO.CustomerPurchaseEvent("2026-01-21 10:00:00", 780.0),
                new SubscriptionDAO.CustomerPurchaseEvent("2026-02-01 10:00:00", 860.0),
                new SubscriptionDAO.CustomerPurchaseEvent("2026-02-11 10:00:00", 940.0),
                new SubscriptionDAO.CustomerPurchaseEvent("2026-02-21 10:00:00", 890.0),
                new SubscriptionDAO.CustomerPurchaseEvent("2026-03-03 10:00:00", 930.0),
                new SubscriptionDAO.CustomerPurchaseEvent("2026-03-13 10:00:00", 960.0),
                new SubscriptionDAO.CustomerPurchaseEvent("2026-03-23 10:00:00", 900.0));
        List<SubscriptionDAO.CustomerRefillEvent> refills = List.of(
                new SubscriptionDAO.CustomerRefillEvent(101, "2026-01-01 10:00:00", 1, 240.0),
                new SubscriptionDAO.CustomerRefillEvent(101, "2026-01-11 10:00:00", 1, 240.0),
                new SubscriptionDAO.CustomerRefillEvent(101, "2026-01-21 10:00:00", 1, 240.0),
                new SubscriptionDAO.CustomerRefillEvent(101, "2026-02-01 10:00:00", 1, 240.0),
                new SubscriptionDAO.CustomerRefillEvent(101, "2026-02-11 10:00:00", 1, 240.0),
                new SubscriptionDAO.CustomerRefillEvent(101, "2026-02-21 10:00:00", 1, 240.0),
                new SubscriptionDAO.CustomerRefillEvent(101, "2026-03-03 10:00:00", 1, 240.0),
                new SubscriptionDAO.CustomerRefillEvent(101, "2026-03-13 10:00:00", 1, 240.0),
                new SubscriptionDAO.CustomerRefillEvent(101, "2026-03-23 10:00:00", 1, 240.0));

        dao.purchaseEventsByCustomerId.put(1001, purchases);
        dao.purchaseEventsByCustomerId.put(1002, purchases);
        dao.refillEventsByCustomerId.put(1001, refills);
        dao.refillEventsByCustomerId.put(1002, refills);

        dao.enrollmentDecisions = List.of(
                new SubscriptionDAO.EnrollmentMonitoringDecisionRow(
                        901,
                        1001,
                        2,
                        "2026-02-20 00:00:00",
                        "2026-02-20 10:00:00"),
                new SubscriptionDAO.EnrollmentMonitoringDecisionRow(
                        902,
                        1002,
                        1,
                        "2026-02-21 00:00:00",
                        "2026-02-21 10:00:00"));

        SubscriptionModelMonitoringService service = new SubscriptionModelMonitoringService(dao);
        SubscriptionModelMonitoringService.MonitoringSnapshot snapshot = service.evaluate(
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 28));

        SubscriptionModelMonitoringService.RecommendationMonitoring recommendation = snapshot.recommendation();
        assertEquals(2, recommendation.totalEnrollments());
        assertEquals(2, recommendation.evaluatedCount());
        assertEquals(1, recommendation.acceptedCount());
        assertEquals(0, recommendation.skippedCount());
        assertEquals(50.0, recommendation.acceptanceRatePercent(), 0.0001);
    }

    private static final class FakeMonitoringSubscriptionDAO extends SubscriptionDAO {
        private List<SubscriptionDAO.EnrollmentAbuseSignalRow> enrollmentSignals = List.of();
        private List<SubscriptionDAO.OverrideAbuseSignalRow> overrideSignals = List.of();
        private List<SubscriptionDAO.PricingIntegrityAlertRow> pricingAlerts = List.of();
        private List<String> confirmedSubjects = List.of();
        private List<SubscriptionDAO.EnrollmentMonitoringDecisionRow> enrollmentDecisions = List.of();
        private List<SubscriptionPlan> plans = List.of();
        private final Map<Integer, List<SubscriptionDAO.CustomerPurchaseEvent>> purchaseEventsByCustomerId = new HashMap<>();
        private final Map<Integer, List<SubscriptionDAO.CustomerRefillEvent>> refillEventsByCustomerId = new HashMap<>();

        @Override
        public List<SubscriptionDAO.EnrollmentAbuseSignalRow> getEnrollmentAbuseSignals(LocalDate start, LocalDate end, int minEvents) {
            return enrollmentSignals;
        }

        @Override
        public List<SubscriptionDAO.OverrideAbuseSignalRow> getOverrideAbuseSignals(LocalDate start, LocalDate end, int minRequests) {
            return overrideSignals;
        }

        @Override
        public List<SubscriptionDAO.PricingIntegrityAlertRow> getPricingIntegrityAlerts(LocalDate start, LocalDate end, int limit) {
            return pricingAlerts;
        }

        @Override
        public List<String> getConfirmedAbuseMonitoringSubjects(LocalDate start, LocalDate end) {
            return confirmedSubjects;
        }

        @Override
        public List<SubscriptionDAO.EnrollmentMonitoringDecisionRow> getEnrollmentMonitoringDecisions(
                LocalDate start,
                LocalDate end,
                int limit) {
            return enrollmentDecisions;
        }

        @Override
        public List<SubscriptionPlan> getAllPlans() {
            return plans;
        }

        @Override
        public List<SubscriptionDAO.CustomerPurchaseEvent> getCustomerPurchaseEvents(
                int customerId,
                int lookbackDays,
                LocalDate referenceDate) {
            return purchaseEventsByCustomerId.getOrDefault(customerId, List.of());
        }

        @Override
        public List<SubscriptionDAO.CustomerRefillEvent> getCustomerRefillEvents(
                int customerId,
                int lookbackDays,
                LocalDate referenceDate) {
            return refillEventsByCustomerId.getOrDefault(customerId, List.of());
        }
    }
}
