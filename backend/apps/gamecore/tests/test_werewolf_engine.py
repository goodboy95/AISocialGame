import random

import pytest

from apps.gamecore.engine import EnginePhase, GameEvent
from apps.gamecore.models import GameSession
from apps.games.werewolf.engine import WerewolfEngine
from apps.rooms.models import Room, RoomPlayer
from apps.users.models import User


@pytest.fixture
def werewolf_room(db):
    owner = User.objects.create_user(username="alpha", password="pass12345", email="a@example.com")
    room = Room.objects.create(
        name="狼人杀测试房",
        code="WWTEST",
        owner=owner,
        max_players=8,
        config={
            "game": {
                "engine": "werewolf",
                "werewolf": {"roles": {"werewolf": 2, "seer": 1, "witch": 1}}
            }
        },
    )
    RoomPlayer.objects.create(room=room, user=owner, seat_number=1, is_host=True, display_name="房主")
    for index in range(2, 7):
        user = User.objects.create_user(
            username=f"player{index}", password="pass12345", email=f"p{index}@example.com"
        )
        RoomPlayer.objects.create(room=room, user=user, seat_number=index, display_name=f"玩家{index}")
    return room


@pytest.mark.django_db
def test_werewolf_engine_full_cycle(werewolf_room):
    random.seed(11)
    session = GameSession.objects.create(room=werewolf_room, engine="werewolf")
    engine = WerewolfEngine(session)
    engine.start_game()

    assignments = engine.state["assignments"]
    assert len(assignments) == werewolf_room.players.filter(is_active=True).count()

    wolves = [int(pid) for pid, meta in assignments.items() if meta["role"] == "werewolf"]
    seer_id = next((int(pid) for pid, meta in assignments.items() if meta["role"] == "seer"), None)
    witch_id = next((int(pid) for pid, meta in assignments.items() if meta["role"] == "witch"), None)
    villagers = [int(pid) for pid, meta in assignments.items() if meta["role"] == "villager"]

    assert len(wolves) == 2
    assert seer_id is not None
    assert witch_id is not None
    assert villagers

    # Night: wolves select a victim
    engine.handle_event(
        GameEvent(type="submit_wolf_target", payload={"target_id": villagers[0]}, actor_id=wolves[0])
    )
    assert engine.state["stage"] in {"night.seer", "night.witch", "day.discussion"}

    if engine.state["stage"] == "night.seer":
        engine.handle_event(
            GameEvent(type="submit_seer_target", payload={"target_id": wolves[1]}, actor_id=seer_id)
        )
        assert engine.state["stage"] in {"night.witch", "day.discussion"}

    if engine.state["stage"] == "night.witch":
        engine.handle_event(GameEvent(type="submit_witch_action", payload={}, actor_id=witch_id))

    assert engine.state["stage"] == "day.discussion"
    assert engine.phase == EnginePhase.DAY

    # Confirm the selected villager was eliminated during the night
    night_result = engine.state.get("last_result", {})
    assert villagers[0] in night_result.get("nightKilled", [])

    # Day discussion: iterate speeches for each alive player
    while engine.state["stage"] == "day.discussion":
        current_id = engine.state.get("current_player_id")
        engine.handle_event(
            GameEvent(type="submit_speech", payload={"content": f"我是玩家{current_id}"}, actor_id=current_id)
        )

    assert engine.state["stage"] == "day.vote"

    alive_after_night = [
        int(pid)
        for pid, meta in engine.state["assignments"].items()
        if meta["is_alive"]
    ]
    # 所有好人将第一位狼人投出局
    target_wolf = wolves[0]
    for pid in alive_after_night:
        engine.handle_event(
            GameEvent(type="submit_vote", payload={"target_id": target_wolf}, actor_id=pid)
        )

    assert engine.state["stage"] in {"night.wolves", "end"}
    assert engine.state["assignments"][str(target_wolf)]["is_alive"] is False

    if engine.state.get("winner"):
        assert engine.phase == EnginePhase.ENDED
    else:
        assert engine.phase == EnginePhase.NIGHT

    # Verify public state sanitization
    seer_player = werewolf_room.players.get(pk=seer_id)
    villager_player = werewolf_room.players.get(pk=villagers[1])
    seer_state = engine.get_public_state(for_user=seer_player.user)
    villager_state = engine.get_public_state(for_user=villager_player.user)

    assert seer_state["private"].get("role") == "seer"
    assert villager_state["private"].get("role") == "villager"
    visible_roles = {entry["playerId"]: entry["role"] for entry in villager_state["assignments"]}
    for wid in wolves:
        if wid == target_wolf:
            assert visible_roles.get(wid) == "werewolf"
        else:
            assert visible_roles.get(wid) in {None, "werewolf"}
