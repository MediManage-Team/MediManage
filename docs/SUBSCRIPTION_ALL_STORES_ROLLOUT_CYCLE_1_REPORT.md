# Subscription All-Stores Rollout Cycle 1 Report

Status date: 2026-02-23  
Scope: First full rollout cycle readiness decision for subscription commerce across all stores.

## 1. Objective

Execute the rollout runbook entry gates with artifacts before broad rollout:

1. 7-day pricing and override monitoring gate.
2. Regression QA gate.
3. Rollout decision with rollback-ready flag posture.

## 2. Executed Command

```powershell
./run_all_stores_rollout_cycle.ps1 -DbPath "medimanage.db" -StartDate "2026-02-17" -EndDate "2026-02-23" -MavenRepoLocal ".m2/repository" -MonitoringOutputPath "docs/pilot-logs/2026-02-17_to_2026-02-23-subscription-rollout-readiness-monitoring.md" -QaOutputLogPath "docs/pilot-logs/2026-02-17_to_2026-02-23-subscription-rollout-readiness-qa.log" -RolloutOutputPath "docs/pilot-logs/2026-02-17_to_2026-02-23-subscription-all-stores-rollout-cycle-1.md"
```

## 3. Result Summary

1. Monitoring gate: `PASS`
2. High/Critical pricing alerts (window): `0`
3. High/Critical override abuse signals (window): `0`
4. Open High/Critical pilot feedback items (window): `0`
5. Regression gate: `PASS` (`46` tests, `0` failures/errors/skips)
6. Rollout decision: `PROCEED`

## 4. Evidence Artifacts

1. Cycle decision artifact: `docs/pilot-logs/2026-02-17_to_2026-02-23-subscription-all-stores-rollout-cycle-1.md`
2. Monitoring artifact: `docs/pilot-logs/2026-02-17_to_2026-02-23-subscription-rollout-readiness-monitoring.md`
3. QA artifact: `docs/pilot-logs/2026-02-17_to_2026-02-23-subscription-rollout-readiness-qa.log`
4. Runbook baseline: `docs/SUBSCRIPTION_ROLLOUT_ROLLBACK_RUNBOOK.md`

## 5. Flag Posture for Rollout and Rollback

Rollout:

- `-Dmedimanage.feature.subscription.release.enabled=true`
- `-Dmedimanage.feature.subscription.commerce.enabled=true`
- `-Dmedimanage.feature.subscription.approvals.enabled=true`
- `-Dmedimanage.feature.subscription.discount.overrides.enabled=true`
- `-Dmedimanage.feature.subscription.pilot.enabled=false`

Rollback:

- `-Dmedimanage.feature.subscription.release.enabled=false`
- `-Dmedimanage.feature.subscription.commerce.enabled=false`
- `-Dmedimanage.feature.subscription.approvals.enabled=false`
- `-Dmedimanage.feature.subscription.discount.overrides.enabled=false`

## 6. Decision

Cycle 1 all-stores rollout readiness is recorded as **PROCEED** with rollback controls documented and validated.
