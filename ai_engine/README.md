# MediManage AI Engine

The AI engine is a Python sidecar for the MediManage desktop app.

## Scope

- Cloud-only AI orchestration
- Pharmacy DB lookup helpers
- MCP server for external AI hosts
- No local model loading
- No model downloads
- No GPU/CPU hardware routing

## Components

- `app/api/routes.py`: Flask routes used by the Java desktop client
- `app/services/cloud.py`: cloud provider HTTP client
- `app/services/db_search.py`: structured pharmacy database lookups
- `app/services/prompts.py`: prompt catalog
- `app/mcp/server.py`: MCP tools and resources
- `server/server.py`: launcher entry point

## API Endpoints

- `GET /health`
- `POST /chat`
- `POST /query_db`
- `POST /orchestrate`
- `POST /cancel_chat`
- `GET /mcp_status`
- `POST /shutdown`

## Dependencies

Install:

```powershell
pip install -r requirements\requirements.txt
```

The dependency set is intentionally cloud-only and does not include local LLM runtimes.

## Run

```powershell
python server\server.py
```

The server listens on `127.0.0.1:5000`.

## MCP

The MCP server stays enabled in this architecture. It runs separately from the Flask app and exposes MediManage inventory, customer, sales, and database tools to compatible AI hosts.

