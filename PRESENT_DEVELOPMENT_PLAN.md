# MediManage Development Plan

Source: Prior agreed phase-based implementation plan (including subscription discounts).  
Captured on: 2026-02-22

## Phase-Based Implementation Plan

1. **Phase 0: Foundation and Governance (2 weeks)**  
Define product requirements and acceptance criteria; finalize RBAC policy where Manager/Admin own controlled operations; add feature flags, migration strategy, and release checklist.

2. **Phase 1: Subscription Commerce (3 weeks)**  
Implement subscription plans, customer enrollment, subscription validity, auto discount at billing, discount visibility on invoice/checkout, and Manager/Admin-only controls for subscription setup and approvals.

3. **Phase 2: Smart Billing Upgrade (3 weeks)**  
Add barcode/QR billing, pack-size conversion, alternate medicine suggestion by generic and stock, therapeutic substitution with approval logs, and enhanced checkout UX.

4. **Phase 3: Supplier and Procurement Core (4 weeks)**  
Build supplier master, purchase orders, GRN, damaged stock handling, supplier return flow, and supplier price comparison recommendations.

5. **Phase 4: Advanced Inventory Control (4 weeks)**  
Add batch/lot inventory, FEFO/FIFO issue logic, expiry return/disposal workflow, label printing, reorder rules, and stock transfer between stores.

6. **Phase 5: Clinical Safety Layer (4 weeks)**  
Implement checkout safety gate: interaction checks, allergy alerts, disease-condition cautions, pregnancy/pediatric/geriatric warnings, and dose schedule printouts.

7. **Phase 6: Prescription Intelligence (5 weeks)**  
Create prescription workflow with e-prescription import, handwritten OCR parsing, validation queue, doctor directory with prescribing stats, and renewal workflows.

8. **Phase 7: Customer Care and Retention (3 weeks)**  
Add customer medication timeline, refill reminders (SMS/WhatsApp/Email), care follow-up tasks, and AI counseling scripts in local language.

9. **Phase 8: Finance and Compliance (4 weeks)**  
Deliver credit and collections module, cash drawer open/close reconciliation, expense budget vs actual, GST/tax export and reconciliation, and archival policy.

10. **Phase 9: Analytics and Reporting (4 weeks)**  
Build advanced analytics dashboard (margin, dead stock, turns, prescriber trends), role-based dashboard widgets, saved report templates, and scheduled email reports.

11. **Phase 10: Security and Control (4 weeks)**  
Add fine-grained permissions, 2FA for Admin, full tamper-evident audit trail, and approval flows for sensitive actions (pricing, substitutions, stock corrections).

12. **Phase 11: Scale and Integrations (5 weeks)**  
Implement multi-store central sync, offline mode with conflict-safe sync, accounting integrations/webhooks (Tally/Zoho/QuickBooks), advanced search filters, and backup/restore automation.

13. **Phase 12: AI and Experience Enhancements (3 weeks)**  
Add voice assistant for lookup, staff SOP chatbot, dark/light theme switch, and accessibility improvements (font scaling, contrast options).

## Subscription Discount Module (Apollo/MedPlus-Style) - Detailed Requirements

1. **Plan Catalog Management (Manager/Admin only)**  
Create, edit, activate, pause, and retire subscription plans with price, duration, eligibility, and discount slabs.

2. **Approval Workflow (Manager/Admin only)**  
Require Manager/Admin approval for plan creation, discount changes, override discounts, and backdated enrollments.

3. **Discount Policy Controls (Manager/Admin only)**  
Set category-level and medicine-level inclusion/exclusion lists, maximum discount caps, and minimum margin protection rules.

4. **Customer Enrollment and Renewal**  
Enable enrollment, renewal, upgrade/downgrade, freeze/unfreeze, and cancellation with full event history.

5. **Checkout Auto-Apply Engine**  
Auto-apply eligible subscription discounts during billing with real-time validation against plan validity and policy rules.

6. **Invoice and Audit Transparency**  
Show subscription name, discount percentage, savings amount, and approval references directly on invoice and bill summary.

