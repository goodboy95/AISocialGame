"""Centralized service configuration for external dependencies."""

from typing import Final

from config.settings.base import env

MYSQL_CONFIG: Final = {
    "HOST": env("MYSQL_HOST", default="mysql"),
    "PORT": env.int("MYSQL_PORT", default=3306),
    "USER": env("MYSQL_USER", default="root"),
    "PASSWORD": env("MYSQL_PASSWORD", default=""),
    "NAME": env("MYSQL_DATABASE", default="aisocialgame"),
}

REDIS_CONFIG: Final = {
    "URL": env("REDIS_URL", default="redis://redis:6379/0"),
}

LLM_CONFIG: Final = {
    "BASE_URL": env("LLM_API_BASE_URL", default="http://localhost:8000/llm"),
    "API_KEY": env("LLM_API_KEY", default=""),
    "MODEL": env("LLM_MODEL_NAME", default="gpt-4o-mini"),
}

