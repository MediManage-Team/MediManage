# Subscription All-Stores Rollout Cycle 1 Report

Generated at: 2026-02-23 17:22:10 +05:30  
Window: 2026-02-17 to 2026-02-23 (inclusive)

## 1. Entry Gate Results

- Monitoring gate decision: PASS
- Monitoring gate reason: No high-severity blockers.
- High/Critical pricing alerts: 0
- High/Critical override abuse signals: 0
- Open High/Critical pilot feedback items: 0
- Regression QA gate: PASS

## 2. Artifacts

- Monitoring report: docs/pilot-logs/2026-02-17_to_2026-02-23-subscription-rollout-readiness-monitoring.md
- Regression QA log: docs/pilot-logs/2026-02-17_to_2026-02-23-subscription-rollout-readiness-qa.log

## 3. Full Rollout Flag Posture

Set these for all-stores rollout:

- -Dmedimanage.feature.subscription.commerce.enabled=true
- -Dmedimanage.feature.subscription.approvals.enabled=true
- -Dmedimanage.feature.subscription.discount.overrides.enabled=true
- -Dmedimanage.feature.subscription.pilot.enabled=false

## 4. Rollback Flag Posture

If rollback is required:

- -Dmedimanage.feature.subscription.commerce.enabled=false
- -Dmedimanage.feature.subscription.approvals.enabled=false
- -Dmedimanage.feature.subscription.discount.overrides.enabled=false

## 5. Decision

- Rollout decision: PROCEED
- Decision reason: Monitoring gate PASS and regression gate PASS.
