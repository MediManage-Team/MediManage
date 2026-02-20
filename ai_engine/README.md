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

## ⚡ Hardware Acceleration (NPU & GPU)

The AI Engine automatically detects your hardware and optimizes the environment for **AMD Ryzen AI NPUs** and **NVIDIA/AMD GPUs**.

### 1. Automated NPU Setup (Zero-Config)
The application detects your CPU model at startup and provisions the correct environment:

| Generation | CPU Examples | Setup |
|------------|--------------|-------|
| **XDNA 1** | Ryzen 7040/8040 (e.g., 7840HS) | **Python 3.10** + OGA 0.7.0.3 (RyzenAI) |
| **XDNA 2** | Ryzen AI 300 (Strix Point) | **Python 3.12** + OGA 0.11.2 (Standard) |
| **Other** | Intel/NVIDIA | Standard or CUDA-accelerated environment |

- **Detection Logic**: See `hardware_detect.py` (`detect_npu_generation()`).
- **Endpoint**: `GET /npu_info` returns the detected generation, TOPS, and setup status.

### 2. Hybrid GPU Acceleration (Optional)
For XDNA 1 users (where the NPU is ~10 TOPS and intended for background tasks), we support **Hybrid Acceleration**:
- **Usage**: Uses **NVIDIA RTX 3050** (via CUDA) or **Radeon iGPU** (via DirectML) for heavy lifting, falling back to CPU for specific operators.
- **Model**: Requires a GPU-optimized model variant (INT4 Block-32).
- **Setup**:
  1. Run the helper script to download the GPU model (3GB+):
     ```bash
     python download_gpu_model.py
     ```
     *(Set `HF_TOKEN` environment variable for faster download)*
  2. The system automatically detects the GPU variant logic in `inference.py` and switches to the best provider (`CUDA` > `DirectML` > `VitisAI` > `CPU`).

### 3. Requirements Files
- `requirements_npu_amd.txt`: Base fallback.
- `requirements_npu_amd_xdna1.txt`: Specific pins for XDNA 1 (numpy<2, older OGA).
- `requirements_npu_amd_xdna2.txt`: Latest pins for XDNA 2.

### 4. Smart Model Loading
The `inference.py` script uses **Smart Variant Selection**:
1. Checks for `gpu/` subdirectories in the model folder.
2. Checks available Hardware Providers (CUDA, DML).
3. **Prioritizes**: GPU Variant + CUDA EP > GPU Variant + DML EP > CPU Variant.

---

## 🤖 MCP Server (AI Agent Interface)

The `mcp_server.py` exposes MediManage as an **MCP (Model Context Protocol) server** — enabling any AI host (Claude, Gemini CLI, VS Code Copilot) to manage the pharmacy through natural language.

### Tools (18 total)
| Category | Tools |
|----------|-------|
| **Inventory** | `search_medicines`, `get_low_stock`, `get_expiring_soon`, `update_stock`, `add_medicine`, `get_inventory_summary` |
| **Customers** | `search_customers`, `get_customer_balance`, `list_top_debtors` |
| **Billing** | `get_daily_sales`, `get_recent_bills`, `get_expenses`, `get_profit_summary` |
| **Prescriptions** | `search_prescriptions`, `get_prescription_details` |
| **AI Engine** | `ai_chat`, `ai_rag_query`, `get_hardware_info` |

### Resources
- `medimanage://schema` — Full database schema
- `medimanage://inventory/summary` — Live inventory KPIs
- `medimanage://sales/today` — Today's sales figures

### Setup
```bash
# Test with MCP Inspector
mcp dev mcp_server.py

# Install in Claude Desktop
mcp install mcp_server.py
```

---

## 🚀 Setup & Run
Depending on your environment:
```bash
# Install dependencies (Auto-handled by Java App)
pip install -r requirements.txt

# Run AI Engine manually (for testing)
python server.py
# The server will start on http://127.0.0.1:5000
```
