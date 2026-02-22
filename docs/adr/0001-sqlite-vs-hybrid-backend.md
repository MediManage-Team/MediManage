# ADR 0001: SQLite First, Hybrid-Ready Storage Path

- Status: Accepted
- Date: 2026-02-22
- Owners: MediManage Engineering

## Context

MediManage is currently a desktop-first pharmacy product with an embedded SQLite database.  
SQLite gives zero-ops deployment and works well for single-terminal or low-contention usage.

Phase 7 requires a credible path to multi-user scale without forcing an immediate backend rewrite.

## Decision

1. Keep SQLite as the default production backend in the near term.
2. Introduce a storage abstraction layer for selected modules (`inventory`, `customers`) so services depend on interfaces instead of concrete DAOs.
3. Add a runtime backend switch:
   - JVM property: `medimanage.storage.backend`
   - Environment variable: `MEDIMANAGE_STORAGE_BACKEND`
4. Ship two backend implementations now:
   - `sqlite` (current DAO-backed behavior)
   - `inmemory` (thread-safe multi-user PoC backend for concurrency and integration testing)
5. Use this abstraction as the integration seam for a future remote/shared backend (for example PostgreSQL service/API adapter) without controller rewrites.

## Alternatives Considered

1. Full immediate migration to shared database (PostgreSQL/MySQL)
- Pros: direct multi-user support now.
- Cons: high migration risk, infrastructure overhead, large refactor surface in one phase.

2. Keep direct DAO coupling and defer abstraction
- Pros: least short-term code churn.
- Cons: future migration cost grows; no safe seam for incremental rollout.

## Consequences

### Positive

- Preserves current local reliability and offline behavior.
- Enables gradual backend evolution with lower risk.
- Supports concurrency PoC and stress testing now.
- Limits refactor scope to service/storage boundaries.

### Negative

- Temporary complexity from dual backend implementations.
- In-memory backend is non-persistent and PoC-only.
- Some modules still directly use DAOs and require follow-up abstraction.

## Follow-up

1. Extend storage abstraction to billing and prescriptions paths.
2. Add remote backend adapter behind existing interfaces.
3. Add migration tooling and data validation checks for remote cutover.
