"""Serializers for room and membership resources."""

from __future__ import annotations

from typing import Any, Optional

from django.utils import timezone
from rest_framework import serializers

from apps.gamecore.services import serialize_session_for_user

from .models import Room, RoomPlayer


class RoomPlayerSerializer(serializers.ModelSerializer):
    """Serialize a room membership entry."""

    user_id = serializers.SerializerMethodField()
    username = serializers.SerializerMethodField()
    display_name = serializers.SerializerMethodField()
    word = serializers.SerializerMethodField()
    role = serializers.SerializerMethodField()

    class Meta:
        model = RoomPlayer
        fields = (
            "id",
            "user_id",
            "username",
            "display_name",
            "seat_number",
            "is_host",
            "is_ai",
            "is_active",
            "joined_at",
            "role",
            "word",
            "is_alive",
            "has_used_skill",
        )
        read_only_fields = fields

    def get_display_name(self, obj: RoomPlayer) -> str:
        return obj.resolved_display_name

    def get_user_id(self, obj: RoomPlayer) -> Optional[int]:
        return obj.user_id

    def get_username(self, obj: RoomPlayer) -> Optional[str]:
        if obj.user:
            return obj.user.username
        return None

    def get_word(self, obj: RoomPlayer) -> str:
        if self._should_reveal(obj):
            return obj.word
        return ""

    def get_role(self, obj: RoomPlayer) -> str:
        if self._should_reveal(obj):
            return obj.role
        return ""

    def _should_reveal(self, obj: RoomPlayer) -> bool:
        viewer_id = self.context.get("viewer_player_id")
        phase = self.context.get("game_phase")
        if phase in {"result", "ended"}:
            return True
        if viewer_id and viewer_id == obj.id:
            return True
        return False


class RoomBaseSerializer(serializers.ModelSerializer):
    """Common serializer fields shared by list/detail views."""

    owner = serializers.SerializerMethodField()
    status_display = serializers.CharField(source="get_status_display", read_only=True)
    phase_display = serializers.CharField(source="get_phase_display", read_only=True)
    player_count = serializers.SerializerMethodField()

    class Meta:
        model = Room
        fields = (
            "id",
            "name",
            "code",
            "owner",
            "status",
            "status_display",
            "phase",
            "phase_display",
            "max_players",
            "current_round",
            "is_private",
            "player_count",
            "created_at",
            "updated_at",
        )
        read_only_fields = (
            "code",
            "status",
            "phase",
            "current_round",
            "created_at",
            "updated_at",
        )

    def get_owner(self, obj: Room) -> dict[str, Any]:
        owner = obj.owner
        return {
            "id": owner.id,
            "username": owner.username,
            "display_name": owner.display_name or owner.username,
        }

    def get_player_count(self, obj: Room) -> int:
        return obj.players.filter(is_active=True).count()


class RoomListSerializer(RoomBaseSerializer):
    """Serialize lobby list items."""

    pass


class RoomDetailSerializer(RoomBaseSerializer):
    """Detailed representation including memberships and config."""

    players = RoomPlayerSerializer(many=True, read_only=True)
    config = serializers.JSONField(read_only=True)
    is_member = serializers.SerializerMethodField()
    is_owner = serializers.SerializerMethodField()
    game_session = serializers.SerializerMethodField()

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        instance = kwargs.get("instance")
        if instance is not None and not isinstance(instance, (list, tuple)):
            self._get_membership(instance)

    class Meta(RoomBaseSerializer.Meta):
        fields = RoomBaseSerializer.Meta.fields + (
            "config",
            "players",
            "is_member",
            "is_owner",
            "game_session",
        )

    def get_is_member(self, obj: Room) -> bool:
        membership = self._get_membership(obj)
        return membership is not None

    def get_is_owner(self, obj: Room) -> bool:
        user = self._resolve_user()
        if user is None:
            return False
        return obj.owner_id == user.id

    def get_game_session(self, obj: Room) -> Optional[dict]:
        session = obj.sessions.filter(status="active").first()
        if not session:
            return None
        user = self._resolve_user()
        data = serialize_session_for_user(session, user=user)
        self.context["game_phase"] = data.get("phase")
        membership = self._get_membership(obj)
        if membership:
            self.context["viewer_player_id"] = membership.id
        return data

    def _resolve_user(self):
        request = self.context.get("request")
        if request and not request.user.is_anonymous:
            return request.user
        return self.context.get("user")

    def _get_membership(self, obj: Room):
        if hasattr(self, "_cached_membership"):
            return self._cached_membership
        user = self._resolve_user()
        membership = None
        if user is not None:
            membership = obj.players.filter(user=user, is_active=True).first()
        self._cached_membership = membership
        if membership:
            self.context.setdefault("viewer_player_id", membership.id)
        return membership


class RoomCreateSerializer(serializers.Serializer):
    """Validate payload for creating a room."""

    name = serializers.CharField(max_length=120)
    max_players = serializers.IntegerField(min_value=2, max_value=12, default=8)
    is_private = serializers.BooleanField(default=False)
    config = serializers.JSONField(required=False)


class RoomJoinByCodeSerializer(serializers.Serializer):
    """Validate payload to join a room via public code."""

    code = serializers.CharField(max_length=12)


class ChatMessageSerializer(serializers.Serializer):
    """Helper serializer shared with websocket consumer."""

    id = serializers.CharField()
    type = serializers.CharField()
    content = serializers.CharField()
    timestamp = serializers.DateTimeField(default=timezone.now)
    sender = serializers.DictField(child=serializers.CharField(), allow_empty=True)
