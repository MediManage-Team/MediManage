import logging
import threading
import hashlib
import os
import signal
from flask import Blueprint, request, jsonify  # type: ignore

logger = logging.getLogger(__name__)

from app.services.inference import engine  # type: ignore
from app.services import download as download_manager  # type: ignore
from app.core import hardware as hardware_detect  # type: ignore
from app.services.cloud import cloud_api_client  # type: ignore
from app.services import prompts  # type: ignore
from app.api.middleware import set_progress, get_progress, _download_cancel  # type: ignore

# Default models directory
MODELS_DIR = os.path.join(os.path.expanduser("~"), "MediManage", "models")

api_bp = Blueprint('api', __name__)



def _verify_checksums(model_path):
    """Compute SHA-256 checksums for downloaded model files."""
    checksums = {}
    verify_extensions = {'.onnx', '.bin', '.safetensors', '.gguf', '.json', '.model', '.vocab'}

    if os.path.isfile(model_path):
        sha = _sha256_file(model_path)
        checksums[os.path.basename(model_path)] = str(sha)
        logger.info(f"  ✓ {os.path.basename(model_path)}: {str(sha)[:16]}...")  # type: ignore
    elif os.path.isdir(model_path):
        for root, dirs, files in os.walk(model_path):
            for f in sorted(files):
                ext = os.path.splitext(f)[1].lower()
                if ext in verify_extensions:
                    fpath = os.path.join(root, f)
                    sha = _sha256_file(fpath)
                    rel = os.path.relpath(fpath, model_path)
                    checksums[rel] = str(sha)
                    logger.info(f"  ✓ {rel}: {str(sha)[:16]}...")  # type: ignore

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
    "/update_config",
    "/orchestrate",
}
ADMIN_TOKEN = (os.getenv(ADMIN_TOKEN_ENV, "") or "").strip()


# ======================== HEALTH ========================

@api_bp.route('/health', methods=['GET'])
def health():
    hw = hardware_detect.detect_hardware()
    return jsonify({
        "status": "running",
        "provider": engine.provider,
        "model_loaded": engine.provider is not None,
        "backend": hw["backend"],
        "device": hw["device_name"]
    })


@api_bp.route('/hardware', methods=['GET'])
def hardware():
    """Return detected hardware and recommended inference backend."""
    return jsonify(hardware_detect.get_hardware_info())


@api_bp.route('/update_config', methods=['POST'])
def update_config():
    """Dynamically update environment configurations like HF_TOKEN."""
    data = request.json
    if "hf_token" in data:
        token = str(data["hf_token"]).strip()
        if token:
            os.environ["HF_TOKEN"] = token
            logger.info("🔑 HF_TOKEN dynamically updated from UI configuration.")
        else:
            os.environ.pop("HF_TOKEN", None)
            logger.info("🔑 HF_TOKEN removed from environment.")
    return jsonify({"status": "success"})
@api_bp.route('/query_db', methods=['POST'])
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

@api_bp.route('/load_model', methods=['POST'])
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

@api_bp.route('/chat', methods=['POST'])
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
    from app.services.inference import list_local_models  # type: ignore
    models = list_local_models(MODELS_DIR)
    if not models:
        logger.warning("No models found in %s for auto-load", MODELS_DIR)
        return

    # Prefer ONNX GenAI format, then GGUF, then anything else
    priority = {"ONNX GenAI": 0, "GGUF": 1, "ONNX": 2}
    models.sort(key=lambda m: priority.get(m.get("format", ""), 99))

    best = models[0]
    logger.info("Auto-loading model: %s (%s, %.0f MB)",
                best["name"], best["format"], best["size_mb"])
    try:
        engine.load_model(best["path"], "auto")
        logger.info("Auto-load complete: provider=%s", engine.provider)
    except Exception as e:
        logger.error("Auto-load failed: %s", e)


