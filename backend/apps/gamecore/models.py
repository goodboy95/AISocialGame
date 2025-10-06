"""Core game coordination models."""

from __future__ import annotations

from django.db import models


class GameSession(models.Model):
    """Persisted snapshot of a running game within a room."""

    class SessionStatus(models.TextChoices):
        ACTIVE = "active", "进行中"
        COMPLETED = "completed", "已结束"

    room = models.ForeignKey("rooms.Room", related_name="sessions", on_delete=models.CASCADE)
    engine = models.CharField(max_length=64)
    state = models.JSONField(default=dict, blank=True)
    status = models.CharField(max_length=16, choices=SessionStatus.choices, default=SessionStatus.ACTIVE)
    current_phase = models.CharField(max_length=32, default="preparing")
    current_player_id = models.PositiveIntegerField(null=True, blank=True)
    round_number = models.PositiveIntegerField(default=1)
    deadline_at = models.DateTimeField(null=True, blank=True)
    timer_context = models.JSONField(default=dict, blank=True)
    started_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)
    ended_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        verbose_name = "游戏会话"
        verbose_name_plural = "游戏会话"
        ordering = ("-started_at",)

    def __str__(self) -> str:  # pragma: no cover - convenience debug output
        return f"{self.engine} session in room {self.room_id} ({self.get_status_display()})"
