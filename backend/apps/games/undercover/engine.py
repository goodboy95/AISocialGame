"""Concrete engine for the 谁是卧底 party game."""

from __future__ import annotations

import random
from collections import Counter
from typing import Any, Dict, List, Optional

from django.db import transaction
from django.utils import timezone

from apps.ai.services import UndercoverAIStrategy
from apps.gamecore.engine import BaseGameEngine, EnginePhase, GameEngineError
from apps.rooms.models import RoomPlayer

from ..models import WordPair


class UndercoverEngine(BaseGameEngine):
    """State machine for managing speaking, voting and result phases."""

    engine_slug = "undercover"

    def __init__(self, session):
        super().__init__(session)
        self.strategy = UndercoverAIStrategy(style=self._config().get("ai_style", "balanced"))

    # ------------------------------------------------------------------
    # Configuration helpers
    # ------------------------------------------------------------------
    def _config(self) -> Dict[str, Any]:
        config = self.room.config or {}
        return config.get("undercover", config.get("game", {}).get("undercover", {}))

    def _word_preferences(self) -> Dict[str, Optional[str]]:
        cfg = self._config()
        return {
            "topic": cfg.get("topic"),
            "difficulty": cfg.get("difficulty"),
        }

    # ------------------------------------------------------------------
    # Lifecycle
    # ------------------------------------------------------------------
    def start_game(self) -> None:
        players = list(
            self.room.players.filter(is_active=True).select_for_update().order_by("seat_number")
        )
        if len(players) < 3:
            raise GameEngineError("至少需要 3 名玩家才能开始游戏")

        cfg = self._config()
        undercover_count = max(1, int(cfg.get("undercover_count", 1)))
        blank_count = max(0, int(cfg.get("blank_count", 0)))
        if undercover_count + blank_count >= len(players):
            raise GameEngineError("卧底与白板数量过多，请调整配置")

        try:
            pair = WordPair.pick_random(**self._word_preferences())
        except WordPair.DoesNotExist as exc:  # pragma: no cover - validated in tests
            raise GameEngineError("词库中暂无合适的词对，请先在后台配置") from exc

        speaking_order = [player.id for player in players]
        random.shuffle(speaking_order)

        assignments: Dict[str, Dict[str, Any]] = {}
        roles_pool: List[str] = (
            ["undercover"] * undercover_count
            + ["blank"] * blank_count
            + ["civilian"] * (len(players) - undercover_count - blank_count)
        )
        random.shuffle(roles_pool)

        with transaction.atomic():
            updates: List[RoomPlayer] = []
            for player, role in zip(players, roles_pool):
                word = pair.undercover_word if role == "undercover" else pair.civilian_word
                if role == "blank":
                    word = ""
                player.role = role
                player.word = word
                player.is_alive = True
                updates.append(player)
                assignments[str(player.id)] = {
                    "player_id": player.id,
                    "role": role,
                    "word": word,
                    "is_ai": player.is_ai,
                    "display_name": player.resolved_display_name,
                    "is_alive": True,
                }
            RoomPlayer.objects.bulk_update(updates, ["role", "word", "is_alive"])

        self.phase = EnginePhase.PREPARING
        self.state = {
            "phase": self.phase.value,
            "round": 1,
            "current_player_id": None,
            "players_order": speaking_order,
            "assignments": assignments,
            "alive_players": [pid for pid in speaking_order],
            "speeches": [],
            "votes": {},
            "vote_round": 1,
            "revote_candidates": [],
            "history": [],
            "word_pair": {
                "topic": pair.topic,
                "difficulty": pair.difficulty,
            },
        }
        self._queue_reset()

    # ------------------------------------------------------------------
    # Event handling
    # ------------------------------------------------------------------
    def handle_event(self, event) -> None:
        if event.type == "ready":
            self._ensure_phase(EnginePhase.PREPARING)
            self.phase = EnginePhase.SPEAKING
            self.state["phase"] = self.phase.value
            self._queue_reset()
            return

        if event.type == "submit_speech":
            self._ensure_phase(EnginePhase.SPEAKING)
            self._process_speech(event.actor_id, event.payload.get("content", ""))
            return

        if event.type == "submit_vote":
            self._ensure_phase(EnginePhase.VOTING)
            self._process_vote(event.actor_id, event.payload.get("target_id"))
            return

        if event.type == "force_result":
            self.phase = EnginePhase.RESULT
            self.state["phase"] = self.phase.value
            return

        raise GameEngineError(f"未知的事件类型: {event.type}")

    # ------------------------------------------------------------------
    # Automatic behaviors
    # ------------------------------------------------------------------
    def run_auto_actions(self) -> bool:
        changed = False
        while True:
            if self.phase == EnginePhase.SPEAKING and self._current_is_ai():
                speaker_id = self.state.get("current_player_id")
                if speaker_id is None:
                    break
                assignment = self._assignment(speaker_id)
                speech = self.strategy.generate_speech(
                    role=assignment["role"],
                    word=assignment["word"],
                    round_number=self.state.get("round", 1),
                    history=self.state.get("speeches", []),
                )
                self._process_speech(speaker_id, speech or "我先简单说两句。", is_ai=True)
                changed = True
                continue

            if self.phase == EnginePhase.VOTING:
                pending = [
                    pid
                    for pid in self._alive_player_ids()
                    if self._assignment(pid)["is_ai"] and str(pid) not in self.state.get("votes", {})
                ]
                if pending:
                    voter = pending[0]
                    target = self.strategy.pick_vote(
                        voter_id=voter,
                        alive_players=self._eligible_vote_targets(),
                        assignments=self.state["assignments"],
                        history=self.state.get("speeches", []),
                    )
                    if target not in self._eligible_vote_targets() or target == voter:
                        alternatives = [pid for pid in self._eligible_vote_targets() if pid != voter]
                        target = random.choice(alternatives) if alternatives else voter
                    self._process_vote(voter, target, is_ai=True)
                    changed = True
                    continue
            break
        return changed

    # ------------------------------------------------------------------
    # Serialization helpers
    # ------------------------------------------------------------------
    def get_public_state(self, *, for_user=None):
        state = super().serialize_state()
        viewer_player_id = None
        if for_user:
            membership = self.room.players.filter(user=for_user, is_active=True).first()
            if membership:
                viewer_player_id = membership.id

        assignments_map = self.state.get("assignments", {})
        public_assignments = []
        for player_id_str, meta in assignments_map.items():
            pid = int(player_id_str)
            entry = {
                "playerId": pid,
                "displayName": meta["display_name"],
                "isAi": meta["is_ai"],
                "isAlive": meta.get("is_alive", True),
            }
            if viewer_player_id is None or pid == viewer_player_id or self.phase in {EnginePhase.RESULT, EnginePhase.ENDED}:
                entry["role"] = meta.get("role")
                entry["word"] = meta.get("word")
            else:
                entry["role"] = None
                entry["word"] = None
            public_assignments.append(entry)

        state["assignments"] = public_assignments
        votes = self.state.get("votes", {})
        tally = Counter(votes.values()) if votes else Counter()
        state["voteSummary"] = {
            "submitted": len(votes),
            "required": len(self._alive_player_ids()),
            "tally": {int(k): v for k, v in tally.items()},
        }
        if viewer_player_id is not None and str(viewer_player_id) in votes:
            state["voteSummary"]["selfTarget"] = votes[str(viewer_player_id)]

        pair = self.state.get("word_pair", {})
        state["word_pair"] = {
            "topic": pair.get("topic"),
            "difficulty": pair.get("difficulty"),
        }
        if viewer_player_id is not None and str(viewer_player_id) in assignments_map:
            state["word_pair"]["selfWord"] = assignments_map[str(viewer_player_id)]["word"]

        return state

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------
    def _ensure_phase(self, phase: EnginePhase) -> None:
        if self.phase != phase:
            raise GameEngineError(f"当前阶段无法执行该操作（需要 {phase.value}，当前 {self.phase.value}）")

    def _assignment(self, player_id: int) -> Dict[str, Any]:
        try:
            return self.state["assignments"][str(player_id)]
        except KeyError as exc:  # pragma: no cover - defensive
            raise GameEngineError("未知玩家") from exc

    def _queue_reset(self):
        alive = [pid for pid in self.state.get("players_order", []) if self._assignment(pid)["is_alive"]]
        self.state["alive_players"] = alive
        self.state["queue"] = list(alive)
        self.state["current_player_id"] = alive[0] if alive else None

    def _advance_queue(self):
        queue = self.state.get("queue", [])
        if queue:
            queue.pop(0)
        if queue:
            self.state["current_player_id"] = queue[0]
        else:
            self.state["current_player_id"] = None

    def _alive_player_ids(self) -> List[int]:
        return [pid for pid in self.state.get("players_order", []) if self._assignment(pid)["is_alive"]]

    def _current_is_ai(self) -> bool:
        current = self.state.get("current_player_id")
        if current is None:
            return False
        return bool(self._assignment(current)["is_ai"])

    def _eligible_vote_targets(self) -> List[int]:
        allowed = self.state.get("revote_candidates") or self._alive_player_ids()
        return [int(pid) for pid in allowed]

    def _process_speech(self, actor_id: Optional[int], content: str, *, is_ai: bool = False) -> None:
        if actor_id is None:
            raise GameEngineError("缺少发言玩家信息")
        queue = self.state.get("queue", [])
        if not queue or queue[0] != actor_id:
            raise GameEngineError("当前并非该玩家的发言回合")
        sanitized = content.strip() or ("AI" if is_ai else "")
        if not sanitized:
            raise GameEngineError("发言内容不能为空")
        self.state.setdefault("speeches", []).append(
            {
                "player_id": actor_id,
                "content": sanitized,
                "is_ai": is_ai,
                "timestamp": timezone.now().isoformat(),
            }
        )
        self._advance_queue()
        if self.state.get("current_player_id") is None:
            self.phase = EnginePhase.VOTING
            self.state["phase"] = self.phase.value
            self.state["votes"] = {}
            self.state["vote_round"] = self.state.get("vote_round", 1)
            self.state["revote_candidates"] = []

    def _process_vote(self, actor_id: Optional[int], target_id: Optional[int], *, is_ai: bool = False) -> None:
        if actor_id is None:
            raise GameEngineError("缺少投票玩家")
        if target_id is None:
            raise GameEngineError("需要指定投票目标")
        if str(actor_id) in self.state.get("votes", {}):
            raise GameEngineError("已完成投票，无法重复操作")
        if actor_id not in self._alive_player_ids():
            raise GameEngineError("被淘汰的玩家无法投票")
        if target_id not in self._eligible_vote_targets():
            raise GameEngineError("投票目标不合法")

        self.state.setdefault("votes", {})[str(actor_id)] = int(target_id)
        if len(self.state["votes"]) >= len(self._alive_player_ids()):
            self._finalize_votes()

    def _finalize_votes(self):
        votes = self.state.get("votes", {})
        tally = Counter(votes.values())
        if not tally:
            return
        highest = max(tally.values())
        leaders = [candidate for candidate, count in tally.items() if count == highest]
        self.state.setdefault("history", []).append(
            {
                "round": self.state.get("round", 1),
                "votes": {int(k): v for k, v in tally.items()},
                "vote_round": self.state.get("vote_round", 1),
            }
        )
        if len(leaders) > 1:
            self.state["revote_candidates"] = leaders
            self.state["votes"] = {}
            self.state["vote_round"] = self.state.get("vote_round", 1) + 1
            return

        eliminated = leaders[0]
        self._eliminate_player(eliminated)
        winner = self._check_winner()
        if winner:
            self.phase = EnginePhase.RESULT
            self.state["phase"] = self.phase.value
            self.state["winner"] = winner
            self.state["current_player_id"] = None
            return

        # Next round
        self.state["round"] = self.state.get("round", 1) + 1
        self.state["speeches"] = []
        self.state["votes"] = {}
        self.state["vote_round"] = 1
        self.state["revote_candidates"] = []
        self.phase = EnginePhase.SPEAKING
        self.state["phase"] = self.phase.value
        self._queue_reset()

    def _eliminate_player(self, player_id: int) -> None:
        assignment = self._assignment(player_id)
        assignment["is_alive"] = False
        RoomPlayer.objects.filter(pk=player_id).update(is_alive=False)

    def _check_winner(self) -> Optional[str]:
        alive_assignments = [self._assignment(pid) for pid in self._alive_player_ids()]
        undercovers = [meta for meta in alive_assignments if meta["role"] == "undercover"]
        civilians = [meta for meta in alive_assignments if meta["role"] != "undercover"]
        if not undercovers:
            return "civilian"
        if len(undercovers) >= len(civilians):
            return "undercover"
        return None

