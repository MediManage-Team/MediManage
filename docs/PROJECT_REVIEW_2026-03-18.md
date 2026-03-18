# MediManage Project Review - 2026-03-18

## Scope

Reviewed the JavaFX desktop app, the Python AI sidecar, and the Node.js WhatsApp bridge.

## Project Snapshot

- Main app: Java 21 + JavaFX + SQLite desktop application under `src/main/java`.
- AI sidecar: Python Flask service under `ai_engine`.
- Messaging sidecar: Node/Express WhatsApp bridge under `whatsapp-server`.
- Size: about 111 Java source files and 13 Java test files, plus 3 Python AI-engine tests.

## Checks Run

- Java test suite: passed with 46 tests using Maven.
- Python AI-engine tests: passed with 7 tests using `python -m unittest`.
- Node bridge syntax: `node --check` passed for `whatsapp-server/index.js` and `whatsapp-server/start_protected.js`.

Note: green tests do not cover several upgrade, packaging, and sidecar lifecycle paths described below.

## Confirmed Bugs And Risks

### 1. Existing installs can skip later schema migrations entirely

- File: `src/main/java/org/example/MediManage/util/DatabaseUtil.java:56`
- Problem: `runSchema()` returns early once core tables exist, so the rest of `schema.sql` is never re-applied for upgrades.
- Why it matters: later tables such as `message_templates`, `payment_splits`, `held_orders`, `suppliers`, and `purchase_orders` are defined in `src/main/resources/db/schema.sql:166`, `:262`, `:283`, `:322`, and `:337`, but existing databases can miss them forever.
- Impact: upgraded installations can boot with an incomplete schema and then fail later when new features touch missing tables or columns.

### 2. Fresh databases are auto-seeded with insecure demo credentials and demo data

- Files: `src/main/java/org/example/MediManage/util/DatabaseUtil.java:39`, `:150`, `:163`, `:164`
- Seed file: `src/main/resources/db/seed_data.sql:6`, `:7`
- Problem: app startup automatically runs sample seed scripts on a fresh database.
- Critical detail: the seed creates `ADMIN` user `1` with password `1`.
- Impact: a production-first install starts with insecure credentials and demo business data mixed into the live system.

### 3. The app trusts any listener on port 5000 as the AI engine

- File: `src/main/java/org/example/MediManage/MediManageApplication.java:80`
- Problem: startup only checks whether something accepts a socket on `127.0.0.1:5000`; if yes, it assumes the correct AI engine is already running and skips startup.
- Impact: a stale process or unrelated local service on port 5000 can disable the bundled AI features or cause the app to talk to the wrong process.

### 4. WhatsApp bridge startup can kill unrelated processes

- Files: `src/main/java/org/example/MediManage/MediManageApplication.java:299`, `:318`
- Problem: if the configured bridge port is already in use, the app force-kills whatever PID is listening on that port with `taskkill /PID ... /F`.
- Impact: any unrelated local app bound to port 3000 can be terminated during MediManage startup.

### 5. WhatsApp bridge shutdown still hardcodes the wrong port

- Files: `src/main/java/org/example/MediManage/config/WhatsAppBridgeConfig.java:18`, `:32`
- Problem file: `src/main/java/org/example/MediManage/MediManageApplication.java:581`
- Problem: bridge config is port-driven and defaults to `3000`, but the shutdown fallback still posts to `127.0.0.1:3001/shutdown`.
- Impact: bridge processes can survive app shutdown and create stale-port problems on the next launch.

### 6. Manual bridge start in Settings bypasses the packaged runtime path

- Good path already used in app startup: `src/main/java/org/example/MediManage/MediManageApplication.java:331`, `:346`
- Broken manual path: `src/main/java/org/example/MediManage/controller/SettingsController.java:1143`
- Problem: Settings starts the bridge with hardcoded `node index.js` instead of using `WhatsAppBridgeConfig.resolveNodeExe()` and `resolveEntryScript()`.
- Impact: packaged installs or systems relying on bundled Node/protected entry scripts can fail from the Settings screen even when auto-start works.

### 7. Model loading UI saves success before the server confirms the model actually loaded

