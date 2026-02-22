# MediManage Execution Tracker

Use this file as your local backlog board.  
Mark items with `[x]` when done.

## Current Focus

- [x] Phase 0: DevOps Baseline
- [x] Phase 1: Security Hardening
- [x] Phase 2: Architecture Refactor

## Phase 0: DevOps Baseline

- [x] Add CI workflow (build + test + lint)
- [x] Ensure tests run in clean environment
- [x] Align version references across docs and build scripts
- [x] Add structured logging baseline (Java + Python)
- [x] Define release checklist (build, package, smoke test)

## Phase 1: Security Hardening

- [x] Add password hashing migration strategy
- [x] Implement hashed auth verification
- [x] Remove plaintext password display in UI and DAO flows
- [x] Move API key storage to secure OS credential store
- [x] Protect local AI admin endpoints with auth token

## Phase 2: Architecture Refactor

- [x] Create app service layer for billing workflows
- [x] Create app service layer for inventory workflows
- [x] Create app service layer for customer workflows
- [x] Remove DAO -> controller DTO coupling
- [x] Replace ad-hoc `new Thread(...)` with managed executors

## Phase 3: Performance Optimization

- [x] Add paginated inventory queries to UI flows
- [x] Add paginated bill history
- [x] Cap heavy queries and add sensible defaults
- [x] Tune indexes based on query plans
- [x] Add KPI cache + invalidation

## Phase 4: AI Integration Hardening

- [x] Route all AI calls through one orchestrator path
- [x] Centralize prompts/templates
- [x] Fix MCP SQL/schema mismatches
- [x] Add MCP contract tests
- [x] Remove blocking async fallback patterns

## Phase 5: UI/UX Consistency

- [x] Reduce inline styles and standardize classes
- [x] Normalize loading/error/retry UX
- [x] Improve keyboard-first workflows in frequent screens

## Phase 6: Feature Completion

- [x] Resolve known placeholders in reports and model search
- [x] Replace placeholder medicine detail data with real fields
- [x] Close user-visible TODO/Coming Soon paths

## Phase 7: Scalability Evolution

- [x] Write architecture decision record (SQLite vs hybrid backend)
- [x] Introduce storage abstraction for dual backend path
- [x] Build multi-user PoC for 1-2 modules
- [x] Write migration + rollback runbook

## Weekly Cadence

- [ ] Monday: Select 3-5 tasks for the week
- [ ] Midweek: Validate risks/blockers
- [ ] Friday: Demo + mark done + plan next week
