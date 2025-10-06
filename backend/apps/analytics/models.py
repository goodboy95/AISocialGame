"""Models used for persistent game analytics and replay metadata."""

from __future__ import annotations

from django.conf import settings
from django.db import models


class GameRecord(models.Model):
    """Snapshot of a finished or active game session used for analytics."""

    session = models.OneToOneField(
        "gamecore.GameSession",
        related_name="analytics_record",
        on_delete=models.CASCADE,
    )
    room = models.ForeignKey("rooms.Room", related_name="analytics_records", on_delete=models.CASCADE)
    engine = models.CharField(max_length=64)
    status = models.CharField(max_length=16)
    started_at = models.DateTimeField()
    ended_at = models.DateTimeField(null=True, blank=True)
    winner = models.CharField(max_length=64, blank=True)
    configuration = models.JSONField(default=dict, blank=True)
    replay_blob = models.JSONField(default=dict, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ("-started_at",)
        verbose_name = "对局记录"
        verbose_name_plural = "对局记录"

    def __str__(self) -> str:  # pragma: no cover - debugging helper
        return f"{self.engine} #{self.pk} ({self.status})"


class RoundEvent(models.Model):
    """Timeline event captured during a game for replay purposes."""

    record = models.ForeignKey(GameRecord, related_name="events", on_delete=models.CASCADE)
    turn = models.PositiveIntegerField(default=1)
    phase = models.CharField(max_length=32)
    event_type = models.CharField(max_length=64)
    actor_id = models.IntegerField(null=True, blank=True)
    payload = models.JSONField(default=dict, blank=True)
    duration = models.FloatField(null=True, blank=True)
    occurred_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ("occurred_at",)
        verbose_name = "阶段事件"
        verbose_name_plural = "阶段事件"


class PlayerResult(models.Model):
    """Per-player outcome summary for a completed game."""

    record = models.ForeignKey(GameRecord, related_name="player_results", on_delete=models.CASCADE)
    player_id = models.IntegerField()
    display_name = models.CharField(max_length=120)
    role = models.CharField(max_length=64, blank=True)
    is_ai = models.BooleanField(default=False)
    ai_style = models.CharField(max_length=64, blank=True)
    survived = models.BooleanField(default=True)
    outcome = models.CharField(max_length=32, blank=True)
    default_actions = models.PositiveIntegerField(default=0)
    extra = models.JSONField(default=dict, blank=True)

    class Meta:
        ordering = ("display_name",)
        verbose_name = "玩家战绩"
        verbose_name_plural = "玩家战绩"
        indexes = [
            models.Index(fields=("player_id", "record")),
            models.Index(fields=("record", "outcome")),
        ]


class PrivateMessage(models.Model):
    """Persisted record of private or faction messages during a session."""

    record = models.ForeignKey(
        GameRecord, related_name="private_messages", on_delete=models.CASCADE, null=True, blank=True
    )
    session = models.ForeignKey("gamecore.GameSession", related_name="private_messages", on_delete=models.CASCADE)
    room = models.ForeignKey("rooms.Room", related_name="private_messages", on_delete=models.CASCADE)
    sender = models.ForeignKey(
        "rooms.RoomPlayer", related_name="sent_private_messages", null=True, blank=True, on_delete=models.SET_NULL
    )
    channel = models.CharField(max_length=32, default="private")
    target_player = models.ForeignKey(
        "rooms.RoomPlayer", related_name="received_private_messages", null=True, blank=True, on_delete=models.SET_NULL
    )
    payload = models.JSONField(default=dict, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ("created_at",)
        verbose_name = "私聊消息"
        verbose_name_plural = "私聊消息"
        indexes = [
            models.Index(fields=("session", "channel")),
            models.Index(fields=("room", "created_at")),
        ]


class AnalyticsSubscription(models.Model):
    """Allow players to opt into additional analytics for their account."""

    user = models.OneToOneField(settings.AUTH_USER_MODEL, related_name="analytics_subscription", on_delete=models.CASCADE)
    opted_in = models.BooleanField(default=True)
    preferences = models.JSONField(default=dict, blank=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = "战绩订阅"
        verbose_name_plural = "战绩订阅"

    def __str__(self) -> str:  # pragma: no cover - readability helper
        return f"AnalyticsSubscription<{self.user_id}>"
