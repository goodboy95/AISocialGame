"""Centralized service configuration for external dependencies."""

from __future__ import annotations

import os
from typing import Final


def _get_env(name: str, default: str | None = None) -> str:
    """Fetch environment variables with optional defaults."""

    value = os.getenv(name, default)
    if value is None:
        raise RuntimeError(f"Environment variable {name} is required")
    return value


def _get_int(name: str, default: int | None = None) -> int:
    raw = _get_env(name, str(default) if default is not None else None)
    try:
        return int(raw)
    except (TypeError, ValueError) as exc:  # pragma: no cover - defensive branch
        raise RuntimeError(f"Environment variable {name} must be an integer") from exc


MYSQL_CONFIG: Final = {
    "HOST": _get_env("MYSQL_HOST", "localhost"),
    "PORT": _get_int("MYSQL_PORT", 3306),
    "USER": _get_env("MYSQL_USER", "duwei"),
    "PASSWORD": _get_env("MYSQL_PASSWORD", "123456"),
    "NAME": _get_env("MYSQL_DATABASE", "aisocialgame"),
}

REDIS_CONFIG: Final = {
    "URL": _get_env("REDIS_URL", "redis://localhost:6379/0"),
    "PASSWORD": _get_env("REDIS_PASSWORD", ""),
}

LLM_CONFIG: Final = {
    "BASE_URL": _get_env("LLM_API_BASE_URL", "https://api.deepseek.com/v1"),
    "API_KEY": _get_env("LLM_API_KEY", "123456"),
    "MODEL": _get_env("LLM_MODEL_NAME", "deepseek-chat"),
}

