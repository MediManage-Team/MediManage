# Subscription AI Conversational Explanation Assistant Baseline (v1)

Status date: 2026-02-23  
Scope: Provide staff-friendly explanations for why subscription discount was applied or rejected at checkout.

## 1) Objective

Enable billing operators to quickly explain subscription discount outcomes to customers using consistent, policy-aware language.

## 2) Inputs

1. Subscription feature flag status.
2. Customer eligibility outcome and reason code.
3. Active enrollment/plan context when available.
4. Discount engine line-level evaluation output (`reasonCode`, applied amount, warnings).

## 3) Output Contract

Assistant response includes:

1. Decision state (`discountApplied`: true/false).
2. Eligibility code snapshot.
3. One-line summary statement for operator communication.
4. Structured talking points (reason + next-step guidance).

## 4) Behavior Rules

1. If subscription feature is disabled, explain flag-gated rejection.
2. If eligibility fails, explain exact reason code and operational next step.
3. If eligibility passes but total discount is zero, explain line-level policy outcomes (for example, exclusions/guardrails).
4. If discount is applied, explain effective savings, affected lines, and residual non-discounted lines.

## 5) Integration

Service API:

- `BillingService.explainSubscriptionDiscountDecision(List<BillItem>, Customer)`

UI hook:

- `BillingController` exposes `Explain Discount Decision (AI)` action in billing screen.

## 6) Safety Notes

1. Assistant is advisory only and does not change billing totals.
2. Existing discount engine remains the source of truth.
3. Response uses deterministic, auditable reason-code mapping (no free-form external model call).

## 7) Validation Coverage

1. Engine unit tests for applied, rejected, and zero-discount explanations.
2. Billing service tests for applied/rejected explanation flows.
