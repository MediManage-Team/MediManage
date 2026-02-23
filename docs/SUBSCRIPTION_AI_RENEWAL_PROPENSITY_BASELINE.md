# Subscription AI Renewal Propensity Baseline (v1)

Status date: 2026-02-23  
Scope: Identify active members likely to churn before renewal date using behavior-based risk scoring.

## 1) Scoring Window and Candidate Set

1. Candidate enrollments are active subscriptions with renewal due within a configurable window.
2. Default renewal scoring window: `21` days (bounded to `1-90` days).
3. Historical behavior lookback: `180` days.

## 2) Features Used

Per candidate enrollment:

1. Days until renewal date.
2. Purchase recency and purchase count in last 30 days.
3. Monthly spend estimate from recent purchases.
4. Refill regularity score from repeated purchase intervals.
5. Historical renewal count from `customer_subscription_events` (`RENEW` events).
6. Plan monthly cost pressure (plan cost vs observed spend behavior).

## 3) Risk Output

Each scored row contains:

1. Enrollment and customer identifiers.
2. Renewal date and days remaining.
3. Churn probability (`0-1`) and churn risk score (`0-100`).
4. Risk band (`LOW`, `MEDIUM`, `HIGH`).
5. Confidence score and rationale text.
6. Recommended action guidance for operations.

Rows are ranked descending by churn risk score.

## 4) Service and UI Integration

Service API:

- `SubscriptionService.scoreRenewalChurnRisk(int renewalWindowDays, int limit)`

UI integration:

- `SubscriptionEnrollmentController` appends selected customer's renewal-risk summary in context label when available.

## 5) Access and Safety

1. Access requires `MANAGE_SUBSCRIPTION_ENROLLMENTS`.
2. Missing or sparse history reduces confidence and falls back to conservative defaults.
3. If no renewal-due candidates exist, service returns an empty list.
4. The score is advisory; enrollment/approval authority remains role-governed by existing policy.

## 6) Validation Coverage

1. Engine-level tests validate ranking behavior and empty-candidate fallback.
2. DAO integration tests validate renewal-due candidate extraction and renewal history counts.
3. Service tests validate permission guards and ranked churn-risk output assembly.
