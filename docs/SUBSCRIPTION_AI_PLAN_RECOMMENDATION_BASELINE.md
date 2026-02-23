# Subscription AI Plan Recommendation Baseline (v1)

Status date: 2026-02-23  
Scope: AI baseline recommendation for selecting the best subscription plan for a customer using purchase history, refill behavior, and expected savings.

## 1) Data Inputs

Lookback window:

- Default: `180` days (bounded to `30-365` days).

Signals used:

1. Customer bill history (`bills`) for monthly spend and purchase cadence.
2. Customer refill events (`bills + bill_items`) for medicine-level repeat intervals.
3. Candidate subscription plans (`subscription_plans`) in `ACTIVE` / `DRAFT` state.

## 2) Behavior Features

For each customer:

1. Estimated monthly spend from observed total billed amount.
2. Estimated monthly bill frequency.
3. Refill interval distribution from repeated medicine purchases.
4. Refill regularity score from interval consistency.
5. Confidence score from history depth + refill signal strength.

## 3) Plan Scoring Logic

For each candidate plan:

1. Compute effective discount percent using plan defaults and cap.
2. Estimate savings using:
   - monthly spend,
   - discount percent,
   - applicability ratio derived from refill regularity + confidence.
3. Convert plan price to monthly cost from plan duration.
4. Compute net monthly and annual expected benefit.
5. Produce a recommendation score (`0-100`) weighted by:
   - net benefit,
   - savings magnitude,
   - refill regularity,
   - refill frequency fit.

Output is sorted by score, then by expected net monthly benefit.

## 4) Service and UI Integration

Service API:

- `SubscriptionService.recommendPlansForCustomer(int customerId)`

UI consumption:

- `SubscriptionEnrollmentController` shows top recommendation summary in enrollment context:
  - recommended plan,
  - score,
  - estimated savings/month,
  - estimated net benefit/month.

## 5) Safety and Fallback Behavior

1. No purchase history -> no recommendation list, explicit status message.
2. Sparse history -> recommendation still generated with low-confidence message.
3. Invalid customer id -> request rejected.
4. Access controlled by `MANAGE_SUBSCRIPTION_ENROLLMENTS` permission.

## 6) Validation Coverage

Automated coverage includes:

1. Engine ranking behavior and no-history fallback.
2. Service-level recommendation flow and permission guard.
3. DAO integration for purchase/refill event extraction.
