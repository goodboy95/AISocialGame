"""Structured logging helpers."""

from __future__ import annotations

import json
import logging
from datetime import datetime, timezone

from .safety import enforce_content_policy


class StructuredJsonFormatter(logging.Formatter):
    """Render log records as JSON for easier ingestion."""

    def format(self, record: logging.LogRecord) -> str:  # type: ignore[override]
        message = record.getMessage()
        safe_message = enforce_content_policy(message, mode="mask")
        payload = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "message": safe_message,
        }
        if hasattr(record, "correlation_id"):
            payload["correlation_id"] = record.correlation_id
        if record.exc_info:
            payload["exception"] = self.formatException(record.exc_info)
        extra_keys = set(record.__dict__.keys()) - logging.LogRecord.__dict__.keys() - {
            "msg",
            "args",
            "levelname",
            "levelno",
            "pathname",
            "filename",
            "module",
            "exc_info",
            "exc_text",
            "stack_info",
            "lineno",
            "funcName",
            "created",
            "msecs",
            "relativeCreated",
            "thread",
            "threadName",
            "processName",
            "process",
            "message",
        }
        if extra_keys:
            payload["extra"] = {
                key: self._serialize_extra(getattr(record, key)) for key in extra_keys
            }
        return json.dumps(payload, ensure_ascii=False, default=str)

    @staticmethod
    def _serialize_extra(value):
        """Ensure extra payload values are JSON serialisable."""
        try:
            json.dumps(value)
            return value
        except TypeError:
            return str(value)


class MaskSensitiveFilter(logging.Filter):
    """Ensure msg attribute is sanitized before emitting."""

    def filter(self, record: logging.LogRecord) -> bool:  # type: ignore[override]
        if isinstance(record.msg, str):
            record.msg = enforce_content_policy(record.msg, mode="mask")
        return True
