# Subscription Eligibility Rules Baseline (v1)

Status date: 2026-02-23  
Scope: Frozen eligibility and discount guardrail behavior for subscription checkout.

## 1) Rule Inputs

Eligibility and discount evaluation uses:

1. Plan-level policy:
   - `default_discount_percent`
   - `max_discount_percent`
   - `minimum_margin_percent`
2. Medicine-level rules (`subscription_plan_medicine_rules`)
3. Category-level rules (`subscription_plan_category_rules`)
4. Medicine category mapping:
   - Sourced from `medicines.generic_name` (normalized lowercase)

## 2) Rule Precedence

For each bill line:

1. Resolve rule in this order:
   - medicine rule (if present)
   - else category rule (if present)
   - else plan defaults
2. Exclusion:
   - if resolved rule has `include_rule=false`, discount is not applied.
3. Discount percent:
   - start from resolved rule percent (or plan default),
   - clamp by plan max discount cap.
4. Margin floor:
   - resolved min margin uses rule min margin if present, else plan minimum margin.
   - if medicine unit cost is unavailable, checkout warns and applies cap-based path.
5. Amount cap:
   - if resolved rule has `max_discount_amount`, per-line discount is capped.

## 3) Frozen Guardrails

1. Rule discount percent cannot exceed plan max discount percent at save time.
2. Rule minimum margin percent cannot be below plan minimum margin percent at save time.
3. Plan max discount and minimum margin are bounded to `0..100`.
4. Rule min margin is bounded to `0..100`.

## 4) Code References

1. `src/main/java/org/example/MediManage/service/subscription/SubscriptionDiscountEngine.java`
2. `src/main/java/org/example/MediManage/service/BillingService.java`
3. `src/main/java/org/example/MediManage/dao/SubscriptionDAO.java`
4. `src/main/java/org/example/MediManage/service/SubscriptionService.java`
