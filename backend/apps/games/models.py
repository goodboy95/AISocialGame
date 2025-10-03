"""Game definitions and word bank models."""

from __future__ import annotations

import random
from typing import Optional

from django.db import models


class GameDefinition(models.Model):
    slug = models.SlugField(unique=True)
    name = models.CharField(max_length=120)
    description = models.TextField(blank=True)

    class Meta:
        verbose_name = "游戏定义"
        verbose_name_plural = "游戏定义"

    def __str__(self) -> str:
        return self.name


class WordPairQuerySet(models.QuerySet):
    def filter_by_preferences(self, *, topic: Optional[str] = None, difficulty: Optional[str] = None):
        queryset = self
        if topic:
            queryset = queryset.filter(topic=topic)
        if difficulty:
            queryset = queryset.filter(difficulty=difficulty)
        return queryset


class WordPair(models.Model):
    """Word pair collection used by Undercover game."""

    DIFFICULTY_CHOICES = (
        ("easy", "简单"),
        ("medium", "适中"),
        ("hard", "困难"),
    )

    topic = models.CharField(max_length=120, blank=True)
    civilian_word = models.CharField(max_length=60)
    undercover_word = models.CharField(max_length=60)
    difficulty = models.CharField(max_length=12, choices=DIFFICULTY_CHOICES, default="easy")
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    objects = WordPairQuerySet.as_manager()

    class Meta:
        verbose_name = "词库词对"
        verbose_name_plural = "词库词对"
        ordering = ("-created_at",)

    def __str__(self) -> str:  # pragma: no cover - human readable helper
        return f"{self.civilian_word} / {self.undercover_word} ({self.topic or '无主题'})"

    @classmethod
    def pick_random(cls, *, topic: Optional[str] = None, difficulty: Optional[str] = None) -> "WordPair":
        queryset = cls.objects.filter_by_preferences(topic=topic, difficulty=difficulty)
        count = queryset.count()
        if count == 0:
            raise cls.DoesNotExist("未找到符合条件的词库词对")
        index = random.randint(0, count - 1)
        return queryset.all()[index]
