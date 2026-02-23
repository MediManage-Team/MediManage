# Subscription AI Offline Evaluation Benchmarks Baseline (v1)

Status date: 2026-02-23  
Scope: Pre-production benchmark gates that must pass before enabling subscription AI features in production rollout.

## 1) Objective

Define deterministic offline gates for each subscription AI feature so release decisions do not rely on ad-hoc judgment.

## 2) Benchmark Engine

Implementation:

1. `src/main/java/org/example/MediManage/service/subscription/SubscriptionAIOfflineEvaluationBenchmarkService.java`

Core outputs per feature:

1. Confusion-matrix counts (`TP`, `FP`, `TN`, `FN`)
2. `precisionPercent`
3. `recallPercent`
4. `accuracyPercent`
5. Fairness positive-rate gap across customer groups
6. Gate pass/fail decision with blocking reasons

## 3) Gate Thresholds (Baseline)

1. Minimum cases per feature: `8`
2. Minimum precision: `50%`
3. Minimum recall: `50%`
4. Maximum group positive-rate gap: `25%`

If any required feature fails a threshold, benchmark snapshot is treated as **not production-ready**.

## 4) Required Feature Keys

1. `PLAN_RECOMMENDATION`
2. `RENEWAL_PROPENSITY`
3. `ABUSE_DETECTION`
4. `DYNAMIC_OFFER`
5. `OVERRIDE_RISK`

## 5) Validation

Automated tests:

1. `src/test/java/org/example/MediManage/service/subscription/SubscriptionAIOfflineEvaluationBenchmarkServiceTest.java`

Test coverage includes:

1. Full fairness test set pass path.
2. Failure path when fairness gap exceeds threshold for one feature.

## 6) Pre-Enablement Command

Run before enabling AI features in production flag rollouts:

```bash
mvn "-Dmaven.repo.local=.m2/repository" "-Dtest=SubscriptionAIOfflineEvaluationBenchmarkServiceTest" test
```

## 7) Governance Rule

1. Do not promote AI feature flags to production ON state unless offline benchmark gate is green.
2. If gate fails, remediate model/rules/prompts and rerun offline benchmarks before release.
