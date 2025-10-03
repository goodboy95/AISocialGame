import random

import pytest

from apps.gamecore.engine import EnginePhase, GameEvent
from apps.gamecore.models import GameSession
from apps.gamecore.services import serialize_session_for_user
from apps.games.models import WordPair
from apps.games.undercover.engine import UndercoverEngine
from apps.rooms.models import Room, RoomPlayer
from apps.users.models import User


@pytest.fixture
def word_pair(db):
    return WordPair.objects.create(
        civilian_word="画笔",
        undercover_word="钢笔",
        topic="文具",
        difficulty="easy",
    )


@pytest.fixture
def room_with_players(db, word_pair):
    owner = User.objects.create_user(username="owner", password="pass12345", email="o@example.com")
    room = Room.objects.create(
        name="卧底测试房",
        code="TEST01",
        owner=owner,
        max_players=6,
        config={"ai": {"auto_fill": False}},
    )
    RoomPlayer.objects.create(room=room, user=owner, seat_number=1, is_host=True, display_name="房主")
    for index in range(2, 5):
        user = User.objects.create_user(
            username=f"player{index}", password="pass12345", email=f"p{index}@example.com"
        )
        RoomPlayer.objects.create(room=room, user=user, seat_number=index, display_name=f"玩家{index}")
    return room


@pytest.mark.django_db
def test_undercover_engine_full_round(room_with_players, word_pair):
    random.seed(7)
    session = GameSession.objects.create(room=room_with_players, engine="undercover")
    engine = UndercoverEngine(session)
    engine.start_game()

    assignments = engine.state["assignments"]
    assert len(assignments) == room_with_players.players.filter(is_active=True).count()
    undercover_id = next(int(pid) for pid, meta in assignments.items() if meta["role"] == "undercover")
    civilians = [int(pid) for pid, meta in assignments.items() if meta["role"] != "undercover"]

    engine.handle_event(GameEvent(type="ready", payload={}, actor_id=civilians[0]))
    while engine.phase == EnginePhase.SPEAKING:
        current_id = engine.state.get("current_player_id")
        engine.handle_event(
            GameEvent(type="submit_speech", payload={"content": "描述一下我的词"}, actor_id=current_id)
        )

    assert engine.phase == EnginePhase.VOTING

    # 两个平民合力投出卧底
    for voter in civilians:
        engine.handle_event(GameEvent(type="submit_vote", payload={"target_id": undercover_id}, actor_id=voter))
    # 卧底反投任意平民
    engine.handle_event(
        GameEvent(type="submit_vote", payload={"target_id": civilians[0]}, actor_id=undercover_id)
    )

    assert engine.phase == EnginePhase.RESULT
    assert engine.state.get("winner") == "civilian"

    session.state = engine.serialize_state()
    session.current_phase = engine.phase.value
    session.round_number = engine.state.get("round", 1)
    session.current_player_id = engine.state.get("current_player_id")
    session.save(update_fields=["state", "current_phase", "round_number", "current_player_id"])

    viewer = room_with_players.players.filter(user__username="player2").first().user
    public_state = serialize_session_for_user(session, user=viewer)
    assignments_view = public_state["state"]["assignments"]
    self_entry = next(item for item in assignments_view if item["playerId"] == viewer.room_memberships.first().id)
    assert self_entry["word"] != ""
    others = [item for item in assignments_view if item["playerId"] != self_entry["playerId"]]
    assert all(entry["word"] != "" for entry in others)  # 结果阶段全部公开

