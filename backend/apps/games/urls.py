"""URL configuration for the games app."""

from rest_framework.routers import DefaultRouter

from .views import WordPairViewSet

router = DefaultRouter()
router.register("word-pairs", WordPairViewSet, basename="word-pair")

urlpatterns = router.urls
