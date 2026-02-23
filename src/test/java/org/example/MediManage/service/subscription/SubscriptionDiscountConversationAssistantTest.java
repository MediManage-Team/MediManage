package org.example.MediManage.service.subscription;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionDiscountConversationAssistantTest {

    private final SubscriptionDiscountConversationAssistant assistant = new SubscriptionDiscountConversationAssistant();

    @Test
    void explainReturnsAppliedNarrativeWhenDiscountExists() {
        SubscriptionDiscountEngine.EvaluationResult evaluation = new SubscriptionDiscountEngine.EvaluationResult(
                200.0,
                30.0,
                170.0,
                List.of(
                        new SubscriptionDiscountEngine.ItemEvaluation(101, "Drug A", 2, 100.0, 200.0, true, "APPLIED", 15.0, 30.0),
                        new SubscriptionDiscountEngine.ItemEvaluation(202, "Drug B", 1, 80.0, 80.0, false, "EXCLUDED_BY_RULE", 0.0, 0.0)),
                List.of());

        SubscriptionDiscountConversationAssistant.AssistantResponse response = assistant.explain(
                new SubscriptionDiscountConversationAssistant.AssistantInput(
                        true,
                        "Alice",
                        SubscriptionEligibilityCode.ELIGIBLE,
                        "Subscription is eligible for discount.",
                        10,
                        "Gold Plan",
                        evaluation));

        assertTrue(response.discountApplied());
        assertEquals(SubscriptionEligibilityCode.ELIGIBLE, response.eligibilityCode());
        assertTrue(response.summary().contains("Applied"));
        assertTrue(response.summary().contains("Alice"));
        assertTrue(response.talkingPoints().stream().anyMatch(line -> line.contains("Discount affected 1 of 2")));
    }

    @Test
    void explainReturnsGuidanceForIneligibleState() {
        SubscriptionDiscountConversationAssistant.AssistantResponse response = assistant.explain(
                new SubscriptionDiscountConversationAssistant.AssistantInput(
                        true,
                        "Alice",
                        SubscriptionEligibilityCode.NO_ENROLLMENT,
                        "No active subscription enrollment found for this customer.",
                        null,
                        null,
                        null));

        assertFalse(response.discountApplied());
        assertEquals(SubscriptionEligibilityCode.NO_ENROLLMENT, response.eligibilityCode());
        assertTrue(response.summary().contains("not applied"));
        assertTrue(response.talkingPoints().stream().anyMatch(line -> line.contains("No active subscription enrollment")));
    }

    @Test
    void explainReturnsNoDiscountNarrativeForEligibleButZeroDiscount() {
        SubscriptionDiscountEngine.EvaluationResult evaluation = new SubscriptionDiscountEngine.EvaluationResult(
                100.0,
                0.0,
                100.0,
                List.of(new SubscriptionDiscountEngine.ItemEvaluation(
                        303,
                        "Drug C",
                        1,
                        100.0,
                        100.0,
                        false,
                        "EXCLUDED_BY_RULE",
                        0.0,
                        0.0)),
                List.of());

        SubscriptionDiscountConversationAssistant.AssistantResponse response = assistant.explain(
                new SubscriptionDiscountConversationAssistant.AssistantInput(
                        true,
                        "Alice",
                        SubscriptionEligibilityCode.ELIGIBLE,
                        "Subscription is eligible for discount.",
                        10,
                        "Gold Plan",
                        evaluation));

        assertFalse(response.discountApplied());
        assertEquals(SubscriptionEligibilityCode.ELIGIBLE, response.eligibilityCode());
        assertTrue(response.summary().contains("No subscription discount was applied"));
        assertTrue(response.talkingPoints().stream().anyMatch(line -> line.contains("excluded")));
    }
}
