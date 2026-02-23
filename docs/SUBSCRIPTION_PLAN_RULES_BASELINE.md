# Subscription Plan Rules Baseline (v1)

Status date: 2026-02-23  
Scope: Finalized operating rules for subscription plan configuration and lifecycle actions.

## 1) Plan Configuration Rules

1. Price:
   - Minimum: `0.00`
   - Maximum: `100000.00`
2. Duration:
   - Minimum: `7` days
   - Maximum: `365` days
3. Grace period:
   - Minimum: `0` days
   - Maximum: `30` days
   - Must not exceed configured plan duration.
4. Renewal behavior:
   - Renewal is an explicit action via enrollment service (`renewEnrollment`).
   - `auto_renew` is an operational intent flag and does not trigger background auto-charging in current version.

## 2) Cancellation and Refund Policy

1. Cancellation requires a non-empty reason.
2. Cancellation immediately sets enrollment status to `CANCELLED` (subscription discount no longer applies).
3. Refund policy baseline:
   - No automatic pro-rated refund is computed by subscription service.
   - Any refund/credit is handled as an operational exception workflow (Manager/Admin governed) outside auto-subscription logic.
4. Cancellation and related state transition events remain auditable through enrollment history and linked references.

## 3) Code Enforcement References

1. `src/main/java/org/example/MediManage/service/SubscriptionService.java`
2. `src/main/java/org/example/MediManage/security/RbacPolicy.java`
3. `src/main/java/org/example/MediManage/model/SubscriptionPlan.java`

## 4) Review and Change Control

Any change to these rule limits or refund policy requires:

1. Product/operations approval,
2. Updated test coverage,
3. Update to this baseline document and `docs/IMPLEMENTATION_ROADMAP.md`.
