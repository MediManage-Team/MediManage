# Subscription UAT Checklist (Pharmacy Operations Team)

Status date: 2026-02-23  
Scope: User-acceptance checks for pilot pharmacy operations before all-stores rollout.

## 1. UAT Participants

1. Ops lead: `<name>`
2. Pharmacist representative: `<name>`
3. Cashier representative: `<name>`
4. Manager approver: `<name>`
5. Engineering support: `<name>`

## 2. UAT Environment Baseline

1. Build version verified (`0.1.5` or target candidate).
2. Feature flags set for UAT scope:
   - `subscription.release.enabled=true`
   - `subscription.commerce.enabled=true`
   - `subscription.approvals.enabled=true`
   - `subscription.discount.overrides.enabled=true`
   - `subscription.pilot.enabled=true`
3. Test dataset loaded (plans, medicines, customers).
4. Backup created before UAT execution.

## 3. Core Checkout Flows

1. New subscription member checkout applies expected discount.
2. Non-member checkout has no subscription discount.
3. Expired/frozen membership does not apply discount and shows clear reason.
4. Invoice shows plan name, discount percent, and savings amount.
5. Tax and total remain correct with/without discount.

Result: `[ ] Pass  [ ] Fail`  
Notes: `<fill>`

## 4. Override and Approval Flows

1. Cashier cannot approve override directly.
2. Manager/Admin can approve/reject override with mandatory reason.
3. Rejected overrides appear in compliance reports.
4. Approval trail (requester, approver, timestamp, reason) is visible.

Result: `[ ] Pass  [ ] Fail`  
Notes: `<fill>`

## 5. Dashboard and Weekly Analytics

1. Weekly panels load correctly (expiry, out-of-stock, near-stock-out, dead stock, fast-moving, return/damaged).
2. Sales and margin + subscription impact panels show expected values.
3. Role-based filters (store/date/category/supplier) behave per role.
4. Drill-down opens medicine and batch-level details from each panel.
5. Weekly anomaly alerts include leakage anomalies when thresholds are crossed.

Result: `[ ] Pass  [ ] Fail`  
Notes: `<fill>`

## 6. Export and Dispatch

1. One-click export works for `PDF`, `EXCEL`, `CSV`.
2. Scheduled dispatch can be created for `EMAIL` and `WHATSAPP` channels.
3. Manual “Run Due Now” processes active due schedules.
4. Dispatch outbox artifacts are generated as expected (`reports/dispatch-outbox`).

Result: `[ ] Pass  [ ] Fail`  
Notes: `<fill>`

## 7. Negative and Recovery Checks

1. Invalid recipient/channel input is blocked with clear validation.
2. System continues checkout when analytics/dispatch is unavailable.
3. High-severity pricing or leakage anomalies are visible for operator action.
4. Rollback runbook references are confirmed with operations lead.

Result: `[ ] Pass  [ ] Fail`  
Notes: `<fill>`

## 8. UAT Sign-Off

1. Overall verdict: `[ ] Go for pilot  [ ] Hold  [ ] Fix and retest`
2. Open issues count: `<fill>`
3. Critical open issues count: `<fill>`
4. Signed by:
   - Ops lead: `<name / timestamp>`
   - Manager approver: `<name / timestamp>`
   - Engineering owner: `<name / timestamp>`