7. **Exception and Override Management (Manager/Admin only)**  
Allow one-time/manual overrides with mandatory reason, approver identity, and tamper-evident audit logging.

8. **Operational Dashboards**  
Track active subscribers, renewal due list, discount leakage, plan-wise revenue impact, and rejected override attempts.

9. **Compliance and Security Guardrails**  
Enforce role-based access, two-step confirmation for sensitive policy edits, and immutable logs for all discount-rule changes.

## Suggested Extra Features (Proposed Additions)

1. **Insurance and Institutional Claims Workflow**  
Add claim creation/submission, claim status tracking, rejection reason capture, and reprocessing flow for insured and corporate billing.

2. **Drug Recall and Safety Bulletin Management**  
Add recall intake, affected-batch auto detection, sale block rules, customer impact list generation, and recall action audit logs.

3. **Cold-Chain Monitoring for Temperature-Sensitive Medicines**  
Track fridge temperature logs, raise threshold breach alerts, and block issuance of stock exposed to out-of-range temperatures until review.

4. **Controlled Substance Compliance Register**  
Maintain mandatory controlled-drug movement logs, dual-approval for sensitive dispensing, and compliance-ready export reports.

5. **Dynamic Pricing and Promotion Engine**  
Support rule-based discounts/offers by brand, category, time window, and customer segment with margin-floor protection controls.

6. **License and Regulatory Document Lifecycle**  
Manage pharmacy/vendor license records, expiry reminders, renewal checklist workflow, and centralized compliance document vault.

7. **Supplier Performance Scorecards**  
Measure lead time, fill rate, quality/rejection rate, and price variance; use scoring to improve procurement recommendations.

8. **Data Quality and Master Data Governance**  
Add duplicate medicine detection, generic-brand mapping cleanup queue, mandatory field completeness checks, and data steward approvals.

9. **Customer Loyalty Wallet**  
Introduce points/cashback wallet with redemption rules, anti-fraud checks, and campaign-level ROI tracking.

10. **Partner API and Webhook Management**  
Provide API keys, scoped access, rate limiting, webhook delivery logs, retries, and signed callback verification for integrations.

11. **Pharmacovigilance and Adverse Event Reporting**  
Capture adverse drug reactions, map them to dispensed medicines and batches, and generate regulator-ready incident reports.

12. **Home Delivery and Route Optimization**  
Add delivery order assignment, route planning, proof-of-delivery capture, delivery ETA notifications, and failed-attempt workflow.

13. **Tele-Pharmacy Consultation Module**  
Enable appointment booking, consultation notes, e-prescription linkage, and follow-up reminders for remote counseling.

14. **Demand Forecasting and Auto-Replenishment**  
Predict medicine demand using seasonality/history, suggest replenishment quantities, and auto-generate draft purchase orders.

15. **Inventory Loss and Theft Detection**  
Track stock variance patterns, flag suspicious adjustments, trigger investigation workflow, and maintain evidence logs.

16. **Expiry Risk Heatmap and Markdown Recommendations**  
Predict near-expiry risk by SKU/store, suggest discount markdown plans, and monitor salvage-rate impact.

17. **Digital Consent and eSignature for Sensitive Dispense**  
Capture patient consent and pharmacist verification signatures for restricted or high-risk medicines.

18. **OTC Recommendation Assistant with Safety Guardrails**  
Guide OTC suggestions with symptom checks, contraindication screening, and escalation prompts to consult a physician.

19. **Queue and Token Management for Peak Hours**  
Add walk-in token issuance, queue display, priority handling rules, and average wait-time analytics.

20. **Business Continuity and Disaster Recovery Drills**  
Automate backup validation, periodic restore drills, outage playbooks, and recovery time objective tracking.

21. **Weekly Data Analysis and Operations Summary Panel**  
Generate week-wise analytics and auto reports for expiry buckets, out-of-stock, near-stockout, dead stock, sales/margin, returns, and action recommendations for Manager/Admin.
