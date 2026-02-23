# Subscription AI Decision Logging Baseline (v1)

Status date: 2026-02-23  
Scope: Persist reason-coded AI decisions for subscription workflows to improve auditability and post-incident investigation.

## 1) Objective

Capture deterministic AI decision trails so operations teams can answer:

1. What AI decision was produced?
2. Why was it produced (clear reason code + message)?
3. Which model component generated it and on which subject?

## 2) Data Model

Table: `subscription_ai_decision_log`

Core fields:

1. `decision_type`
2. `subject_type`
3. `subject_ref`
4. `reason_code`
5. `reason_message`
6. `decision_payload_json`
7. `model_component`
8. `model_version`
9. `prompt_key`
10. `prompt_version`
11. `actor_user_id`
12. `created_at`

Index coverage:

1. `(decision_type, created_at)` for timeline audit queries.
2. `(subject_type, subject_ref, created_at)` for subject-level incident review.
3. `(reason_code, created_at)` for reason-code trend analysis.

## 3) Logged AI Workflows

Current reason-coded logging is wired to:

1. Plan recommendation (`SubscriptionPlanRecommendationEngine`)
2. Dynamic offer suggestion (`SubscriptionDynamicOfferSuggestionEngine`)
3. Renewal churn-risk scoring (`SubscriptionRenewalPropensityEngine`)
4. Discount abuse detection (`SubscriptionDiscountAbuseDetectionEngine`)
5. Override approval risk advisory (`SubscriptionOverrideRiskScoringEngine`)

## 4) Reason Code Contract (Examples)

1. Plan recommendation:
   `PLAN_RECO_NO_ACTIVE_PLANS`, `PLAN_RECO_NO_HISTORY`, `PLAN_RECO_LOW_CONFIDENCE`, `PLAN_RECO_TOP_POSITIVE_BENEFIT`
2. Dynamic offers:
   `DYNAMIC_OFFER_NO_CANDIDATES`, `DYNAMIC_OFFER_GUARDRAIL_CLIPPED`, `DYNAMIC_OFFER_WITHIN_GUARDRAIL`
3. Renewal propensity:
   `RENEWAL_NO_CANDIDATES`, `RENEWAL_RISK_HIGH`, `RENEWAL_RISK_MEDIUM`, `RENEWAL_RISK_LOW`
4. Abuse detection:
   `ABUSE_NO_FINDINGS` and signal/severity combinations (for example `ABUSE_OVERRIDE_PATTERN_HIGH`)
5. Override risk advisory:
   `OVERRIDE_RISK_HIGH_ESCALATE_REQUIRED`, `OVERRIDE_RISK_HIGH_ESCALATE_CONFIRMED`, `OVERRIDE_RISK_MEDIUM_ESCALATE`, `OVERRIDE_RISK_LOW_STANDARD`

## 5) Safety and Operational Notes

1. Decision logging is best-effort and does not block core subscription operations on DAO/write failure.
2. Logs are advisory artifacts; approval ownership and final decisions remain with Manager/Admin policies.
3. Payloads store structured context needed for post-incident analysis without changing runtime decision behavior.

## 6) Validation Coverage

1. `SubscriptionAIDecisionLogServiceTest` validates normalization and failure-safe behavior.
2. `SubscriptionAIDecisionLogDAOIntegrationTest` validates persistence and retrieval.
3. Service-level tests validate reason code emission at key AI decision points.
