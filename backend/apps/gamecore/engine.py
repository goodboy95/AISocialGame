"""Base classes shared by concrete game engines."""

from __future__ import annotations

import copy
from dataclasses import dataclass
from enum import Enum
from typing import Any, Dict, Optional

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
            "updated_at": timezone.now(),
        }

