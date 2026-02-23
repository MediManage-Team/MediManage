# Subscription Pilot Day 1 Run Sheet

Date: 2026-02-23  
Day: 1  
Reference: `docs/SUBSCRIPTION_PILOT_MONITORING_CHECKLIST.md`

## 1) Pilot Scope

- Stores/users in scope: `<fill>`
- Ops owner: `<fill>`
- Engineering owner: `<fill>`
- Business approver: `<fill>`

## 2) Feature Flag Snapshot (Before Start)

- [ ] `subscription.commerce.enabled=true`
- [ ] `subscription.approvals.enabled=true`
- [ ] `subscription.discount.overrides.enabled=true`
- [ ] `subscription.pilot.enabled=true`
- [ ] Pilot allowlist configured (`allowed.usernames` and/or `allowed.user_ids`)

Captured by: `<name>`  
Captured at: `<time>`

## 3) Start-of-Day Checks

- [ ] Reports opened for date window (pilot start to 2026-02-23)
- [ ] Pricing Integrity Alerts reviewed
- [ ] Override Abuse Signals reviewed
- [ ] Pilot Feedback tracker reviewed
- [ ] New incidents logged with owner/severity

### Morning Metrics

- Pricing alerts total: `<fill>`
- Pricing alerts high/critical: `<fill>`
- Override abuse signals total: `<fill>`
- Override abuse high severity: `<fill>`
- Pilot feedback open: `<fill>`
- Pilot feedback high/critical open: `<fill>`

## 4) Midday Check

- [ ] Re-check all monitor sections
- [ ] Confirm all open high/critical items have active owner
- [ ] Escalate unresolved high/critical incidents

### Midday Notes

`<fill>`

## 5) End-of-Day Check

- [ ] Run final monitor review
- [ ] Update all feedback statuses (`OPEN` / `IN_PROGRESS` / `RESOLVED`)
- [ ] Add resolution notes for closed items
- [ ] Confirm next-day action plan

### End-of-Day Metrics

- Pricing alerts total: `<fill>`
- Pricing alerts high/critical: `<fill>`
- Override abuse signals total: `<fill>`
- Override abuse high severity: `<fill>`
- Feedback open: `<fill>`
- Feedback in progress: `<fill>`
- Feedback resolved: `<fill>`

## 6) Incident Log (Day 1)

- `#<id>` `<title>` | severity `<LOW|MEDIUM|HIGH|CRITICAL>` | owner `<name>` | status `<OPEN|IN_PROGRESS|RESOLVED>`
- `#<id>` `<title>` | severity `<LOW|MEDIUM|HIGH|CRITICAL>` | owner `<name>` | status `<OPEN|IN_PROGRESS|RESOLVED>`

## 7) Daily Decision

- [ ] Proceed
- [ ] Hold
- [ ] Rollback

Decision rationale:

`<fill>`

## 8) Sign-Off

- Approver name: `<fill>`
- Role: `<fill>`
- Timestamp: `<fill>`
