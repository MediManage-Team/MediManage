import logging
import threading
import hashlib
import hmac
import os
import signal
from flask import Flask, request, jsonify
from logging_setup import (
    clear_correlation_id,
    configure_structured_logging,
    get_correlation_id,
    set_correlation_id,
)
from inference import engine
import download_manager
import hardware_detect

app = Flask(__name__)

# Configure structured logging
configure_structured_logging(force=True)
logger = logging.getLogger(__name__)

# Thread-safe download progress state
_progress_lock = threading.Lock()
_download_progress = {"status": "idle", "percent": 0, "message": "", "speed": ""}
_download_cancel = threading.Event()  # Set this to cancel a running download


@app.before_request
def _bind_correlation_id():
    correlation_id = request.headers.get("X-Correlation-Id") or request.headers.get("X-Request-Id")
    set_correlation_id(correlation_id)


@app.after_request
def _propagate_correlation_id(response):
    response.headers["X-Correlation-Id"] = get_correlation_id()
    return response


@app.teardown_request
def _clear_request_context(_error):
    clear_correlation_id()


@app.before_request
def _enforce_admin_token():
    if request.path not in ADMIN_PROTECTED_ROUTES:
        return None

    if not ADMIN_TOKEN:
        logger.error("Admin route access denied: %s is not configured.", ADMIN_TOKEN_ENV)
        return jsonify({"error": "Admin token not configured on server."}), 503

    provided = request.headers.get(ADMIN_TOKEN_HEADER, "")
    if not provided or not hmac.compare_digest(provided, ADMIN_TOKEN):
        logger.warning("Unauthorized admin request for %s", request.path)
        return jsonify({"error": "Unauthorized"}), 401

    return None


def _set_progress(data):
    """Thread-safe update to download progress."""
    global _download_progress
    with _progress_lock:
        _download_progress = data


def _get_progress():
    """Thread-safe read of download progress."""
    with _progress_lock:
        return dict(_download_progress)


def _verify_checksums(model_path):
    """Compute SHA-256 checksums for downloaded model files."""
    checksums = {}
    verify_extensions = {'.onnx', '.bin', '.safetensors', '.gguf', '.json', '.model', '.vocab'}

    if os.path.isfile(model_path):
        sha = _sha256_file(model_path)
        checksums[os.path.basename(model_path)] = sha
        logger.info(f"  ✓ {os.path.basename(model_path)}: {sha[:16]}...")
    elif os.path.isdir(model_path):
        for root, dirs, files in os.walk(model_path):
            for f in sorted(files):
                ext = os.path.splitext(f)[1].lower()
                if ext in verify_extensions:
                    fpath = os.path.join(root, f)
                    sha = _sha256_file(fpath)
                    rel = os.path.relpath(fpath, model_path)
                    checksums[rel] = sha
                    logger.info(f"  ✓ {rel}: {sha[:16]}...")

    return checksums


def _sha256_file(filepath, chunk_size=8192):
    """Compute SHA-256 hash of a file."""
    h = hashlib.sha256()
    with open(filepath, 'rb') as f:
        while True:
            chunk = f.read(chunk_size)
            if not chunk:
                break
            h.update(chunk)
    return h.hexdigest()


# Default models directory
MODELS_DIR = os.path.join(os.path.expanduser("~"), "MediManage", "models")
ADMIN_TOKEN_ENV = "MEDIMANAGE_LOCAL_API_TOKEN"
ADMIN_TOKEN_HEADER = "X-MediManage-Admin-Token"
ADMIN_PROTECTED_ROUTES = {
    "/load_model",
    "/download_model",
    "/stop_download",
    "/delete_model",
    "/shutdown",
}
ADMIN_TOKEN = (os.getenv(ADMIN_TOKEN_ENV, "") or "").strip()


# ======================== HEALTH ========================

@app.route('/health', methods=['GET'])
def health():
    hw = hardware_detect.detect_hardware()
    return jsonify({
        "status": "running",
        "provider": engine.provider,
        "model_loaded": engine.provider is not None,
        "backend": hw["backend"],
        "device": hw["device_name"]
    })


@app.route('/hardware', methods=['GET'])
def hardware():
    """Return detected hardware and recommended inference backend."""
    return jsonify(hardware_detect.get_hardware_info())


@app.route('/npu_info', methods=['GET'])
def npu_info():
    """Return AMD NPU generation detection and setup status."""
    return jsonify(hardware_detect.get_npu_setup_info())


