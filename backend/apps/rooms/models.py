"""Room related models."""

from django.conf import settings
from django.db import models
from django.db.models import Q


class Room(models.Model):
    """Game room basic structure with lobby state tracking."""

    class RoomStatus(models.TextChoices):
        WAITING = "waiting", "等待中"
        IN_PROGRESS = "in_progress", "进行中"
        COMPLETED = "completed", "已结束"
        CLOSED = "closed", "已解散"

    class RoomPhase(models.TextChoices):
        LOBBY = "lobby", "大厅"
        PREPARE = "prepare", "准备阶段"
        PLAYING = "playing", "游戏中"
        REVIEW = "review", "总结"

    name = models.CharField(max_length=120)
    code = models.CharField(max_length=12, unique=True)
    owner = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        related_name="owned_rooms",
        on_delete=models.CASCADE,
    )
    status = models.CharField(
        max_length=20,
        choices=RoomStatus.choices,
        default=RoomStatus.WAITING,
    )
    phase = models.CharField(
        max_length=20,
        choices=RoomPhase.choices,
        default=RoomPhase.LOBBY,
    )
    max_players = models.PositiveIntegerField(default=8)
    current_round = models.PositiveIntegerField(default=0)
    config = models.JSONField(default=dict, blank=True)
    is_private = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ("-created_at",)
        verbose_name = "房间"
        verbose_name_plural = "房间"

    def __str__(self) -> str:
        return f"{self.name} ({self.code})"

    @property
    def player_count(self) -> int:
        """Return current joined player count."""

        return self.players.filter(is_active=True).count()


class RoomPlayer(models.Model):
    """Player membership within a room."""

    room = models.ForeignKey(Room, related_name="players", on_delete=models.CASCADE)
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        related_name="room_memberships",
        on_delete=models.CASCADE,
        null=True,
        blank=True,
    )
    display_name = models.CharField(max_length=120, blank=True)
    seat_number = models.PositiveIntegerField()
    is_host = models.BooleanField(default=False)
    is_ai = models.BooleanField(default=False)
    is_active = models.BooleanField(default=True)
    joined_at = models.DateTimeField(auto_now_add=True)
    role = models.CharField(max_length=40, blank=True)
    word = models.CharField(max_length=120, blank=True)
    is_alive = models.BooleanField(default=True)

    class Meta:
        ordering = ("seat_number", "joined_at")
        verbose_name = "房间成员"
        verbose_name_plural = "房间成员"
        constraints = [
            models.UniqueConstraint(
                fields=("room", "seat_number"),
                name="unique_room_seat",
            ),
            models.UniqueConstraint(
                fields=("room", "user"),
                condition=Q(user__isnull=False),
                name="unique_room_user",
            ),
        ]

    def __str__(self) -> str:  # pragma: no cover - human readable helper
        user_display = self.display_name or (self.user and str(self.user)) or "AI"
        return f"{user_display} in {self.room.code}"

    @property
    def resolved_display_name(self) -> str:
        """Prefer explicit display name and fallback to related user."""

        if self.display_name:
            return self.display_name
        if self.user:
            return self.user.display_name or self.user.username
        return "AI 玩家"
