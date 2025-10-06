"""Serializers for analytics API endpoints."""

from rest_framework import serializers

from .models import GameRecord, PlayerResult, PrivateMessage, RoundEvent


class RoundEventSerializer(serializers.ModelSerializer):
    class Meta:
        model = RoundEvent
        fields = (
            "id",
            "turn",
            "phase",
            "event_type",
            "actor_id",
            "payload",
            "duration",
            "occurred_at",
        )


class PlayerResultSerializer(serializers.ModelSerializer):
    class Meta:
        model = PlayerResult
        fields = (
            "player_id",
            "display_name",
            "role",
            "is_ai",
            "ai_style",
            "survived",
            "outcome",
            "default_actions",
            "extra",
        )


class PrivateMessageSerializer(serializers.ModelSerializer):
    sender_id = serializers.IntegerField(source="sender_id", read_only=True)
    target_player_id = serializers.IntegerField(source="target_player_id", read_only=True)

    class Meta:
        model = PrivateMessage
        fields = (
            "id",
            "channel",
            "sender_id",
            "target_player_id",
            "payload",
            "created_at",
        )


class GameRecordSerializer(serializers.ModelSerializer):
    events = RoundEventSerializer(many=True, read_only=True)
    player_results = PlayerResultSerializer(many=True, read_only=True)

    class Meta:
        model = GameRecord
        fields = (
            "id",
            "engine",
            "status",
            "started_at",
            "ended_at",
            "winner",
            "configuration",
            "replay_blob",
            "events",
            "player_results",
        )


class GameRecordListSerializer(serializers.ModelSerializer):
    class Meta:
        model = GameRecord
        fields = (
            "id",
            "engine",
            "status",
            "started_at",
            "ended_at",
            "winner",
        )


class PlayerSummarySerializer(serializers.Serializer):
    player_id = serializers.IntegerField()
    total_games = serializers.IntegerField()
    wins = serializers.IntegerField()
    losses = serializers.IntegerField()
    win_rate = serializers.FloatField()
    default_actions = serializers.IntegerField()
    last_played_at = serializers.DateTimeField()
    engines = serializers.DictField(child=serializers.IntegerField())
