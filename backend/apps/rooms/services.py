"""Business logic helpers for room management."""

from __future__ import annotations

import logging
import secrets
import string
import time
from dataclasses import dataclass
from typing import Optional, Callable, TypeVar

from asgiref.sync import async_to_sync
from channels.layers import get_channel_layer
from django.db import OperationalError, transaction
from django.db.models import Count, Q
from django.utils import timezone
from rest_framework import status

from apps.ai import ai_style_label, generate_ai_display_name, random_ai_style, resolve_ai_style
from apps.gamecore.engine import GameEngineError
from apps.gamecore.services import serialize_session_for_user, start_room_game

from .models import Room, RoomPlayer

LOGGER = logging.getLogger(__name__)

T = TypeVar("T")


class RoomServiceError(Exception):
    """Base error for room service operations."""


class RoomFullError(RoomServiceError):
    """Raised when attempting to join a full room."""


class RoomClosedError(RoomServiceError):
    """Raised when operating on a closed room."""


@dataclass
class RoomOperationResult:
    """Helper dataclass to transport room mutation results."""

    room: Room
    actor: Optional[RoomPlayer] = None
    event: str = ""
    message: str = ""


def _generate_room_code(length: int = 6) -> str:
    alphabet = string.ascii_uppercase + string.digits
    for _ in range(20):
        code = "".join(secrets.choice(alphabet) for _ in range(length))
        if not Room.objects.filter(code=code).exists():
            return code
    raise RuntimeError("无法生成唯一的房间号，请重试")


def _broadcast(event_type: str, payload: dict) -> None:
    channel_layer = get_channel_layer()
    if not channel_layer:
        LOGGER.debug("Channel layer not configured, skip broadcast")
        return
    group = payload.get("room", {}).get("id")
    if group is None:
        LOGGER.debug("Room id missing in payload, skip broadcast")
        return
    async_to_sync(channel_layer.group_send)(
        f"room_{group}",
        {
            "type": event_type,
            "payload": payload,
        },
    )


def _execute_with_retry(func: Callable[[], T], *, retries: int = 3, delay: float = 0.1) -> T:
    """Run ``func`` and retry when SQLite reports a database lock."""

    for attempt in range(retries):
        try:
            return func()
        except OperationalError as exc:  # pragma: no cover - depends on sqlite timing
            message = str(exc).lower()
            if "locked" not in message or attempt == retries - 1:
                raise
            sleep_for = delay * (attempt + 1)
            LOGGER.warning(
                "Database locked while executing room operation, retrying in %.2fs", sleep_for
            )
            time.sleep(sleep_for)

    raise RuntimeError("Exceeded retry attempts")


def _serialize_room(room: Room) -> dict:
    from .serializers import RoomDetailSerializer  # Lazy import to avoid circular dependency

    data = RoomDetailSerializer(room).data
    session = room.sessions.filter(status="active").first()
    if session:
        data["game_session"] = serialize_session_for_user(session)
    else:
        data["game_session"] = None
    return data


def _should_autofill_ai(room: Room) -> bool:
    config = room.config or {}
    ai_cfg = config.get("ai", {})
    return ai_cfg.get("auto_fill", True)


def _ai_fill_target(room: Room) -> int:
    config = room.config or {}
    ai_cfg = config.get("ai", {})
    return int(ai_cfg.get("fill_to", room.max_players))


def _ensure_ai_players(room: Room) -> list[RoomPlayer]:
    if not _should_autofill_ai(room):
        return []
    target = max(2, min(room.max_players, _ai_fill_target(room)))
    active_players = list(room.players.filter(is_active=True))
    needed = max(0, target - len(active_players))
    if needed == 0:
        return []
    existing_names = {player.resolved_display_name for player in active_players}
    created: list[RoomPlayer] = []
    for _ in range(needed):
        seat = _next_available_seat(room)
        display_name = generate_ai_display_name(existing_names)
        existing_names.add(display_name)
        created.append(
            RoomPlayer.objects.create(
                room=room,
                user=None,
                seat_number=seat,
                is_host=False,
                is_ai=True,
                ai_style=random_ai_style(),
                is_active=True,
                display_name=display_name,
            )
        )
    LOGGER.info("Room %s auto-filled %s AI players", room.code, len(created))
    return created


