"""REST API views for the games application."""

from __future__ import annotations

from django.db import models
from rest_framework import status, viewsets
from rest_framework.decorators import action
from rest_framework.permissions import IsAuthenticated
from rest_framework.request import Request
from rest_framework.response import Response

from .models import WordPair
from .serializers import WordPairSerializer


class WordPairViewSet(viewsets.ModelViewSet):
    """Manage word pairs used by the Undercover game mode."""

    queryset = WordPair.objects.all().order_by("-created_at")
    serializer_class = WordPairSerializer
    permission_classes = [IsAuthenticated]

    def get_queryset(self):
        queryset = super().get_queryset()
        request = self.request
        topic = request.query_params.get("topic")
        difficulty = request.query_params.get("difficulty")
        keyword = request.query_params.get("q")

        if topic:
            queryset = queryset.filter(topic__icontains=topic)
        if difficulty:
            queryset = queryset.filter(difficulty=difficulty)
        if keyword:
            queryset = queryset.filter(
                models.Q(civilian_word__icontains=keyword)
                | models.Q(undercover_word__icontains=keyword)
                | models.Q(topic__icontains=keyword)
            )
        return queryset

    @action(detail=False, methods=["post"], url_path="import")
    def bulk_import(self, request: Request) -> Response:
        items = request.data.get("items")
        if not isinstance(items, list):
            return Response(
                {"detail": "请求体格式错误，items 字段必须为数组"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        serializer = self.get_serializer(data=items, many=True)
        serializer.is_valid(raise_exception=True)
        created_pairs = [WordPair.objects.create(**item) for item in serializer.validated_data]
        response_data = self.get_serializer(created_pairs, many=True).data
        return Response(
            {"items": response_data, "created": len(created_pairs)},
            status=status.HTTP_201_CREATED,
        )

    @action(detail=False, methods=["get"], url_path="export")
    def export(self, request: Request) -> Response:
        queryset = self.filter_queryset(self.get_queryset())
        data = self.get_serializer(queryset, many=True).data
        return Response({"items": data})

