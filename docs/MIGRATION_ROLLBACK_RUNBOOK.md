# MediManage Storage Migration and Rollback Runbook

## Scope

This runbook defines the migration path from local SQLite-first operation toward a hybrid/shared backend strategy, and a safe rollback procedure.

Current code supports backend selection through:

- JVM property: `medimanage.storage.backend`
- Environment variable: `MEDIMANAGE_STORAGE_BACKEND`

Supported values:

- `sqlite` (default, persistent local DB)
- `inmemory` (thread-safe PoC backend, non-persistent)

## Pre-Migration Checklist

1. Verify tests pass:
   - `mvn -B -ntp -o test`
2. Back up active SQLite DB file:
   - Location is configured by `DatabaseConfig` (typically `%APPDATA%/MediManage/medimanage.db`).
3. Record app build/version and commit hash.
4. Notify users of migration window if a shared backend cutover is planned.

## Phase A: Controlled PoC Rollout

Use `inmemory` only for controlled evaluation.

### Startup Configuration

PowerShell example:

```powershell
$env:MEDIMANAGE_STORAGE_BACKEND="inmemory"
mvn javafx:run
```

Or JVM property:

```powershell
mvn javafx:run -Dmedimanage.storage.backend=inmemory
```

### Validation

1. Inventory module:
   - Create/update/delete medicine.
   - Verify list/search/pagination behavior.
2. Customers module:
   - Add/update/delete/search customer.
3. Concurrency smoke:
   - Perform updates from multiple sessions/threads and verify no crashes/data races.

Note: `inmemory` is reset on restart and should never be used for production data retention.

## Phase B: Shared Backend Cutover (Future Adapter)

When a persistent shared backend adapter is introduced behind storage interfaces:

1. Deploy shared backend schema and connectivity.
2. Export SQLite data.
3. Import into shared backend.
4. Run data parity checks:
   - row counts by table
   - spot-check high-value records (bills, stock, balances)
5. Enable backend via config flag in pilot environment.
6. Monitor errors/latency and functional KPIs.
7. Promote to broader rollout.

## SQLite to PostgreSQL Migration (Now Available in Settings)

In **Settings → Database Configuration**:

1. Choose `PostgreSQL` and fill connection fields.
2. Keep the SQLite path pointing at the source `.db` file.
3. Click `Test Database Connection`.
4. Click `Migrate SQLite → PostgreSQL`.
5. Review migration summary and backup path.
6. Click `Save Settings` to persist PostgreSQL as active backend.

## Rollback Procedure

Trigger rollback for any of:

- sustained functional regression
- data inconsistency
- unacceptable latency/availability

### Immediate Steps

1. Set backend to SQLite:
   - `MEDIMANAGE_STORAGE_BACKEND=sqlite` or `-Dmedimanage.storage.backend=sqlite`
2. Restart application services/clients.
3. Restore latest verified SQLite backup if required.
4. Re-run critical smoke tests (login, billing, inventory update, customer update).

### Post-Rollback Validation

1. Confirm no pending writes are lost beyond accepted window.
2. Audit logs for failed operations during cutover window.
3. Capture incident notes and root-cause actions.

## Operational Guardrails

1. Never run production with `inmemory`.
2. Keep SQLite backups before each migration attempt.
3. Gate backend changes behind explicit config values.
4. Maintain compatibility tests for all supported backends on every release.
