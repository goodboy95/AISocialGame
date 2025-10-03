"""Room related models."""

from django.conf import settings
from django.db import models


class Room(models.Model):
    """Game room basic structure."""

    name = models.CharField(max_length=120)
    code = models.CharField(max_length=12, unique=True)
    owner = models.ForeignKey(settings.AUTH_USER_MODEL, related_name="owned_rooms", on_delete=models.CASCADE)
    is_private = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ("-created_at",)
        verbose_name = "房间"
        verbose_name_plural = "房间"

    def __str__(self) -> str:
        return f"{self.name} ({self.code})"
