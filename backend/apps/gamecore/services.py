"""Coordinator utilities for running game engines within rooms."""

from __future__ import annotations

import logging
from typing import Any, Dict, Optional

from asgiref.sync import async_to_sync
from channels.layers import get_channel_layer
from django.utils import timezone
from django.utils.module_loading import import_string

from apps.analytics import services as analytics_services
from apps.rooms.models import Room, RoomPlayer

from .engine import BaseGameEngine, EnginePhase, GameEngineError, GameEvent
from .models import GameSession
from .tasks import schedule_session_timer

LOGGER = logging.getLogger(__name__)

ENGINE_REGISTRY: Dict[str, str] = {
    "undercover": "apps.games.undercover.engine.UndercoverEngine",
    "werewolf": "apps.games.werewolf.engine.WerewolfEngine",
}


class EngineNotRegistered(GameEngineError):
    """Raised when requesting an unknown engine slug."""


def _import_engine(engine_slug: str) -> type[BaseGameEngine]:
    try:
        dotted_path = ENGINE_REGISTRY[engine_slug]
    except KeyError as exc:  # pragma: no cover - defensive guard
        raise EngineNotRegistered(f"未注册的引擎: {engine_slug}") from exc
    return import_string(dotted_path)


def get_engine_for_session(session: GameSession) -> BaseGameEngine:
    """Instantiate engine for a stored session."""

    engine_cls = _import_engine(session.engine)
    return engine_cls(session)


def serialize_session_for_user(session: GameSession, *, user=None) -> Dict[str, Any]:
    """Expose sanitized session snapshot for REST / WS consumers."""

    engine = get_engine_for_session(session)
    return {
        "id": session.id,
        "engine": session.engine,
        "phase": session.current_phase,
        "round": session.round_number,
        "currentPlayerId": session.current_player_id,
        "status": session.status,
        "startedAt": session.started_at.isoformat(),
        "updatedAt": session.updated_at.isoformat(),
        "deadlineAt": session.deadline_at.isoformat() if session.deadline_at else None,
        "timer": session.timer_context or {},
        "state": engine.get_public_state(for_user=user),
    }


def _broadcast_game_event(room: Room, payload: Dict[str, Any]) -> None:
    """Send game updates to room websocket group."""

    channel_layer = get_channel_layer()
    if not channel_layer:  # pragma: no cover - channel-less env
        LOGGER.debug("Channel layer missing, skip game broadcast")
        return
    async_to_sync(channel_layer.group_send)(
        f"room_{room.id}",
        {
            "type": "game.event",
            "payload": payload,
        },
    )


def start_room_game(room: Room, *, engine_slug: str = "undercover") -> GameSession:
    """Create a new session for the room and bootstrap engine state."""

    room.sessions.filter(status=GameSession.SessionStatus.ACTIVE).update(
        status=GameSession.SessionStatus.COMPLETED,
        ended_at=timezone.now(),
    )
    session = GameSession.objects.create(room=room, engine=engine_slug)
    engine = get_engine_for_session(session)
    engine.start_game()
    fields = engine.to_session_fields()
    session.state = fields["state"]
    session.current_phase = fields["current_phase"]
    session.current_player_id = fields["current_player_id"]
    session.round_number = fields["round_number"]
    session.deadline_at = fields.get("deadline_at")
    session.timer_context = fields.get("timer_context") or {}
    session.save(update_fields=[
        "state",
        "current_phase",
        "current_player_id",
        "round_number",
        "deadline_at",
        "timer_context",
        "updated_at",
    ])
    analytics_services.ensure_record_for_session(session)
    analytics_services.update_record_from_session(session)
    schedule_session_timer(session)
    LOGGER.info("Room %s started game session %s", room.code, session.id)
    public_session = serialize_session_for_user(session)
    from apps.rooms.serializers import RoomDetailSerializer  # local import

    room_snapshot = RoomDetailSerializer(room).data
    room_snapshot["game_session"] = public_session
    _broadcast_game_event(
        room,
        {
            "event": "game_started",
            "room": room_snapshot,
            "session": public_session,
        },
    )
    return session


def handle_room_event(*, room: Room, actor: Optional[RoomPlayer], event_type: str, payload: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    """Push an event into the active engine and broadcast the result."""

    payload = payload or {}
    session = (
        room.sessions.filter(status=GameSession.SessionStatus.ACTIVE)
        .select_for_update()
        .first()
    )
    if not session:
        raise GameEngineError("房间尚未开始游戏")

    engine = get_engine_for_session(session)
    event = GameEvent(type=event_type, payload=payload, actor_id=actor.id if actor else None)
    engine.handle_event(event)
    changed = engine.run_auto_actions()
    fields = engine.to_session_fields()
    session.state = fields["state"]
    session.current_phase = fields["current_phase"]
    session.current_player_id = fields["current_player_id"]
    session.round_number = fields["round_number"]
    session.deadline_at = fields.get("deadline_at")
    session.timer_context = fields.get("timer_context") or {}
    if engine.phase == EnginePhase.ENDED or fields["state"].get("winner"):
        session.status = GameSession.SessionStatus.COMPLETED
        session.ended_at = timezone.now()
    session.save(update_fields=[
        "state",
        "current_phase",
        "current_player_id",
        "round_number",
        "deadline_at",
        "timer_context",
        "status",
        "ended_at",
        "updated_at",
    ])

    analytics_services.log_round_event(
        session=session,
        event_type=event_type,
        payload=payload,
        actor_id=actor.id if actor else None,
    )
    defaults_snapshot = fields["state"].get("default_actions", {}) if isinstance(fields.get("state"), dict) else {}
    formatted_defaults = {
        key: {"defaults": value}
        for key, value in defaults_snapshot.items()
    }
    if session.status == GameSession.SessionStatus.COMPLETED:
        analytics_services.finalize_record(session, defaults=formatted_defaults)
    else:
        analytics_services.update_record_from_session(session)

    public_session = serialize_session_for_user(session)
    from apps.rooms.serializers import RoomDetailSerializer  # local import to avoid circular

    room_snapshot = RoomDetailSerializer(room).data
    room_snapshot["game_session"] = public_session
    _broadcast_game_event(
        room,
        {
            "event": event_type,
            "actor": actor.id if actor else None,
            "session": public_session,
            "room": room_snapshot,
        },
    )
    for extra in engine.consume_pending_events():
        if not extra:
            continue
        _broadcast_game_event(
            room,
            {
                "event": extra.get("type"),
                "payload": extra.get("payload", {}),
            },
        )
    schedule_session_timer(session)
    if changed:
        LOGGER.debug("Engine auto actions executed after %s", event_type)
    return public_session

