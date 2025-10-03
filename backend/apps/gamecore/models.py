"""Core game coordination models placeholder."""

from django.db import models


class GameSession(models.Model):
    room = models.ForeignKey("rooms.Room", related_name="sessions", on_delete=models.CASCADE)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        verbose_name = "游戏会话"
        verbose_name_plural = "游戏会话"

    def __str__(self) -> str:
        return f"Session for {self.room_id} at {self.created_at:%Y-%m-%d %H:%M}"
