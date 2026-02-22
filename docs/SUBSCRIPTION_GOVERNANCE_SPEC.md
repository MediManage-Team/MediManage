# Subscription Governance Spec (Draft for Stakeholder Sign-Off)

Status date: 2026-02-23  
Scope: Apollo/MedPlus-style subscription discounts with Manager/Admin governance.

## 1) Permission Matrix (Proposed)

| Capability | Admin | Manager | Pharmacist | Cashier | Staff |
|---|---|---|---|---|---|
| Create/update/pause/retire plans | Allow | Allow | Deny | Deny | Deny |
| Change discount policy (caps, margin floor, include/exclude) | Allow | Allow | Deny | Deny | Deny |
| Approve backdated enrollments | Allow | Allow | Deny | Deny | Deny |
| Approve manual discount overrides | Allow | Allow | Deny | Deny | Deny |
| Enroll customer in active plan | Allow | Allow | Allow | Allow | Deny |
| Renew/freeze/unfreeze/cancel enrollment | Allow | Allow | Allow | Allow | Deny |
| Apply automatic subscription discount at checkout | Allow | Allow | Allow | Allow | Deny |
| Request manual override at checkout | Allow | Allow | Allow | Allow | Deny |
| View override/audit reports | Allow | Allow | Read-only | Read-only | Deny |

Notes:

1. Approval actions are restricted to Manager/Admin only.
2. Non-approver users may request override, but cannot self-approve.
3. RBAC in code currently enforces Manager/Admin for subscription policy and override approvals.

## 2) Approval Workflow (Proposed)

## 2.1 Plan Create/Update/Rule Change

1. Requester submits plan/rule change with reason and effective date.
2. System creates a `subscription_approvals` row in `PENDING`.
3. Manager/Admin reviewer approves or rejects.
4. On approval:
   - policy change is persisted,
   - `subscription_audit_log` row is written with checksum chaining.
5. On rejection:
   - status is set `REJECTED`,
   - reason remains immutable.

## 2.2 Backdated Enrollment

1. Request includes customer, plan, requested start date, and reason.
2. Request is blocked from direct write until approval exists.
3. Manager/Admin approval sets `APPROVED` with approver ID and timestamp.
4. Enrollment and event history rows are written with approval reference.

## 2.3 Manual Discount Override

1. Cashier/Pharmacist/Manager/Admin can request override with mandatory reason.
2. If requester is Cashier/Pharmacist, approver must be Manager/Admin (different user).
3. System stores both requested and approved discount values.
4. Approved override reference is attached to bill for invoice transparency.
5. All actions are logged into audit chain.

## 3) Policy Guardrails (Proposed)

1. Max discount cap per plan and per rule.
2. Minimum margin floor per plan/rule.
3. Optional include/exclude by category and specific medicine.
4. Expired or frozen enrollments cannot auto-discount.
5. Override cannot bypass hard cap without explicit `APPROVED` record.

## 4) Open Decisions for Stakeholders

1. Whether Pharmacist can perform enrollment freeze/unfreeze or only request it.
2. Whether Manager can approve self-created policy changes or must be dual-control.
3. Grace-period default (0 vs 7 days) and treatment at checkout.
4. Whether cancellation supports pro-rated refund and how it is computed.
