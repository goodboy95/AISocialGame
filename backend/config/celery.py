"""Celery application bootstrap for asynchronous workers."""

from __future__ import annotations

import os

from celery import Celery

os.environ.setdefault("DJANGO_SETTINGS_MODULE", "config.settings.base")

app = Celery("aisocialgame")
app.config_from_object("django.conf:settings", namespace="CELERY")
app.autodiscover_tasks()


@app.task(bind=True)
def debug_task(self):  # pragma: no cover - developer utility
    """Simple debug hook to inspect worker execution."""

    print(f"Request: {self.request!r}")
