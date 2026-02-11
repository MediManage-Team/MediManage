# 🤖 Agent & AI Context Guide

**Welcome, AI Agent!** This file is your starting point to understand the MediManage project.

## 📌 Critical Documentation
- **Root README**: [README.md](./README.md) - Main entry point. Architecture, Tech Stack, and Setup.
- **AI Engine**: [ai_engine/README.md](./ai_engine/README.md) - Python microservice docs (Flask, local inference, "zombie" process handling).

## 🏗️ System Architecture
- **Frontend**: JavaFX (Java 21).
- **Backend**:
    - **Core**: Java (Spring-less, pure JavaFX + JDBC).
    - **AI**: Python Flask server (`ai_engine/server.py`) running on `localhost:5000`.
- **Database**: SQLite (`medimanage.db`).

## ⚠️ Known Quirks / "Gotchas"
1.  **Zombie Processes**: The Python server can become a "zombie" if the Java app crashes. We use a `/shutdown` endpoint and `taskkill` logic to handle this. **Always check for port 5000 usage.**
2.  **Windows Environment**: This project targets Windows.
    - File path issues with `*` in `huggingface_hub` downloads are patched in `server.py`.
    - `ProcessBuilder` uses `python` (ensure it's in PATH).

## 🚀 Key Files to Analyze
- `src/main/java/org/example/MediManage/MediManageApplication.java`: Main entry, handles Python server lifecycle.
- `ai_engine/server.py`: The Python AI backend.
- `src/main/java/org/example/MediManage/ModelStoreController.java`: Java UI for downloading models.
