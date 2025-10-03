"""Admin bindings for games app."""

from django.contrib import admin

from .models import GameDefinition, WordPair


@admin.register(GameDefinition)
class GameDefinitionAdmin(admin.ModelAdmin):
    list_display = ("name", "slug")
    search_fields = ("name", "slug")


@admin.register(WordPair)
class WordPairAdmin(admin.ModelAdmin):
    list_display = ("civilian_word", "undercover_word", "topic", "difficulty", "created_at")
    list_filter = ("difficulty", "topic")
    search_fields = ("civilian_word", "undercover_word", "topic")