@app.route('/query_db', methods=['POST'])
def query_db():
    """Return pharmacy database results INSTANTLY — no AI model needed.
    Used by quick report buttons for sub-second responses."""
    data = request.json
    query = data.get("query", "")

    if not query:
        return jsonify({"error": "No query provided"}), 400

    try:
        result = engine.search_pharmacy_db(query)
        if result:
            return jsonify({"response": result, "source": "database"})
        else:
            return jsonify({"response": "No matching data found in the pharmacy database.",
                            "source": "database"})
    except Exception as e:
        logger.error(f"DB query error: {e}")
        return jsonify({"error": str(e)}), 500

# ======================== MODEL LOADING ========================

@app.route('/load_model', methods=['POST'])
def load_model():
    data = request.json
    model_path = data.get("model_path")
    hardware_config = data.get("hardware_config", "auto")

    try:
        engine.load_model(model_path, hardware_config)
        return jsonify({"status": "success", "provider": engine.provider})
    except Exception as e:
        logger.error(f"Error loading model: {e}")
        return jsonify({"status": "error", "message": str(e)}), 500


# ======================== CHAT / INFERENCE ========================

@app.route('/chat', methods=['POST'])
def chat():
    data = request.json
    prompt = data.get("prompt")
    use_search = data.get("use_search", False)

    if not prompt:
        return jsonify({"error": "No prompt provided"}), 400

    # Auto-load model if not loaded yet
    if engine.provider is None:
        logger.info("No model loaded - auto-loading first available model...")
        _auto_load_model()

    try:
        response_text = engine.generate(prompt, use_search=use_search)
        return jsonify({"response": response_text})
    except Exception as e:
        logger.error(f"Inference error: {e}")
        return jsonify({"error": str(e)}), 500


def _auto_load_model():
    """Auto-detect and load the best available model from the models directory."""
    from inference import list_local_models
    models = list_local_models(MODELS_DIR)
    if not models:
        logger.warning("No models found in %s for auto-load", MODELS_DIR)
        return

    # Prefer ONNX GenAI format, then GGUF, then anything else
    priority = {"ONNX GenAI": 0, "GGUF": 1, "ONNX": 2, "OpenVINO": 3}
    models.sort(key=lambda m: priority.get(m.get("format", ""), 99))

    best = models[0]
    logger.info("Auto-loading model: %s (%s, %.0f MB)",
                best["name"], best["format"], best["size_mb"])
    try:
        engine.load_model(best["path"], "auto")
        logger.info("Auto-load complete: provider=%s", engine.provider)
    except Exception as e:
        logger.error("Auto-load failed: %s", e)


@app.route('/chat/rag', methods=['POST'])
def chat_rag():
    """Context-aware business query.
    Java sends real DB data (inventory, sales, expiry) as 'context'
    alongside the user prompt, giving the local model business awareness.
    """
    data = request.json
    prompt = data.get("prompt", "")
    context = data.get("context", "")
    use_search = data.get("use_search", False)

    if not prompt:
        return jsonify({"error": "No prompt provided"}), 400

    # Build augmented prompt with business context
    augmented_prompt = prompt
    if context:
        augmented_prompt = (
            "### Business Context (Real-time Data from Database)\n"
            f"{context}\n\n"
            "### User Query\n"
            f"{prompt}\n\n"
            "Analyze the business data above and answer the query. "
            "Be specific with numbers, trends, and actionable recommendations."
        )

    # Auto-load model if not loaded yet
    if engine.provider is None:
        logger.info("RAG: No model loaded - auto-loading...")
        _auto_load_model()

    try:
        response_text = engine.generate(augmented_prompt, use_search=use_search)
        return jsonify({"response": response_text, "context_used": bool(context)})
    except Exception as e:
        logger.error(f"RAG inference error: {e}")
        return jsonify({"error": str(e)}), 500


# ======================== MODEL DOWNLOAD ========================

