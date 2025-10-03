"""Game definitions placeholder."""

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
