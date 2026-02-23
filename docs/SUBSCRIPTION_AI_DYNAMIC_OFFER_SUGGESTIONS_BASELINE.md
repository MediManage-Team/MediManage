# Subscription AI Dynamic Offer Suggestions Baseline (v1)

Status date: 2026-02-23  
Scope: Generate customer-specific dynamic subscription offer suggestions while enforcing strict discount guardrails.

## 1) Objective

Recommend retention/upsell offer discounts dynamically based on customer behavior and churn signal, without violating policy limits.

## 2) Inputs

1. Plan recommendation outputs (expected savings and effective discount baseline).
2. Customer renewal-churn risk band/score.
3. Plan policy guardrails:
   - `max_discount_percent`
   - `minimum_margin_percent`

## 3) Guardrail Enforcement

Suggested offer discount is clipped by:

1. Plan cap: `offer <= plan.max_discount_percent`
2. Margin floor-derived cap: `offer <= (100 - plan.minimum_margin_percent)`

Final enforced bound:

- `offer <= min(plan cap, margin-floor cap)`

## 4) Output Contract

Each dynamic offer row includes:

1. Plan identity and suggested discount percent.
2. Guardrail cap values and whether clipping was applied.
3. Expected monthly savings and incremental savings.
4. Expected net monthly benefit.
5. Customer risk context and explanation.

## 5) Service and UI Integration

Service API:

- `SubscriptionService.suggestDynamicOffersForCustomer(int customerId, int limit)`

UI integration:

- `SubscriptionEnrollmentController` shows top offer summary in customer context line:
  - plan code,
  - suggested discount,
  - guardrail status.

## 6) Safety Notes

1. Offers are advisory and do not auto-apply.
2. Existing checkout discount enforcement remains source-of-truth.
3. Suggestions never exceed configured discount cap and margin-floor constraints.
4. Role access remains controlled via `MANAGE_SUBSCRIPTION_ENROLLMENTS`.

## 7) Validation Coverage

1. Engine tests verify guardrail clipping and empty-input fallback.
2. Service tests verify permission enforcement and guardrail-constrained output.
