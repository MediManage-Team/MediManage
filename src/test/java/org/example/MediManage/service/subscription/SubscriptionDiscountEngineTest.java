package org.example.MediManage.service.subscription;

import org.example.MediManage.model.BillItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionDiscountEngineTest {

    private final SubscriptionDiscountEngine engine = new SubscriptionDiscountEngine();

    @Test
    void excludesMedicineWhenRuleIsExclude() {
        SubscriptionDiscountEngine.EvaluationContext context = new SubscriptionDiscountEngine.EvaluationContext(
                new SubscriptionDiscountEngine.PlanPolicy(true, 10.0, 25.0, 0.0),
                Map.of(101, new SubscriptionDiscountEngine.DiscountRule(false, 15.0, null, null, true)),
                Map.of(),
                Map.of(),
                Map.of());

        SubscriptionDiscountEngine.EvaluationResult result = engine.evaluate(
                List.of(item(101, "Drug A", 2, 100.0)),
                context);

        assertEquals(200.0, result.subtotal(), 0.0001);
        assertEquals(0.0, result.totalDiscount(), 0.0001);
        assertFalse(result.items().get(0).discountApplied());
        assertEquals("EXCLUDED_BY_RULE", result.items().get(0).reasonCode());
    }

    @Test
    void appliesMaxPlanCapWhenDefaultDiscountExceedsCap() {
        SubscriptionDiscountEngine.EvaluationContext context = new SubscriptionDiscountEngine.EvaluationContext(
                new SubscriptionDiscountEngine.PlanPolicy(true, 20.0, 15.0, 0.0),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of());

        SubscriptionDiscountEngine.EvaluationResult result = engine.evaluate(
                List.of(item(201, "Drug B", 2, 100.0)),
                context);

        assertEquals(30.0, result.totalDiscount(), 0.0001);
        assertEquals(15.0, result.items().get(0).appliedPercent(), 0.0001);
    }

    @Test
    void enforcesMarginFloorWhenCostIsAvailable() {
        SubscriptionDiscountEngine.EvaluationContext context = new SubscriptionDiscountEngine.EvaluationContext(
                new SubscriptionDiscountEngine.PlanPolicy(true, 20.0, 25.0, 10.0),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(301, 85.0));

        SubscriptionDiscountEngine.EvaluationResult result = engine.evaluate(
                List.of(item(301, "Drug C", 1, 100.0)),
                context);

        assertEquals(5.0, result.totalDiscount(), 0.0001);
        assertEquals(5.0, result.items().get(0).appliedPercent(), 0.0001);
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void appliesCategoryRuleAmountCap() {
        SubscriptionDiscountEngine.EvaluationContext context = new SubscriptionDiscountEngine.EvaluationContext(
                new SubscriptionDiscountEngine.PlanPolicy(true, 5.0, 30.0, 0.0),
                Map.of(),
                Map.of("antibiotic", new SubscriptionDiscountEngine.DiscountRule(true, 20.0, 12.0, null, true)),
                Map.of(401, "antibiotic"),
                Map.of());

        SubscriptionDiscountEngine.EvaluationResult result = engine.evaluate(
                List.of(item(401, "Drug D", 1, 100.0)),
                context);

        assertEquals(12.0, result.totalDiscount(), 0.0001);
        assertEquals(12.0, result.items().get(0).appliedPercent(), 0.0001);
    }

    @Test
    void warnsWhenMarginFloorCannotBeValidatedDueToMissingCost() {
        SubscriptionDiscountEngine.EvaluationContext context = new SubscriptionDiscountEngine.EvaluationContext(
                new SubscriptionDiscountEngine.PlanPolicy(true, 10.0, 30.0, 12.0),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of());

        SubscriptionDiscountEngine.EvaluationResult result = engine.evaluate(
                List.of(item(501, "Drug E", 1, 100.0)),
                context);

        assertEquals(10.0, result.totalDiscount(), 0.0001);
        assertFalse(result.warnings().isEmpty());
    }

    @Test
    void returnsNoDiscountWhenPlanIsInactive() {
        SubscriptionDiscountEngine.EvaluationContext context = new SubscriptionDiscountEngine.EvaluationContext(
                new SubscriptionDiscountEngine.PlanPolicy(false, 10.0, 25.0, 0.0),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of());

        SubscriptionDiscountEngine.EvaluationResult result = engine.evaluate(
                List.of(item(601, "Drug F", 1, 50.0)),
                context);

        assertEquals(0.0, result.totalDiscount(), 0.0001);
        assertEquals("PLAN_INACTIVE_OR_MISSING", result.items().get(0).reasonCode());
    }

    private static BillItem item(int medicineId, String name, int qty, double unitPrice) {
        return new BillItem(medicineId, name, "2030-12-31", qty, unitPrice, 0.0);
    }
}
