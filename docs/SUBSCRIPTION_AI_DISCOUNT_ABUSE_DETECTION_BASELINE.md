# Subscription AI Discount Abuse Detection Baseline (v1)

Status date: 2026-02-23  
Scope: Detect suspicious subscription discount behavior across enrollments, overrides, and billing integrity patterns.

## 1) Detection Window and Access

1. Default evaluation window: last `30` days.
2. Caller can provide custom date range.
3. Access is restricted to `APPROVE_SUBSCRIPTION_OVERRIDES`.

## 2) Input Signals

The detector combines three signal families:

1. **Enrollment pattern signals**
   - Frequent lifecycle events (`ENROLL`, `PLAN_CHANGE`, `FREEZE`, `CANCEL`) per customer.
   - Backdated enrollments (`start_date < created_at`).
   - Distinct-plan switching concentration.
2. **Override pattern signals**
   - High override volume.
   - Elevated rejection ratio.
   - Unusually high requested discount percentages.
3. **Billing integrity signals**
   - Pricing anomaly alerts (`NEGATIVE_SAVINGS`, `SAVINGS_EXCEED_GROSS`, etc.).
   - High-severity alert accumulation by subscription plan.

## 3) Output Contract

Returned findings are normalized as:

1. `signalType`: `ENROLLMENT_PATTERN`, `OVERRIDE_PATTERN`, `BILLING_PATTERN`
2. `severity`: `LOW`, `MEDIUM`, `HIGH`
3. `riskScore`: `0-100`
4. `subjectReference`: customer/user/plan identifier
5. `summary` and `thresholdRule`
6. `firstObservedAt` and `latestObservedAt`

Findings are ranked by risk score (descending).

## 4) Service API

- `SubscriptionService.detectDiscountAbuse(LocalDate startDate, LocalDate endDate, int limit)`

The API:

1. Pulls enrollment/override/billing signals from `SubscriptionDAO`.
2. Runs `SubscriptionDiscountAbuseDetectionEngine`.
3. Returns top-ranked findings with configurable limit.

## 5) Safety and Governance Notes

1. Output is advisory for compliance monitoring and review prioritization.
2. No auto-block or auto-approval is performed by this detector.
3. Manual action remains governed by existing role/approval policy.
4. This baseline does not use sensitive unnecessary fields beyond operational discount logs.

## 6) Validation Coverage

1. Engine tests validate cross-signal ranking and empty-input fallback.
2. DAO integration tests validate enrollment abuse signal extraction.
3. Service tests validate permission enforcement and merged finding output.
