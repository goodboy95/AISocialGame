"""Celery tasks supporting timer expirations and AI decisions."""

from __future__ import annotations

from datetime import datetime
from typing import Optional

from celery import shared_task
from django.apps import apps
from django.utils import timezone


def _get_session(session_id: int):
    GameSession = apps.get_model("gamecore", "GameSession")
    return (
        GameSession.objects.select_related("room")
        .filter(pk=session_id)
        .first()
    )


@shared_task(bind=True, max_retries=3, default_retry_delay=5)
def process_timer_expiry(self, session_id: int) -> bool:
    """Handle session timer expiration by injecting a timeout event."""

    session = _get_session(session_id)
    if not session:
        return False
    if session.status != session.SessionStatus.ACTIVE:
        return False
    if not session.deadline_at or session.deadline_at > timezone.now():
        return False

    from .services import handle_room_event

    handle_room_event(
        room=session.room,
        actor=None,
        event_type="timeout",
        payload={"timer": session.timer_context or {}},
    )
    return True


def schedule_session_timer(session, *, eta: Optional[datetime] = None) -> None:
    """Register a Celery task to handle the active session timer."""

    if not session.deadline_at:
        return
    eta = eta or session.deadline_at
    if eta <= timezone.now():
        process_timer_expiry.apply_async(args=[session.id], countdown=1)
        return
    process_timer_expiry.apply_async(args=[session.id], eta=eta)
