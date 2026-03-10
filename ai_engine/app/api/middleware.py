import threading
import hmac
import logging
import os
from flask import request, jsonify
from app.core.logger import get_correlation_id, set_correlation_id, clear_correlation_id

logger = logging.getLogger(__name__)

# Constants
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

# Thread-safe download progress state
_progress_lock = threading.Lock()
_download_progress = {"status": "idle", "percent": 0, "message": "", "speed": ""}
_download_cancel = threading.Event()

def set_progress(data):
    global _download_progress
    with _progress_lock:
        _download_progress = data

def get_progress():
    with _progress_lock:
        return dict(_download_progress)

def setup_middleware(app):
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
