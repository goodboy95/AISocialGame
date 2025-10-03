"""View layer for room REST API."""

from __future__ import annotations

from django.shortcuts import get_object_or_404
from rest_framework import mixins, permissions, status, viewsets
from rest_framework.decorators import action
from rest_framework.pagination import PageNumberPagination
from rest_framework.response import Response

from . import services
from .models import Room
from .serializers import (
    RoomCreateSerializer,
    RoomDetailSerializer,
    RoomJoinByCodeSerializer,
    RoomListSerializer,
)


class RoomPagination(PageNumberPagination):
    page_size = 12
    max_page_size = 50


class RoomViewSet(viewsets.GenericViewSet, mixins.ListModelMixin, mixins.RetrieveModelMixin):
    """Expose CRUD-style operations for lobby and room management."""

    queryset = Room.objects.all().select_related("owner").prefetch_related("players__user")
    permission_classes = (permissions.IsAuthenticated,)
    pagination_class = RoomPagination

    def get_serializer_class(self):
        if self.action == "create":
            return RoomCreateSerializer
        if self.action == "list":
            return RoomListSerializer
        return RoomDetailSerializer

    def get_permissions(self):
        if self.action in {"list", "retrieve"}:
            return [permissions.AllowAny()]
        return super().get_permissions()

    def get_queryset(self):
        queryset = super().get_queryset()
        status_filter = self.request.query_params.get("status")
        search = self.request.query_params.get("search")
        privacy = self.request.query_params.get("is_private")
        if status_filter:
            queryset = queryset.filter(status=status_filter)
        if search:
            queryset = queryset.filter(name__icontains=search)
        if privacy is not None:
            if privacy.lower() in {"true", "1"}:
                queryset = queryset.filter(is_private=True)
            elif privacy.lower() in {"false", "0"}:
                queryset = queryset.filter(is_private=False)
        return queryset

    def list(self, request, *args, **kwargs):
        queryset = self.filter_queryset(self.get_queryset())
        page = self.paginate_queryset(queryset)
        if page is not None:
            serializer = RoomListSerializer(page, many=True, context={"request": request})
            return self.get_paginated_response(serializer.data)
        serializer = RoomListSerializer(queryset, many=True, context={"request": request})
        return Response(serializer.data)

    def retrieve(self, request, *args, **kwargs):
        room = self.get_object()
        serializer = RoomDetailSerializer(room, context={"request": request})
        return Response(serializer.data)

    def create(self, request, *args, **kwargs):
        serializer = RoomCreateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        room = services.create_room(owner=request.user, **serializer.validated_data)
        output = RoomDetailSerializer(room, context={"request": request})
        return Response(output.data, status=status.HTTP_201_CREATED)

    @action(methods=["post"], detail=True)
    def join(self, request, pk=None):
        room = self.get_object()
        try:
            result = services.join_room(room=room, user=request.user)
        except services.RoomServiceError as exc:
            return Response({"detail": str(exc)}, status=status.HTTP_400_BAD_REQUEST)
        serializer = RoomDetailSerializer(result.room, context={"request": request})
        return Response(serializer.data, status=status.HTTP_200_OK)

    @action(methods=["post"], detail=True)
    def leave(self, request, pk=None):
        room = self.get_object()
        try:
            result = services.leave_room(room=room, user=request.user)
        except services.RoomServiceError as exc:
            return Response({"detail": str(exc)}, status=status.HTTP_400_BAD_REQUEST)
        serializer = RoomDetailSerializer(result.room, context={"request": request})
        return Response(serializer.data)

    @action(methods=["post"], detail=True)
    def start(self, request, pk=None):
        room = self.get_object()
        try:
            services.start_room(room=room, user=request.user)
        except services.RoomServiceError as exc:
            return Response({"detail": str(exc)}, status=status.HTTP_400_BAD_REQUEST)
        serializer = RoomDetailSerializer(room, context={"request": request})
        return Response(serializer.data)

    def destroy(self, request, *args, **kwargs):
        room = self.get_object()
        try:
            services.dissolve_room(room=room, user=request.user)
        except services.RoomServiceError as exc:
            return Response({"detail": str(exc)}, status=status.HTTP_403_FORBIDDEN)
        return Response(status=status.HTTP_204_NO_CONTENT)

    @action(methods=["post"], detail=False, url_path="join-by-code", url_name="join-by-code")
    def join_by_code(self, request):
        serializer = RoomJoinByCodeSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        room = get_object_or_404(Room, code=serializer.validated_data["code"])
        try:
            services.join_room(room=room, user=request.user)
        except services.RoomServiceError as exc:
            return Response({"detail": str(exc)}, status=status.HTTP_400_BAD_REQUEST)
        output = RoomDetailSerializer(room, context={"request": request})
        return Response(output.data)
