"""Django project configuration package."""

from __future__ import annotations

# Ensure Celery app is always imported when Django starts so that shared tasks
# are registered automatically by worker and web processes.
from .celery import app as celery_app

__all__ = ["celery_app"]
