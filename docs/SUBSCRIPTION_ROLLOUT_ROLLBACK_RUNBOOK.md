# Subscription Rollout and Rollback Runbook

Status date: 2026-02-23  
Scope: Subscription discount module rollout from pilot users to all stores.

## 1. Objective

Use a controlled wave rollout for subscription commerce while keeping a fast rollback path through feature flags and operational checkpoints.

## 2. Required Feature Flags

These flags must be set for rollout:

- `medimanage.feature.subscription.release.enabled=true`
- `medimanage.feature.subscription.commerce.enabled=true`
- `medimanage.feature.subscription.approvals.enabled=true`
- `medimanage.feature.subscription.discount.overrides.enabled=true`

Pilot-scoped controls:

- Pilot mode on: `medimanage.feature.subscription.pilot.enabled=true`
- Pilot mode off (full rollout): `medimanage.feature.subscription.pilot.enabled=false`
- Pilot allowlists (used only when pilot mode is on):
  - `medimanage.feature.subscription.pilot.allowed.usernames`
  - `medimanage.feature.subscription.pilot.allowed.user_ids`

## 3. Rollout Entry Criteria

Before rolling past pilot:

1. Pricing integrity monitor shows no unresolved high-severity anomalies for the last 7 days.
2. Override abuse monitor has no unresolved high-severity requester signals for the last 7 days.
3. Pilot feedback tracker has:
   - zero `OPEN` items with severity `CRITICAL`,
   - no stale `OPEN` `HIGH` items older than 48 hours.
4. Regression tests are green:
   - subscription DAO/report tests,
   - checkout/invoice subscription tests,
   - approval workflow tests.
5. Stakeholders confirm go/no-go in change meeting.

## 4. Rollout Waves

Suggested progression:

1. Wave 0: Pilot users only (already active).
2. Wave 1: 10-20% stores for 1 business day.
3. Wave 2: 50% stores for 2 business days.
4. Wave 3: 100% stores.

Progress to next wave only if entry checks stay within thresholds.

## 5. Per-Wave Checklist

Pre-wave:

1. Backup active DB files for impacted stores.
2. Capture current flag values and app version/commit.
3. Announce rollout window and rollback owner.

During wave:

1. Enable/adjust feature flags for target scope.
2. Run smoke scenarios:
   - enrollment create/renew/freeze/cancel,
   - checkout with and without active enrollment,
   - manual override request and approval path,
   - invoice savings display.
3. Monitor in Reports:
   - pricing integrity alerts,
   - override abuse signals,
   - pilot feedback tracker.

Post-wave:

1. Log incidents into pilot feedback tracker.
2. Close resolved incidents with notes.
3. Decide hold/proceed/rollback in wave review.

Automation helper:

- `./run_all_stores_rollout_cycle.ps1 -StartDate YYYY-MM-DD -EndDate YYYY-MM-DD`
- This runs monitoring + regression gates and writes a decision artifact under `docs/pilot-logs/`.

## 6. Rollback Triggers

Trigger rollback immediately if any of these occur:

1. Repeated high-severity pricing integrity alerts.
2. Confirmed incorrect invoice totals or tax calculations.
3. Override workflow failures preventing checkout continuity.
4. Widespread user-impacting UI/permission regressions.

## 7. Rollback Procedure

Immediate containment:

1. Disable subscription runtime paths:
   - `medimanage.feature.subscription.release.enabled=false`
   - `medimanage.feature.subscription.commerce.enabled=false`
   - `medimanage.feature.subscription.approvals.enabled=false`
   - `medimanage.feature.subscription.discount.overrides.enabled=false`
2. Restart application sessions/services using updated configuration.
3. Verify fallback behavior:
   - standard billing works,
   - subscription screens are hidden/blocked for users.

Data safety and verification:

1. Preserve logs and snapshots for incident analysis.
2. Reconcile affected invoices and override records.
3. Record rollback incident in pilot feedback tracker with owner and resolution plan.

## 8. Recovery and Re-Enable

Before attempting re-enable:

1. Fix root cause and add/extend automated tests.
2. Validate in test and pilot scopes.
3. Re-enter rollout at Wave 1 (not directly full rollout).

## 9. Sign-Off Record

For each wave, record:

1. Date/time window.
2. Scope (stores/users).
3. Operator and approver.
4. Observed anomalies and resolutions.
5. Final decision (proceed, hold, rollback).
