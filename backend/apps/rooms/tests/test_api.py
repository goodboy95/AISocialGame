import pytest
from rest_framework.test import APIClient

from apps.rooms import services
from apps.games.models import WordPair
from apps.rooms.models import Room
from apps.users.models import User


@pytest.fixture
def api_client():
    return APIClient()


@pytest.fixture
def user(db):
    return User.objects.create_user(username="owner", password="password123", email="owner@example.com")


@pytest.fixture
def member(db):
    return User.objects.create_user(username="member", password="password123", email="member@example.com")


@pytest.mark.django_db
def test_create_room(api_client, user):
    api_client.force_authenticate(user)
    payload = {"name": "测试房间", "max_players": 6, "is_private": False}
    response = api_client.post("/api/rooms/", payload, format="json")
    assert response.status_code == 201
    data = response.json()
    assert data["name"] == "测试房间"
    assert data["owner"]["id"] == user.id
    assert data["player_count"] == 1
    assert len(data["players"]) == 1


@pytest.mark.django_db
def test_join_and_leave_room_flow(api_client, user, member):
    room = services.create_room(owner=user, name="协作房间", max_players=4)

    api_client.force_authenticate(member)
    join_response = api_client.post(f"/api/rooms/{room.id}/join/", format="json")
    assert join_response.status_code == 200
    data = join_response.json()
    assert data["player_count"] == 2

    leave_response = api_client.post(f"/api/rooms/{room.id}/leave/", format="json")
    assert leave_response.status_code == 200
    updated = leave_response.json()
    assert updated["player_count"] == 1
    assert Room.objects.get(pk=room.pk).status == Room.RoomStatus.WAITING


@pytest.mark.django_db
def test_only_owner_can_start(api_client, user, member, word_pair):
    room = services.create_room(owner=user, name="开始测试", max_players=4)
    services.join_room(room=room, user=member)

    api_client.force_authenticate(member)
    forbidden = api_client.post(f"/api/rooms/{room.id}/start/", format="json")
    assert forbidden.status_code == 400

    api_client.force_authenticate(user)
    allowed = api_client.post(f"/api/rooms/{room.id}/start/", format="json")
    assert allowed.status_code == 200
    room.refresh_from_db()
    assert room.status == Room.RoomStatus.IN_PROGRESS
    assert room.phase == Room.RoomPhase.PLAYING
    body = allowed.json()
    assert body.get("game_session")
    assert body["game_session"]["phase"] == "preparing"
    assert room.players.filter(is_ai=True, is_active=True).count() >= 1
@pytest.fixture
def word_pair(db):
    return WordPair.objects.create(
        civilian_word="苹果",
        undercover_word="梨子",
        topic="水果",
        difficulty="easy",
    )

