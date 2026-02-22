# Subscription Schema and Migration Plan

Status date: 2026-02-23  
Purpose: Database design and migration plan for Phase 1 Subscription Commerce.

## 1) New Tables Added

1. `subscription_plans`
2. `subscription_plan_category_rules`
3. `subscription_plan_medicine_rules`
4. `customer_subscriptions`
5. `customer_subscription_events`
6. `subscription_approvals`
7. `subscription_discount_overrides`
8. `subscription_audit_log`

Implemented in:

1. `src/main/resources/db/schema.sql`
2. `src/main/resources/db/schema_postgresql.sql`

## 2) Existing Tables Extended

### `bills`

1. `subscription_enrollment_id`
2. `subscription_plan_id`
3. `subscription_discount_percent`
4. `subscription_savings_amount`
5. `subscription_approval_reference`

### `bill_items`

1. `subscription_discount_percent`
2. `subscription_discount_amount`
3. `subscription_rule_source`

## 3) Migration Mechanics

1. New tables are created with `CREATE TABLE IF NOT EXISTS`.
2. New columns are introduced via `ALTER TABLE ... ADD COLUMN`.
3. `DatabaseUtil` already skips duplicate `ALTER TABLE ... ADD COLUMN` migrations by checking schema metadata.
4. Existing installs remain bootable because statements are idempotent or safely skipped.

## 4) Rollback Strategy (Phase 1 Safe Rollback)

1. Disable subscription flags:
   - `subscription.commerce.enabled=false`
   - `subscription.approvals.enabled=false`
   - `subscription.discount.overrides.enabled=false`
2. Keep new tables/columns in place (non-destructive rollback).
3. Revert application logic paths to legacy billing behavior.
4. If rollback must be full schema rollback, restore DB from pre-migration backup as documented in `docs/MIGRATION_ROLLBACK_RUNBOOK.md`.

## 5) Data Integrity Notes

1. Approval statuses use constrained values (`PENDING`, `APPROVED`, `REJECTED`).
2. Subscription statuses are constrained (`ACTIVE`, `FROZEN`, `CANCELLED`, `EXPIRED`).
3. Overrides and audit events are linked to requesting/approving users.
4. Audit log supports checksum chaining via `previous_checksum` + `checksum`.

## 6) Next Implementation Step

Build subscription DAO/service layer on top of these tables and connect discount evaluation into `BillingService` before enabling flags for pilot.
