import contextvars
import json
import logging
import os
import uuid
from datetime import datetime, timezone
from typing import Optional


_CORRELATION_ID = contextvars.ContextVar("medimanage_correlation_id", default="-")


class StructuredJsonFormatter(logging.Formatter):
    """Format logs as JSON lines for easier parsing in local files/CI pipelines."""

    def format(self, record: logging.LogRecord) -> str:
        payload = {
            "timestamp": datetime.fromtimestamp(record.created, tz=timezone.utc).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "thread": record.threadName,
            "correlation_id": getattr(record, "correlation_id", get_correlation_id()),
            "message": record.getMessage(),
        }
        exc_info = record.exc_info
        if exc_info and exc_info[0] is not None:
            payload["exception"] = self.formatException(exc_info)
        return json.dumps(payload, ensure_ascii=True)


def configure_structured_logging(level: Optional[str] = None, force: bool = False) -> logging.Logger:
    """
    Configure root logging to emit one JSON event per line.

    If handlers already exist and force is False, this leaves logging unchanged.
    """
    root_logger = logging.getLogger()
    if root_logger.handlers and not force:
        return root_logger

    root_logger.handlers.clear()

    stream_handler = logging.StreamHandler()
    stream_handler.setFormatter(StructuredJsonFormatter())
    root_logger.addHandler(stream_handler)

    level_name = (level or os.getenv("MEDIMANAGE_LOG_LEVEL", "INFO")).upper()
    resolved_level = getattr(logging, level_name, logging.INFO)
    root_logger.setLevel(resolved_level)
    logging.captureWarnings(True)
    return root_logger


def set_correlation_id(correlation_id: Optional[str] = None) -> str:
    value = (correlation_id or "").strip() or str(uuid.uuid4())
    _CORRELATION_ID.set(value)
    return value


def get_correlation_id() -> str:
    return _CORRELATION_ID.get()


def clear_correlation_id() -> None:
    _CORRELATION_ID.set("-")
