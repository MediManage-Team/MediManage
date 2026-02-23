package org.example.MediManage.service.subscription;

import org.example.MediManage.dao.SubscriptionDAO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionDiscountAbuseDetectionEngineTest {

    private final SubscriptionDiscountAbuseDetectionEngine engine = new SubscriptionDiscountAbuseDetectionEngine();

    @Test
    void detectRanksHighRiskSignalsFirstAcrossEnrollmentOverrideAndBilling() {
        List<SubscriptionDAO.EnrollmentAbuseSignalRow> enrollmentSignals = List.of(
                new SubscriptionDAO.EnrollmentAbuseSignalRow(
                        501,
                        9,
                        4,
                        3,
                        2,
                        2,
                        3,
                        "2026-02-01 10:00:00",
                        "2026-02-23 10:00:00"));

        List<SubscriptionDAO.OverrideAbuseSignalRow> overrideSignals = List.of(
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

        List<SubscriptionDAO.PricingIntegrityAlertRow> billingAlerts = List.of(
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
                        95.0,
                        25.0,
                        20.8,
                        20.8,
                        "DISCOUNT_PERCENT_OUT_OF_RANGE",
                        "HIGH"));

        List<SubscriptionDiscountAbuseDetectionEngine.AbuseFinding> findings = engine.detect(
                enrollmentSignals,
                overrideSignals,
                billingAlerts,
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 23),
                20);

        assertEquals(3, findings.size());
        assertTrue(findings.get(0).riskScore() >= findings.get(1).riskScore());
        assertTrue(findings.stream().anyMatch(row -> "ENROLLMENT_PATTERN".equals(row.signalType())));
        assertTrue(findings.stream().anyMatch(row -> "OVERRIDE_PATTERN".equals(row.signalType())));
        assertTrue(findings.stream().anyMatch(row -> "BILLING_PATTERN".equals(row.signalType())));
    }

    @Test
    void detectReturnsEmptyWhenNoSignalsPresent() {
        List<SubscriptionDiscountAbuseDetectionEngine.AbuseFinding> findings = engine.detect(
                List.of(),
                List.of(),
                List.of(),
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 23),
                20);
        assertTrue(findings.isEmpty());
    }
}