- UI code: `src/main/java/org/example/MediManage/controller/ModelStoreController.java:536`, `:537`, `:542`, `:545`
- Service code: `src/main/java/org/example/MediManage/service/ai/LocalAIService.java:53`, `:73`, `:90`, `:96`, `:129`
- Problem: the UI writes `local_model_path` and shows success immediately after firing an async `/load_model` request.
- Impact: if model loading fails, the app still persists a bad active-model path and tells the user the model is active.

### 8. Deleting an active model leaves a stale preference behind

- UI code: `src/main/java/org/example/MediManage/controller/ModelStoreController.java:561`, `:563`, `:565`
- Server delete route: `ai_engine/app/api/routes.py:525`, `:543`, `:545`
- Load path reuse: `src/main/java/org/example/MediManage/service/ai/LocalAIService.java:53`, `:73`, `:80`
- Problem: deleting a model removes files from disk, but the saved `local_model_path` preference is not cleared if that model was active.
- Impact: next startup can keep trying to load a model path that no longer exists.

### 9. WhatsApp bridge has a local-security weakness around auth and path validation

- File: `whatsapp-server/index.js:15`, `:30`, `:148`, `:185`, `:264`
- Problem A: the bridge exposes `/send` without a shared secret or local-only bind.
- Problem B: PDF path validation is a plain string prefix check: `absolutePath.startsWith(ALLOWED_PDF_DIR)`.
- Impact: the bridge is easier to abuse from local-origin traffic than it should be, and the file allowlist check is weaker than a canonical path comparison.

## Recommended Improvements

### Priority 0

- Replace the ad hoc schema bootstrap with a real migration system such as Flyway or Liquibase, and add upgrade tests from older database states.
- Remove automatic demo seeding from normal startup. Make demo data opt-in and require first-run admin creation with a forced password change.
- Make sidecar ownership explicit: store PID/lock metadata, verify a health endpoint before trusting an occupied port, and never kill a process by port alone.
- Fix the WhatsApp bridge lifecycle to use `WhatsAppBridgeConfig` everywhere, including shutdown and Settings actions.

### Priority 1

- Change model loading from fire-and-forget to request/response state management, and only persist `local_model_path` after a successful `/load_model`.
- Clear or repair stale AI model preferences when the selected model no longer exists.
- Bind the WhatsApp bridge to `127.0.0.1` by default and add a local admin token similar to the Python sidecar.
- Replace the bridge PDF allowlist prefix check with canonical-path + separator-safe validation.

### Priority 2

- Reduce direct `System.out.println` / `printStackTrace` usage in production code and route everything through structured logging.
- Split very large controllers and DAOs, especially `AIController`, `MedicineDAO`, and `BillingController`, into smaller testable components.
- Add packaging tests for bundled Python/Node startup, not only fresh-dev-path tests.

## Feature Recommendations

### High-value features

- First-run setup wizard for admin creation, pharmacy profile, AI provider choice, and WhatsApp onboarding.
- One-click backup and restore, with scheduled SQLite backups and restore validation.
- Audit trail for stock changes, price changes, user management, and settings changes.
- Purchase-order suggestions generated directly from low-stock, fast-moving, and expiry analytics already present in the codebase.
- Customer follow-up workflows: refill reminders, pending-credit reminders, and expiring-prescription reminders via WhatsApp/email.

### Longer-term features

- Multi-branch sync and remote API mode for shared inventory across locations.
- Robust health dashboard for AI engine, MCP server, Node bridge, active model, and port/process diagnostics.
- Report scheduling and automatic dispatch for managers.

## Test Gaps To Add

- Upgrade-path tests from an older database that already contains only the original core tables.
- Startup tests for "port occupied by unrelated process" for both Python and WhatsApp sidecars.
- Packaging tests for the Settings-based WhatsApp start path.
- Model-management tests covering failed load, deleted active model, and recovery from stale preferences.
- Security tests for the WhatsApp bridge path guard and unauthenticated access.

## Suggested Execution Order

1. Fix schema migration flow.
2. Remove automatic demo seeding and insecure default credentials.
3. Fix WhatsApp bridge lifecycle and ownership checks.
4. Fix model-store state handling.
5. Add upgrade and sidecar-integration tests so these regressions stay caught.
