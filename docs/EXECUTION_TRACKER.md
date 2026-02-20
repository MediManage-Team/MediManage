# MediManage Execution Tracker

Use this file as your local backlog board.  
Mark items with `[x]` when done.

## Current Focus

- [ ] Phase 0: DevOps Baseline

## Phase 0: DevOps Baseline

- [x] Add CI workflow (build + test + lint)
- [ ] Ensure tests run in clean environment
- [x] Align version references across docs and build scripts
- [x] Add structured logging baseline (Java + Python)
- [x] Define release checklist (build, package, smoke test)

## Phase 1: Security Hardening

- [ ] Add password hashing migration strategy
- [ ] Implement hashed auth verification
- [ ] Remove plaintext password display in UI and DAO flows
- [ ] Move API key storage to secure OS credential store
- [ ] Protect local AI admin endpoints with auth token

## Phase 2: Architecture Refactor

- [ ] Create app service layer for billing workflows
- [ ] Create app service layer for inventory workflows
- [ ] Create app service layer for customer workflows
- [ ] Remove DAO -> controller DTO coupling
- [ ] Replace ad-hoc `new Thread(...)` with managed executors

## Phase 3: Performance Optimization

- [ ] Add paginated inventory queries to UI flows
- [ ] Add paginated bill history
- [ ] Cap heavy queries and add sensible defaults
- [ ] Tune indexes based on query plans
- [ ] Add KPI cache + invalidation

## Phase 4: AI Integration Hardening

- [ ] Route all AI calls through one orchestrator path
- [ ] Centralize prompts/templates
- [ ] Fix MCP SQL/schema mismatches
- [ ] Add MCP contract tests
- [ ] Remove blocking async fallback patterns

## Phase 5: UI/UX Consistency

- [ ] Reduce inline styles and standardize classes
- [ ] Normalize loading/error/retry UX
- [ ] Improve keyboard-first workflows in frequent screens

## Phase 6: Feature Completion

- [ ] Resolve known placeholders in reports and model search
- [ ] Replace placeholder medicine detail data with real fields
- [ ] Close user-visible TODO/Coming Soon paths

## Phase 7: Scalability Evolution

- [ ] Write architecture decision record (SQLite vs hybrid backend)
- [ ] Introduce storage abstraction for dual backend path
- [ ] Build multi-user PoC for 1-2 modules
- [ ] Write migration + rollback runbook

## Weekly Cadence

- [ ] Monday: Select 3-5 tasks for the week
- [ ] Midweek: Validate risks/blockers
- [ ] Friday: Demo + mark done + plan next week
