# MediManage Execution Tracker

Use this file as the active backlog board for the current roadmap in `PRESENT_DEVELOPMENT_PLAN.md`.
Mark items with `[x]` when done.

## Active Program (Roadmap v2)

- [ ] Phase 0: Foundation and Governance
- [ ] Phase 1: Subscription Commerce
- [ ] Phase 2: Smart Billing Upgrade
- [ ] Phase 3: Supplier and Procurement Core
- [ ] Phase 4: Advanced Inventory Control
- [ ] Phase 5: Clinical Safety Layer
- [ ] Phase 6: Prescription Intelligence
- [ ] Phase 7: Customer Care and Retention
- [ ] Phase 8: Finance and Compliance
- [ ] Phase 9: Analytics and Reporting
- [ ] Phase 10: Security and Control
- [ ] Phase 11: Scale and Integrations
- [ ] Phase 12: AI and Experience Enhancements

## Phase 0: Foundation and Governance (In Progress)

Reference: `docs/PHASE_0_FOUNDATION_GOVERNANCE.md`

- [x] Define Phase 0 requirements and acceptance criteria
- [x] Centralize RBAC policy and permission catalog
- [x] Enforce RBAC in controlled DAO and settings operations
- [x] Add feature-flag infrastructure and defaults
- [x] Wire feature flags into migration control and AI sidebar visibility
- [x] Link migration strategy and release checklist into governance pack
- [ ] Complete team review/sign-off of Phase 0 governance doc
- [ ] Confirm Phase 0 CI gate on all new tests

## Phase 1: Subscription Commerce (Backlog Ready)

Reference: `docs/IMPLEMENTATION_ROADMAP.md`

- [x] Draft permission matrix and approval workflow (`docs/SUBSCRIPTION_GOVERNANCE_SPEC.md`)
- [x] Design schema + migration artifacts (`docs/SUBSCRIPTION_SCHEMA_MIGRATION_PLAN.md`)
- [x] Implement plan management and enrollment service baseline (`service/SubscriptionService`)
- [x] Implement discount evaluation engine baseline (`service/subscription/SubscriptionDiscountEngine`)
- [x] Integrate auto-discount in checkout + invoice metadata persistence
- [x] Implement approval/override flow service with audit checksum chain (`service/SubscriptionApprovalService`)
- [x] Add eligibility result codes for subscription validation (`SubscriptionEligibilityCode`)
- [x] Add automated tests for discount, eligibility, permissions, and override approvals
- [x] Build Manager/Admin subscription operations screen (plan catalog, medicine rules, pending override approvals)
- [x] Build customer enrollment/renewal workflow screen (`subscription-enrollment-view`)
- [x] Add two-step confirmation for sensitive rule updates + policy audit-chain hardening
- [x] Add billing override request modal with mandatory reason + pending-state UX
- [ ] Confirm permission matrix and approval workflow with stakeholders
- [ ] Ship first QA cycle for pilot release

## Legacy Program (Completed Baseline)

Historical phases below were completed under the previous execution plan:

- [x] DevOps Baseline
- [x] Security Hardening
- [x] Architecture Refactor
- [x] Performance Optimization
- [x] AI Integration Hardening
- [x] UI/UX Consistency
- [x] Feature Completion
- [x] Scalability Evolution

## Weekly Cadence

- [ ] Monday: Select 3-5 tasks for the week
- [ ] Midweek: Validate risks/blockers
- [ ] Friday: Demo + mark done + plan next week
