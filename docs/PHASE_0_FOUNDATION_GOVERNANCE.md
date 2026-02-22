# Phase 0: Foundation and Governance

Source plan: `PRESENT_DEVELOPMENT_PLAN.md`  
Started: 2026-02-23

## Objective

Establish enforceable governance before feature expansion:

1. Freeze baseline product requirements and acceptance criteria.
2. Finalize RBAC rules for controlled operations.
3. Add feature flags for staged rollout and controlled activation.
4. Confirm migration and release controls are part of standard delivery.

## Codebase Baseline (Analysis Snapshot)

1. Architecture is layered JavaFX -> Service -> DAO -> SQLite/PostgreSQL config.
2. Security checks existed in individual DAOs but not under one central RBAC policy class.
3. Migration and release docs existed:
   - `docs/MIGRATION_ROLLBACK_RUNBOOK.md`
   - `docs/RELEASE_CHECKLIST.md`
4. Feature flag system did not exist.
5. Role model exists in `UserRole` with `ADMIN`, `MANAGER`, `PHARMACIST`, `CASHIER`, `STAFF`.

## Phase 0 Deliverables

- [x] Central RBAC policy class and permission catalog.
- [x] DAO permission checks switched to centralized RBAC enforcement.
- [x] Settings flow checks for migration/save actions enforce RBAC.
- [x] Feature flag infrastructure with defaults file and runtime overrides.
- [x] Feature flag wired into at least one sensitive path (`POSTGRES_MIGRATION`) and one UX path (`AI_ASSISTANT` visibility).
- [x] Governance document with requirements and acceptance criteria.
- [x] Migration strategy and release checklist linked into Phase 0 policy package.

## Product Requirements (Phase 0 Scope)

1. Controlled write operations must enforce explicit role permissions.
2. High-risk operational actions (database migration, system settings mutation) must require Manager/Admin authorization.
3. New feature families must be disabled by default unless explicitly enabled.
4. Runtime rollout controls must be overridable via JVM properties and environment variables.
5. Release and migration procedures must remain documented and mandatory before production cutover.

## Acceptance Criteria

1. Unauthorized users receive `SecurityException` on controlled operations.
2. Admin can manage users; Manager cannot manage users.
3. Admin/Manager can perform inventory mutations and database migration operations.
4. `subscription.*` feature flags default to disabled.
5. `POSTGRES_MIGRATION` can be disabled by flag and hides migration action in settings.
6. Release and migration runbooks are present and referenced by the active governance documentation.

## RBAC Policy Matrix

| Permission | ADMIN | MANAGER | PHARMACIST | CASHIER | STAFF |
|---|---|---|---|---|---|
| `MANAGE_USERS` | Allow | Deny | Deny | Deny | Deny |
| `MANAGE_MEDICINES` | Allow | Allow | Deny | Deny | Deny |
| `MANAGE_SYSTEM_SETTINGS` | Allow | Allow | Deny | Deny | Deny |
| `EXECUTE_DATABASE_MIGRATION` | Allow | Allow | Deny | Deny | Deny |
| `MANAGE_SUBSCRIPTION_POLICY` | Allow | Allow | Deny | Deny | Deny |
| `APPROVE_SUBSCRIPTION_OVERRIDES` | Allow | Allow | Deny | Deny | Deny |

## Feature Flag Catalog (Phase 0)

Defaults are in `src/main/resources/feature-flags.properties`.

| Flag | Default | Intent |
|---|---|---|
| `ai.assistant.enabled` | `true` | Allows AI assistant menu visibility. |
| `database.postgres.migration.enabled` | `true` | Enables/disables SQLite->PostgreSQL migration action. |
| `subscription.commerce.enabled` | `false` | Phase 1 gate. |
| `subscription.approvals.enabled` | `false` | Phase 1 approval workflow gate. |
| `subscription.discount.overrides.enabled` | `false` | Phase 1 override workflow gate. |

Override precedence:

1. JVM property: `medimanage.feature.<flag-key>`
2. Environment variable: `MEDIMANAGE_FEATURE_<FLAG_KEY>`
3. `feature-flags.properties` defaults
4. Enum fallback default

## Operational Controls

1. Migration strategy runbook: `docs/MIGRATION_ROLLBACK_RUNBOOK.md`
2. Release checklist: `docs/RELEASE_CHECKLIST.md`

## Phase 0 Exit Gate

Phase 0 is complete when:

1. RBAC checks are centralized and test-covered.
2. Feature flag framework is live and tested.
3. Governance docs are reviewed and referenced by execution tracker.
4. Build/tests pass after governance changes.
