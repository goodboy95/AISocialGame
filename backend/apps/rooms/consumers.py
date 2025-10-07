"""Channels consumer implementing room chat protocol."""

from __future__ import annotations

import logging
import uuid
from typing import Optional

from channels.db import database_sync_to_async
from channels.generic.websocket import AsyncJsonWebsocketConsumer
from django.contrib.auth import get_user_model
from django.utils import timezone
from rest_framework_simplejwt.authentication import JWTAuthentication

from apps.common import ContentPolicyViolation, enforce_content_policy
from apps.analytics.models import PrivateMessage
from apps.common.metrics import WS_CONNECTION_GAUGE
from apps.gamecore.engine import GameEngineError
from apps.gamecore.services import handle_room_event
from apps.gamecore.models import GameSession

from .models import Room, RoomPlayer
from .serializers import RoomDetailSerializer

User = get_user_model()
logger = logging.getLogger(__name__)


class RoomConsumer(AsyncJsonWebsocketConsumer):
    """Handle websocket connections for a single room."""

    room_id: int
    group_name: str
    user: User

    async def connect(self):
        path = self.scope.get("path")
        token_present = bool(self._extract_token())
        logger.info(
            "WebSocket connect attempt path=%s channel=%s token_present=%s",
            path,
            self.channel_name,
            token_present,
        )
        try:
            self.room_id = int(self.scope["url_route"]["kwargs"]["room_id"])
        except (KeyError, ValueError):  # pragma: no cover - routing guard
            logger.warning(
                "WebSocket rejected: invalid room id path=%s channel=%s",
                path,
                self.channel_name,
            )
            await self.close(code=4000)
            return
        self.group_name = f"room_{self.room_id}"

        user = await self._authenticate()
        if not user:
            logger.warning(
                "WebSocket rejected: authentication failed room_id=%s channel=%s",
                self.room_id,
                self.channel_name,
            )
            await self.close(code=4001)
            return
        self.user = user

        membership = await self._get_membership()
        if not membership:
            logger.warning(
                "WebSocket rejected: no active membership room_id=%s user_id=%s channel=%s",
                self.room_id,
                self.user.id,
                self.channel_name,
            )
            await self.close(code=4003)
            return
        self.player_id = membership.id

        await self.channel_layer.group_add(self.group_name, self.channel_name)
        await self.channel_layer.group_add(self._player_group(self.player_id), self.channel_name)
        await self.accept()
        logger.info(
            "WebSocket connected room_id=%s user_id=%s player_id=%s channel=%s",
            self.room_id,
            self.user.id,
            self.player_id,
            self.channel_name,
        )
        WS_CONNECTION_GAUGE.labels(room_id=str(self.room_id)).inc()
        await self._send_room_snapshot()

    async def disconnect(self, code):  # pragma: no cover - cleanup is best effort
        logger.info(
            "WebSocket disconnect room_id=%s user_id=%s player_id=%s code=%s channel=%s",
            getattr(self, "room_id", None),
            getattr(getattr(self, "user", None), "id", None),
            getattr(self, "player_id", None),
            code,
            self.channel_name,
        )
        if hasattr(self, "group_name"):
            await self.channel_layer.group_discard(self.group_name, self.channel_name)
        if hasattr(self, "player_id"):
            await self.channel_layer.group_discard(self._player_group(self.player_id), self.channel_name)
        if hasattr(self, "room_id"):
            WS_CONNECTION_GAUGE.labels(room_id=str(self.room_id)).dec()

    async def receive_json(self, content: dict, **kwargs):
        message_type = content.get("type")
        payload = content.get("payload", {})
        logger.debug(
            "WebSocket message received room_id=%s user_id=%s type=%s",
            getattr(self, "room_id", None),
            getattr(getattr(self, "user", None), "id", None),
            message_type,
        )
        if message_type == "chat.message":
            await self._handle_chat_message(payload)
        elif message_type == "chat.private":
            await self._handle_private_message(payload)
        elif message_type == "chat.faction":
            await self._handle_faction_message(payload)
        elif message_type == "ping":
            await self.send_json({"type": "pong", "timestamp": timezone.now().isoformat()})
        elif message_type == "game.event":
            await self._handle_game_event(payload)
        else:
            await self.send_json({"type": "error", "detail": "未知的消息类型"})

    async def chat_message(self, event: dict):
        await self.send_json({"type": "chat.message", "payload": event["payload"]})

    async def chat_direct(self, event: dict):
        await self.send_json({"type": "chat.direct", "payload": event["payload"]})

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

    async def _handle_private_message(self, payload: dict):
        content = (payload or {}).get("content", "").strip()
        target_id = (payload or {}).get("targetPlayerId")
        if not content or not target_id:
            await self.send_json({"type": "error", "detail": "私聊需要指定内容和目标"})
            return
        membership = await self._get_membership()
        if not membership:
            await self.send_json({"type": "error", "detail": "仅限房间成员操作"})
            return
        target = await self._get_player(int(target_id))
        if not target:
            await self.send_json({"type": "error", "detail": "目标玩家不存在或已离场"})
            return
        try:
            safe_content = enforce_content_policy(content, mode="reject")
        except ContentPolicyViolation as exc:
            await self.send_json({"type": "error", "detail": str(exc)})
            return
        session = await self._get_active_session()
        if not session:
            await self.send_json({"type": "error", "detail": "当前无进行中的对局"})
            return
        record = getattr(session, "analytics_record", None)
        message = {
            "id": str(uuid.uuid4()),
            "roomId": self.room_id,
            "sessionId": session.id if session else None,
            "channel": "private",
            "content": safe_content,
            "timestamp": timezone.now().isoformat(),
            "sender": {
                "id": membership.id,
                "displayName": membership.resolved_display_name,
            },
            "targetPlayerId": target.id,
        }
        await database_sync_to_async(PrivateMessage.objects.create)(
            record=record,
            session=session,
            room=membership.room,
            sender=membership,
            channel="private",
            target_player=target,
            payload={"content": safe_content},
        )
        recipients = {membership.id, target.id}
        for pid in recipients:
            await self.channel_layer.group_send(
                self._player_group(pid),
                {"type": "chat.direct", "payload": message},
            )

    async def _handle_faction_message(self, payload: dict):
        content = (payload or {}).get("content", "").strip()
        if not content:
            await self.send_json({"type": "error", "detail": "消息内容不能为空"})
            return
        membership = await self._get_membership()
        if not membership:
            await self.send_json({"type": "error", "detail": "仅限房间成员操作"})
            return
        session = await self._get_active_session()
        if not session:
            await self.send_json({"type": "error", "detail": "当前无进行中的对局"})
            return
        assignments = (session.state or {}).get("assignments", {})
        sender_meta = assignments.get(str(membership.id))
        if not sender_meta:
            await self.send_json({"type": "error", "detail": "暂无角色信息，无法发送阵营消息"})
            return
        faction = (payload or {}).get("faction") or sender_meta.get("role")
        if faction not in {"undercover", "werewolf"}:
            await self.send_json({"type": "error", "detail": "当前角色不支持阵营频道"})
            return
        if sender_meta.get("role") != faction:
            await self.send_json({"type": "error", "detail": "无法发送其他阵营的消息"})
            return
        try:
            safe_content = enforce_content_policy(content, mode="reject")
        except ContentPolicyViolation as exc:
            await self.send_json({"type": "error", "detail": str(exc)})
            return
        recipients = [
            int(pid)
            for pid, meta in assignments.items()
            if meta.get("role") == faction and meta.get("is_alive", True)
        ]
        if not recipients:
            recipients = [membership.id]
        message = {
            "id": str(uuid.uuid4()),
            "roomId": self.room_id,
            "sessionId": session.id,
            "channel": "faction",
            "faction": faction,
            "content": safe_content,
            "timestamp": timezone.now().isoformat(),
            "sender": {
                "id": membership.id,
                "displayName": membership.resolved_display_name,
            },
            "recipients": recipients,
        }
        record = getattr(session, "analytics_record", None)
        await database_sync_to_async(PrivateMessage.objects.create)(
            record=record,
            session=session,
            room=membership.room,
            sender=membership,
            channel="faction",
            payload={"content": safe_content, "faction": faction},
        )
        for pid in recipients:
            await self.channel_layer.group_send(
                self._player_group(pid),
                {"type": "chat.direct", "payload": message},
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

    def _player_group(self, player_id: int) -> str:
        return f"room_{self.room_id}_player_{player_id}"

    async def _get_active_session(self) -> Optional[GameSession]:
        return await database_sync_to_async(
            lambda: GameSession.objects.filter(
                room_id=self.room_id, status=GameSession.SessionStatus.ACTIVE
            ).first()
        )()

    async def _get_player(self, player_id: int) -> Optional[RoomPlayer]:
        return await database_sync_to_async(
            lambda: RoomPlayer.objects.filter(room_id=self.room_id, pk=player_id, is_active=True).first()
        )()