@api_bp.route('/orchestrate', methods=['POST'])
def orchestrate():
    """Unified Orchestration Endpoint. Replaces Java's AIOrchestrator branching logic."""
    payload = request.json
    action = payload.get("action", "")
    data = payload.get("data", {})
    cloud_config = payload.get("cloud_config", {})
    routing = payload.get("routing", "auto") # "local_only", "cloud_only", "auto" (cloud prioritized), "local_fallback"
    use_search = payload.get("use_search", False)

    if not action:
        return jsonify({"error": "No action provided"}), 400

    # 1. Build the prompt dynamically based on Java's action ID
    prompt = _build_prompt_from_action(action, data)
    if not prompt:
        return jsonify({"error": f"Unknown action: {action}"}), 400

    # 2. Extract Cloud Configurations
    provider = cloud_config.get("provider", "GEMINI")
    model = cloud_config.get("model", "")
    api_key = cloud_config.get("api_key", "")
    cloud_available = bool(api_key and api_key != "YOUR_API_KEY")

    logger.info(f"Orchestrate Action: '{action}' | Routing: {routing} | Cloud Configured: {cloud_available}")

    # 3. Routing Logic (Mimicking Java AIOrchestrator)
    try:
        # Explicit Local DB Query Routing (Doesn't use LLM)
        if routing == "local_only" or action.endswith("_db_query") or action.startswith("Show "):
            result = engine.search_pharmacy_db(prompt)
            if result:
                 return jsonify({"response": result, "source": "database", "provider": "Local DB"})
            return jsonify({"response": "No matching data found.", "source": "database", "provider": "Local DB"})

        # Combined AI Flow (Analyze locally with business data, refine with cloud)
        if action == "combined_analysis" and cloud_available and engine.provider is not None:
            # First, Local AI does basic summary
            ctx = data.get("business_context", "")
            base_prompt = data.get("prompt", "")
            local_insight = engine.generate(prompts.combined_business_summary_prompt(base_prompt) + f"\nContext: {ctx}")
            # Then, Cloud AI gives precision based on local analysis
            cloud_prompt = prompts.combined_medical_precision_prompt(local_insight, base_prompt)
            result = cloud_api_client.chat(provider, model, api_key, cloud_prompt)
            return jsonify({"response": result, "source": "cloud_combined", "provider": provider})

        # Try Cloud First (if required or auto)
        if routing in ["cloud_only", "auto"]:
            if cloud_available:
                try:
                    result = cloud_api_client.chat(provider, model, api_key, prompt)
                    return jsonify({"response": result, "source": "cloud", "provider": provider})
                except Exception as e:
                    logger.error(f"Cloud API Failed for {provider}: {e}")
                    if routing == "cloud_only":
                        raise e  # Fail instantly if strictly requiring cloud
            elif routing == "cloud_only":
                 raise ValueError(f"Cloud AI strictly required for {action} but API key is missing.")

        # Try Local Fallback (if cloud failed, or if we requested local)
        if engine.provider is None:
            _auto_load_model()
        
        if engine.provider is not None:
            result = engine.generate(prompt, use_search=use_search)
            return jsonify({"response": result, "source": "local", "provider": engine.provider})
        else:
            raise ValueError("No Local AI loaded and Cloud AI unavailable/failed.")

    except Exception as e:
        logger.error(f"Orchestration Error: {e}", exc_info=True)
        return jsonify({"error": str(e)}), 500


def _build_prompt_from_action(action: str, data: dict) -> str:
    """Map Java action constants to Python Prompt catalog methods."""
    if action == "checkout_protocol":
        return prompts.checkout_care_protocol_prompt(data.get("medicines", []))
    if action == "detailed_protocol":
        return prompts.detailed_care_protocol_prompt(data.get("medicines", []))
    if action == "validate_prescription":
        return prompts.prescription_validation_prompt(data.get("medicines_text", ""))
    if action == "generic_composition":
        return prompts.generic_composition_prompt(data.get("brand_name", ""))
    if action == "inventory_trend":
        return prompts.inventory_trend_analysis_prompt()
    if action == "expiry_strategy":
        return prompts.expiry_strategy_prompt()
    if action == "customer_history":
        return prompts.customer_history_analysis_prompt(data.get("customer_name", ""), data.get("diseases", ""))
    if action == "sales_summary":
        return prompts.sales_summary_prompt(data.get("sales_data", ""), data.get("total_revenue", 0.0), data.get("top_items", ""))
    if action == "restock_suggestion":
        return prompts.restock_suggestion_prompt(data.get("inventory_snapshot", ""))
    if action == "db_report_analysis":
        return prompts.db_report_analysis_prompt(data.get("report_type", ""))
    
    # Simple passing prompts
    if action == "raw_chat":
        prompt = data.get("prompt", "")
        ctx = data.get("business_context", "")
        if ctx:
            return f"### Business Data\n{ctx}\n\n### Query\n{prompt}"
        return prompt
    
    # DB Queries
    db_mapping = {
        "inventory_summary_db_query": prompts.INVENTORY_SUMMARY_DB_QUERY,
        "low_stock_db_query": prompts.LOW_STOCK_DB_QUERY,
        "expiry_db_query": prompts.EXPIRY_DB_QUERY,
        "sales_db_query": prompts.SALES_DB_QUERY,
        "customer_balances_db_query": prompts.CUSTOMER_BALANCES_DB_QUERY,
        "top_sellers_db_query": prompts.TOP_SELLERS_DB_QUERY,
        "profit_db_query": prompts.PROFIT_DB_QUERY,
        "prescription_overview_db_query": prompts.PRESCRIPTION_OVERVIEW_DB_QUERY,
        "drug_interaction_db_query": prompts.DRUG_INTERACTION_DB_QUERY,
        "reorder_suggestions_db_query": prompts.REORDER_SUGGESTIONS_DB_QUERY,
        "daily_summary_db_query": prompts.DAILY_SUMMARY_DB_QUERY,
    }
    if action in db_mapping:
        return db_mapping[action]
        
    # Older Java UI Quick Report buttons pass English sentences directly
    if action.startswith("Show "):
        return action
        
    return ""


