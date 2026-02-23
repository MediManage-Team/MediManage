# Subscription Governance Spec (v2)

Status date: 2026-02-23  
Scope: Subscription commerce governance for plan policy, enrollments, and overrides.

Related policy baseline:

- `docs/SUBSCRIPTION_PLAN_RULES_BASELINE.md`
- `docs/SUBSCRIPTION_ELIGIBILITY_RULES_BASELINE.md`
- `docs/SUBSCRIPTION_AI_PLAN_RECOMMENDATION_BASELINE.md`
- `docs/SUBSCRIPTION_AI_RENEWAL_PROPENSITY_BASELINE.md`
- `docs/SUBSCRIPTION_AI_DISCOUNT_ABUSE_DETECTION_BASELINE.md`
- `docs/SUBSCRIPTION_AI_OVERRIDE_RISK_SCORING_BASELINE.md`
- `docs/SUBSCRIPTION_AI_DYNAMIC_OFFER_SUGGESTIONS_BASELINE.md`
- `docs/SUBSCRIPTION_AI_CONVERSATIONAL_ASSISTANT_BASELINE.md`
- `docs/SUBSCRIPTION_AI_MULTILINGUAL_EXPLANATION_SNIPPETS_BASELINE.md`
- `docs/SUBSCRIPTION_AI_MODEL_MONITORING_DASHBOARD_BASELINE.md`
- `docs/SUBSCRIPTION_AI_FALLBACK_RULES_ENGINE_BASELINE.md`
- `docs/SUBSCRIPTION_AI_INPUT_SAFETY_PII_MASKING_BASELINE.md`
- `docs/SUBSCRIPTION_AI_PROMPT_VERSION_REGISTRY_BASELINE.md`

## 1) Exact Permission Matrix

Source of truth in code:

- `src/main/java/org/example/MediManage/security/Permission.java`
- `src/main/java/org/example/MediManage/security/RbacPolicy.java`

| Capability | Permission | Admin | Manager | Pharmacist | Cashier | Staff |
|---|---|---|---|---|---|---|
| Create/update/pause/retire plans | `MANAGE_SUBSCRIPTION_POLICY` | Allow | Allow | Deny | Deny | Deny |
| Change plan rules (include/exclude, caps, margin floor) | `MANAGE_SUBSCRIPTION_POLICY` | Allow | Allow | Deny | Deny | Deny |
| Backdated enrollment initiation | `MANAGE_SUBSCRIPTION_POLICY` + `MANAGE_SUBSCRIPTION_ENROLLMENTS` | Allow | Allow | Deny | Deny | Deny |
| Enroll/renew/freeze/unfreeze/cancel customer subscription | `MANAGE_SUBSCRIPTION_ENROLLMENTS` | Allow | Allow | Allow | Allow | Deny |
| Request manual override at checkout | `MANAGE_SUBSCRIPTION_ENROLLMENTS` | Allow | Allow | Allow | Allow | Deny |
| Approve/reject manual override | `APPROVE_SUBSCRIPTION_OVERRIDES` | Allow | Allow | Deny | Deny | Deny |
| View/operate override frequency alerts | `APPROVE_SUBSCRIPTION_OVERRIDES` | Allow | Allow | Deny | Deny | Deny |

## 2) Approval Workflow Policy

### 2.1 Plan and Rule Policy Changes

1. Only `Admin`/`Manager` can create or modify plans/rules.
2. Every policy mutation is written to tamper-evident `subscription_audit_log` with checksum chain.
3. If governance requires dual-control for specific store operations, enforce at process level until code-level dual-approval is introduced.

### 2.2 Backdated Enrollment

1. Enrollment operations require `MANAGE_SUBSCRIPTION_ENROLLMENTS`.
2. If `start_date < today`, service enforces `MANAGE_SUBSCRIPTION_POLICY` authority.
3. Enrollment history stores approver metadata (`approved_by_user_id`, `approval_reference`) when provided.

### 2.3 Manual Discount Override

1. Request step:
   - Actor must have `MANAGE_SUBSCRIPTION_ENROLLMENTS`.
   - Reason is mandatory.
   - Request is persisted as `PENDING` with linked `subscription_approvals` record.
2. Decision step:
   - Actor must have `APPROVE_SUBSCRIPTION_OVERRIDES`.
   - Decision reason is mandatory.
   - **Self-approval is blocked**: requester cannot approve/reject their own request.
   - If AI risk assessment marks request as `escalationRecommended=true`, approval requires explicit human-review confirmation by the approver.
3. On decision:
   - Approval status and override status are updated atomically through service flow.
   - Audit chain is appended with before/after state snapshots and AI risk metadata.
   - Dashboard subscription KPIs are invalidated/refreshed.

## 3) Guardrails

1. Discount percent validations remain bounded (`0 < percent <= 100`) for requested and approved values.
2. Eligibility and policy checks in discount engine continue to enforce caps, exclusions, and margin-floor behavior.
3. Override transparency is maintained via bill-level approval linkage.

## 4) Stakeholder Confirmation Record

This document is the governance baseline to review in change-control meeting for operational sign-off:

1. Pharmacy operations owner.
2. Engineering owner.
3. Business approver.

Sign-off artifact attached for current window:

- `docs/pilot-logs/2026-02-23-subscription-governance-stakeholder-confirmation.md`
