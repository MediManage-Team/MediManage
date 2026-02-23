# Subscription AI Override Risk Scoring Baseline (v2)

Status date: 2026-02-23  
Scope: Assist `Manager`/`Admin` before manual override approval by scoring risk for each pending override request.

## 1) Objective

Provide an advisory risk score per override request before approval/rejection, so approvers can prioritize deeper review for suspicious exceptions.

## 2) Inputs

Override request context:

1. Requested discount percent.
2. Request reason quality (empty/short reasons increase risk).
3. Requester identity and historical override signal profile.
4. Customer lifecycle anomaly profile (plan changes/cancellations/backdated enrollments).

Source signals:

1. `subscription_discount_overrides` aggregates (request volume, rejection rate, requested percent profile).
2. `customer_subscription_events` aggregates (enrollment churn patterns).

## 3) Scoring Output

Per override:

1. `riskScore` (`0-100`)
2. `riskBand` (`LOW`, `MEDIUM`, `HIGH`)
3. `escalationRecommended` flag
4. Summary + rationale + recommended approver action

## 4) Service APIs

Approval service provides:

1. `getOverrideRiskAssessment(int overrideId)`
2. `getPendingOverrideRiskAssessments(int limit)`
3. `approveManualOverride(int overrideId, double approvedDiscountPercent, String decisionReason, boolean highRiskHumanReviewConfirmed)`

Both APIs require `APPROVE_SUBSCRIPTION_OVERRIDES`.

## 5) UI Integration

`SubscriptionAdminController` includes risk output in selected override context text:

- displays `AI Risk: <band> <score>/100` to approver before approve/reject.
- for `escalationRecommended=true`, UI requires explicit "manual high-risk review completed" confirmation before approval.

## 6) Governance and Safety

1. Scoring is advisory; final decision remains human and role-controlled.
2. No automatic approve/reject action is triggered by score.
3. Existing mandatory reason capture and self-approval block remain enforced.
4. High-risk (`escalationRecommended=true`) approvals require explicit human-review confirmation.
5. Approval ownership remains `Manager`/`Admin`; no role expansion is introduced.
6. Audit-chain approval payload now includes AI risk metadata and high-risk review confirmation state.

## 7) Validation Coverage

1. Engine unit tests for high-vs-low risk differentiation.
2. Approval-service tests for risk assessment access and output.
3. High-risk approval is blocked unless explicit human-review confirmation is provided.
4. Existing approval flow tests retained for approve/reject correctness.