@app.route('/download_model', methods=['POST'])
def download_model():
    data = request.json
    repo_id = data.get("repo_id", "")
    filename = data.get("filename", "")
    local_dir = data.get("local_dir", MODELS_DIR)
    source = data.get("source", "")  # "huggingface", "ollama", "url", or auto-detect

    if not repo_id:
        return jsonify({"error": "No repo_id provided"}), 400

    # Auto-detect source
    if not source:
        if repo_id.startswith("http://") or repo_id.startswith("https://"):
            source = "url"
        elif "/" in repo_id and "." not in repo_id.split("/")[0]:
            source = "huggingface"
        else:
            source = "ollama"

    def download_task():
        _download_cancel.clear()
        download_manager.set_cancel_event(_download_cancel)

        try:
            if source == "huggingface":
                target_path = download_manager.download_hf_model(
                    repo_id, filename, local_dir, _set_progress
                )
            elif source == "ollama":
                target_path = download_manager.download_ollama_model(
                    repo_id, local_dir, _set_progress
                )
            elif source == "url":
                target_path = download_manager.download_direct_url(
                    repo_id, local_dir, _set_progress
                )
            else:
                raise ValueError(f"Unknown source: {source}")

            # Checksum verification
            _set_progress({
                "status": "verifying",
                "percent": 100,
                "message": "Verifying checksums...",
                "path": target_path
            })

            checksums = _verify_checksums(target_path)
            checksum_summary = f"{len(checksums)} file(s) verified"

            _set_progress({
                "status": "completed",
                "percent": 100,
                "message": f"Download Complete! {checksum_summary}",
                "path": target_path,
                "checksums": checksums
            })
            logger.info(f"Download complete: {target_path} ({checksum_summary})")

        except InterruptedError:
            _set_progress({"status": "cancelled", "percent": 0, "message": "Download cancelled."})
            logger.info("Download cancelled by user.")
        except Exception as e:
            _set_progress({"status": "error", "percent": 0, "message": str(e)})
            logger.error(f"Download failed: {e}")

    thread = threading.Thread(target=download_task, daemon=True)
    thread.start()

    return jsonify({"status": "started", "source": source})


@app.route('/download_status', methods=['GET'])
def download_status():
    return jsonify(_get_progress())


@app.route('/stop_download', methods=['POST'])
def stop_download():
    """Cancel an ongoing download."""
    _download_cancel.set()
    logger.info("Download cancellation requested.")
    return jsonify({"status": "cancelling"})


# ======================== MODEL MANAGEMENT (ComfyUI-Style) ========================

@app.route('/list_models', methods=['GET'])
def list_models():
    """Scan models directory and return list of available models with metadata."""
    models_dir = request.args.get("models_dir", MODELS_DIR)

    if not os.path.exists(models_dir):
        return jsonify({"models": [], "models_dir": models_dir})

    models = []
    try:
        for entry in os.listdir(models_dir):
            entry_path = os.path.join(models_dir, entry)
            model_info = _scan_model(entry_path, entry)
            if model_info:
                models.append(model_info)
    except Exception as e:
        logger.error(f"Error listing models: {e}")
        return jsonify({"error": str(e)}), 500

    return jsonify({"models": models, "models_dir": models_dir})


@app.route('/model_info', methods=['POST'])
def model_info():
    """Get detailed info about a specific model."""
    data = request.json
    model_path = data.get("model_path", "")

    if not model_path or not os.path.exists(model_path):
        return jsonify({"error": "Model path not found"}), 404

    name = os.path.basename(model_path)
    info = _scan_model(model_path, name)
    if info:
        # Add detailed file list
        if os.path.isdir(model_path):
            files = []
            for root, dirs, filenames in os.walk(model_path):
                for f in filenames:
                    fp = os.path.join(root, f)
                    files.append({
                        "name": os.path.relpath(fp, model_path),
                        "size_mb": round(os.path.getsize(fp) / 1024 / 1024, 2)
                    })
            info["files"] = files

        # Check if currently loaded
        info["is_loaded"] = (engine.provider is not None and
                             hasattr(engine, '_current_model_path') and
                             engine._current_model_path == model_path)

        return jsonify(info)
    return jsonify({"error": "Could not read model info"}), 500


@app.route('/delete_model', methods=['POST'])
def delete_model():
    """Delete a model from the local filesystem."""
    data = request.json
    model_path = data.get("model_path", "")

    if not model_path or not os.path.exists(model_path):
        return jsonify({"error": "Model path not found"}), 404

    # Safety: only allow deletion within the models directory
    abs_model = os.path.abspath(model_path)
    abs_models_dir = os.path.abspath(MODELS_DIR)
    if not abs_model.startswith(abs_models_dir):
        return jsonify({"error": "Cannot delete files outside models directory"}), 403

    try:
        import shutil
        if os.path.isdir(model_path):
            shutil.rmtree(model_path)
        else:
            os.remove(model_path)
        logger.info(f"Deleted model: {model_path}")
        return jsonify({"status": "deleted", "path": model_path})
    except Exception as e:
        logger.error(f"Delete failed: {e}")
        return jsonify({"error": str(e)}), 500


