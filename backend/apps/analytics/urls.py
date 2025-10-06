from django.urls import include, path
from rest_framework.routers import DefaultRouter

from .views import AnalyticsDashboardViewSet, GameRecordViewSet

router = DefaultRouter()
router.register("records", GameRecordViewSet, basename="analytics-record")
router.register("dashboard", AnalyticsDashboardViewSet, basename="analytics-dashboard")

urlpatterns = [
    path("", include(router.urls)),
]
