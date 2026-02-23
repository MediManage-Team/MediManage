package org.example.MediManage.service.subscription;

import org.example.MediManage.model.SubscriptionPlan;
import org.example.MediManage.model.SubscriptionPlanStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionPlanRecommendationEngineTest {

    private final SubscriptionPlanRecommendationEngine engine = new SubscriptionPlanRecommendationEngine();

    @Test
    void recommendationRanksPlanWithBestNetBenefitHigher() {
        List<SubscriptionPlan> plans = List.of(
                plan(1, "PREMIUM", "Premium Care", 1500.0, 30, 25.0, 25.0),
                plan(2, "SAVER", "Saver Care", 199.0, 30, 10.0, 15.0),
                plan(3, "BASIC", "Basic Care", 99.0, 30, 3.0, 5.0));

        List<SubscriptionPlanRecommendationEngine.PurchaseEvent> purchases = List.of(
                purchase("2026-01-01 10:00:00", 820.0),
                purchase("2026-01-11 10:00:00", 910.0),
                purchase("2026-01-21 10:00:00", 780.0),
                purchase("2026-02-01 10:00:00", 860.0),
                purchase("2026-02-11 10:00:00", 940.0),
                purchase("2026-02-21 10:00:00", 890.0),
                purchase("2026-03-03 10:00:00", 930.0),
                purchase("2026-03-13 10:00:00", 960.0),
                purchase("2026-03-23 10:00:00", 900.0));

        List<SubscriptionPlanRecommendationEngine.RefillEvent> refills = List.of(
                refill(101, "2026-01-01 10:00:00", 1, 240.0),
                refill(101, "2026-01-11 10:00:00", 1, 240.0),
                refill(101, "2026-01-21 10:00:00", 1, 240.0),
                refill(101, "2026-02-01 10:00:00", 1, 240.0),
                refill(101, "2026-02-11 10:00:00", 1, 240.0),
                refill(101, "2026-02-21 10:00:00", 1, 240.0),
                refill(101, "2026-03-03 10:00:00", 1, 240.0),
                refill(101, "2026-03-13 10:00:00", 1, 240.0),
                refill(101, "2026-03-23 10:00:00", 1, 240.0));

        SubscriptionPlanRecommendationEngine.RecommendationResult result = engine.recommend(
                plans,
                purchases,
                refills,
                180,
                LocalDate.of(2026, 3, 31));

        assertFalse(result.recommendations().isEmpty());
        assertEquals("SAVER", result.recommendations().get(0).planCode());
        assertTrue(result.recommendations().get(0).expectedNetMonthlyBenefit() > 0.0);
        assertTrue(result.statusMessage().contains("Recommendation generated"));
    }

    @Test
    void recommendationReturnsEmptyWhenCustomerHasNoPurchaseHistory() {
        List<SubscriptionPlan> plans = List.of(
                plan(1, "SAVER", "Saver Care", 199.0, 30, 10.0, 15.0));

        SubscriptionPlanRecommendationEngine.RecommendationResult result = engine.recommend(
                plans,
                List.of(),
                List.of(),
                180,
                LocalDate.of(2026, 3, 31));

        assertTrue(result.recommendations().isEmpty());
        assertTrue(result.statusMessage().contains("No customer purchase history"));
    }

    private static SubscriptionPlan plan(
            int planId,
            String code,
            String name,
            double price,
            int durationDays,
            double defaultDiscount,
            double maxDiscount) {
        return new SubscriptionPlan(
                planId,
                code,
                name,
                "test plan",
                price,
                durationDays,
                7,
                defaultDiscount,
                maxDiscount,
                5.0,
                SubscriptionPlanStatus.ACTIVE,
                false,
                true,
                null,
                null,
                null,
                null);
    }

    private static SubscriptionPlanRecommendationEngine.PurchaseEvent purchase(String billDate, double amount) {
        return new SubscriptionPlanRecommendationEngine.PurchaseEvent(billDate, amount);
    }

    private static SubscriptionPlanRecommendationEngine.RefillEvent refill(
            int medicineId,
            String billDate,
            int quantity,
            double lineTotal) {
        return new SubscriptionPlanRecommendationEngine.RefillEvent(medicineId, billDate, quantity, lineTotal);
    }
}
