"""Test settings optimized for pytest."""

from .base import *  # noqa: F401,F403

from config.service_settings import MYSQL_CONFIG

DEBUG = False
SECRET_KEY = "test-secret-key"
ALLOWED_HOSTS = ["*"]

REST_FRAMEWORK["DEFAULT_PERMISSION_CLASSES"] = (
    "rest_framework.permissions.AllowAny",
)

PASSWORD_HASHERS = [
    "django.contrib.auth.hashers.MD5PasswordHasher",
]

CHANNEL_LAYERS = {
    "default": {
        "BACKEND": "channels.layers.InMemoryChannelLayer",
    }
}

DATABASES["default"] = {
    "ENGINE": "django.db.backends.mysql",
    "HOST": MYSQL_CONFIG["HOST"],
    "PORT": MYSQL_CONFIG["PORT"],
    "USER": MYSQL_CONFIG["USER"],
    "PASSWORD": MYSQL_CONFIG["PASSWORD"],
    "NAME": env("MYSQL_TEST_DATABASE", default=f"{MYSQL_CONFIG['NAME']}_test"),
    "OPTIONS": {
        "charset": "utf8mb4",
        "init_command": "SET sql_mode='STRICT_TRANS_TABLES'",
    },
    "CONN_MAX_AGE": 0,
}

EMAIL_BACKEND = "django.core.mail.backends.locmem.EmailBackend"
