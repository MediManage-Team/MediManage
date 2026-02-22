# TODO - Subscription Discount Module (Current Focus)

Status date: 2026-02-22  
Scope: Apollo/MedPlus-style subscription discounts with Manager/Admin governance.

## 1) Requirements and Governance
- [ ] Finalize plan rules: duration, price, renewal behavior, grace period, and cancellation/refund policy.
- [ ] Freeze eligibility rules (medicine/category include/exclude lists, minimum margin floor, max discount caps).
- [ ] Define exact permission matrix for `Admin`, `Manager`, `Pharmacist`, `Cashier`.
- [ ] Define approval policy for plan changes, overrides, and backdated enrollment.
- [ ] Add release feature flag for staged rollout.

## 2) Data Model and Migration
- [x] Create tables/entities for subscription plans, enrollments, renewals, and membership status history.
- [x] Create rule tables for category-level and medicine-level discount applicability.
- [x] Create override and approval tables with reason, approver, timestamp, and checksum.
- [x] Add immutable audit log table for all policy and discount actions.
- [x] Write DB migration scripts and rollback plan.

## 3) Backend Services
- [x] Implement plan management service (create/update/activate/pause/retire).
- [x] Implement enrollment service (new, renewal, upgrade/downgrade, freeze/unfreeze, cancel).
- [x] Implement discount evaluation engine (eligibility + caps + margin protection).
- [x] Integrate auto-apply discount into billing pipeline.
- [x] Implement manager/admin override endpoint with mandatory reason capture.
- [x] Add API validation and error codes for expired/ineligible subscriptions.

## 4) RBAC and Security Controls
- [x] Restrict plan/rule/override admin operations to `Manager` and `Admin`.
- [x] Add two-step confirmation for sensitive rule updates.
- [x] Enforce tamper-evident audit logging for all discount policy changes.
- [ ] Add alerting for unusual override frequency.

## 5) UI/UX Implementation
- [x] Build Manager/Admin screens for plan catalog and discount rule configuration.
- [x] Build customer enrollment and renewal workflow screens.
- [x] Update billing screen to show real-time subscription discount breakdown.
- [x] Update invoice print/view templates with plan name, discount %, and savings amount.
- [x] Add override modal with approval capture and reason input.

## 6) Reporting and Monitoring
- [ ] Add dashboard tiles: active subscribers, renewals due, discount value, override count.
- [ ] Add reports for plan-wise revenue impact and discount leakage.
- [ ] Add rejected override attempts report for compliance review.

## 7) Testing and QA
- [x] Unit tests for discount engine edge cases (caps, exclusions, margin floor, expiry).
- [x] Integration tests for end-to-end billing with and without active subscription.
- [x] Permission tests to verify non-manager/admin roles are blocked.
- [ ] Regression tests for invoice totals and tax calculations after discount.
- [ ] UAT checklist with pharmacy operations team.

## 8) Release and Rollout
- [ ] Enable feature behind flag for pilot store/users only.
- [ ] Run pilot with monitoring for pricing errors and override abuse.
- [ ] Collect feedback and fix issues from pilot.
- [ ] Roll out to all stores with rollback checklist ready.

## 9) AI Implementations (Extra)
- [ ] Add AI plan recommendation engine based on customer purchase history, refill behavior, and expected savings.
- [ ] Add AI renewal propensity scoring to identify members likely to churn before renewal date.
- [ ] Add AI discount abuse detection for suspicious enrollment, override, and billing patterns.
- [ ] Add AI override risk scoring to assist Manager/Admin before approving manual discount exceptions.
- [ ] Add AI dynamic offer suggestions with guardrails (never exceed configured discount cap and margin floor).
- [ ] Add AI anomaly alerts for sudden spikes in subscription-linked discount leakage.
- [ ] Add conversational assistant for staff to explain why a discount was applied or rejected.
- [ ] Add multilingual AI explanation snippets on invoice/checkout for subscription savings transparency.
- [ ] Add model monitoring dashboard (precision/recall for abuse detection, recommendation acceptance rate).
- [ ] Add fallback rules engine path when AI is unavailable to ensure checkout continuity.

## 10) AI Safety, Compliance, and Quality
- [ ] Define approved data fields for AI features and block sensitive unnecessary fields from model input.
- [ ] Add PII masking/tokenization pipeline before AI inference where applicable.
- [ ] Add human-in-the-loop requirement for high-risk AI suggestions (override approval stays Manager/Admin owned).
- [ ] Add prompt/version registry with change tracking and rollback support.
- [ ] Add AI decision logging with clear reason codes for audit and post-incident review.
- [ ] Create test set for fairness and bias checks across customer groups.
- [ ] Add offline evaluation benchmarks before enabling each AI feature in production.
- [ ] Add incident runbook for AI misclassification, drift, or service outage.

## 11) Week-Wise Data Analysis and Summary Panels
- [ ] Define weekly reporting window rules (default Monday-Sunday, store timezone aware).
- [ ] Build weekly analytics aggregation jobs and materialized summary tables.
- [ ] Add `Expiry Medicines` panel with buckets: already expired, 0-30 days, 31-60 days, 61-90 days.
- [ ] Add `Out of Stock` panel with SKU count, days out-of-stock, and revenue impact estimate.
- [ ] Add `Near Stock-Out` panel using reorder threshold and average daily consumption.
- [ ] Add `Dead Stock` panel (no movement in configurable N days).
- [ ] Add `Fast-Moving` panel with top SKUs by quantity and revenue.
- [ ] Add `Return/Damaged` panel with quantity, value, and root-cause tags.
- [ ] Add `Sales and Margin` weekly summary panel (gross sales, net sales, gross margin, discount burn).
- [ ] Add `Subscription Impact` weekly panel (members billed, savings given, renewal due, override count).
- [ ] Add role-based filters for store, date range, medicine category, and supplier.
- [ ] Add one-click export (PDF/Excel/CSV) and scheduled email/WhatsApp report dispatch.
- [ ] Add drill-down from each panel to medicine-level and batch-level details.
- [ ] Add weekly anomaly alerts for sudden expiry spikes, stock-outs, and unusual discount leakage.
- [ ] Add Manager/Admin action tracker: owner, due date, and closure status for each alert.

## Immediate Next 5 Tasks
- [ ] Confirm permission matrix and approval workflow with stakeholders.
- [x] Design and review DB schema + migrations.
- [x] Implement discount evaluation engine in service layer.
- [x] Integrate auto-discount at checkout and invoice breakdown.
- [ ] Ship first QA cycle for pilot release.
