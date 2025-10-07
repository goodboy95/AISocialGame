import pytest
from rest_framework.test import APIClient

from apps.games.models import WordPair
from apps.users.models import User


@pytest.fixture
def api_client():
    return APIClient()


@pytest.fixture
def user(db):
    return User.objects.create_user(
        username="manager",
        password="secure-pass-123",
        email="manager@example.com",
    )


@pytest.fixture
def auth_client(api_client, user):
    api_client.force_authenticate(user)
    return api_client


@pytest.mark.django_db
def test_list_word_pairs_with_filters(auth_client):
    WordPair.objects.create(
        civilian_word="苹果",
        undercover_word="梨子",
        topic="水果",
        difficulty="easy",
    )
    WordPair.objects.create(
        civilian_word="海豚",
        undercover_word="鲸鱼",
        topic="动物",
        difficulty="medium",
    )

    response = auth_client.get("/api/games/word-pairs/", {"difficulty": "easy"})
    assert response.status_code == 200
    payload = response.json()
    assert len(payload) == 1
    assert payload[0]["topic"] == "水果"


@pytest.mark.django_db
def test_create_word_pair(auth_client):
    payload = {
        "civilian_word": "台灯",
        "undercover_word": "路灯",
        "topic": "家居",
        "difficulty": "easy",
    }

    response = auth_client.post("/api/games/word-pairs/", payload, format="json")
    assert response.status_code == 201
    data = response.json()
    assert data["civilian_word"] == "台灯"
    assert WordPair.objects.filter(topic="家居").count() == 1


@pytest.mark.django_db
def test_update_word_pair(auth_client):
    pair = WordPair.objects.create(
        civilian_word="北京",
        undercover_word="上海",
        topic="城市",
        difficulty="medium",
    )

    response = auth_client.patch(
        f"/api/games/word-pairs/{pair.id}/",
        {"difficulty": "hard"},
        format="json",
    )
    assert response.status_code == 200
    pair.refresh_from_db()
    assert pair.difficulty == "hard"


@pytest.mark.django_db
def test_delete_word_pair(auth_client):
    pair = WordPair.objects.create(
        civilian_word="白雪",
        undercover_word="白霜",
        topic="自然",
        difficulty="medium",
    )

    response = auth_client.delete(f"/api/games/word-pairs/{pair.id}/")
    assert response.status_code == 204
    assert not WordPair.objects.filter(id=pair.id).exists()


@pytest.mark.django_db
def test_bulk_import(auth_client):
    payload = {
        "items": [
            {
                "civilian_word": "书包",
                "undercover_word": "背包",
                "topic": "学习",
                "difficulty": "easy",
            },
            {
                "civilian_word": "飞机",
                "undercover_word": "火箭",
                "topic": "交通",
                "difficulty": "hard",
            },
        ]
    }

    response = auth_client.post(
        "/api/games/word-pairs/import/",
        payload,
        format="json",
    )
    assert response.status_code == 201
    data = response.json()
    assert data["created"] == 2
    assert WordPair.objects.count() == 2


@pytest.mark.django_db
def test_export_returns_filtered_items(auth_client):
    WordPair.objects.create(
        civilian_word="红茶",
        undercover_word="绿茶",
        topic="饮品",
        difficulty="easy",
    )
    WordPair.objects.create(
        civilian_word="篮球",
        undercover_word="排球",
        topic="运动",
        difficulty="medium",
    )

    response = auth_client.get("/api/games/word-pairs/export/", {"topic": "饮品"})
    assert response.status_code == 200
    items = response.json()["items"]
    assert len(items) == 1
    assert items[0]["civilian_word"] == "红茶"


@pytest.mark.django_db
def test_validation_rejects_identical_words(auth_client):
    payload = {
        "civilian_word": "电脑",
        "undercover_word": "电脑",
        "topic": "数码",
        "difficulty": "easy",
    }

    response = auth_client.post("/api/games/word-pairs/", payload, format="json")
    assert response.status_code == 400
    assert "不能相同" in response.json()["non_field_errors"][0]
