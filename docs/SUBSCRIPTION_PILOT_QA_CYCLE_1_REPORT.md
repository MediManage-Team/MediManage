# Subscription Pilot QA Cycle 1 Report

Status date: 2026-02-23  
Scope: First QA cycle for pilot-ready subscription commerce + weekly analytics delivery.

## 1. Objective

Validate pilot readiness of:

1. Subscription discount core flows.
2. Weekly analytics dashboards and insights.
3. New export and scheduled dispatch module.

## 2. Build/Runtime Context

1. Project: `Project_File` (`0.1.5`)
2. Java: `21`
3. Maven local repo override: `.m2/repository`
4. Database backend in tests: SQLite (ephemeral per integration test class).

## 3. Executed QA Command

```powershell
mvn "-Dmaven.repo.local=.m2/repository" "-Dtest=AnalyticsReportDispatchDAOIntegrationTest,MedicineDAOInsightsIntegrationTest,DashboardKpiServiceTest,SubscriptionDAOIntegrationTest,ReportingWindowUtilsTest,FeatureFlagsTest,BillingServiceSubscriptionCheckoutTest,SubscriptionApprovalServiceTest,WeeklyAnomalyAlertEvaluatorTest,AnomalyActionTrackerDAOIntegrationTest" test
```

Reproducible wrapper script:

```powershell
./run_pilot_qa_cycle.ps1
```

## 4. Test Result Summary

1. Result: `BUILD SUCCESS`
2. Total tests: `42`
3. Failures: `0`
4. Errors: `0`
5. Skipped: `0`
6. Completion timestamp: 2026-02-23 17:05:48 (+05:30)
7. Execution log artifact: `docs/pilot-logs/qa-cycle-1-latest.log`

## 5. Coverage Included in Cycle 1

1. Subscription flag behavior and permission checks.
2. Billing checkout with subscription discount flows.
3. Subscription approval service flows.
4. Subscription DAO integration coverage.
5. Weekly reporting window utilities and anomaly evaluator logic.
6. Action tracker integration.
7. Medicine analytics insights integration.
8. Dispatch scheduling DAO integration (new).

## 6. Defects Found in This Cycle

No blocking or high-severity defects were observed in the automated cycle.

## 7. Residual Risks / Follow-Ups

1. Scheduled dispatch currently writes to local outbox files (`reports/dispatch-outbox`) and metadata envelopes; external gateway delivery (SMTP/WhatsApp provider) is pending integration.
2. UAT with pharmacy operations team remains open and must be completed before all-stores rollout.

## 8. QA Decision

Cycle 1 QA is considered **shipped for pilot release baseline** based on passing automated regression/integration scope.
