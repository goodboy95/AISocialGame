"""Simplified Werewolf game engine implementation."""

from __future__ import annotations

import random
from collections import Counter
from typing import Any, Dict, List, Optional

from django.db import transaction
from django.utils import timezone

from apps.ai import WerewolfAIStrategy
from apps.ai.llm import get_llm_client
from apps.common import ContentPolicyViolation, enforce_content_policy
from apps.gamecore.engine import BaseGameEngine, EnginePhase, GameEngineError, GameEvent
from apps.rooms.models import RoomPlayer


class WerewolfEngine(BaseGameEngine):
    """State machine orchestrating a lightweight Werewolf experience."""

    engine_slug = "werewolf"

    def __init__(self, session):
        super().__init__(session)
        self._strategy_cache: Dict[str, WerewolfAIStrategy] = {}
        cfg = self._config()
        self._llm_enabled = bool(cfg.get("use_llm")) if isinstance(cfg, dict) else False
        self._llm_client = None

    # ------------------------------------------------------------------
    # Configuration helpers
    # ------------------------------------------------------------------
    def _config(self) -> Dict[str, Any]:
        config = self.room.config or {}
        game_cfg = config.get("game", {}) if isinstance(config, dict) else {}
        return game_cfg.get("werewolf", config.get("werewolf", {}))

    def _witch_config(self) -> Dict[str, Any]:
        cfg = self._config()
        return cfg.get("witch", {}) if isinstance(cfg, dict) else {}

    def _timer_config(self) -> Dict[str, Any]:
        cfg = self._config()
        timers = cfg.get("timers", {}) if isinstance(cfg, dict) else {}
        return timers if isinstance(timers, dict) else {}

    def _stage_duration(self, key: str, default: int) -> int:
        value = self._timer_config().get(key)
        try:
            return int(value)
        except (TypeError, ValueError):
            return default

    def _role_counts(self, player_count: int) -> Dict[str, int]:
        cfg = self._config()
        roles_cfg = cfg.get("roles", {}) if isinstance(cfg, dict) else {}
        wolves = int(roles_cfg.get("werewolf", max(1, player_count // 4)))
        seer = int(roles_cfg.get("seer", 1 if player_count >= 5 else 0))
        witch = int(roles_cfg.get("witch", 1 if player_count >= 6 else 0))
        if wolves < 1:
            wolves = 1
        wolves = min(wolves, player_count - 1)
        seer = max(0, min(seer, 1))
        witch = max(0, min(witch, 1))
        villager = player_count - wolves - seer - witch
        if villager < 1:
            raise GameEngineError("角色配置不合理，导致村民数量不足")
        return {
            "werewolf": wolves,
            "seer": seer,
            "witch": witch,
            "villager": villager,
        }

    def _strategy_for_style(self, style: Optional[str]) -> WerewolfAIStrategy:
        resolved = style or self._config().get("ai_style", "balanced")
        client = None
        if self._llm_enabled:
            if self._llm_client is None:
                self._llm_client = get_llm_client()
            client = self._llm_client
        cache_key = f"{resolved}:{'llm' if client else 'fallback'}"
        if cache_key not in self._strategy_cache:
            self._strategy_cache[cache_key] = WerewolfAIStrategy(style=resolved, llm_client=client)
        return self._strategy_cache[cache_key]

    # ------------------------------------------------------------------
    # Lifecycle
    # ------------------------------------------------------------------
    def start_game(self) -> None:
        players = list(
            self.room.players.filter(is_active=True).select_for_update().order_by("seat_number")
        )
        if len(players) < 4:
            raise GameEngineError("狼人杀至少需要 4 名玩家")

        counts = self._role_counts(len(players))
        role_pool: List[str] = (
            ["werewolf"] * counts["werewolf"]
            + ["seer"] * counts["seer"]
            + ["witch"] * counts["witch"]
            + ["villager"] * counts["villager"]
        )
        random.shuffle(role_pool)

        assignments: Dict[str, Dict[str, Any]] = {}
        wolves: List[int] = []
        seer_id: Optional[int] = None
        witch_id: Optional[int] = None

        with transaction.atomic():
            updates: List[RoomPlayer] = []
            for player, role in zip(players, role_pool):
                player.role = role
                player.word = ""
                player.is_alive = True
                player.has_used_skill = False
                updates.append(player)
                assignments[str(player.id)] = {
                    "player_id": player.id,
                    "role": role,
                    "is_ai": player.is_ai,
                    "ai_style": player.ai_style,
                    "display_name": player.resolved_display_name,
                    "is_alive": True,
                    "revealed": False,
                }
                if role == "werewolf":
                    wolves.append(player.id)
                elif role == "seer":
                    seer_id = player.id
                elif role == "witch":
                    witch_id = player.id
            RoomPlayer.objects.bulk_update(updates, ["role", "word", "is_alive", "has_used_skill"])

        witch_cfg = self._witch_config()
        self.phase = EnginePhase.NIGHT
        self.state = {
            "phase": self.phase.value,
            "stage": "night.wolves",
            "round": 1,
            "assignments": assignments,
            "players_order": [player.id for player in players],
            "alive_players": [player.id for player in players],
            "wolves": wolves,
            "seer": seer_id,
            "witch": witch_id,
            "witch_potions": {
                "antidote": bool(witch_id) and bool(witch_cfg.get("antidote", True)),
                "poison": bool(witch_id) and bool(witch_cfg.get("poison", True)),
                "allow_double": bool(witch_cfg.get("allow_double", False)),
            },
            "night_actions": {
                "wolves_target": None,
                "seer_target": None,
                "seer_result": None,
                "witch_saved": False,
                "witch_poison_target": None,
            },
            "speeches": [],
            "votes": {},
            "vote_round": 1,
            "revote_candidates": [],
            "last_result": {"nightKilled": [], "lynched": []},
            "seer_history": [],
            "winner": None,
        }
        self.state["current_player_id"] = None
        self._arm_stage_timer("night.wolves")

    # ------------------------------------------------------------------
    # Event handling
    # ------------------------------------------------------------------
    def handle_event(self, event: GameEvent) -> None:
        stage = self.state.get("stage")
        if event.type == "submit_wolf_target":
            self._ensure_stage("night.wolves")
            self._handle_wolf_target(event.actor_id, event.payload.get("target_id"))
            return
        if event.type == "submit_seer_target":
            self._ensure_stage("night.seer")
            self._handle_seer_target(event.actor_id, event.payload.get("target_id"))
            return
        if event.type == "submit_witch_action":
            self._ensure_stage("night.witch")
            self._handle_witch_action(
                event.actor_id,
                use_antidote=event.payload.get("use_antidote", False),
                use_poison=event.payload.get("use_poison", False),
                poison_target=event.payload.get("target_id"),
            )
            return
        if event.type == "submit_speech":
            self._ensure_stage("day.discussion")
            self._process_speech(event.actor_id, event.payload.get("content", ""))
            return
        if event.type == "submit_vote":
            self._ensure_stage("day.vote")
            self._process_vote(event.actor_id, event.payload.get("target_id"))
            return
        if event.type == "force_result":
            self.phase = EnginePhase.ENDED
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
            stage = self.state.get("stage")
            if self.phase == EnginePhase.NIGHT and stage == "night.wolves":
                if self.state.get("night_actions", {}).get("wolves_target") is None:
                    alive = self._alive_player_ids()
                    wolves = [wid for wid in self._wolves() if self._is_alive(wid)]
                    ai_wolves = [wid for wid in wolves if self._assignment(wid)["is_ai"]]
                    if ai_wolves and alive:
                        wolf_assignment = self._assignment(ai_wolves[0])
                        strategy = self._strategy_for_style(wolf_assignment.get("ai_style"))
                        target = strategy.pick_wolf_target(
                            wolves=wolves,
                            alive_players=alive,
                            assignments=self.state["assignments"],
                        )
                        self._handle_wolf_target(ai_wolves[0], target, is_ai=True)
                        changed = True
                        continue
                    if not wolves:
                        self._advance_after_wolves()
                        changed = True
                        continue
            if self.phase == EnginePhase.NIGHT and stage == "night.seer":
                seer = self._seer()
                if not seer or not self._is_alive(seer):
                    self._advance_after_seer()
                    changed = True
                    continue
                if self._assignment(seer)["is_ai"] and self.state.get("night_actions", {}).get("seer_result") is None:
                    strategy = self._strategy_for_style(self._assignment(seer).get("ai_style"))
                    target = strategy.pick_seer_target(
                        seer_id=seer,
                        alive_players=self._alive_player_ids(),
                        known_results=self.state.get("seer_history", []),
                    )
                    self._handle_seer_target(seer, target, is_ai=True)
                    changed = True
                    continue
            if self.phase == EnginePhase.NIGHT and stage == "night.witch":
                witch = self._witch()
                if not witch or not self._is_alive(witch):
                    self._resolve_night()
                    changed = True
                    continue
                actions = self.state.get("night_actions", {})
                if (
                    self._assignment(witch)["is_ai"]
                    and not actions.get("witch_resolved", False)
                ):
                    strategy = self._strategy_for_style(self._assignment(witch).get("ai_style"))
                    decision = strategy.pick_witch_action(
                        pending_kill=actions.get("wolves_target"),
                        potions=self.state.get("witch_potions", {}),
                        alive_players=self._alive_player_ids(),
                        wolves=self._wolves(),
                    )
                    self._handle_witch_action(
                        witch,
                        use_antidote=decision.get("use_antidote", False),
                        use_poison=decision.get("use_poison", False),
                        poison_target=decision.get("poison_target"),
                        is_ai=True,
                    )
                    changed = True
                    continue
            if stage == "day.discussion" and self._current_is_ai():
                current = self.state.get("current_player_id")
                if current is None:
                    break
                assignment = self._assignment(current)
                strategy = self._strategy_for_style(assignment.get("ai_style"))
                speech = strategy.generate_day_speech(
                    role=assignment["role"],
                    round_number=self.state.get("round", 1),
                    last_result=self.state.get("last_result", {}),
                    history=self.state.get("speeches", []),
                )
                self._process_speech(current, speech or "我先简单说两句。", is_ai=True)
                changed = True
                continue
            if stage == "day.vote":
                alive = self._alive_player_ids()
                votes = self.state.get("votes", {})
                pending_ai = [
                    pid
                    for pid in alive
                    if self._assignment(pid)["is_ai"] and str(pid) not in votes
                ]
                if pending_ai:
                    voter = pending_ai[0]
                    voter_assignment = self._assignment(voter)
                    strategy = self._strategy_for_style(voter_assignment.get("ai_style"))
                    target = strategy.pick_vote(
                        voter_id=voter,
                        alive_players=alive,
                        wolves=self._wolves(),
                        assignments=self.state["assignments"],
                    )
                    if target not in alive:
                        others = [pid for pid in alive if pid != voter]
                        target = random.choice(others) if others else voter
                    self._process_vote(voter, target, is_ai=True)
                    changed = True
                    continue
            break
        return changed

    # ------------------------------------------------------------------
    # Serialization helpers
    # ------------------------------------------------------------------
    def get_public_state(self, *, for_user=None) -> Dict[str, Any]:
        state = super().serialize_state()
        viewer_player_id = None
        if for_user:
            membership = self.room.players.filter(user=for_user, is_active=True).first()
            if membership:
                viewer_player_id = membership.id
        assignments_map = self.state.get("assignments", {})
        public_assignments: List[Dict[str, Any]] = []
        for player_id_str, meta in assignments_map.items():
            pid = int(player_id_str)
            entry = {
                "playerId": pid,
                "displayName": meta["display_name"],
                "isAi": meta["is_ai"],
                "isAlive": meta.get("is_alive", True),
                "aiStyle": meta.get("ai_style"),
            }
            if (
                meta.get("revealed")
                or (viewer_player_id is not None and viewer_player_id == pid)
                or self.state.get("winner")
            ):
                entry["role"] = meta.get("role")
            else:
                entry["role"] = None
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

        state["stage"] = self.state.get("stage")
        state["last_result"] = self.state.get("last_result", {})
        state.pop("wolves", None)
        state.pop("seer", None)
        state.pop("witch", None)
        state.pop("witch_potions", None)
        state.pop("night_actions", None)
        state.pop("seer_history", None)

        private_payload: Dict[str, Any] = {}
        if viewer_player_id is not None and str(viewer_player_id) in assignments_map:
            viewer_assignment = assignments_map[str(viewer_player_id)]
            role = viewer_assignment.get("role")
            private_payload["role"] = role
            if role == "werewolf":
                allies = [
                    {
                        "playerId": wid,
                        "displayName": assignments_map[str(wid)]["display_name"],
                        "isAlive": assignments_map[str(wid)]["is_alive"],
                        "isAi": assignments_map[str(wid)]["is_ai"],
                    }
                    for wid in self._wolves()
                    if str(wid) in assignments_map
                ]
                private_payload["wolves"] = {
                    "allies": allies,
                    "selectedTarget": self.state.get("night_actions", {}).get("wolves_target"),
                }
            if role == "seer":
                private_payload["seer"] = {
                    "history": self.state.get("seer_history", []),
                    "lastResult": self.state.get("night_actions", {}).get("seer_result"),
                }
            if role == "witch":
                potions = self.state.get("witch_potions", {})
                private_payload["witch"] = {
                    "antidoteAvailable": potions.get("antidote", False),
                    "poisonAvailable": potions.get("poison", False),
                    "pendingKill": self.state.get("night_actions", {}).get("wolves_target"),
                }
        state["private"] = private_payload
        return state

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------
    def _ensure_stage(self, expected: str) -> None:
        stage = self.state.get("stage")
        if stage != expected:
            raise GameEngineError(f"当前阶段无法执行该操作（需要 {expected}，当前 {stage}）")

    def _assignment(self, player_id: int) -> Dict[str, Any]:
        try:
            return self.state["assignments"][str(player_id)]
        except KeyError as exc:  # pragma: no cover - defensive
            raise GameEngineError("未知玩家") from exc

    def _wolves(self) -> List[int]:
        return list(self.state.get("wolves", []))

    def _seer(self) -> Optional[int]:
        return self.state.get("seer")

    def _witch(self) -> Optional[int]:
        return self.state.get("witch")

    def _is_alive(self, player_id: int) -> bool:
        return bool(self._assignment(player_id).get("is_alive"))

    def _alive_player_ids(self) -> List[int]:
        return [pid for pid in self.state.get("players_order", []) if self._assignment(pid)["is_alive"]]

    def _current_is_ai(self) -> bool:
        current = self.state.get("current_player_id")
        if current is None:
            return False
        return bool(self._assignment(current)["is_ai"])

    def _set_stage(self, stage: str) -> None:
        self.state["stage"] = stage
        self._arm_stage_timer(stage)

    # Night actions -----------------------------------------------------
    def _handle_wolf_target(self, actor_id: Optional[int], target_id: Optional[int], *, is_ai: bool = False) -> None:
        wolves = self._wolves()
        if not wolves:
            self._advance_after_wolves()
            return
        if actor_id is not None and actor_id not in wolves:
            raise GameEngineError("只有狼人可以选择击杀目标")
        if target_id is None:
            raise GameEngineError("需要指定击杀目标")
        if target_id not in self._alive_player_ids():
            raise GameEngineError("目标必须是存活玩家")
        self.state.setdefault("night_actions", {})["wolves_target"] = int(target_id)
        self._advance_after_wolves()

    def _advance_after_wolves(self) -> None:
        seer = self._seer()
        if seer and self._is_alive(seer):
            self._set_stage("night.seer")
        else:
            self._advance_after_seer()

    def _handle_seer_target(self, actor_id: Optional[int], target_id: Optional[int], *, is_ai: bool = False) -> None:
        seer = self._seer()
        if not seer or not self._is_alive(seer):
            self._advance_after_seer()
            return
        if actor_id != seer:
            raise GameEngineError("只有预言家可以执行该操作")
        if target_id is None or target_id not in self._alive_player_ids():
            raise GameEngineError("目标必须是存活玩家")
        result = {
            "player_id": int(target_id),
            "role": self._assignment(target_id)["role"],
            "timestamp": timezone.now().isoformat(),
        }
        actions = self.state.setdefault("night_actions", {})
        actions["seer_target"] = int(target_id)
        actions["seer_result"] = result
        self.state.setdefault("seer_history", []).append(result)
        RoomPlayer.objects.filter(pk=seer).update(has_used_skill=True)
        self._advance_after_seer()

    def _advance_after_seer(self) -> None:
        witch = self._witch()
        if witch and self._is_alive(witch):
            self._set_stage("night.witch")
        else:
            self._resolve_night()

    def _handle_witch_action(
        self,
        actor_id: Optional[int],
        *,
        use_antidote: bool,
        use_poison: bool,
        poison_target: Optional[int],
        is_ai: bool = False,
    ) -> None:
        witch = self._witch()
        if not witch or not self._is_alive(witch):
            self._resolve_night()
            return
        if actor_id != witch:
            raise GameEngineError("只有女巫可以执行该操作")
        actions = self.state.setdefault("night_actions", {})
        potions = self.state.setdefault("witch_potions", {})
        if actions.get("witch_resolved"):
            raise GameEngineError("女巫已经完成操作")
        saved = False
        poisoned: Optional[int] = None
        allow_double = potions.get("allow_double", False)
        if use_antidote:
            if not potions.get("antidote", False):
                raise GameEngineError("解药已用完")
            target = actions.get("wolves_target")
            if target is None:
                raise GameEngineError("当前夜晚没有击杀目标")
            actions["witch_saved"] = True
            potions["antidote"] = False
            saved = True
        if use_poison:
            if not potions.get("poison", False):
                raise GameEngineError("毒药已用完")
            if poison_target is None or poison_target not in self._alive_player_ids():
                raise GameEngineError("需要指定毒药目标")
            actions["witch_poison_target"] = int(poison_target)
            potions["poison"] = False
            poisoned = int(poison_target)
        if (use_antidote and use_poison) and not allow_double:
            raise GameEngineError("该配置下无法同时使用解药和毒药")
        if saved or poisoned is not None:
            RoomPlayer.objects.filter(pk=witch).update(has_used_skill=True)
        actions["witch_resolved"] = True
        self._resolve_night()

    def _resolve_night(self) -> None:
        actions = self.state.setdefault("night_actions", {})
        killed: List[int] = []
        saved_target = None
        target = actions.get("wolves_target")
        if target is not None and not actions.get("witch_saved"):
            killed.append(int(target))
        else:
            saved_target = target if target is not None else None
        poison_target = actions.get("witch_poison_target")
        if poison_target is not None:
            killed.append(int(poison_target))
        killed = [pid for pid in killed if pid is not None]
        for victim in killed:
            if victim in self._alive_player_ids():
                self._eliminate_player(victim, reveal=True)
        actions.update({
            "wolves_target": None,
            "seer_target": None,
            "witch_saved": False,
            "witch_poison_target": None,
            "seer_result": None,
            "witch_resolved": False,
        })
        self.state["night_actions"] = actions
        last_result = self.state.setdefault("last_result", {})
        last_result["nightKilled"] = killed
        last_result["saved"] = saved_target
        winner = self._check_winner()
        if winner:
            self.state["winner"] = winner
            self.phase = EnginePhase.ENDED
            self.state["phase"] = self.phase.value
            self._reveal_all()
            self.state["stage"] = "end"
            self.state["current_player_id"] = None
            return
        self.phase = EnginePhase.DAY
        self.state["phase"] = self.phase.value
        self._set_stage("day.discussion")
        self.state["speeches"] = []
        self.state["votes"] = {}
        self.state["vote_round"] = 1
        self.state["revote_candidates"] = []
        self._queue_reset()

    # Day flow ---------------------------------------------------------
    def _queue_reset(self) -> None:
        alive = self._alive_player_ids()
        self.state["alive_players"] = alive
        self.state["queue"] = list(alive)
        self.state["current_player_id"] = alive[0] if alive else None
        if self.state.get("stage") == "day.discussion":
            self._arm_discussion_timer()

    def _advance_queue(self) -> None:
        queue = self.state.get("queue", [])
        if queue:
            queue.pop(0)
        self.state["queue"] = queue
        self.state["current_player_id"] = queue[0] if queue else None
        if self.state.get("stage") == "day.discussion":
            self._arm_discussion_timer()

    def _process_speech(self, actor_id: Optional[int], content: str, *, is_ai: bool = False) -> None:
        if actor_id is None:
            raise GameEngineError("缺少发言玩家信息")
        queue = self.state.get("queue", [])
        if not queue or queue[0] != actor_id:
            raise GameEngineError("当前并非该玩家的发言回合")
        raw = content.strip()
        try:
            sanitized = enforce_content_policy(raw, mode="mask" if is_ai else "reject")
        except ContentPolicyViolation as exc:
            raise GameEngineError(str(exc)) from exc
        sanitized = sanitized or ("AI" if is_ai else "")
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
            self._set_stage("day.vote")
            self.state["votes"] = {}
            self.state["vote_round"] = 1
            self.state["revote_candidates"] = []
            self._arm_vote_timer()

    def _process_vote(self, actor_id: Optional[int], target_id: Optional[int], *, is_ai: bool = False) -> None:
        if actor_id is None:
            raise GameEngineError("缺少投票玩家")
        if target_id is None:
            raise GameEngineError("需要指定投票目标")
        if actor_id not in self._alive_player_ids():
            raise GameEngineError("被淘汰的玩家无法投票")
        allowed = self._eligible_vote_targets()
        if target_id not in allowed:
            raise GameEngineError("投票目标不合法")
        votes = self.state.setdefault("votes", {})
        if str(actor_id) in votes:
            raise GameEngineError("已完成投票，无法重复操作")
        votes[str(actor_id)] = int(target_id)
        self._arm_vote_timer()
        if len(votes) >= len(self._alive_player_ids()):
            self._finalize_votes()

    def _eligible_vote_targets(self) -> List[int]:
        allowed = self.state.get("revote_candidates") or self._alive_player_ids()
        return [int(pid) for pid in allowed]

    def _finalize_votes(self) -> None:
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
            self._arm_vote_timer()
            return
        eliminated = leaders[0]
        self._eliminate_player(eliminated, reveal=True)
        last_result = self.state.setdefault("last_result", {})
        lynched = last_result.get("lynched", [])
        if eliminated not in lynched:
            lynched.append(eliminated)
        last_result["lynched"] = lynched
        winner = self._check_winner()
        if winner:
            self.state["winner"] = winner
            self.phase = EnginePhase.ENDED
            self.state["phase"] = self.phase.value
            self._reveal_all()
            self._set_stage("end")
            self.state["current_player_id"] = None
            self.clear_timer()
            return
        self.state["round"] = self.state.get("round", 1) + 1
        self.phase = EnginePhase.NIGHT
        self.state["phase"] = self.phase.value
        self._set_stage("night.wolves")
        self.state["speeches"] = []
        self.state["votes"] = {}
        self.state["vote_round"] = 1
        self.state["revote_candidates"] = []
        self.state["night_actions"] = {
            "wolves_target": None,
            "seer_target": None,
            "seer_result": None,
            "witch_saved": False,
            "witch_poison_target": None,
            "witch_resolved": False,
        }
        self._queue_reset()

    def _eliminate_player(self, player_id: int, *, reveal: bool = False) -> None:
        assignment = self._assignment(player_id)
        if not assignment.get("is_alive"):
            return
        assignment["is_alive"] = False
        if reveal:
            assignment["revealed"] = True
        RoomPlayer.objects.filter(pk=player_id).update(is_alive=False)

    def _reveal_all(self) -> None:
        for meta in self.state.get("assignments", {}).values():
            meta["revealed"] = True

    def _check_winner(self) -> Optional[str]:
        alive_assignments = [self._assignment(pid) for pid in self._alive_player_ids()]
        wolves = [meta for meta in alive_assignments if meta["role"] == "werewolf"]
        villagers = [meta for meta in alive_assignments if meta["role"] != "werewolf"]
        if not wolves:
            return "villager"
        if len(wolves) >= len(villagers):
            return "werewolf"
        return None

    # ------------------------------------------------------------------
    # Timer helpers
    # ------------------------------------------------------------------
    def _arm_stage_timer(self, stage: str) -> None:
        if stage == "end":
            self.clear_timer()
            return
        if stage.startswith("night"):
            durations = {
                "night.wolves": self._stage_duration("night_wolves", 45),
                "night.seer": self._stage_duration("night_seer", 30),
                "night.witch": self._stage_duration("night_witch", 30),
            }
            duration = durations.get(stage, 0)
            if duration <= 0:
                self.clear_timer()
                return
            default_action = {
                "night.wolves": {"type": "auto_wolf_attack"},
                "night.seer": {"type": "auto_seer"},
                "night.witch": {"type": "auto_witch"},
            }.get(stage, {})
            descriptions = {
                "night.wolves": "超时将由系统为狼人选择击杀目标",
                "night.seer": "超时后预言家将自动查看一名玩家",
                "night.witch": "超时后女巫将默认跳过",
            }
            self.enter_phase(
                self.phase,
                duration=duration,
                default_action=default_action,
                description=descriptions.get(stage),
                metadata={"stage": stage},
            )
            return
        if stage == "day.discussion":
            self._arm_discussion_timer()
            return
        if stage == "day.vote":
            self._arm_vote_timer()
            return
        self.clear_timer()

    def _arm_discussion_timer(self) -> None:
        if self.state.get("stage") != "day.discussion":
            return
        current = self.state.get("current_player_id")
        if current is None:
            self.clear_timer()
            return
        self.enter_phase(
            self.phase,
            duration=self._stage_duration("day_discussion", 90),
            default_action={"type": "auto_speech", "player_id": current},
            description="超时将自动跳过当前玩家的发言",
            metadata={"stage": "day.discussion", "current": current},
        )

    def _pending_vote_players(self) -> List[int]:
        votes = self.state.get("votes", {})
        return [pid for pid in self._alive_player_ids() if str(pid) not in votes]

    def _arm_vote_timer(self) -> None:
        if self.state.get("stage") != "day.vote":
            return
        pending = self._pending_vote_players()
        if not pending:
            self.clear_timer()
            return
        self.enter_phase(
            self.phase,
            duration=self._stage_duration("day_vote", 45),
            default_action={"type": "auto_vote"},
            description="超时后未投票玩家将由系统自动投票",
            metadata={"stage": "day.vote", "pending": pending},
        )

    def _record_timeout(self, metadata: Optional[Dict[str, Any]] = None) -> None:
        self.state.setdefault("history", []).append(
            {
                "type": "timeout",
                "stage": self.state.get("stage"),
                "phase": self.phase.value,
                "round": self.state.get("round", 1),
                "timestamp": timezone.now().isoformat(),
                "metadata": metadata or {},
            }
        )

    def _handle_timeout(self, payload: Dict[str, Any]) -> None:
        metadata = payload.get("timer") or self.state.get("timer") or {}
        self._record_timeout(metadata)
        stage = self.state.get("stage")
        defaults = self.state.setdefault("default_actions", {})
        if stage == "night.wolves":
            wolves = [wid for wid in self._wolves() if self._is_alive(wid)]
            alive = [pid for pid in self._alive_player_ids() if pid not in wolves]
            target_pool = alive or self._alive_player_ids()
            if not target_pool:
                self._advance_after_wolves()
                return
            for wolf in wolves:
                defaults[str(wolf)] = defaults.get(str(wolf), 0) + 1
            target = random.choice(target_pool)
            actor = wolves[0] if wolves else None
            self._handle_wolf_target(actor, target, is_ai=True)
            return
        if stage == "night.seer":
            seer = self._seer()
            if not seer or not self._is_alive(seer):
                self._advance_after_seer()
                return
            defaults[str(seer)] = defaults.get(str(seer), 0) + 1
            candidates = [pid for pid in self._alive_player_ids() if pid != seer]
            target = random.choice(candidates) if candidates else seer
            self._handle_seer_target(seer, target, is_ai=True)
            return
        if stage == "night.witch":
            witch = self._witch()
            if not witch or not self._is_alive(witch):
                self._resolve_night()
                return
            defaults[str(witch)] = defaults.get(str(witch), 0) + 1
            self._handle_witch_action(witch, use_antidote=False, use_poison=False, poison_target=None, is_ai=True)
            return
        if stage == "day.discussion":
            current = self.state.get("current_player_id")
            if current is None:
                self._arm_vote_timer()
                return
            defaults[str(current)] = defaults.get(str(current), 0) + 1
            self._process_speech(current, "（系统）玩家超时未发言", is_ai=True)
            return
        if stage == "day.vote":
            for voter in self._pending_vote_players():
                defaults[str(voter)] = defaults.get(str(voter), 0) + 1
                target = self._default_vote_target(voter)
                self._process_vote(voter, target, is_ai=True)

    def _default_vote_target(self, voter: int) -> int:
        alive = self._alive_player_ids()
        candidates = [pid for pid in alive if pid != voter]
        if not candidates:
            return voter
        return random.choice(candidates)
