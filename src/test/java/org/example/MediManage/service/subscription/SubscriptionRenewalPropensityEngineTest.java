package org.example.MediManage.service.subscription;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionRenewalPropensityEngineTest {

    private final SubscriptionRenewalPropensityEngine engine = new SubscriptionRenewalPropensityEngine();

    @Test
    void highRiskCandidateRanksAboveLowRiskCandidate() {
        SubscriptionRenewalPropensityEngine.RenewalCandidate highRisk = new SubscriptionRenewalPropensityEngine.RenewalCandidate(
                1001,
                11,
                21,
                "PLAN-HI",
                "High Risk Plan",
                "2025-12-01 00:00:00",
                "2026-03-03 00:00:00",
                700.0,
                0,
                List.of(
                        new SubscriptionRenewalPropensityEngine.PurchaseEvent("2026-01-01 10:00:00", 120.0)),
                List.of());

        SubscriptionRenewalPropensityEngine.RenewalCandidate lowRisk = new SubscriptionRenewalPropensityEngine.RenewalCandidate(
                1002,
                12,
                22,
                "PLAN-LO",
                "Low Risk Plan",
                "2025-12-01 00:00:00",
                "2026-03-20 00:00:00",
                200.0,
                3,
                List.of(
                        new SubscriptionRenewalPropensityEngine.PurchaseEvent("2026-03-20 10:00:00", 350.0),
                        new SubscriptionRenewalPropensityEngine.PurchaseEvent("2026-03-22 10:00:00", 300.0),
                        new SubscriptionRenewalPropensityEngine.PurchaseEvent("2026-03-24 10:00:00", 280.0),
                        new SubscriptionRenewalPropensityEngine.PurchaseEvent("2026-03-26 10:00:00", 320.0),
                        new SubscriptionRenewalPropensityEngine.PurchaseEvent("2026-03-28 10:00:00", 310.0)),
                List.of(
                        new SubscriptionRenewalPropensityEngine.PurchaseEvent("2026-03-20 10:00:00", 200.0),
                        new SubscriptionRenewalPropensityEngine.PurchaseEvent("2026-03-24 10:00:00", 210.0),
                        new SubscriptionRenewalPropensityEngine.PurchaseEvent("2026-03-28 10:00:00", 220.0)));

        List<SubscriptionRenewalPropensityEngine.RenewalPropensityScore> scores = engine.score(
                List.of(highRisk, lowRisk),
                21,
                LocalDate.of(2026, 3, 31));

        assertEquals(2, scores.size());
        assertEquals(1001, scores.get(0).enrollmentId());
        assertTrue(scores.get(0).churnRiskScore() > scores.get(1).churnRiskScore());
        assertEquals("HIGH", scores.get(0).riskBand());
    }

    @Test
    void returnsEmptyWhenNoCandidatesProvided() {
        List<SubscriptionRenewalPropensityEngine.RenewalPropensityScore> scores = engine.score(
                List.of(),
                21,
                LocalDate.of(2026, 3, 31));
        assertTrue(scores.isEmpty());
    }
}
