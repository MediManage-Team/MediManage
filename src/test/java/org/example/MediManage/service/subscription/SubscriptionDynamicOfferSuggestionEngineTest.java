package org.example.MediManage.service.subscription;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionDynamicOfferSuggestionEngineTest {

    private final SubscriptionDynamicOfferSuggestionEngine engine = new SubscriptionDynamicOfferSuggestionEngine();

    @Test
    void suggestEnforcesPlanCapAndMarginGuardrails() {
        List<SubscriptionDynamicOfferSuggestionEngine.OfferCandidate> candidates = List.of(
                new SubscriptionDynamicOfferSuggestionEngine.OfferCandidate(
                        1,
                        "PLAN-A",
                        "Plan A",
                        15.0,
                        300.0,
                        200.0,
                        20.0,
                        85.0),
                new SubscriptionDynamicOfferSuggestionEngine.OfferCandidate(
                        2,
                        "PLAN-B",
                        "Plan B",
                        10.0,
                        200.0,
                        150.0,
                        18.0,
                        80.0));

        List<SubscriptionDynamicOfferSuggestionEngine.DynamicOfferSuggestion> offers = engine.suggest(
                candidates,
                "HIGH",
                90.0,
                5);

        assertEquals(2, offers.size());
        SubscriptionDynamicOfferSuggestionEngine.DynamicOfferSuggestion planA = offers.stream()
                .filter(row -> row.planId() == 1)
                .findFirst()
                .orElseThrow();
        SubscriptionDynamicOfferSuggestionEngine.DynamicOfferSuggestion planB = offers.stream()
                .filter(row -> row.planId() == 2)
                .findFirst()
                .orElseThrow();

        // Plan A: cap=20 but margin guard => max 15.
        assertEquals(15.0, planA.offerDiscountPercent(), 0.0001);
        assertTrue(planA.guardrailCapApplied());

        // Plan B: uplift=6.8 => 10 + 6.8 = 16.8, still within cap and margin guardrails.
        assertEquals(16.8, planB.offerDiscountPercent(), 0.0001);
        assertFalse(planB.guardrailCapApplied());
        assertTrue(planB.offerDiscountPercent() <= planB.guardrailMaxByPlanCapPercent() + 0.0001);
        assertTrue(planB.offerDiscountPercent() <= planB.guardrailMaxByMarginPercent() + 0.0001);
    }

    @Test
    void suggestReturnsEmptyWhenNoCandidates() {
        List<SubscriptionDynamicOfferSuggestionEngine.DynamicOfferSuggestion> offers = engine.suggest(
                List.of(),
                "LOW",
                10.0,
                3);
        assertTrue(offers.isEmpty());
    }
}
