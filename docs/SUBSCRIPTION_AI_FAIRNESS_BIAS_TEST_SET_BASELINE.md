# Subscription AI Fairness/Bias Test Set Baseline (v1)

Status date: 2026-02-23  
Scope: Standard offline fairness test set across customer groups for subscription AI features.

## 1) Objective

Create a repeatable cross-group test set used before production AI enablement to detect:

1. Uneven positive-decision rates across customer groups.
2. Feature-level precision/recall drops tied to segment imbalance.
3. Missing coverage for specific AI feature outputs.

## 2) Dataset Artifact

Baseline dataset file:

1. `src/test/resources/subscription-ai/fairness-bias-test-set-v1.csv`

Columns:

1. `case_id`
2. `feature_key`
3. `customer_group`
4. `predicted_positive`
5. `actual_positive`

## 3) Coverage Contract

Required feature keys:

1. `PLAN_RECOMMENDATION`
2. `RENEWAL_PROPENSITY`
3. `ABUSE_DETECTION`
4. `DYNAMIC_OFFER`
5. `OVERRIDE_RISK`

Customer-group coverage in baseline set:

1. `ADULT`
2. `SENIOR`
3. `RURAL`
4. `URBAN`

## 4) Usage

The fairness test set is evaluated by:

1. `SubscriptionAIOfflineEvaluationBenchmarkService`
2. `SubscriptionAIOfflineEvaluationBenchmarkServiceTest`

The test validates both:

1. Feature gate readiness metrics.
2. Fairness positive-rate gap bounds across customer groups.

## 5) Operational Notes

1. This baseline dataset is synthetic and intended for offline regression gate checks.
2. Production fairness reviews should periodically extend this file with fresh adjudicated samples.
