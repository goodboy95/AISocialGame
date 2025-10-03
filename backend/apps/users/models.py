"""User related models."""

from django.contrib.auth.models import AbstractUser
from django.db import models


class User(AbstractUser):
    """Custom user model with profile fields."""

    display_name = models.CharField("显示名称", max_length=64, blank=True)
    avatar = models.URLField("头像", blank=True)
    bio = models.TextField("个人简介", blank=True)

    class Meta:
        verbose_name = "用户"
        verbose_name_plural = "用户"

    def __str__(self) -> str:
        return self.display_name or self.username
