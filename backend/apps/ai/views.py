"""Views for AI metadata and configuration."""

from rest_framework.response import Response
from rest_framework.views import APIView

from . import list_ai_styles


class AIStyleListView(APIView):
    authentication_classes: tuple = ()
    permission_classes: tuple = ()

    def get(self, _request):
        styles = list_ai_styles()
        return Response({"styles": styles})
