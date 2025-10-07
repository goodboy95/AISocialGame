"""Channels routing configuration."""

from django.urls import path, re_path

from apps.rooms.consumers import RoomConsumer

websocket_urlpatterns = [
    # Primary room route used by the frontend (`/ws/rooms/<id>/`).
    path("ws/rooms/<int:room_id>/", RoomConsumer.as_asgi(), name="room-consumer"),
    # Gracefully handle missing trailing slashes (e.g. `ws://.../ws/rooms/<id>`).
    re_path(r"^ws/rooms/(?P<room_id>\d+)$", RoomConsumer.as_asgi(), name="room-consumer-legacy"),
]