def add_ai_player(*, room: Room, user, style: str | None = None, display_name: str | None = None) -> RoomOperationResult:
    try:
        membership = room.players.get(user=user, is_active=True)
    except RoomPlayer.DoesNotExist as exc:
        raise RoomServiceError("只有房主可以添加 AI 玩家") from exc
    if not membership.is_host:
        raise RoomServiceError("只有房主可以添加 AI 玩家")

    with transaction.atomic():
        locked_room = Room.objects.select_for_update().get(pk=room.pk)
        active_count = locked_room.players.filter(is_active=True).count()
        if active_count >= locked_room.max_players:
            raise RoomFullError("房间已满员，无法继续添加 AI")
        seat = _next_available_seat(locked_room)
        existing_names = {player.resolved_display_name for player in locked_room.players.filter(is_active=True)}
        resolved_style = resolve_ai_style(style)
        resolved_name = display_name.strip() if display_name else generate_ai_display_name(existing_names)
        ai_player = RoomPlayer.objects.create(
            room=locked_room,
            user=None,
            seat_number=seat,
            is_host=False,
            is_ai=True,
            ai_style=resolved_style,
            is_active=True,
            display_name=resolved_name,
        )

    room.refresh_from_db()
    message = f"新增 AI 玩家 {ai_player.resolved_display_name}（{ai_style_label(resolved_style)}）加入房间"
    payload = {
        "room": _serialize_room(room),
        "actor": {
            "id": membership.user_id,
            "display_name": membership.resolved_display_name,
            "username": membership.user.username if membership.user else None,
        },
        "message": message,
        "timestamp": timezone.now().isoformat(),
        "event": "ai_player_added",
        "status_code": status.HTTP_200_OK,
        "context": {
            "aiPlayer": {
                "id": ai_player.id,
                "displayName": ai_player.resolved_display_name,
                "style": resolved_style,
                "styleLabel": ai_style_label(resolved_style),
            }
        },
    }
    _broadcast("system.broadcast", payload)
    LOGGER.info(
        "Room %s host %s added AI player %s with style %s",
        room.code,
        membership.resolved_display_name,
        ai_player.resolved_display_name,
        resolved_style,
    )
    return RoomOperationResult(room=room, actor=ai_player, event="ai_player_added", message=message)


def _resolve_engine_slug(room: Room) -> str:
    """Determine which game engine should be used for the room."""

    config = room.config or {}
    if isinstance(config, dict):
        game_cfg = config.get("game")
        if isinstance(game_cfg, dict):
            engine = game_cfg.get("engine") or game_cfg.get("mode")
            if isinstance(engine, str) and engine:
                return engine
        engine = config.get("engine")
        if isinstance(engine, str) and engine:
            return engine
    return "undercover"


def create_room(*, owner, name: str, max_players: int = 8, is_private: bool = False, config: Optional[dict] = None) -> Room:
    if max_players < 2:
        raise RoomServiceError("房间人数下限为 2 人")

    if config is None:
        config = {}

    with transaction.atomic():
        room = Room.objects.create(
            name=name,
            owner=owner,
            code=_generate_room_code(),
            max_players=max_players,
            is_private=is_private,
            config=config,
        )
        RoomPlayer.objects.create(
            room=room,
            user=owner,
            seat_number=1,
            is_host=True,
            is_ai=False,
            display_name=owner.display_name or owner.username,
        )
    LOGGER.info("Room %s created by %s", room.code, owner)
    return room


def list_available_rooms(*, filters: Optional[dict] = None):
    filters = filters or {}
    queryset = (
        Room.objects.exclude(status=Room.RoomStatus.CLOSED)
        .annotate(player_count=Count("players", filter=Q(players__is_active=True)))
        .select_related("owner")
    )
    status_filter = filters.get("status")
    if status_filter:
        queryset = queryset.filter(status=status_filter)
    if name := filters.get("search"):
        queryset = queryset.filter(name__icontains=name)
    if filters.get("is_private") is not None:
        queryset = queryset.filter(is_private=filters["is_private"])
    return queryset


def _next_available_seat(room: Room) -> int:
    existing = set(
        room.players.filter(is_active=True).values_list("seat_number", flat=True)
    )
    for seat in range(1, room.max_players + 1):
        if seat not in existing:
            return seat
    raise RoomFullError("房间已满员")


def join_room(*, room: Room, user) -> RoomOperationResult:
    if room.status in {Room.RoomStatus.CLOSED, Room.RoomStatus.COMPLETED}:
        raise RoomClosedError("房间已关闭，无法加入")

    with transaction.atomic():
        locked_room = Room.objects.select_for_update().get(pk=room.pk)
        membership = (
            locked_room.players.select_for_update()
            .filter(user=user)
            .select_related("user")
            .first()
        )

        state_changed = False
        if membership:
            if not membership.is_active:
                membership.is_active = True
                membership.save(update_fields=["is_active"])
                state_changed = True
        else:
            active_count = locked_room.players.filter(is_active=True).count()
            if active_count >= locked_room.max_players:
                raise RoomFullError("房间已满员")
            membership = RoomPlayer.objects.create(
                room=locked_room,
                user=user,
                seat_number=_next_available_seat(locked_room),
                display_name=user.display_name or user.username,
                is_ai=False,
            )
            state_changed = True

    event = "player_joined" if state_changed else "player_reconnected"
    message = ""
    if state_changed:
        message = f"{membership.resolved_display_name} 加入了房间"
        payload = {
            "room": _serialize_room(locked_room),
            "actor": {
                "id": membership.user_id,
                "display_name": membership.resolved_display_name,
                "username": membership.user.username if membership.user else None,
            },
            "message": message,
            "timestamp": timezone.now().isoformat(),
            "event": event,
            "status_code": status.HTTP_200_OK,
        }
        _broadcast("system.broadcast", payload)

    return RoomOperationResult(
        room=locked_room,
        actor=membership,
        event=event,
        message=message,
    )


