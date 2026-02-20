# MediManage Implementation Roadmap (No Jira Required)

This roadmap converts the architecture/security/performance analysis into a direct execution plan.

## Priority Order

1. Phase 0: DevOps Baseline
2. Phase 1: Security Hardening
3. Phase 2: Architecture Refactor
4. Phase 3: Performance Optimization
5. Phase 4: AI Integration Hardening
6. Phase 5: UI/UX Consistency
7. Phase 6: Feature Completion
8. Phase 7: Scalability Evolution

## Phase 0: DevOps Baseline (Sprint 1)

Goal: Stable build/test/release loop.

- Add CI pipeline for build + tests + lint.
- Align runtime versions across `README.md`, `pom.xml`, scripts, installer config.
- Add baseline structured logging for Java and Python services.
- Add deterministic local test harness (DB + AI stubs where needed).

Definition of Done:
- Every PR has automated checks.
- Build is reproducible on a clean machine.
- Logs are usable for troubleshooting.

## Phase 1: Security Hardening (Sprint 2)

Goal: Remove major credential/secrets risk.

- Migrate auth to hashed passwords (bcrypt), with compatibility migration for existing users.
- Remove plaintext password exposure from UI and DAO flows.
- Move cloud API keys from plain preferences to secure OS-backed storage.
- Protect localhost AI admin/destructive endpoints with token-based checks.

Definition of Done:
- No plaintext credential storage/query path remains.
- API keys are not stored in plain text in app settings.
- Local AI service rejects unauthorized admin calls.

## Phase 2: Architecture Refactor (Sprint 3)

Goal: Reduce controller complexity and coupling.

- Introduce application service layer for billing, inventory, customers, prescriptions.
- Remove DAO-to-UI coupling (DAO must not return controller DTO classes).
- Replace scattered ad-hoc threads with managed executors.
- Add integration tests for transactional workflows.

Definition of Done:
- Controllers are thinner and mostly orchestration/UI.
- Service boundaries are explicit and testable.

## Phase 3: Performance Optimization (Sprint 4)

Goal: Keep UI responsive for larger datasets.

- Move heavy screens to paginated data loading (inventory/history).
- Cap and paginate history queries.
- Tune SQL/indexes based on measured query plans.
- Add KPI caching with explicit invalidation rules.

Definition of Done:
- No large full-table UI loads on open.
- Measurable load-time improvement on dashboard/history/inventory.

## Phase 4: AI Integration Hardening (Sprint 5)

Goal: Reliable and maintainable AI behavior.

- Route all AI calls through one orchestration path.
- Centralize prompts/templates.
- Fix MCP schema mismatches and add contract tests.
- Remove blocking fallback patterns in async AI flows.

Definition of Done:
- AI behavior is predictable across screens.
- MCP tools are schema-compatible and test-covered.

## Phase 5: UI/UX Consistency (Sprint 6, part A)

Goal: Improve workflow clarity and reduce UI drift.

- Reduce inline styles; standardize on shared style classes.
- Standardize loading/error/retry UX patterns across screens.
- Improve operator speed in frequent flows (billing, customer lookup, inventory edit).

Definition of Done:
- Core screens follow one UX language.
- Async operations give consistent feedback.

## Phase 6: Feature Completion (Sprint 6, part B)

Goal: Resolve known partial/placeholder behaviors.

- Complete report/module placeholders.
- Replace sample/placeholder medicine detail outputs with real data.
- Close known TODO/Coming Soon gaps tied to user-facing flows.

Definition of Done:
- No user-facing placeholders remain in core modules.

## Phase 7: Scalability Evolution (Sprint 7+)

Goal: Prepare growth path beyond single-machine SQLite constraints.

- Create architecture decision record: desktop-only vs hybrid server model.
- Introduce repository abstraction supporting SQLite now and server DB later.
- Build focused multi-user proof of concept for selected modules.
- Write migration/rollout/rollback runbook.

Definition of Done:
- Team can choose and execute a safe scaling path without a full rewrite.

## Release Gates

- Gate A (after Phase 1): Security baseline complete.
- Gate B (after Phase 4): Architecture/performance/AI reliability baseline complete.
- Gate C (after Phase 7): Scalability strategy validated.

