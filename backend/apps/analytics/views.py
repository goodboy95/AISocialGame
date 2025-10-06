"""REST API endpoints exposing analytics datasets."""

from __future__ import annotations

from collections import Counter

from django.db.models import Count, DateTimeField, F, Max, Q
from django.db.models.functions import Cast
from rest_framework import mixins, viewsets
from rest_framework.decorators import action
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response

from .models import GameRecord
from .serializers import (
    GameRecordListSerializer,
    GameRecordSerializer,
    PlayerSummarySerializer,
)


class GameRecordViewSet(mixins.ListModelMixin, mixins.RetrieveModelMixin, viewsets.GenericViewSet):
    """Allow players to browse past sessions and inspect replay payloads."""

    queryset = GameRecord.objects.select_related("room").prefetch_related("events", "player_results")
    serializer_class = GameRecordSerializer
    permission_classes = [IsAuthenticated]
    filterset_fields = ("engine", "status", "winner")
    search_fields = ("room__name", "room__code")
    ordering_fields = ("started_at", "ended_at")
    ordering = ("-started_at",)

    def get_serializer_class(self):
        if self.action == "list":
            return GameRecordListSerializer
        return super().get_serializer_class()

    def get_queryset(self):
        qs = super().get_queryset()
        user = self.request.user
        if not user.is_staff:
            qs = qs.filter(Q(room__owner=user) | Q(room__players__user=user)).distinct()
        return qs

    @action(detail=False, methods=["get"], url_path="players/(?P<player_id>[^/.]+)/summary")
    def player_summary(self, request, player_id=None):
        qs = self.get_queryset().filter(player_results__player_id=player_id)
        aggregate = qs.aggregate(
            total_games=Count("id", distinct=True),
            wins=Count("id", filter=Q(winner="player"), distinct=True),
            last_played_at=Max("ended_at"),
        )
        total_games = aggregate.get("total_games") or 0
        wins = aggregate.get("wins") or 0
        losses = max(total_games - wins, 0)
        default_actions = (
            qs.filter(player_results__player_id=player_id)
            .annotate(total_default=F("player_results__default_actions"))
            .aggregate(total_default=Count("player_results__id"))
            .get("total_default")
            or 0
        )
        engines_counter = Counter(qs.values_list("engine", flat=True))
        serializer = PlayerSummarySerializer(
            {
                "player_id": int(player_id),
                "total_games": total_games,
                "wins": wins,
                "losses": losses,
                "win_rate": float(wins / total_games) if total_games else 0.0,
                "default_actions": default_actions,
                "last_played_at": aggregate.get("last_played_at"),
                "engines": dict(engines_counter),
            }
        )
        return Response(serializer.data)

    @action(detail=False, methods=["get"], url_path="rooms/(?P<room_id>[^/.]+)/history")
    def room_history(self, request, room_id=None):
        qs = self.get_queryset().filter(room_id=room_id)
        serializer = GameRecordListSerializer(qs, many=True)
        return Response(serializer.data)


class AnalyticsDashboardViewSet(viewsets.ViewSet):
    """Provide aggregated statistics for dashboards."""

    permission_classes = [IsAuthenticated]

    def list(self, request):
        qs = GameRecord.objects.all()
        if not request.user.is_staff:
            qs = qs.filter(Q(room__owner=request.user) | Q(room__players__user=request.user)).distinct()
        engine_counts = Counter(qs.values_list("engine", flat=True))
        winners = Counter(qs.exclude(winner="").values_list("winner", flat=True))
        total_duration = qs.annotate(duration=Cast(F("ended_at"), DateTimeField()) - Cast(F("started_at"), DateTimeField()))
        response = {
            "engines": dict(engine_counts),
            "winners": dict(winners),
            "total": qs.count(),
            "avgDuration": total_duration.aggregate(avg=Max("duration")).get("avg"),
        }
        return Response(response)
