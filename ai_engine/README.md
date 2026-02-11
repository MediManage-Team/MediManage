# MediManage AI Engine

The **AI Engine** is a specialized Flask microservice that handles LLM inference and model management for the MediManage desktop application. It bridges the JavaFX frontend with the Python ecosystem (Hugging Face, ONNX, PyTorch).

## 🏗️ Architecture

- **Server**: Flask (running on `localhost:5000`)
- **Protocol**: HTTP/JSON
- **Model Backend**: `huggingface_hub` for downloads, `onnxruntime` (via `inference.py`) for execution.

## 🔌 API Endpoints

### 1. `/download_model` (POST)
Initiates a background thread to download a model from Hugging Face.
- **Payload**:
  ```json
  {
    "repo_id": "microsoft/Phi-3-mini-4k-instruct-onnx",
    "filename": "*",  // or specific file/pattern
    "local_dir": "models"
  }
  ```
- **Special Logic**:
  - `filename="*"`: Triggers a **full snapshot download**. This bypasses `allow_patterns` to avoid `[Errno 22] Invalid argument` on Windows (which disallows `*` in filenames during pattern matching).
  - `local_dir_use_symlinks=False`: Ensures models are physical files, not symlinks (better for "normal" file management).

### 2. `/download_status` (GET)
Returns the real-time status of the current download.
- **Response**:
  ```json
  {
    "status": "downloading",
    "percent": 45.2,
    "message": "Downloading... 45.2% (1.2 GB / 2.6 GB)"
  }
  ```
- **Note**: Uses a **Monkey-Patched `tqdm`** to capture progress from `huggingface_hub` internal calls.

### 3. `/shutdown` (POST)
Gracefully terminates the Flask server.
- **Usage**: Called by the main Java application (`MediManageApplication.java`) on exit to prevent "zombie" processes.
- **Mechanism**: Tries `werkzeug.server.shutdown` first, falls back to `os.kill(pid, SIGINT)`.

### 4. `/health` (GET)
Simple ping to check if the server is up.

## 🛠️ Key Implementation Details

### TQDM Patching (Progress Bar)
To report progress to the Java UI, we monkey-patch `tqdm` in `server.py`.
> **Critical**: This code is wrapped in a `try...except` block because `tqdm` internals can vary between versions/environments. If `tqdm.auto` is missing, it skips the patch to prevent crashing the download thread.

### Zombie Process Handling
Java's `Process.destroy()` isn't always reliable on Windows.
- **Solution**: The Java app sends a POST request to `/shutdown` *before* attempting `destroyForcibly()`.
- **Result**: Even if the Java app crashes and leaves the Python server running, the *next* launch will detect port 5000 in use (or the user can hit "Stop" in debug mode) and kill it.

## 🚀 Setup & Run
Depending on your environment:
```bash
# Install dependencies
pip install -r requirements.txt

# Run manually (for testing)
python server.py
# The server will start on http://127.0.0.1:5000
```
