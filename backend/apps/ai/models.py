"""AI integration placeholders."""

from django.db import models


class AssistantProfile(models.Model):
    name = models.CharField(max_length=64)
    model = models.CharField(max_length=128)
    description = models.TextField(blank=True)

    class Meta:
        verbose_name = "AI 助手配置"
        verbose_name_plural = "AI 助手配置"

    def __str__(self) -> str:
        return self.name
