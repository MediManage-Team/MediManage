# MediManage Project Review PR Breakdown - 2026-03-18

## Purpose

This document turns the findings in `PROJECT_REVIEW_2026-03-18.md` into PR-sized work items with scope, acceptance criteria, and test expectations.

## Working Rules

- Keep each PR narrowly scoped to one failure mode or one tightly related workstream.
- Do not mix security hardening, lifecycle fixes, and UI-state fixes in the same PR unless one blocks the other.
- Add tests in the same PR as the fix.
- Prefer behavior-preserving refactors only when they directly enable a reviewed fix.

## PR-01: Fix Database Upgrade Migrations

- Priority: P0
- Review finding: existing installs can skip later schema migrations entirely.
- Main objective: make schema upgrades deterministic for both fresh installs and older databases.

### Scope

- Replace the current "core tables exist, so stop" bootstrap logic in `src/main/java/org/example/MediManage/util/DatabaseUtil.java`.
- Ensure later tables and columns in `src/main/resources/db/schema.sql` are applied for upgraded databases.
- If a full migration framework is too large for one PR, introduce a versioned migration mechanism now and leave Flyway/Liquibase as a follow-up ADR.

### Acceptance Criteria

- A database created from an older core-only schema upgrades to the current schema without manual intervention.
- Missing tables called out in the review are created on upgrade.
- Re-running startup is idempotent.
- Schema initialization failure surfaces a clear error instead of partially succeeding silently.

### Tests

- Add upgrade-path tests starting from a fixture that contains only the original core tables.
- Add a repeat-run test that executes schema initialization twice on the same database.

## PR-02: Remove Default Demo Seeding From Production Startup

- Priority: P0
- Review finding: fresh databases are auto-seeded with insecure demo credentials and demo data.
- Main objective: separate demo/bootstrap data from real production startup.

### Scope

- Stop automatic seed execution in `src/main/java/org/example/MediManage/util/DatabaseUtil.java`.
- Remove or isolate insecure defaults in `src/main/resources/db/seed_data.sql`.
- Introduce an explicit first-run bootstrap path for admin creation, or at minimum block startup until a secure admin exists.

### Acceptance Criteria

- A fresh production database does not contain demo business data.
- No default `ADMIN / 1` credential exists.
- First-run setup requires a non-trivial admin password.
- Demo data remains available only through an explicit dev or demo path.

### Tests

- Add a fresh-install test proving no default insecure user is created.
- Add a test for first-run bootstrap validation if that flow is introduced here.

## PR-03: Make AI Engine Ownership And Health Checks Explicit

- Priority: P0
- Review finding: the app trusts any listener on port `5000` as the AI engine.
- Main objective: only trust the bundled AI sidecar when it proves identity and health.

### Scope

- Replace the port-only readiness check in `src/main/java/org/example/MediManage/MediManageApplication.java` with a health or identity probe.
- Store ownership metadata for the launched AI process where practical.
- Ensure startup distinguishes:
  - bundled AI sidecar already running
  - unrelated process on the same port
  - stale but dead ownership metadata

### Acceptance Criteria

- An unrelated local service on port `5000` is not treated as the AI engine.
- The app can still reuse a healthy sidecar it started itself.
- Startup logs explain why the AI engine was reused, started, or rejected.

### Tests

- Add startup tests for "occupied by unrelated process".
- Add a health-check success test for a valid sidecar.

## PR-04: Stop Killing Arbitrary Processes On WhatsApp Bridge Startup

- Priority: P0
- Review finding: bridge startup can kill unrelated processes if the configured port is occupied.
- Main objective: make bridge startup fail safely when ownership is unclear.

### Scope

- Remove the kill-by-port behavior from `src/main/java/org/example/MediManage/MediManageApplication.java`.
- Rework bridge startup to use process ownership metadata or handshake-based verification instead of `taskkill` on any listener.
- Preserve the ability to restart the bridge only when the app can prove ownership.

### Acceptance Criteria

- MediManage never terminates an unrelated process purely because it holds the configured bridge port.
- If the port is occupied by another app, MediManage reports the conflict and leaves the process alone.
- If the occupied process is the app-owned bridge, restart or reuse logic still works.

### Tests

- Add startup tests for unrelated-process port occupation on the bridge port.
- Add a test for owned-bridge reuse or safe restart.

## PR-05: Unify WhatsApp Bridge Lifecycle Configuration

- Priority: P0
- Review findings:
  - shutdown hardcodes the wrong port
  - manual bridge start in Settings bypasses packaged runtime resolution
- Main objective: route every bridge lifecycle action through one config-resolved path.

### Scope

- Use `src/main/java/org/example/MediManage/config/WhatsAppBridgeConfig.java` consistently from startup, manual start, status checks, and shutdown.
- Fix the shutdown fallback in `src/main/java/org/example/MediManage/MediManageApplication.java`.
- Fix the manual-start path in `src/main/java/org/example/MediManage/controller/SettingsController.java`.

