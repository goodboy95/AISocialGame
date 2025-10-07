"""Main URL configuration for the AI Social Game backend."""

from django.contrib import admin
from django.http import HttpResponse
from django.urls import include, path
from rest_framework.decorators import api_view
from rest_framework.response import Response

from prometheus_client import CONTENT_TYPE_LATEST, generate_latest


@api_view(["GET"])
def health_check(_request):
    return Response({"status": "ok"})


urlpatterns = [
    path("admin/", admin.site.urls),
    path("api/health/", health_check, name="health-check"),
    path("api/metrics/", lambda _request: HttpResponse(generate_latest(), content_type=CONTENT_TYPE_LATEST)),
    path("api/auth/", include("apps.users.urls")),
    path("api/meta/", include("apps.ai.urls")),
    path("api/analytics/", include("apps.analytics.urls")),
    path("api/games/", include("apps.games.urls")),
    path("api/", include("apps.rooms.urls")),
]
