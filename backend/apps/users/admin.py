from django.contrib import admin
from django.contrib.auth.admin import UserAdmin as DjangoUserAdmin

from .models import User


@admin.register(User)
class UserAdmin(DjangoUserAdmin):
    fieldsets = DjangoUserAdmin.fieldsets + (
        ("Profile", {"fields": ("display_name", "avatar", "bio")}),
    )
    list_display = ("username", "email", "display_name", "is_active", "is_staff")
    search_fields = ("username", "email", "display_name")
