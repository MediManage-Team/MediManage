# Subscription Pilot Monitoring Checklist

Status date: 2026-02-23  
Scope: Operational checklist for running pilot monitoring before all-stores rollout.

## 1. Purpose

Run the subscription pilot with a repeatable daily monitoring loop focused on:

1. Pricing correctness.
2. Override abuse control.
3. Fast feedback capture and resolution.

Use this with:

- `docs/SUBSCRIPTION_ROLLOUT_ROLLBACK_RUNBOOK.md`
- `docs/IMPLEMENTATION_ROADMAP.md`
- Daily run sheets under `docs/pilot-logs/` (example: `docs/pilot-logs/2026-02-23-subscription-pilot-day-1.md`)
- Monitoring execution script: `./run_pilot_monitoring_cycle.ps1`

## 2. Pre-Pilot Setup (Day 0)

Complete before starting pilot day 1:

1. Confirm feature flags for pilot scope:
   - `subscription.release.enabled=true`
   - `subscription.commerce.enabled=true`
   - `subscription.approvals.enabled=true`
   - `subscription.discount.overrides.enabled=true`
   - `subscription.pilot.enabled=true`
   - pilot allowlist configured (`allowed.usernames` or `allowed.user_ids`)
2. Confirm backup of active DB files.
3. Confirm test baseline is green for subscription DAO/approval/billing tests.
4. Confirm pilot owners:
   - Ops owner
   - Engineering owner
   - Business approver
5. Confirm incident SLA:
   - Critical: immediate triage
   - High: same business day
   - Medium/Low: next review cycle

## 3. Daily Monitoring Cadence

Run this cadence every pilot day.

### Morning Check (Start of Day)

1. Open Reports and set date range to pilot start -> today.
2. Review:
   - Pricing Integrity Alerts
   - Override Abuse Signals
   - Pilot Feedback and Issue Tracker
3. Create feedback items for any new anomalies.
4. Assign owners for all open high/critical items.

### Midday Check

1. Re-check monitor tables for new high-severity issues.
2. Confirm in-progress feedback items have updates.
3. Escalate unresolved high-severity issues.

### End-of-Day Check

1. Summarize day outcome in log template (below).
2. Ensure each incident has:
   - owner,
   - status,
   - resolution notes or next action.
3. Decide next-day state:
   - proceed,
   - hold,
   - rollback.
4. Run automated monitoring report capture:
   - `./run_pilot_monitoring_cycle.ps1 -StartDate YYYY-MM-DD -EndDate YYYY-MM-DD -OutputLogPath "docs/pilot-logs/YYYY-MM-DD-subscription-pilot-monitoring.md"`

## 4. Stop/Hold Conditions

Hold pilot progression (or rollback) if:

1. Any unresolved critical pricing integrity alert remains open.
2. Repeated high-severity pricing mismatch appears across multiple bills.
3. Override abuse high-severity signals grow day-over-day without mitigation.
4. Checkout continuity is impacted for pilot users.

## 5. Exit Criteria for Pilot

Pilot is considered stable when all are true for at least 3 consecutive business days:

1. No unresolved critical feedback items.
2. No unresolved high-severity pricing integrity alerts.
3. Override abuse high-severity signals are zero or explicitly mitigated.
4. Open feedback backlog trend is stable or decreasing.
5. Stakeholder sign-off recorded.

## 6. Daily Sign-Off Template

Copy this block per day:

```text
Pilot Day: YYYY-MM-DD
Window: HH:MM - HH:MM
Operators: <names>

Pricing Integrity Alerts:
- Total:
- High/Critical:
- New today:
- Resolved today:

Override Abuse Signals:
- Total requesters flagged:
- High severity:
- New today:
- Mitigation action:

Pilot Feedback Tracker:
- Open:
- In Progress:
- Resolved:
- High/Critical open:

Incidents Created Today:
- #<id> <title> <severity> <owner> <status>

Decision:
- [ ] Proceed
- [ ] Hold
- [ ] Rollback

Approver Sign-Off:
- Name:
- Role:
- Timestamp:
```

## 7. Pilot Closeout Template

At pilot completion:

1. Total alerts observed by type.
2. Total incidents raised/resolved.
3. Top 3 fixes applied.
4. Residual risks and mitigation plan.
5. Go/No-Go decision for all-stores rollout.
