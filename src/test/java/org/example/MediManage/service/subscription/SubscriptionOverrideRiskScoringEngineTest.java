package org.example.MediManage.service.subscription;

import org.example.MediManage.dao.SubscriptionDAO;
import org.example.MediManage.model.SubscriptionDiscountOverride;
import org.example.MediManage.model.SubscriptionDiscountOverrideStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SubscriptionOverrideRiskScoringEngineTest {

    private final SubscriptionOverrideRiskScoringEngine engine = new SubscriptionOverrideRiskScoringEngine();

    @Test
    void highRiskSignalsProduceHigherScoreThanLowRiskSignals() {
        SubscriptionDiscountOverride override = new SubscriptionDiscountOverride(
                1001,
                null,
                null,
                5001,
                7001,
                32.0,
                null,
                SubscriptionDiscountOverrideStatus.PENDING,
                "Urgent override requested.",
                88,
                null,
                null,
                "2026-02-23 10:00:00",
                null);

        SubscriptionDAO.OverrideAbuseSignalRow highRequester = new SubscriptionDAO.OverrideAbuseSignalRow(
                88,
                "cashier-x",
                9,
                2,
                6,
                1,
                22.2,
                66.6,
                28.0,
                40.0,
                "2026-02-01 00:00:00",
                "2026-02-23 00:00:00",
                "HIGH");
        SubscriptionDAO.EnrollmentAbuseSignalRow highCustomer = new SubscriptionDAO.EnrollmentAbuseSignalRow(
                5001,
                8,
                4,
                2,
                1,
                1,
                3,
                "2026-02-01 00:00:00",
                "2026-02-23 00:00:00");

        SubscriptionOverrideRiskScoringEngine.OverrideRiskAssessment high = engine.score(
                override,
                highRequester,
                highCustomer,
                30);

        SubscriptionDAO.OverrideAbuseSignalRow lowRequester = new SubscriptionDAO.OverrideAbuseSignalRow(
                88,
                "cashier-x",
                1,
                1,
                0,
                0,
                100.0,
                0.0,
                8.0,
                8.0,
                "2026-02-20 00:00:00",
                "2026-02-23 00:00:00",
                "LOW");
        SubscriptionDAO.EnrollmentAbuseSignalRow lowCustomer = new SubscriptionDAO.EnrollmentAbuseSignalRow(
                5001,
                1,
                0,
                0,
                0,
                0,
                1,
                "2026-02-20 00:00:00",
                "2026-02-23 00:00:00");

        SubscriptionOverrideRiskScoringEngine.OverrideRiskAssessment low = engine.score(
                override,
                lowRequester,
                lowCustomer,
                30);

        assertTrue(high.riskScore() > low.riskScore());
        assertEquals("HIGH", high.riskBand());
    }
}
