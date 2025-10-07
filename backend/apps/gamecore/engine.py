"""Base classes shared by concrete game engines."""

from __future__ import annotations

import copy
from dataclasses import dataclass
from datetime import datetime, timedelta
from enum import Enum
from typing import Any, Dict, List, Optional

from django.utils import timezone

from .models import GameSession


class GameEngineError(Exception):
    """Base exception for engine level failures."""


class EnginePhase(str, Enum):
    """Common lifecycle phases used by turn-based party games."""

    PREPARING = "preparing"
    SPEAKING = "speaking"
    VOTING = "voting"
    RESULT = "result"
    ENDED = "ended"
    NIGHT = "night"
    DAY = "day"


@dataclass
class GameEvent:
    """Normalized event payload consumed by engines."""

    type: str
    payload: Dict[str, Any]
    actor_id: Optional[int] = None


class BaseGameEngine:
    """Minimal interface every game implementation must follow."""

    engine_slug: str = "base"

    def __init__(self, session: GameSession):
        self.session = session
        self.room = session.room
        self.state: Dict[str, Any] = copy.deepcopy(session.state or {})
        self.phase = EnginePhase(self.state.get("phase", session.current_phase or EnginePhase.PREPARING.value))
        self._deadline_at: Optional[datetime] = session.deadline_at
        self._timer_context: Dict[str, Any] = copy.deepcopy(session.timer_context or {})
        if self._timer_context:
            self.state.setdefault("timer", copy.deepcopy(self._timer_context))
        else:
            self.state.pop("timer", None)
        self._timer_dirty = False
        self._pending_events: List[Dict[str, Any]] = []

    # ------------------------------------------------------------------
    # Lifecycle hooks
    # ------------------------------------------------------------------
    def start_game(self) -> None:
        """Initialize engine state. Must be implemented by subclasses."""

        raise NotImplementedError

    def handle_event(self, event: GameEvent) -> None:
        """Process a user or system event and mutate engine state."""

        raise NotImplementedError

    def run_auto_actions(self) -> bool:
        """Allow engines to enqueue automatic behaviors (AI turns, timers)."""

        return False

    # ------------------------------------------------------------------
    # Timer helpers
    # ------------------------------------------------------------------
    def enter_phase(
        self,
        phase: EnginePhase,
        *,
        duration: Optional[int] = None,
        default_action: Optional[Dict[str, Any]] = None,
        description: Optional[str] = None,
        metadata: Optional[Dict[str, Any]] = None,
    ) -> None:
        """Switch to a new phase and optionally arm a countdown timer."""

        self.phase = phase
        self.state["phase"] = self.phase.value
        if duration is not None and duration > 0:
            deadline = timezone.now() + timedelta(seconds=int(duration))
            timer_payload: Dict[str, Any] = {
                "phase": self.phase.value,
                "duration": int(duration),
                "expiresAt": deadline.isoformat(),
                "defaultAction": default_action or {},
            }
            if description:
                timer_payload["description"] = description
            if metadata:
                timer_payload["metadata"] = metadata
            self.state["timer"] = timer_payload
            self._deadline_at = deadline
            self._timer_context = copy.deepcopy(timer_payload)
        else:
            self.state.pop("timer", None)
            self._deadline_at = None
            self._timer_context = {}
        self._timer_dirty = True

    def clear_timer(self) -> None:
        """Remove any active timer from the session."""

        self.state.pop("timer", None)
        self._deadline_at = None
        self._timer_context = {}
        self._timer_dirty = True

    # ------------------------------------------------------------------
    # State helpers
    # ------------------------------------------------------------------
    def serialize_state(self) -> Dict[str, Any]:
        """Return the authoritative internal state."""

        data = copy.deepcopy(self.state)
        data["phase"] = self.phase.value
        return data

    def get_public_state(self, *, for_user=None) -> Dict[str, Any]:  # pragma: no cover - interface shim
        """Produce a sanitized state for external consumers."""

        return self.serialize_state()

    def to_session_fields(self) -> Dict[str, Any]:
        """Map internal state to ``GameSession`` persistence fields."""

        state = self.serialize_state()
        return {
            "state": state,
            "current_phase": state.get("phase", EnginePhase.PREPARING.value),
            "current_player_id": state.get("current_player_id"),
            "round_number": state.get("round", self.session.round_number or 1),
            "deadline_at": self._deadline_at,
            "timer_context": self._timer_context,
            "updated_at": timezone.now(),
        }

    # ------------------------------------------------------------------
    # Event helpers
    # ------------------------------------------------------------------
    def emit_event(self, event_type: str, payload: Optional[Dict[str, Any]] = None) -> None:
        """Queue an auxiliary event to be broadcast to room members."""

        if not event_type:
            return
        self._pending_events.append({
            "type": event_type,
            "payload": copy.deepcopy(payload or {}),
        })

    def consume_pending_events(self) -> List[Dict[str, Any]]:
        """Return and clear any queued auxiliary events."""

        events = self._pending_events[:]
        self._pending_events.clear()
        return events

