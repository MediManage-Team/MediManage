# MediManage DB Surface Audit - 2026-03-18

## Goal

Identify schema surface that is:

- live and already backed by the app
- partially implemented and worth finishing
- effectively unused roadmap ballast that should not be expanded without an explicit product decision

## Priority Summary

### P0: Implemented now

- `inventory_adjustments`
  - Before: schema existed and analytics queried it, but operators had no workflow to write data.
  - Now: Inventory exposes `Record Return / Damage`, records adjustment rows, reduces stock transactionally, and shows a recent adjustment feed.
  - Reason: highest-value unused operational table with low blast radius and direct production benefit.

### P1: Keep, already live

- `held_orders`
  - Billing already supports hold/recall.
- `receipt_settings`
  - Used by receipt/email/WhatsApp rendering.
- `purchase_orders`, `purchase_order_items`
  - Now fully production-facing after the purchase-screen upgrade.
- `employee_attendance`
  - Used through attendance DAO/controller flow.
- `prescriptions`
  - Used by prescriptions UI and assistant reporting.

### P2: Partially implemented, defer rather than remove

- `locations`
- `location_stock`
- `stock_transfers`
  - DAO exists, but the workflow is not surfaced in the main UI.
  - Recommendation: only finish if multi-branch inventory is a real near-term requirement.
  - Do not remove casually: this is a coherent feature slice, not random dead schema.

### P3: Roadmap ballast, removal candidates

- `anomaly_action_tracker`
  - No app code path writes or reads it.
- `analytics_report_dispatch_schedules`
  - No scheduler, UI, or service path uses it.
- `ai_prompt_registry`
  - No prompt versioning workflow uses it.
- `subscription_plans`
  - Seed/dashboard references exist, but the core app has no live subscription-plan workflow.

## Removal Guidance

These P3 tables should not be dropped ad hoc from `schema.sql` without a migration decision because:

- existing customer databases may already contain them
- seed/report scripts may still reference them
- dropping schema is a breaking change, unlike simply not using it

Recommended removal flow:

1. Stop seeding or reporting on the table.
2. Remove any documentation that claims the feature exists.
3. Add an explicit migration for safe archival or drop behavior.
4. Only then remove it from the base schema.

## Suggested Next Implementation Order

1. Multi-location inventory UI if branch/warehouse support matters.
2. Batch-aware sales depletion if strict FEFO / expiry-aware dispensing is required.
3. Only after product confirmation: either build or retire anomaly/report-schedule/prompt-registry/subscription tables.
