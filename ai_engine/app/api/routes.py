import hmac
import logging
import os
import signal
import threading

from flask import Blueprint, jsonify, request  # type: ignore

from app.services import prompts  # type: ignore
from app.services.cloud import CloudProviderError, cloud_api_client  # type: ignore
from app.services.db_search import search_pharmacy_db  # type: ignore

logger = logging.getLogger(__name__)

api_bp = Blueprint("api", __name__)

ADMIN_TOKEN_ENV = "MEDIMANAGE_LOCAL_API_TOKEN"
ADMIN_TOKEN_HEADER = "X-MediManage-Admin-Token"
SERVICE_NAME = "medimanage-ai-engine"
ADMIN_PROTECTED_ROUTES = {"/orchestrate", "/shutdown"}
ADMIN_TOKEN = (os.getenv(ADMIN_TOKEN_ENV, "") or "").strip()
CARE_PROTOCOL_ACTIONS = {"checkout_protocol", "detailed_protocol"}


def _is_owner_verified() -> bool:
    if not ADMIN_TOKEN:
        return False
    provided = request.headers.get(ADMIN_TOKEN_HEADER, "")
    return bool(provided) and hmac.compare_digest(provided, ADMIN_TOKEN)


def _build_contextual_prompt(prompt: str, context: str) -> str:
    prompt = (prompt or "").strip()
    context = (context or "").strip()
    if context and prompt:
        return f"### Business Data\n{context}\n\n### Query\n{prompt}"
    if context:
        return context
    return prompt


def _build_prompt_from_action(action: str, data: dict) -> str:
    if action == "checkout_protocol":
        return prompts.checkout_care_protocol_prompt(data.get("medicines", []))
    if action == "detailed_protocol":
        return prompts.detailed_care_protocol_prompt(data.get("medicines", []))
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
    if action in {"combined_analysis", "raw_chat"}:
        return _build_contextual_prompt(data.get("prompt", ""), data.get("business_context", ""))

    db_mapping = {
        "inventory_summary_db_query": prompts.INVENTORY_SUMMARY_DB_QUERY,
        "low_stock_db_query": prompts.LOW_STOCK_DB_QUERY,
        "expiry_db_query": prompts.EXPIRY_DB_QUERY,
        "sales_db_query": prompts.SALES_DB_QUERY,
        "customer_balances_db_query": prompts.CUSTOMER_BALANCES_DB_QUERY,
        "top_sellers_db_query": prompts.TOP_SELLERS_DB_QUERY,
        "profit_db_query": prompts.PROFIT_DB_QUERY,
        "drug_interaction_db_query": prompts.DRUG_INTERACTION_DB_QUERY,
        "reorder_suggestions_db_query": prompts.REORDER_SUGGESTIONS_DB_QUERY,
        "daily_summary_db_query": prompts.DAILY_SUMMARY_DB_QUERY,
    }
    if action in db_mapping:
        return db_mapping[action]
    if action.startswith("Show "):
        return action
    return ""


@api_bp.route("/health", methods=["GET"])
def health():
    return jsonify(
        {
            "service": SERVICE_NAME,
            "status": "running",
            "healthy": True,
            "owner_verified": _is_owner_verified(),
            "mode": "cloud_only",
        }
    )


@api_bp.route("/query_db", methods=["POST"])
def query_db():
    data = request.json or {}
    query = data.get("query", "")
    if not query:
        return jsonify({"error": "No query provided"}), 400
    try:
        result = search_pharmacy_db(query)
        return jsonify({"response": result or "No matching data found in the pharmacy database.", "source": "database"})
    except Exception as e:
        logger.error("DB query error: %s", e)
        return jsonify({"error": str(e)}), 500


@api_bp.route("/chat", methods=["POST"])
def chat():
    data = request.get_json(silent=True) or {}
    prompt = data.get("prompt", "")
    cloud_config = data.get("cloud_config", {})
    if not prompt:
        return jsonify({"error": "No prompt provided"}), 400

    provider = cloud_config.get("provider", "GEMINI")
    model = cloud_config.get("model", "")
    api_key = cloud_config.get("api_key", "")
    if not api_key:
        return jsonify({"error": "Cloud API key not configured."}), 400

    try:
        response = cloud_api_client.chat(provider, model, api_key, prompt, data.get("requires_json", False))
        return jsonify({"response": response, "source": "cloud", "provider": provider})
    except Exception as e:
        logger.error("Cloud chat error: %s", e)
        return jsonify({"error": str(e)}), 500


@api_bp.route("/orchestrate", methods=["POST"])
def orchestrate():
    payload = request.get_json(silent=True) or {}
    action = payload.get("action", "")
    data = payload.get("data", {})
    cloud_config = payload.get("cloud_config", {})
    if not action:
        return jsonify({"error": "No action provided"}), 400

    provider = cloud_config.get("provider", "GEMINI")
    model = cloud_config.get("model", "")
    api_key = cloud_config.get("api_key", "")

    try:
        prompt = _build_prompt_from_action(action, data)
        if not prompt:
            return jsonify({"error": f"Unknown action: {action}"}), 400

        if action.endswith("_db_query") or action.startswith("Show "):
            result = search_pharmacy_db(prompt)
            return jsonify({"response": result or "No matching data found.", "source": "database", "provider": "Local DB"})

        if not api_key:
            raise ValueError(f"Cloud AI API key is missing for {provider}.")

        response = cloud_api_client.chat(provider, model, api_key, prompt)
        return jsonify({"response": response, "source": "cloud", "provider": provider})
    except CloudProviderError as e:
        if action in CARE_PROTOCOL_ACTIONS:
            logger.warning("Care protocol fallback triggered: %s", e)
            return jsonify(
                {
                    "response": "",
                    "source": "local_fallback",
                    "provider": provider,
                    "warning": str(e),
                }
            )
        logger.warning("Cloud orchestration failure: %s", e)
        status = 503 if e.retryable or e.status_code is None else e.status_code
        return jsonify({"error": str(e)}), status
    except Exception as e:
        logger.error("Orchestration Error: %s", e, exc_info=True)
        return jsonify({"error": str(e)}), 500


@api_bp.route("/cancel_chat", methods=["POST"])
def cancel_chat():
    return jsonify({"status": "cancel_requested"})


@api_bp.route("/mcp_status", methods=["GET"])
def mcp_status():
    import sys

    mcp_file = os.path.join(os.path.dirname(__file__), "mcp_server.py")
    mcp_config = os.path.join(os.path.dirname(__file__), "mcp_config.json")
    mcp_installed = False
    try:
        import mcp  # type: ignore
        mcp_installed = True
    except ImportError:
        pass

    return jsonify(
        {
            "mcp_server_file": os.path.isfile(mcp_file),
            "mcp_package_installed": mcp_installed,
            "mcp_config_file": os.path.isfile(mcp_config),
            "python_executable": sys.executable,
            "connect_instructions": {
                "claude_desktop": "Add to claude_desktop_config.json -> mcpServers -> medimanage",
                "vscode": "Add to .vscode/mcp.json or Copilot MCP settings",
                "test": "cd ai_engine && mcp dev mcp_server.py",
                "install": "cd ai_engine && mcp install mcp_server.py",
            },
        }
    )


@api_bp.route("/shutdown", methods=["POST"])
def shutdown():
    logger.info("Shutdown requested...")

    def _kill():
        os.kill(os.getpid(), signal.SIGINT)

    timer = threading.Timer(0.5, _kill)
    timer.daemon = True
    timer.start()
    return jsonify({"status": "shutting_down"})
