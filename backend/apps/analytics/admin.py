from django.contrib import admin

from .models import AnalyticsSubscription, GameRecord, PlayerResult, PrivateMessage, RoundEvent


@admin.register(GameRecord)
class GameRecordAdmin(admin.ModelAdmin):
    list_display = ("id", "engine", "status", "started_at", "winner")
    list_filter = ("engine", "status", "winner")
    search_fields = ("id", "room__name", "room__code")
    readonly_fields = ("created_at", "updated_at")


@admin.register(RoundEvent)
class RoundEventAdmin(admin.ModelAdmin):
    list_display = ("id", "record", "phase", "event_type", "occurred_at")
    list_filter = ("phase", "event_type")
    search_fields = ("record__room__name",)


@admin.register(PlayerResult)
class PlayerResultAdmin(admin.ModelAdmin):
    list_display = ("id", "record", "player_id", "display_name", "outcome", "survived")
    list_filter = ("outcome", "survived", "is_ai")
    search_fields = ("display_name",)


@admin.register(PrivateMessage)
class PrivateMessageAdmin(admin.ModelAdmin):
    list_display = ("id", "session", "channel", "sender", "target_player", "created_at")
    list_filter = ("channel",)
    search_fields = ("payload",)


@admin.register(AnalyticsSubscription)
class AnalyticsSubscriptionAdmin(admin.ModelAdmin):
    list_display = ("id", "user", "opted_in", "updated_at")
    list_filter = ("opted_in",)
    search_fields = ("user__username",)
