"""Serializers for games application APIs."""

from __future__ import annotations

from rest_framework import serializers

from .models import WordPair


class WordPairSerializer(serializers.ModelSerializer):
    """Serializer providing validation for word bank management."""

    class Meta:
        model = WordPair
        fields = (
            "id",
            "topic",
            "civilian_word",
            "undercover_word",
            "difficulty",
            "created_at",
            "updated_at",
        )
        read_only_fields = ("id", "created_at", "updated_at")

    def validate(self, attrs):
        civilian_word = attrs.get("civilian_word") or getattr(self.instance, "civilian_word", "")
        undercover_word = attrs.get("undercover_word") or getattr(
            self.instance, "undercover_word", ""
        )
        if civilian_word and undercover_word and civilian_word == undercover_word:
            raise serializers.ValidationError("平民词语与卧底词语不能相同")
        return super().validate(attrs)

    def validate_difficulty(self, value: str) -> str:
        allowed = {choice for choice, _ in WordPair.DIFFICULTY_CHOICES}
        if value not in allowed:
            raise serializers.ValidationError("难度取值无效")
        return value

