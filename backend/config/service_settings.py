"""Centralized service configuration for external dependencies."""

from typing import Final

from config.settings.base import env

MYSQL_CONFIG: Final = {
    "HOST": env("MYSQL_HOST", default="localhost"),
    "PORT": env.int("MYSQL_PORT", default=3306),
    "USER": env("MYSQL_USER", default="duwei"),
    "PASSWORD": env("MYSQL_PASSWORD", default="123456"),
    "NAME": env("MYSQL_DATABASE", default="aisocialgame"),
}

REDIS_CONFIG: Final = {
    "URL": env("REDIS_URL", default="redis://localhost:6379/0"),
}

LLM_CONFIG: Final = {
    "BASE_URL": env("LLM_API_BASE_URL", default="https://api.deepseek.com/v1"),
    "API_KEY": env("LLM_API_KEY", default="sk-8ff648f3e46a4c45a369508322e01e1a"),
    "MODEL": env("LLM_MODEL_NAME", default="deepseek-chat"),
}

