# Subscription AI Model Monitoring Dashboard Baseline (v1)

Status date: 2026-02-23  
Scope: Dashboard monitoring metrics for AI abuse detection quality and recommendation adoption.

## 1) Dashboard Surface

Monitoring metrics are surfaced in the reports subscription section (`reports-view.fxml`) and computed through:

- `SubscriptionModelMonitoringService`
- `ReportsController`

## 2) Abuse Detection Monitoring Metric

Metric type: precision/recall for discount abuse detection.

Ground-truth source (current baseline):

1. `subscription_pilot_feedback` rows in selected date window.
2. Only `HIGH`/`CRITICAL` severity rows with `RESOLVED` status are treated as confirmed positives.
3. Confirmed positives are mapped by linked entities:
   - `linked_override_id` -> `user:<requester_username>`
   - `linked_bill_id` -> `plan:<plan_code>`

Predicted positives source:

1. `SubscriptionDiscountAbuseDetectionEngine` outputs over selected window.
2. Only monitorable signal types are used for precision/recall:
   - `OVERRIDE_PATTERN`
   - `BILLING_PATTERN`

Definitions:

1. `TP` = predicted subject intersects confirmed subject.
2. `FP` = predicted subject not confirmed.
3. `FN` = confirmed subject not predicted.
4. `Precision` = `TP / (TP + FP)`.
5. `Recall` = `TP / (TP + FN)`.

## 3) Recommendation Acceptance Metric

Metric type: recommendation acceptance rate for enrollment decisions.

Population:

1. Enrollments created in selected date window (`customer_subscriptions.created_at`).
2. For each enrollment, recommendation is replayed at enrollment reference date using:
   - plan catalog,
   - purchase history lookback,
   - refill history lookback.

Acceptance definition:

1. Accepted if enrolled plan id equals top recommended plan id.
2. Evaluated enrollment = recommendation list is non-empty.
3. Skipped enrollment = recommendation list empty.

Formula:

1. `Acceptance Rate` = `accepted / evaluated`.

## 4) Constraints and Interpretation

1. Abuse precision/recall currently covers override and billing subjects where explicit pilot feedback linkage exists.
2. Enrollment-pattern abuse signals remain operationally visible but are not yet part of precision/recall ground truth in this baseline.
3. Recommendation acceptance is decision-adoption monitoring, not a direct business-outcome metric.

## 5) Validation Coverage

1. `SubscriptionModelMonitoringServiceTest` validates abuse precision/recall calculation and recommendation acceptance rate calculation.
2. `SubscriptionDAOIntegrationTest` validates monitoring data extraction queries for confirmed abuse subjects and enrollment decision rows.
