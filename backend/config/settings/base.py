"""Base settings shared across environments."""

import inspect
from pathlib import Path
from datetime import timedelta
from urllib.parse import urlsplit, urlunsplit

import environ
from corsheaders.defaults import default_headers
from django.utils.functional import cached_property

BASE_DIR = Path(__file__).resolve().parent.parent.parent

env = environ.Env(
    DEBUG=(bool, False),
    SECRET_KEY=(str, "please-change-me"),
)

if env.bool("DJANGO_READ_DOT_ENV_FILE", default=True):
    environ.Env.read_env(BASE_DIR / ".env")

from config.service_settings import MYSQL_CONFIG, REDIS_CONFIG

from mysql.connector.django.base import DatabaseWrapper as _MySQLDatabaseWrapper


def _patch_mysql_connector_display_name() -> None:
    """Ensure mysql-connector's DatabaseWrapper exposes a bound display_name."""

    descriptor = _MySQLDatabaseWrapper.__dict__.get("display_name")
    if not isinstance(descriptor, cached_property):
        return

    func = getattr(descriptor, "func", None)
    if func is None or len(inspect.signature(func).parameters) != 0:
        return

    def _display_name(self: _MySQLDatabaseWrapper) -> str:
        return "MySQL"

    descriptor = cached_property(_display_name)
    _MySQLDatabaseWrapper.display_name = descriptor
    set_name = getattr(descriptor, "__set_name__", None)
    if set_name is not None:
        set_name(_MySQLDatabaseWrapper, "display_name")


_patch_mysql_connector_display_name()


def _ensure_redis_password(url: str, password: str) -> str:
    if not password:
        return url

    parsed = urlsplit(url)
    if parsed.password:
        return url

    hostname = parsed.hostname or ""
    if ":" in hostname and not hostname.startswith("["):
        hostname = f"[{hostname}]"

    port = f":{parsed.port}" if parsed.port else ""
    username = parsed.username or ""
    credentials = f"{username}:{password}" if username else f":{password}"
    netloc = f"{credentials}@{hostname}{port}" if hostname else parsed.netloc

    if not netloc:
        return url

    return urlunsplit(parsed._replace(netloc=netloc))


RAW_REDIS_URL = REDIS_CONFIG["URL"]
REDIS_PASSWORD = REDIS_CONFIG["PASSWORD"]
REDIS_URL = _ensure_redis_password(RAW_REDIS_URL, REDIS_PASSWORD)

SECRET_KEY = env("SECRET_KEY")
DEBUG = env.bool("DEBUG", default=False)
ALLOWED_HOSTS = env.list("ALLOWED_HOSTS", default=["localhost", "127.0.0.1"])

INSTALLED_APPS = [
    "django.contrib.admin",
    "django.contrib.auth",
    "django.contrib.contenttypes",
    "django.contrib.sessions",
    "django.contrib.messages",
    "django.contrib.staticfiles",
    "rest_framework",
    "rest_framework.authtoken",
    "rest_framework_simplejwt.token_blacklist",
    "corsheaders",
    "channels",
    "apps.analytics",
    "apps.users",
    "apps.rooms",
    "apps.gamecore",
    "apps.games",
    "apps.ai",
]

MIDDLEWARE = [
    "corsheaders.middleware.CorsMiddleware",
    "django.middleware.security.SecurityMiddleware",
    "apps.common.middleware.CorrelationIdMiddleware",
    "django.contrib.sessions.middleware.SessionMiddleware",
    "django.middleware.common.CommonMiddleware",
    "django.middleware.csrf.CsrfViewMiddleware",
    "django.contrib.auth.middleware.AuthenticationMiddleware",
    "django.contrib.messages.middleware.MessageMiddleware",
    "django.middleware.clickjacking.XFrameOptionsMiddleware",
    "apps.common.middleware.MetricsMiddleware",
]

ROOT_URLCONF = "config.urls"

TEMPLATES = [
    {
        "BACKEND": "django.template.backends.django.DjangoTemplates",
        "DIRS": [BASE_DIR / "templates"],
        "APP_DIRS": True,
        "OPTIONS": {
            "context_processors": [
                "django.template.context_processors.debug",
                "django.template.context_processors.request",
                "django.contrib.auth.context_processors.auth",
                "django.contrib.messages.context_processors.messages",
            ],
        },
    }
]

WSGI_APPLICATION = "config.wsgi.application"
ASGI_APPLICATION = "config.asgi.application"

DATABASES = {
    "default": {
        "ENGINE": "mysql.connector.django",
        "HOST": MYSQL_CONFIG["HOST"],
        "PORT": MYSQL_CONFIG["PORT"],
        "USER": MYSQL_CONFIG["USER"],
        "PASSWORD": MYSQL_CONFIG["PASSWORD"],
        "NAME": MYSQL_CONFIG["NAME"],
        "OPTIONS": {
            "charset": "utf8mb4",
            "init_command": "SET sql_mode='STRICT_TRANS_TABLES'",
        },
        "CONN_MAX_AGE": env.int("MYSQL_CONN_MAX_AGE", default=60),
    },
}

