"""Views for user APIs."""

from __future__ import annotations

import logging

from django.contrib.auth import get_user_model
from django.utils import timezone
from rest_framework import generics, permissions, status
from rest_framework.response import Response
from rest_framework.views import APIView
from rest_framework_simplejwt.exceptions import TokenError
from rest_framework_simplejwt.tokens import RefreshToken

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


class LogoutView(APIView):
    permission_classes = (permissions.IsAuthenticated,)

    def post(self, request):
        refresh_token = request.data.get("refresh")
        if not refresh_token:
            return Response({"detail": "缺少刷新令牌"}, status=status.HTTP_400_BAD_REQUEST)

        try:
            token = RefreshToken(refresh_token)
        except TokenError:
            return Response({"detail": "刷新令牌无效或已过期"}, status=status.HTTP_400_BAD_REQUEST)

        token_user_id = str(token.get("user_id"))
        if token_user_id != str(request.user.id):
            LOGGER.warning(
                "Refresh token user mismatch during logout user_id=%s token_user_id=%s",
                request.user.id,
                token_user_id,
            )
            return Response({"detail": "刷新令牌与当前用户不匹配"}, status=status.HTTP_400_BAD_REQUEST)

        try:
            token.blacklist()
        except AttributeError as error:  # pragma: no cover - defensive guard
            LOGGER.error("Token blacklist app not available: %s", error)
            return Response({"detail": "服务暂不可用，请稍后再试"}, status=status.HTTP_503_SERVICE_UNAVAILABLE)
        except TokenError:
            return Response({"detail": "刷新令牌重复注销"}, status=status.HTTP_400_BAD_REQUEST)

        LOGGER.info("User %s logged out and refresh token revoked", request.user.id)
        return Response(status=status.HTTP_204_NO_CONTENT)
