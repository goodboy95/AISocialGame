"""Channels routing configuration."""

from django.urls import path

from apps.rooms.consumers import RoomConsumer

websocket_urlpatterns = [
    path("ws/rooms/<int:room_id>/", RoomConsumer.as_asgi(), name="room-consumer"),
]
