"""Concrete engine for the 谁是卧底 party game."""

from __future__ import annotations

import logging
import random
from collections import Counter
from typing import Any, Dict, List, Optional

from django.db import transaction
from django.utils import timezone

from apps.ai import UndercoverAIStrategy
from apps.ai.llm import get_llm_client
from apps.common import ContentPolicyViolation, enforce_content_policy
from apps.gamecore.engine import BaseGameEngine, EnginePhase, GameEngineError
from apps.rooms.models import RoomPlayer

from ..models import WordPair


LOGGER = logging.getLogger(__name__)


class UndercoverEngine(BaseGameEngine):
    """State machine for managing speaking, voting and result phases."""

    engine_slug = "undercover"

    def __init__(self, session):
        super().__init__(session)
        self._strategy_cache: Dict[str, UndercoverAIStrategy] = {}
        self._llm_enabled = bool(self._config().get("use_llm"))
        self._llm_client = None

    # ------------------------------------------------------------------
    # Configuration helpers
    # ------------------------------------------------------------------
    def _config(self) -> Dict[str, Any]:
        config = self.room.config or {}
        return config.get("undercover", config.get("game", {}).get("undercover", {}))

    def _timer_config(self) -> Dict[str, Any]:
        cfg = self._config()
        timers = cfg.get("timers", {})
        return timers if isinstance(timers, dict) else {}

    def _phase_duration(self, key: str, default: int) -> int:
        value = self._timer_config().get(key)
        try:
            return int(value)
        except (TypeError, ValueError):
            return default

    def _word_preferences(self) -> Dict[str, Optional[str]]:
        cfg = self._config()
        return {
            "topic": cfg.get("topic"),
            "difficulty": cfg.get("difficulty"),
        }

    def _strategy_for_style(self, style: Optional[str]) -> UndercoverAIStrategy:
        resolved = style or self._config().get("ai_style", "balanced")
        client = None
        if self._llm_enabled:
            if self._llm_client is None:
                self._llm_client = get_llm_client()
            client = self._llm_client
        cache_key = f"{resolved}:{'llm' if client else 'fallback'}"
        if cache_key not in self._strategy_cache:
            self._strategy_cache[cache_key] = UndercoverAIStrategy(style=resolved, llm_client=client)
        return self._strategy_cache[cache_key]

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
                    "ai_style": player.ai_style,
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
            "ai_vote_reveals": [],
            "vote_round": 1,
            "revote_candidates": [],
            "history": [],
            "word_pair": {
                "topic": pair.topic,
                "difficulty": pair.difficulty,
            },
        }
        self._queue_reset()
        self.enter_phase(
            EnginePhase.PREPARING,
            duration=self._phase_duration("preparing", 30),
            default_action={"type": "auto_start"},
            description="超时后系统将自动开始发言阶段",
        )

    # ------------------------------------------------------------------
    # Event handling
    # ------------------------------------------------------------------
    def handle_event(self, event) -> None:
        if event.type == "ready":
            self._ensure_phase(EnginePhase.PREPARING)
            self._transition_to_speaking(auto=False)
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
            self.clear_timer()
            return

        if event.type == "timeout":
            self._handle_timeout(event.payload or {})
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
                strategy = self._strategy_for_style(assignment.get("ai_style"))
                speech = strategy.generate_speech(
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
                    voter_strategy = self._strategy_for_style(self._assignment(voter).get("ai_style"))
                    decision = voter_strategy.pick_vote(
                        voter_id=voter,
                        alive_players=self._eligible_vote_targets(),
                        assignments=self.state["assignments"],
                        history=self.state.get("speeches", []),
                    )
                    target = decision.target
                    reason = decision.reason
                    if target not in self._eligible_vote_targets() or target == voter:
                        alternatives = [pid for pid in self._eligible_vote_targets() if pid != voter]
                        fallback = random.choice(alternatives) if alternatives else voter
                        LOGGER.debug(
                            "Undercover AI %s attempted invalid vote target %s, fallback to %s",
                            voter,
                            target,
                            fallback,
                        )
                        target = fallback
                    if reason:
                        LOGGER.info(
                            "Undercover AI %s voting for %s: %s",
                            voter,
                            target,
                            reason,
                        )
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
        reveal_roles = self.phase in {EnginePhase.RESULT, EnginePhase.ENDED} or viewer_player_id is None
        for player_id_str, meta in assignments_map.items():
            pid = int(player_id_str)
            entry = {
                "playerId": pid,
                "displayName": meta["display_name"],
                "isAi": meta["is_ai"],
                "isAlive": meta.get("is_alive", True),
                "aiStyle": meta.get("ai_style"),
            }
            entry["role"] = meta.get("role") if reveal_roles else None
            if reveal_roles or pid == viewer_player_id:
                entry["word"] = meta.get("word")
            else:
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

        reveals = []
        for reveal in self.state.get("ai_vote_reveals", []):
            try:
                player_id = int(reveal.get("player_id"))
                target_id = int(reveal.get("target_id"))
            except (TypeError, ValueError):
                continue
            reveals.append(
                {
                    "playerId": player_id,
                    "targetId": target_id,
                    "timestamp": reveal.get("timestamp"),
                }
            )
        state["ai_vote_reveals"] = reveals

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
        if self.phase == EnginePhase.SPEAKING:
            self._arm_speaking_timer()

    def _advance_queue(self):
        queue = self.state.get("queue", [])
        if queue:
            queue.pop(0)
        if queue:
            self.state["current_player_id"] = queue[0]
        else:
            self.state["current_player_id"] = None
        if self.phase == EnginePhase.SPEAKING:
            self._arm_speaking_timer()

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

    def _ensure_current_speaker(self, actor_id: Optional[int]) -> None:
        if actor_id is None:
            raise GameEngineError("缺少发言玩家信息")
        queue = self.state.get("queue", [])
        if not queue or queue[0] != actor_id:
            raise GameEngineError("当前并非该玩家的发言回合")

    def _sanitize_speech_content(self, content: str, *, is_ai: bool) -> str:
        raw = (content or "").strip()
        try:
            sanitized = enforce_content_policy(raw, mode="mask" if is_ai else "reject")
        except ContentPolicyViolation as exc:
            raise GameEngineError(str(exc)) from exc
        sanitized = sanitized or ("AI" if is_ai else "")
        if not sanitized:
            raise GameEngineError("发言内容不能为空")
        return sanitized

    def _append_speech(self, actor_id: int, *, content: str, is_ai: bool, timestamp: str) -> None:
        self.state.setdefault("speeches", []).append(
            {
                "player_id": actor_id,
                "content": content,
                "is_ai": is_ai,
                "timestamp": timestamp,
            }
        )

    def _emit_streaming_speech(self, actor_id: int, *, content: str, timestamp: str) -> None:
        partial = ""
        for index, char in enumerate(content):
            partial += char
            self.emit_event(
                "undercover.speech_stream",
                {
                    "playerId": actor_id,
                    "chunk": char,
                    "content": partial,
                    "index": index,
                    "timestamp": timestamp,
                    "isAi": True,
                    "done": index == len(content) - 1,
                },
            )

    def _process_speech(self, actor_id: Optional[int], content: str, *, is_ai: bool = False) -> None:
        self._ensure_current_speaker(actor_id)
        sanitized = self._sanitize_speech_content(content, is_ai=is_ai)
        timestamp = timezone.now().isoformat()
        if is_ai:
            self._emit_streaming_speech(actor_id, content=sanitized, timestamp=timestamp)
        self._append_speech(actor_id, content=sanitized, is_ai=is_ai, timestamp=timestamp)
        self._advance_queue()
        if self.state.get("current_player_id") is None:
            self.phase = EnginePhase.VOTING
            self.state["phase"] = self.phase.value
            self.state["votes"] = {}
            self.state["ai_vote_reveals"] = []
            self.state["vote_round"] = self.state.get("vote_round", 1)
            self.state["revote_candidates"] = []
            self._arm_voting_timer()

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

        vote_target = int(target_id)
        self.state.setdefault("votes", {})[str(actor_id)] = vote_target
        if is_ai:
            timestamp = timezone.now().isoformat()
            self.state.setdefault("ai_vote_reveals", []).append(
                {
                    "player_id": actor_id,
                    "target_id": vote_target,
                    "timestamp": timestamp,
                }
            )
            try:
                player_meta = self._assignment(actor_id)
                target_meta = self._assignment(vote_target)
            except GameEngineError:
                player_meta = {"display_name": str(actor_id)}
                target_meta = {"display_name": str(vote_target)}
            self.emit_event(
                "undercover.vote_cast",
                {
                    "playerId": actor_id,
                    "targetId": vote_target,
                    "timestamp": timestamp,
                    "playerName": player_meta.get("display_name"),
                    "targetName": target_meta.get("display_name"),
                },
            )
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
            self._arm_voting_timer()
            return

        eliminated = leaders[0]
        self._eliminate_player(eliminated)
        winner = self._check_winner()
        if winner:
            self.phase = EnginePhase.RESULT
            self.state["phase"] = self.phase.value
            self.state["winner"] = winner
            self.state["current_player_id"] = None
            self.clear_timer()
            return

        # Next round
        self.state["round"] = self.state.get("round", 1) + 1
        self.state["speeches"] = []
        self.state["votes"] = {}
        self.state["vote_round"] = 1
        self.state["revote_candidates"] = []
        self.state["ai_vote_reveals"] = []
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

    # ------------------------------------------------------------------
    # Timeout helpers
    # ------------------------------------------------------------------
    def _arm_speaking_timer(self, *, auto: bool = False) -> None:
        if self.phase != EnginePhase.SPEAKING:
            return
        current = self.state.get("current_player_id")
        if current is None:
            self.clear_timer()
            return
        self.enter_phase(
            EnginePhase.SPEAKING,
            duration=self._phase_duration("speaking", 60),
            default_action={"type": "auto_speech", "player_id": current},
            description="超时将自动跳过当前玩家的发言",
            metadata={"auto": auto, "current": current},
        )

    def _arm_voting_timer(self) -> None:
        if self.phase != EnginePhase.VOTING:
            return
        pending = [pid for pid in self._alive_player_ids() if str(pid) not in self.state.get("votes", {})]
        if not pending:
            self.clear_timer()
            return
        self.enter_phase(
            EnginePhase.VOTING,
            duration=self._phase_duration("voting", 45),
            default_action={"type": "auto_vote"},
            description="超时后未投票玩家将由系统自动投票",
            metadata={"pending": pending},
        )

    def _transition_to_speaking(self, *, auto: bool) -> None:
        self.phase = EnginePhase.SPEAKING
        self.state["phase"] = self.phase.value
        self._queue_reset()
        self._arm_speaking_timer(auto=auto)

    def _record_timeout(self, *, phase: EnginePhase, metadata: Optional[Dict[str, Any]] = None) -> None:
        self.state.setdefault("history", []).append(
            {
                "type": "timeout",
                "phase": phase.value,
                "round": self.state.get("round", 1),
                "timestamp": timezone.now().isoformat(),
                "metadata": metadata or {},
            }
        )

    def _handle_timeout(self, payload: Dict[str, Any]) -> None:
        metadata = payload.get("timer") or self.state.get("timer") or {}
        self._record_timeout(phase=self.phase, metadata=metadata)
        if self.phase == EnginePhase.PREPARING:
            self._transition_to_speaking(auto=True)
            return
        if self.phase == EnginePhase.SPEAKING:
            current = self.state.get("current_player_id")
            if current is None:
                self._arm_voting_timer()
                return
            defaults = self.state.setdefault("default_actions", {})
            defaults[str(current)] = defaults.get(str(current), 0) + 1
            default_content = payload.get("default_content") or "（系统）玩家超时未发言"
            self._process_speech(current, default_content, is_ai=True)
            return
        if self.phase == EnginePhase.VOTING:
            pending = [
                pid
                for pid in self._alive_player_ids()
                if str(pid) not in self.state.get("votes", {})
            ]
            for voter in pending:
                defaults = self.state.setdefault("default_actions", {})
                defaults[str(voter)] = defaults.get(str(voter), 0) + 1
                target = self._pick_default_vote(voter)
                self._process_vote(voter, target, is_ai=True)

    def _pick_default_vote(self, voter: int) -> int:
        candidates = [pid for pid in self._eligible_vote_targets() if pid != voter]
        if not candidates:
            return voter
        return random.choice(candidates)

