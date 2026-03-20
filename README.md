# MediManage 7.0.0

MediManage is a JavaFX desktop application for single-store pharmacy management.

## Current Product Shape

- Single-store pharmacy workflow
- Cloud-only AI through the bundled Python sidecar
- MCP server kept on the Python side for external AI tooling
- No local model downloads, no model store, and no local inference setup
- No locations/transfers feature
- No prescriptions/doctors workflow

## Runtime Architecture

- Desktop UI: Java 21 + JavaFX
- Database: SQLite
- AI backend: Python Flask service on `127.0.0.1:5000`
- MCP server: Python MCP sidecar on `127.0.0.1:5001`
- WhatsApp bridge: local Node.js sidecar

Java is the desktop client only. All AI routing, prompts, provider calls, and MCP logic live in Python.

## Main Modules

- `src/main/java/org/example/MediManage`:
  JavaFX application, controllers, DAO layer, services, security, and utilities
- `src/main/resources/org/example/MediManage`:
  FXML views, CSS, fonts, and packaged UI assets
- `src/main/resources/db/schema.sql`:
  Canonical runtime SQLite schema
- `ai_engine/app`:
  Cloud AI API routes, DB search helpers, cloud provider client, MCP server
- `whatsapp-server`:
  WhatsApp invoice bridge

## AI Behavior

- Java sends user actions and payloads to the Python API
- Python builds prompts, chooses provider/model, performs cloud calls, and returns results
- Supported providers: Gemini, Groq, OpenRouter, OpenAI, Claude
- Cloud API keys are configured from Settings in the desktop app

## Database Notes

- `base_medimanage.db` is the packaged starter database used by the installer
- The packaged DB now starts with:
  - empty audit history
  - no backup history
  - no locations/location stock/transfers tables
  - no prescriptions table
- `Clear Demo Data` preserves only the default `admin/admin` account and minimal runtime templates

## Local Development

### Java app

```powershell
.\mvnw.cmd clean compile
.\mvnw.cmd javafx:run
```

```bash
./mvnw clean compile
./mvnw javafx:run
```

### Quick launchers

From the repo root:

```powershell
.\run_windows.bat
```

```bash
bash ./run_linux.sh
```

These scripts prefer the packaged app image if it exists, then the installed
app location, and fall back to the Maven development launcher.

### Python AI engine

```powershell
python -m venv ai_engine\.venv
ai_engine\.venv\Scripts\pip.exe install -r ai_engine\requirements\requirements.txt
ai_engine\.venv\Scripts\python.exe ai_engine\server\server.py
```

```bash
python3 -m venv ai_engine/.venv
ai_engine/.venv/bin/pip install -r ai_engine/requirements/requirements.txt
ai_engine/.venv/bin/python ai_engine/server/server.py
```

### Standalone launcher

```powershell
.\run_ai_engine.bat
```

On Linux, the desktop app provisions its own user-local AI venv at
`~/.local/share/MediManage/ai_engine/.venv` on first launch when the bundled
runtime is not present.

## Packaging

- `build_full_installer.bat` builds the desktop app image and installer
- `scripts/prepare_offline_environments.bat` prepares the portable Python and Node runtimes used for packaging
- `scripts/compile_source_code.bat` protects the packaged Python and WhatsApp bridge sources
- `setup.iss` creates the Windows installer
- `scripts/build_linux_package.sh` builds the Linux app image and `.deb` package

### Linux runtime requirements

- JDK 21+ with `jpackage`
- Python 3 with `venv` support (`python3-venv` on Ubuntu)
- Node.js + `npm`
- Chromium, Google Chrome, or Microsoft Edge for the WhatsApp bridge

### Linux packaging

```bash
bash scripts/build_linux_package.sh
```

From Windows with WSL configured:

```powershell
.\scripts\build_linux_package_wsl.ps1
```

The Linux package bundles the raw `ai_engine` and `whatsapp-server` sources.
At runtime:

- the SQLite database is stored under `~/.local/share/MediManage/runtime/db`
- the AI engine venv is stored under `~/.local/share/MediManage/ai_engine/.venv`
- the WhatsApp bridge working copy is stored under `~/.local/share/MediManage/whatsapp-server`

## Important Settings Areas

- Database path and health
- Backup and restore
- Cloud AI provider, model, and API keys
- SMTP and WhatsApp bridge settings
- Receipt branding
- Message templates
- Operations audit viewer

## Removed From This Version

- Local LLM integration
- Model store UI
- Python environment manager and downloader flow
- Hardware selection for AI
- Locations and transfers
- Prescription and doctor flows
- Demo seeding SQL and local model test scaffolding
