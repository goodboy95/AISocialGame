from django.contrib import admin

from .models import Room, RoomPlayer


class RoomPlayerInline(admin.TabularInline):
    model = RoomPlayer
    extra = 0
    fields = (
        "seat_number",
        "user",
        "display_name",
        "is_host",
        "is_ai",
        "is_active",
    )
    readonly_fields = ("joined_at",)


@admin.register(Room)
class RoomAdmin(admin.ModelAdmin):
    list_display = (
        "name",
        "code",
        "owner",
        "status",
        "phase",
        "max_players",
        "created_at",
    )
    search_fields = ("name", "code", "owner__username")
    list_filter = ("status", "phase", "is_private")
    inlines = (RoomPlayerInline,)


@admin.register(RoomPlayer)
class RoomPlayerAdmin(admin.ModelAdmin):
    list_display = (
        "room",
        "seat_number",
        "resolved_display_name",
        "is_host",
        "is_ai",
        "is_active",
    )
    list_filter = ("is_host", "is_ai", "is_active")
    search_fields = ("room__code", "display_name", "user__username")