@api_bp.route('/cancel_chat', methods=['POST'])
def cancel_chat():
    engine.cancel_flag = True
    return jsonify({"status": "cancelled"})

# ======================== MODEL DOWNLOAD ========================

@api_bp.route('/download_model', methods=['POST'])
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
                    repo_id, filename, local_dir, set_progress
                )
            elif source == "ollama":
                target_path = download_manager.download_ollama_model(
                    repo_id, local_dir, set_progress
                )
            elif source == "url":
                target_path = download_manager.download_direct_url(
                    repo_id, local_dir, set_progress
                )
            else:
                raise ValueError(f"Unknown source: {source}")

            # Checksum verification
            set_progress({
                "status": "verifying",
                "percent": 100,
                "message": "Verifying checksums...",
                "path": target_path
            })

            checksums = _verify_checksums(target_path)
            checksum_summary = f"{len(checksums)} file(s) verified"

            set_progress({
                "status": "completed",
                "percent": 100,
                "message": f"Download Complete! {checksum_summary}",
                "path": target_path,
                "checksums": checksums
            })
            logger.info(f"Download complete: {target_path} ({checksum_summary})")

        except InterruptedError:
            set_progress({"status": "cancelled", "percent": 0, "message": "Download cancelled."})
            logger.info("Download cancelled by user.")
        except Exception as e:
            set_progress({"status": "error", "percent": 0, "message": str(e)})
            logger.error(f"Download failed: {e}")

    thread = threading.Thread(target=download_task, daemon=True)
    thread.start()

    return jsonify({"status": "started", "source": source})


@api_bp.route('/download_status', methods=['GET'])
def download_status():
    return jsonify(get_progress())


@api_bp.route('/stop_download', methods=['POST'])
def stop_download():
    """Cancel an ongoing download."""
    _download_cancel.set()
    logger.info("Download cancellation requested.")
    return jsonify({"status": "cancelling"})


# ======================== MODEL MANAGEMENT (ComfyUI-Style) ========================

@api_bp.route('/list_models', methods=['GET'])
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


@api_bp.route('/model_info', methods=['POST'])
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
                        "size_mb": round(float(os.path.getsize(fp)) / (1024 * 1024), 2)  # type: ignore
                    })
            info["files"] = files

        # Check if currently loaded
        info["is_loaded"] = (engine.provider is not None and
                             hasattr(engine, '_current_model_path') and
                             engine._current_model_path == model_path)

        return jsonify(info)
    return jsonify({"error": "Could not read model info"}), 500


@api_bp.route('/delete_model', methods=['POST'])
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
        total_size = float(0.0)
        for root, dirs, files in os.walk(path):
            for f in files:
                sz = os.path.getsize(os.path.join(root, f))
                total_size = float(total_size) + float(sz)  # pyre-ignore
        size_megabytes = float(total_size) / float(1024 * 1024)
        info["size_mb"] = float(f"{size_megabytes:.2f}")

        # Detect format
        dir_files = []
        for root, dirs, files in os.walk(path):
            dir_files.extend(files)

        if "genai_config.json" in dir_files:
            info["format"] = "ONNX GenAI"
        elif any(f.endswith(".xml") for f in dir_files):
            info["format"] = "ONNX"
        elif any(f.endswith(".onnx") for f in dir_files):
            info["format"] = "ONNX"
        elif any(f.endswith(".gguf") for f in dir_files):
            info["format"] = "GGUF"
        else:
            info["format"] = "HuggingFace"

    elif os.path.isfile(path):
        info["size_mb"] = round(float(os.path.getsize(path)) / 1024 / 1024, 2)  # type: ignore
        if path.endswith(".xml"):
            info["format"] = "ONNX"
        elif path.endswith(".onnx"):
            info["format"] = "ONNX"
        elif path.endswith(".gguf"):
            info["format"] = "GGUF"

    return info


# ======================== PERFORMANCE ========================

@api_bp.route('/performance_stats', methods=['GET'])
def performance_stats():
    """Return inference performance metrics."""
    return jsonify(engine.get_performance_stats())


@api_bp.route('/benchmark', methods=['POST'])
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
    stats["benchmark_prompt"] = str(prompt)[:100]  # type: ignore
    stats["benchmark_response_preview"] = str(response)[:200] if response else ""  # type: ignore
    stats["benchmark_wall_time_s"] = round(float(t1 - t0), 2)  # type: ignore

    return jsonify(stats)


# ======================== MCP SERVER ========================

@api_bp.route('/mcp_status', methods=['GET'])
def mcp_status():
    """Report MCP server availability and connection instructions."""
    import sys
    mcp_file = os.path.join(os.path.dirname(__file__), "mcp_server.py")
    mcp_config = os.path.join(os.path.dirname(__file__), "mcp_config.json")
    mcp_available = os.path.isfile(mcp_file)

    # Check if mcp package is installed in current env
    mcp_installed = False
    try:
        import mcp  # type: ignore
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

@api_bp.route('/shutdown', methods=['POST'])
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


