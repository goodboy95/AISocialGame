"""API routes for AI metadata."""

from django.urls import path

from .views import AIStyleListView

app_name = "ai"

urlpatterns = [
    path("styles/", AIStyleListView.as_view(), name="styles"),
]
