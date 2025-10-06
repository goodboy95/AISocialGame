"""Helper utilities for writing analytics records from game sessions."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Any, Dict, Optional

from django.db import transaction
from django.utils import timezone

from .models import GameRecord, PlayerResult, RoundEvent


@dataclass
class TimerMetadata:
    """Lightweight structure describing an active timer."""

    duration: Optional[int]
    deadline_at: Optional[datetime]
    default_action: Dict[str, Any]


def ensure_record_for_session(session) -> GameRecord:
    """Create a :class:`GameRecord` for the given session if it doesn't exist."""

    record, _created = GameRecord.objects.get_or_create(
        session=session,
        defaults={
            "room": session.room,
            "engine": session.engine,
            "status": session.status,
            "started_at": session.started_at,
            "configuration": session.room.config or {},
        },
    )
    return record


def log_round_event(*, session, event_type: str, payload: Optional[Dict[str, Any]] = None, actor_id: Optional[int] = None) -> None:
    """Persist a timeline event for analytics and replay."""

    payload = payload or {}
    record = ensure_record_for_session(session)
    RoundEvent.objects.create(
        record=record,
        turn=session.round_number,
        phase=session.current_phase,
        event_type=event_type,
        actor_id=actor_id,
        payload=payload,
    )


def update_record_from_session(session) -> None:
    """Mirror session metadata onto the analytics record."""

    record = ensure_record_for_session(session)
    record.status = session.status
    record.started_at = session.started_at
    record.ended_at = session.ended_at
    state = session.state or {}
    record.winner = state.get("winner", "")
    record.replay_blob = {
        "state": state,
        "updatedAt": timezone.now().isoformat(),
    }
    record.save(update_fields=["status", "started_at", "ended_at", "winner", "replay_blob", "updated_at"])


def store_player_results(session, *, defaults: Optional[Dict[str, Dict[str, Any]]] = None) -> None:
    """Persist per-player outcome snapshot when a game concludes."""

    defaults = defaults or {}
    record = ensure_record_for_session(session)
    assignments = (session.state or {}).get("assignments", {})
    existing_ids = set(record.player_results.values_list("player_id", flat=True))
    with transaction.atomic():
        for player_id_str, assignment in assignments.items():
            player_id = int(player_id_str)
            meta = {
                "player_id": player_id,
                "display_name": assignment.get("display_name", "未知玩家"),
                "role": assignment.get("role", ""),
                "is_ai": assignment.get("is_ai", False),
                "ai_style": assignment.get("ai_style", ""),
                "survived": assignment.get("is_alive", False),
                "outcome": "win" if record.winner and assignment.get("role") == record.winner else "loss",
                "default_actions": defaults.get(player_id_str, {}).get("defaults", 0),
                "extra": defaults.get(player_id_str, {}).get("extra", {}),
            }
            if player_id in existing_ids:
                record.player_results.filter(player_id=player_id).update(**meta)
            else:
                PlayerResult.objects.create(record=record, **meta)


def finalize_record(session, *, defaults: Optional[Dict[str, Dict[str, Any]]] = None) -> None:
    """Finalize analytics data when the session ends."""

    update_record_from_session(session)
    store_player_results(session, defaults=defaults)