### Acceptance Criteria

- Startup, manual start, and shutdown all resolve the same Node executable, entry script, and port.
- The packaged runtime path works from Settings, not only from app startup.
- No code path hardcodes `3001` or `node index.js` outside the config layer unless intentionally documented.

### Tests

- Add packaging-oriented tests for the Settings-based bridge start path.
- Add a shutdown test proving the configured port is used.

## PR-06: Make Model Loading State Truthful

- Priority: P1
- Review finding: the model-loading UI saves success before the server confirms the model actually loaded.
- Main objective: persist active model state only after a successful server response.

### Scope

- Rework async handling in `src/main/java/org/example/MediManage/controller/ModelStoreController.java`.
- Ensure `src/main/java/org/example/MediManage/service/ai/LocalAIService.java` returns a clear success/failure contract for model loading.
- Update UI feedback so "active model" reflects actual server state, not request dispatch.

### Acceptance Criteria

- The selected model path is not persisted until `/load_model` succeeds.
- Load failures leave the previous valid state intact.
- Success and failure messages match actual backend outcome.

### Tests

- Add tests for failed load requests and success-path persistence.
- Add a UI-state or controller-level test verifying no optimistic success is shown on failure.

## PR-07: Clear Stale Active Model References

- Priority: P1
- Review finding: deleting an active model leaves a stale preference behind.
- Main objective: keep persisted model selection aligned with what exists on disk.

### Scope

- Update delete flow in `src/main/java/org/example/MediManage/controller/ModelStoreController.java`.
- Update model-delete behavior in `ai_engine/app/api/routes.py` only if needed for better signaling.
- Add startup recovery in `src/main/java/org/example/MediManage/service/ai/LocalAIService.java` so nonexistent paths are cleared or ignored safely.

### Acceptance Criteria

- Deleting the active model clears or repairs `local_model_path`.
- App startup does not repeatedly try to load a deleted model.
- Recovery behavior is visible to the user or log output.

### Tests

- Add tests for deleting the currently active model.
- Add startup tests for stale saved model preferences.

## PR-08: Harden WhatsApp Bridge Local Security

- Priority: P1
- Review finding: bridge auth and path validation are weaker than they should be.
- Main objective: reduce local attack surface without breaking intended desktop integration.

### Scope

- Bind the bridge to `127.0.0.1` by default in `whatsapp-server/index.js`.
- Require a local admin token or shared secret for privileged endpoints.
- Replace prefix-only path validation with canonical-path and separator-safe checks.

### Acceptance Criteria

- Bridge endpoints reject requests without the expected local token.
- Allowed PDF files must resolve under the approved directory after canonicalization.
- Requests using path-traversal tricks or path-prefix collisions are rejected.

### Tests

- Add unauthenticated access tests.
- Add path-validation tests for traversal, symlink-like edge cases where applicable, and prefix-collision cases.

## PR-09: Add Regression Coverage For Packaging And Sidecars

- Priority: P2
- Review finding: current green tests miss upgrade, packaging, and sidecar lifecycle paths.
- Main objective: lock in the reviewed fixes so they do not regress silently.

### Scope

- Add test harness coverage for:
  - database upgrade paths
  - AI and WhatsApp occupied-port scenarios
  - packaged bridge start path
  - failed model load and stale-model recovery
- Prefer integration-style tests around public service boundaries instead of controller internals when practical.

### Acceptance Criteria

- Every P0 and P1 review fix lands with at least one regression test.
- Test names clearly map back to the reviewed failure modes.
- CI can run the new tests without manual packaging steps.

## PR-10: Logging And Refactor Cleanup

- Priority: P2
- Review findings:
  - production code still uses ad hoc console output in places
  - large controllers and DAOs are hard to test
- Main objective: do the cleanup only after correctness and security fixes land.

### Scope

- Replace remaining `System.out.println` and `printStackTrace` usage in touched areas with structured logging.
- Extract narrowly targeted helpers from oversized classes only where needed to improve testability for the reviewed fixes.

### Acceptance Criteria

- No new review-fix PR introduces ad hoc console logging.
- Refactors remain subordinate to behavior fixes and do not broaden blast radius.

## Suggested Merge Order

1. PR-01
2. PR-02
3. PR-03
4. PR-04
5. PR-05
6. PR-06
7. PR-07
8. PR-08
9. PR-09
10. PR-10

## Release Gate Before Packaging

- No insecure default admin user exists on fresh install.
- Upgrade from an older database fixture succeeds.
- AI sidecar and WhatsApp bridge both reject unrelated occupied ports safely.
- Settings-based bridge start works in packaged mode.
- Deleted or failed model loads cannot leave a broken active-model preference behind.
- WhatsApp bridge privileged routes require local authentication.
