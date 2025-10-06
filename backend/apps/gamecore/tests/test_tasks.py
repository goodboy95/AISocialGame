import datetime

import pytest
from django.utils import timezone

from apps.gamecore.models import GameSession
from apps.gamecore.tasks import process_timer_expiry, schedule_session_timer
from apps.rooms.models import Room
from apps.users.models import User


@pytest.fixture
def room(db):
    owner = User.objects.create_user(username="owner", password="pass12345", email="owner@example.com")
    return Room.objects.create(name="Timer Room", code="TMR001", owner=owner)


@pytest.mark.django_db
def test_process_timer_expiry_triggers_timeout(monkeypatch, room):
    session = GameSession.objects.create(
        room=room,
        engine="undercover",
        deadline_at=timezone.now() - datetime.timedelta(seconds=5),
        timer_context={"phase": "speaking"},
    )

    captured = {}

    def fake_handle_room_event(*, room, actor, event_type, payload):
        captured.update(
            {
                "room": room,
                "actor": actor,
                "event_type": event_type,
                "payload": payload,
            }
        )

    monkeypatch.setattr("apps.gamecore.services.handle_room_event", fake_handle_room_event)

    result = process_timer_expiry(session.id)

    assert result is True
    assert captured["room"] == room
    assert captured["actor"] is None
    assert captured["event_type"] == "timeout"
    assert captured["payload"] == {"timer": {"phase": "speaking"}}


@pytest.mark.django_db
def test_process_timer_expiry_ignored_when_not_due(room):
    session = GameSession.objects.create(
        room=room,
        engine="undercover",
        deadline_at=timezone.now() + datetime.timedelta(seconds=30),
        timer_context={"phase": "voting"},
    )

    assert process_timer_expiry(session.id) is False


@pytest.mark.django_db
def test_schedule_session_timer(monkeypatch, room):
    session = GameSession.objects.create(
        room=room,
        engine="undercover",
        deadline_at=timezone.now() + datetime.timedelta(seconds=60),
    )

    calls = []

    def fake_apply_async(*args, **kwargs):
        calls.append(
            {
                "args": kwargs.get("args") or (args[1] if len(args) > 1 else None),
                "eta": kwargs.get("eta"),
                "countdown": kwargs.get("countdown"),
            }
        )

    monkeypatch.setattr(process_timer_expiry, "apply_async", fake_apply_async)

    schedule_session_timer(session)

    assert calls, "apply_async should be invoked"
    assert calls[0]["args"] == [session.id]
    assert isinstance(calls[0]["eta"], datetime.datetime)
    assert calls[0]["countdown"] is None

    # When the deadline has passed we expect an immediate countdown task
    session.deadline_at = timezone.now() - datetime.timedelta(seconds=2)
    calls.clear()

    schedule_session_timer(session)

    assert calls[0]["countdown"] == 1
