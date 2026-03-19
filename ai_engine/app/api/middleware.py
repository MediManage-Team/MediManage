import hmac
import logging
import os
from flask import request, jsonify  # type: ignore
from app.core.logger import get_correlation_id, set_correlation_id, clear_correlation_id  # type: ignore

logger = logging.getLogger(__name__)

# Constants
ADMIN_TOKEN_ENV = "MEDIMANAGE_LOCAL_API_TOKEN"
ADMIN_TOKEN_HEADER = "X-MediManage-Admin-Token"
ADMIN_PROTECTED_ROUTES = {
    "/shutdown",
    "/orchestrate",
}
ADMIN_TOKEN = (os.getenv(ADMIN_TOKEN_ENV, "") or "").strip()

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
