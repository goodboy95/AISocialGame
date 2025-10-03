import pytest
from rest_framework.test import APIClient

import pytest
from rest_framework.test import APIClient

from apps.rooms import services
from apps.users.models import User


@pytest.fixture
def api_client():
    return APIClient()


@pytest.fixture
def user(db):
    return User.objects.create_user(
        username="tester",
        password="password123",
        email="tester@example.com",
        display_name="测试用户",
    )


@pytest.mark.django_db
def test_export_profile_contains_memberships(api_client, user):
    room = services.create_room(owner=user, name="导出房间", max_players=4)
    api_client.force_authenticate(user)

    response = api_client.get("/api/auth/me/export/")

    assert response.status_code == 200
    payload = response.json()
    assert payload["profile"]["username"] == "tester"
    assert payload["ownedRooms"][0]["id"] == room.id
    assert payload["statistics"]["ownedRooms"] == 1


@pytest.mark.django_db
def test_delete_profile_anonymizes_user(api_client, user):
    services.create_room(owner=user, name="注销房间", max_players=4)
    api_client.force_authenticate(user)

    response = api_client.delete("/api/auth/me/")

    assert response.status_code == 204
    user.refresh_from_db()
    assert not user.is_active
    assert user.display_name == "已注销用户"