def leave_room(*, room: Room, user) -> RoomOperationResult:
    def _leave() -> tuple[Room, RoomPlayer, bool]:
        with transaction.atomic():
            locked_room = Room.objects.select_for_update().get(pk=room.pk)
            try:
                membership = (
                    locked_room.players.select_for_update()
                    .select_related("user")
                    .get(user=user)
                )
            except RoomPlayer.DoesNotExist as exc:  # pragma: no cover - defensive
                raise RoomServiceError("用户不在房间内") from exc

            was_host = membership.is_host
            membership.is_active = False
            membership.is_host = False
            membership.save(update_fields=["is_active", "is_host"])

            remaining_members = list(
                locked_room.players.filter(is_active=True).order_by("seat_number", "joined_at")
            )
            if not remaining_members:
                locked_room.status = Room.RoomStatus.CLOSED
                locked_room.phase = Room.RoomPhase.LOBBY
                locked_room.save(update_fields=["status", "phase"])
            elif was_host:
                new_host = remaining_members[0]
                if not new_host.is_host:
                    new_host.is_host = True
                    new_host.save(update_fields=["is_host"])
                if new_host.user:
                    locked_room.owner = new_host.user
                    locked_room.save(update_fields=["owner"])
                LOGGER.info("Room %s host transferred to %s", locked_room.code, new_host)

            return locked_room, membership, was_host

    locked_room, membership, _ = _execute_with_retry(_leave)

    message = f"{membership.resolved_display_name} 离开了房间"
    payload = {
        "room": _serialize_room(locked_room),
        "actor": {
            "id": membership.user_id,
            "display_name": membership.resolved_display_name,
            "username": membership.user.username if membership.user else None,
        },
        "message": message,
        "timestamp": timezone.now().isoformat(),
        "event": "player_left",
        "status_code": status.HTTP_200_OK,
    }
    _broadcast("system.broadcast", payload)
    return RoomOperationResult(room=locked_room, actor=membership, event="player_left", message=message)


def start_room(*, room: Room, user) -> Room:
    if room.status != Room.RoomStatus.WAITING:
        raise RoomServiceError("房间当前状态无法开始游戏")
    try:
        membership = room.players.get(user=user, is_active=True)
    except RoomPlayer.DoesNotExist as exc:  # pragma: no cover - defensive
        raise RoomServiceError("只有房间成员才能开始游戏") from exc
    if not membership.is_host:
        raise RoomServiceError("只有房主可以开始游戏")

    with transaction.atomic():
        locked_room = (
            Room.objects.select_for_update()
            .prefetch_related("players__user")
            .get(pk=room.pk)
        )
        _ensure_ai_players(locked_room)
        locked_room.status = Room.RoomStatus.IN_PROGRESS
        locked_room.phase = Room.RoomPhase.PREPARE
        locked_room.current_round = 1
        locked_room.save(update_fields=["status", "phase", "current_round", "updated_at"])

    try:
        engine_slug = _resolve_engine_slug(locked_room)
        session = start_room_game(room, engine_slug=engine_slug)
    except GameEngineError as exc:
        room.status = Room.RoomStatus.WAITING
        room.phase = Room.RoomPhase.LOBBY
        room.save(update_fields=["status", "phase"])
        raise RoomServiceError(str(exc)) from exc

    room.status = Room.RoomStatus.IN_PROGRESS
    room.phase = Room.RoomPhase.PLAYING
    room.current_round = session.round_number
    room.save(update_fields=["phase", "current_round", "updated_at"])

    payload = {
        "room": _serialize_room(room),
        "actor": {
            "id": membership.user_id,
            "display_name": membership.resolved_display_name,
            "username": membership.user.username if membership.user else None,
        },
        "message": "房主已启动游戏",
        "timestamp": timezone.now().isoformat(),
        "event": "room_started",
        "status_code": status.HTTP_200_OK,
    }
    _broadcast("system.broadcast", payload)
    LOGGER.info("Room %s started by %s", room.code, membership)
    return room


def dissolve_room(*, room: Room, user) -> RoomOperationResult:
    try:
        membership = room.players.get(user=user, is_active=True)
    except RoomPlayer.DoesNotExist as exc:
        raise RoomServiceError("只有房主可以解散房间") from exc

    if not membership.is_host:
        raise RoomServiceError("只有房主可以解散房间")

    with transaction.atomic():
        room.status = Room.RoomStatus.CLOSED
        room.phase = Room.RoomPhase.LOBBY
        room.save(update_fields=["status", "phase"])
        room.players.update(is_active=False, is_host=False)

    payload = {
        "room": _serialize_room(room),
        "actor": {
            "id": membership.user_id,
            "display_name": membership.resolved_display_name,
            "username": membership.user.username if membership.user else None,
        },
        "message": "房间已被房主解散",
        "timestamp": timezone.now().isoformat(),
        "event": "room_dissolved",
        "status_code": status.HTTP_200_OK,
    }
    _broadcast("system.broadcast", payload)
    return RoomOperationResult(room=room, actor=membership, event="room_dissolved", message="房间已解散")


def get_room_by_code(code: str) -> Room:
    return Room.objects.get(code=code)
