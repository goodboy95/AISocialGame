"""Views for user APIs."""

from __future__ import annotations

import logging

from django.contrib.auth import get_user_model
from django.utils import timezone
from rest_framework import generics, permissions, status
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.rooms.models import Room, RoomPlayer

from .serializers import RegisterSerializer, UserSerializer

User = get_user_model()


LOGGER = logging.getLogger(__name__)


class RegisterView(generics.CreateAPIView):
    queryset = User.objects.all()
    permission_classes = (permissions.AllowAny,)
    serializer_class = RegisterSerializer


class ProfileView(APIView):
    permission_classes = (permissions.IsAuthenticated,)

    def get(self, request):
        return Response(UserSerializer(request.user).data)

    def delete(self, request):
        user = request.user
        user.is_active = False
        anonymized_name = f"user-{user.id}"
        user.username = anonymized_name
        user.display_name = "已注销用户"
        user.email = ""
        user.avatar = ""
        user.bio = ""
        user.save(update_fields=["is_active", "username", "display_name", "email", "avatar", "bio"])
        RoomPlayer.objects.filter(user=user).update(
            user=None,
            display_name="已离场玩家",
            is_active=False,
        )
        LOGGER.info("User %s requested account deletion", user.id)
        return Response(status=status.HTTP_204_NO_CONTENT)


class ProfileExportView(APIView):
    permission_classes = (permissions.IsAuthenticated,)

    def get(self, request):
        user = request.user
        memberships = (
            RoomPlayer.objects.filter(user=user)
            .select_related("room")
            .order_by("-joined_at")
        )
        owned_rooms = Room.objects.filter(owner=user).order_by("-created_at")
        data = {
            "exported_at": timezone.now().isoformat(),
            "profile": UserSerializer(user).data,
            "memberships": [
                {
                    "roomId": membership.room_id,
                    "roomName": membership.room.name,
                    "roomCode": membership.room.code,
                    "status": membership.room.status,
                    "joinedAt": membership.joined_at.isoformat(),
                    "isHost": membership.is_host,
                    "isAi": membership.is_ai,
                    "aiStyle": membership.ai_style,
                    "role": membership.role,
                    "word": membership.word,
                    "alive": membership.is_alive,
                }
                for membership in memberships
            ],
            "ownedRooms": [
                {
                    "id": room.id,
                    "name": room.name,
                    "code": room.code,
                    "createdAt": room.created_at.isoformat(),
                    "status": room.status,
                }
                for room in owned_rooms
            ],
        }
        data["statistics"] = {
            "joinedRooms": len(data["memberships"]),
            "ownedRooms": len(data["ownedRooms"]),
        }
        return Response(data)
