from django.contrib import admin

from .models import Room


@admin.register(Room)
class RoomAdmin(admin.ModelAdmin):
    list_display = ("name", "code", "owner", "is_private", "created_at")
    search_fields = ("name", "code")
    list_filter = ("is_private",)