AUTH_PASSWORD_VALIDATORS = [
    {
        "NAME": "django.contrib.auth.password_validation.UserAttributeSimilarityValidator",
    },
    {
        "NAME": "django.contrib.auth.password_validation.MinimumLengthValidator",
        "OPTIONS": {"min_length": 8},
    },
]

LANGUAGE_CODE = "zh-hans"
TIME_ZONE = "Asia/Shanghai"
USE_I18N = True
USE_TZ = True

STATIC_URL = "/static/"
STATIC_ROOT = BASE_DIR / "staticfiles"
MEDIA_URL = "/media/"
MEDIA_ROOT = BASE_DIR / "media"

DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"
AUTH_USER_MODEL = "users.User"

REST_FRAMEWORK = {
    "DEFAULT_AUTHENTICATION_CLASSES": (
        "rest_framework_simplejwt.authentication.JWTAuthentication",
    ),
    "DEFAULT_PERMISSION_CLASSES": (
        "rest_framework.permissions.IsAuthenticated",
    ),
    "DEFAULT_THROTTLE_CLASSES": (
        "rest_framework.throttling.UserRateThrottle",
        "rest_framework.throttling.AnonRateThrottle",
    ),
    "DEFAULT_THROTTLE_RATES": {
        "user": env("THROTTLE_RATE_USER", default="120/min"),
        "anon": env("THROTTLE_RATE_ANON", default="30/min"),
    },
}

SIMPLE_JWT = {
    "ACCESS_TOKEN_LIFETIME": timedelta(hours=24),
    "REFRESH_TOKEN_LIFETIME": timedelta(days=7),
    "ROTATE_REFRESH_TOKENS": True,
    "BLACKLIST_AFTER_ROTATION": True,
    "AUTH_HEADER_TYPES": ("Bearer",),
}

CORS_ALLOW_ALL_ORIGINS = env.bool("CORS_ALLOW_ALL_ORIGINS", default=True)

if CORS_ALLOW_ALL_ORIGINS:
    CORS_ALLOW_CREDENTIALS = env.bool("CORS_ALLOW_CREDENTIALS", default=False)
else:
    CORS_ALLOW_CREDENTIALS = env.bool("CORS_ALLOW_CREDENTIALS", default=True)
    CORS_ALLOWED_ORIGINS = env.list(
        "CORS_ALLOWED_ORIGINS", default=["http://localhost:5173"]
    )

CORS_ALLOW_HEADERS = list(default_headers) + [
    "x-request-id",
]

CHANNEL_LAYERS = {
    "default": {
        "BACKEND": "channels_redis.core.RedisChannelLayer",
        "CONFIG": {
            "hosts": [REDIS_URL],
        },
    }
}

LOGGING = {
    "version": 1,
    "disable_existing_loggers": False,
    "formatters": {
        "structured": {
            "()": "apps.common.logging.StructuredJsonFormatter",
        }
    },
    "filters": {
        "mask_sensitive": {
            "()": "apps.common.logging.MaskSensitiveFilter",
        }
    },
    "handlers": {
        "console": {
            "class": "logging.StreamHandler",
            "formatter": "structured",
            "filters": ["mask_sensitive"],
        }
    },
    "root": {
        "handlers": ["console"],
        "level": "INFO",
    },
}

CONTENT_SAFETY = {
    "banned_words": env.list(
        "CONTENT_BANNED_WORDS",
        default=["敏感词", "涉政", "违规"],
    ),
}


CELERY_BROKER_URL = env("CELERY_BROKER_URL", default=REDIS_URL)
CELERY_RESULT_BACKEND = env("CELERY_RESULT_BACKEND", default=CELERY_BROKER_URL)
CELERY_TASK_DEFAULT_QUEUE = env("CELERY_DEFAULT_QUEUE", default="default")
CELERY_TASK_TIME_LIMIT = env.int("CELERY_TASK_TIME_LIMIT", default=120)
CELERY_TASK_SOFT_TIME_LIMIT = env.int("CELERY_TASK_SOFT_TIME_LIMIT", default=90)
CELERY_TASK_TRACK_STARTED = True
CELERY_ENABLE_UTC = True


LLM_CONFIG = {
    "provider": env("LLM_PROVIDER", default="openai"),
    "api_base": env("LLM_API_BASE", default=""),
    "api_key": env("LLM_API_KEY", default=""),
    "model": env("LLM_MODEL", default="gpt-4o-mini"),
    "timeout": env.float("LLM_TIMEOUT", default=30.0),
}
