"""Serializers for room and membership resources."""

from __future__ import annotations

from typing import Any, Optional

from django.utils import timezone
from rest_framework import serializers

from .models import Room, RoomPlayer


class RoomPlayerSerializer(serializers.ModelSerializer):
    """Serialize a room membership entry."""

    user_id = serializers.SerializerMethodField()
    username = serializers.SerializerMethodField()
    display_name = serializers.SerializerMethodField()

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

    class Meta(RoomBaseSerializer.Meta):
        fields = RoomBaseSerializer.Meta.fields + (
            "config",
            "players",
            "is_member",
            "is_owner",
        )

    def get_is_member(self, obj: Room) -> bool:
        user = self._resolve_user()
        if user is None:
            return False
        return obj.players.filter(user=user, is_active=True).exists()

    def get_is_owner(self, obj: Room) -> bool:
        user = self._resolve_user()
        if user is None:
            return False
        return obj.owner_id == user.id

    def _resolve_user(self):
        request = self.context.get("request")
        if request and not request.user.is_anonymous:
            return request.user
        return self.context.get("user")


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
