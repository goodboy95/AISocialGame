"""Channels consumer implementing room chat protocol."""

from __future__ import annotations

import uuid
from typing import Optional

from channels.db import database_sync_to_async
from channels.generic.websocket import AsyncJsonWebsocketConsumer
from django.contrib.auth import get_user_model
from django.utils import timezone
from rest_framework_simplejwt.authentication import JWTAuthentication

from apps.common import ContentPolicyViolation, enforce_content_policy
from apps.common.metrics import WS_CONNECTION_GAUGE
from apps.gamecore.engine import GameEngineError
from apps.gamecore.services import handle_room_event

from .models import Room, RoomPlayer
from .serializers import RoomDetailSerializer

User = get_user_model()


class RoomConsumer(AsyncJsonWebsocketConsumer):
    """Handle websocket connections for a single room."""

    room_id: int
    group_name: str
    user: User

    async def connect(self):
        try:
            self.room_id = int(self.scope["url_route"]["kwargs"]["room_id"])
        except (KeyError, ValueError):  # pragma: no cover - routing guard
            await self.close(code=4000)
            return
        self.group_name = f"room_{self.room_id}"

        user = await self._authenticate()
        if not user:
            await self.close(code=4001)
            return
        self.user = user

        membership = await self._get_membership()
        if not membership:
            await self.close(code=4003)
            return

        await self.channel_layer.group_add(self.group_name, self.channel_name)
        await self.accept()
        WS_CONNECTION_GAUGE.labels(room_id=str(self.room_id)).inc()
        await self._send_room_snapshot()

    async def disconnect(self, code):  # pragma: no cover - cleanup is best effort
        if hasattr(self, "group_name"):
            await self.channel_layer.group_discard(self.group_name, self.channel_name)
        if hasattr(self, "room_id"):
            WS_CONNECTION_GAUGE.labels(room_id=str(self.room_id)).dec()

    async def receive_json(self, content: dict, **kwargs):
        message_type = content.get("type")
        payload = content.get("payload", {})
        if message_type == "chat.message":
            await self._handle_chat_message(payload)
        elif message_type == "ping":
            await self.send_json({"type": "pong", "timestamp": timezone.now().isoformat()})
        elif message_type == "game.event":
            await self._handle_game_event(payload)
        else:
            await self.send_json({"type": "error", "detail": "未知的消息类型"})

    async def chat_message(self, event: dict):
        await self.send_json({"type": "chat.message", "payload": event["payload"]})

    async def system_broadcast(self, event: dict):
        await self.send_json({"type": "system.broadcast", "payload": event["payload"]})

    async def system_sync(self, event: dict):
        await self.send_json({"type": "system.sync", "payload": event["payload"]})

    async def _handle_chat_message(self, payload: dict):
        content = (payload or {}).get("content", "").strip()
        if not content:
            await self.send_json({"type": "error", "detail": "消息内容不能为空"})
            return
        try:
            safe_content = enforce_content_policy(content, mode="reject")
        except ContentPolicyViolation as exc:
            await self.send_json({"type": "error", "detail": str(exc)})
            return
        message = {
            "id": str(uuid.uuid4()),
            "room_id": self.room_id,
            "content": safe_content,
            "timestamp": timezone.now().isoformat(),
            "sender": {
                "id": self.user.id,
                "username": self.user.username,
                "display_name": getattr(self.user, "display_name", self.user.username),
            },
        }
        await self.channel_layer.group_send(
            self.group_name,
            {"type": "chat.message", "payload": message},
        )

    async def _handle_game_event(self, payload: dict):
        event_type = (payload or {}).get("event")
        event_payload = (payload or {}).get("payload") or {}
        if not event_type:
            await self.send_json({"type": "error", "detail": "缺少事件类型"})
            return
        membership = await self._get_membership()
        if not membership:
            await self.send_json({"type": "error", "detail": "仅限房间成员操作"})
            return
        try:
            await database_sync_to_async(handle_room_event)(
                room=membership.room,
                actor=membership,
                event_type=event_type,
                payload=event_payload,
            )
        except GameEngineError as exc:
            await self.send_json({"type": "error", "detail": str(exc)})

    async def _authenticate(self) -> Optional[User]:
        token = self._extract_token()
        if not token:
            return None
        authenticator = JWTAuthentication()
        try:
            validated = authenticator.get_validated_token(token)
            user = await database_sync_to_async(authenticator.get_user)(validated)
        except Exception:  # pragma: no cover - JWT auth raises descriptive errors
            return None
        return user

    def _extract_token(self) -> Optional[str]:
        query_string = self.scope.get("query_string", b"").decode()
        if not query_string:
            return None
        from urllib.parse import parse_qs

        parsed = parse_qs(query_string)
        tokens = parsed.get("token")
        return tokens[0] if tokens else None

    async def _get_membership(self) -> Optional[RoomPlayer]:
        return await database_sync_to_async(
            lambda: RoomPlayer.objects.filter(
                room_id=self.room_id, user_id=self.user.id, is_active=True
            ).first()
        )()

    async def _send_room_snapshot(self) -> None:
        data = await database_sync_to_async(self._serialize_room_snapshot)()
        await self.send_json({"type": "system.sync", "payload": data})

    def _serialize_room_snapshot(self):
        room = Room.objects.select_related("owner").prefetch_related("players__user").get(pk=self.room_id)
        return RoomDetailSerializer(room, context={"user": self.user}).data