def _scan_model(path, name):
    """Scan a model path and return metadata."""
    if not os.path.exists(path):
        return None

    info = {
        "name": name,
        "path": path,
        "format": "unknown",
        "size_mb": 0
    }

    if os.path.isdir(path):
        # Calculate total size
        total_size = 0
        for root, dirs, files in os.walk(path):
            for f in files:
                total_size += os.path.getsize(os.path.join(root, f))
        info["size_mb"] = round(total_size / 1024 / 1024, 2)

        # Detect format
        dir_files = []
        for root, dirs, files in os.walk(path):
            dir_files.extend(files)

        if "genai_config.json" in dir_files:
            info["format"] = "ONNX GenAI"
        elif any(f.endswith(".xml") for f in dir_files):
            info["format"] = "OpenVINO"
        elif any(f.endswith(".onnx") for f in dir_files):
            info["format"] = "ONNX"
        elif any(f.endswith(".gguf") for f in dir_files):
            info["format"] = "GGUF"
        else:
            info["format"] = "HuggingFace"

    elif os.path.isfile(path):
        info["size_mb"] = round(os.path.getsize(path) / 1024 / 1024, 2)
        if path.endswith(".xml"):
            info["format"] = "OpenVINO"
        elif path.endswith(".onnx"):
            info["format"] = "ONNX"
        elif path.endswith(".gguf"):
            info["format"] = "GGUF"

    return info


# ======================== PERFORMANCE ========================

@app.route('/performance_stats', methods=['GET'])
def performance_stats():
    """Return inference performance metrics."""
    return jsonify(engine.get_performance_stats())


@app.route('/benchmark', methods=['POST'])
def benchmark():
    """Run a quick benchmark comparing current model performance.
    POST body (optional): {"prompt": "...", "max_tokens": 100}
    """
    data = request.json or {}
    prompt = data.get('prompt', 'Explain the concept of machine learning in simple terms.')
    max_tokens = data.get('max_tokens', 100)

    if not engine.provider:
        return jsonify({"error": "No model loaded"}), 400

    import time as _time
    t0 = _time.time()
    response = engine.generate(prompt, max_tokens=max_tokens)
    t1 = _time.time()

    stats = engine.get_performance_stats()
    stats["benchmark_prompt"] = prompt[:100]
    stats["benchmark_response_preview"] = response[:200] if response else ""
    stats["benchmark_wall_time_s"] = round(t1 - t0, 2)

    return jsonify(stats)


# ======================== MCP SERVER ========================

@app.route('/mcp_status', methods=['GET'])
def mcp_status():
    """Report MCP server availability and connection instructions."""
    import sys
    mcp_file = os.path.join(os.path.dirname(__file__), "mcp_server.py")
    mcp_config = os.path.join(os.path.dirname(__file__), "mcp_config.json")
    mcp_available = os.path.isfile(mcp_file)

    # Check if mcp package is installed in current env
    mcp_installed = False
    try:
        import mcp
        mcp_installed = True
    except ImportError:
        pass

    return jsonify({
        "mcp_server_file": mcp_available,
        "mcp_package_installed": mcp_installed,
        "mcp_config_file": os.path.isfile(mcp_config),
        "python_executable": sys.executable,
        "connect_instructions": {
            "claude_desktop": "Add to claude_desktop_config.json → mcpServers → medimanage",
            "vscode": "Add to .vscode/mcp.json or Copilot MCP settings",
            "test": f"cd ai_engine && mcp dev mcp_server.py",
            "install": f"cd ai_engine && mcp install mcp_server.py",
        },
        "tools_count": 18,
        "resources_count": 3,
        "prompts_count": 2,
    })


# ======================== SHUTDOWN ========================

@app.route('/shutdown', methods=['POST'])
def shutdown():
    """Gracefully shutdown the Flask server."""
    logger.info("Shutdown requested...")

    def _kill():
        os.kill(os.getpid(), signal.SIGINT)

    # Schedule kill after response is sent
    timer = threading.Timer(0.5, _kill)
    timer.daemon = True
    timer.start()

    return jsonify({"status": "shutting_down"})


# ======================== MAIN ========================

if __name__ == '__main__':
    logger.info("Starting AI Engine Server on port 5000...")
    logger.info(f"Models directory: {MODELS_DIR}")
    logger.info("Admin token protection: %s", "enabled" if ADMIN_TOKEN else "missing")
    os.makedirs(MODELS_DIR, exist_ok=True)
    app.run(host='127.0.0.1', port=5000)
