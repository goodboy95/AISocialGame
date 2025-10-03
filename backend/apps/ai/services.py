"""Lightweight AI helpers used by the Undercover engine."""

from __future__ import annotations

import random
from typing import Dict, Iterable, List


AI_NAME_POOL = [
    "星火分析师",
    "逻辑黄鹂",
    "飞驰信使",
    "风趣段子手",
    "冷静观察员",
    "灵感画师",
    "麦田诗人",
    "暴风推理家",
]

STYLE_HINTS = {
    "balanced": {
        "prefix": ["我觉得", "简单聊聊", "我的理解是"],
        "suffix": ["大家怎么看?", "也许有别的解读", "供参考"],
    },
    "rational": {
        "prefix": ["从线索看", "根据词义", "综合考虑"],
        "suffix": ["逻辑上比较合理", "欢迎补充", "这是我的判断"],
    },
    "humor": {
        "prefix": ["哈哈", "讲个笑话", "别紧张"],
        "suffix": ["别投我啊", "我先说到这", "别打我"]
    },
}


def generate_ai_display_name(existing: Iterable[str]) -> str:
    """Pick a unique-ish display name for auto-filled AI members."""

    existing = set(existing)
    candidates = AI_NAME_POOL[:]
    random.shuffle(candidates)
    for name in candidates:
        if name not in existing:
            return name
    return f"AI玩家{random.randint(100, 999)}"


class UndercoverAIStrategy:
    """Heuristic based promptless AI fallback."""

    def __init__(self, style: str = "balanced") -> None:
        self.style = style if style in STYLE_HINTS else "balanced"

    def _style_snippet(self) -> str:
        hints = STYLE_HINTS[self.style]
        return random.choice(hints["prefix"]), random.choice(hints["suffix"])

    def generate_speech(
        self,
        *,
        role: str,
        word: str,
        round_number: int,
        history: List[Dict],
    ) -> str:
        prefix, suffix = self._style_snippet()
        if not word:
            body = "我这边空白，只能听听大家的方向"
        elif role == "undercover":
            body = f"这个词让我想到『{word}』，感觉可以往形象一点的方向聊"
        else:
            body = f"我的词比较倾向『{word}』，先抛个共识看看"
        if round_number > 1 and history:
            body += "，上一轮的讨论我还在回味"
        return f"{prefix}{body}{suffix}"

    def pick_vote(
        self,
        *,
        voter_id: int,
        alive_players: List[int],
        assignments: Dict[str, Dict],
        history: List[Dict],
    ) -> int:
        """Return a target seat id for the given voter."""

        suspects = [pid for pid in alive_players if pid != voter_id]
        if not suspects:
            return voter_id
        # Prefer players没有发言或在上一条 history 中
        if history:
            last = history[-1].get("player_id")
            if last and last in suspects:
                return last
        # 保留一点倾向：卧底更易投向非卧底
        voter_role = assignments.get(str(voter_id), {}).get("role")
        civilians = [pid for pid in suspects if assignments.get(str(pid), {}).get("role") != "undercover"]
        undercovers = [pid for pid in suspects if assignments.get(str(pid), {}).get("role") == "undercover"]
        if voter_role == "undercover" and civilians:
            return random.choice(civilians)
        if voter_role != "undercover" and undercovers:
            return random.choice(undercovers)
        return random.choice(suspects)


class WerewolfAIStrategy:
    """Heuristics driving simplified Werewolf night/day behaviors."""

    def __init__(self, style: str = "balanced") -> None:
        self.style = style if style in STYLE_HINTS else "balanced"

    # ------------------------------------------------------------------
    # Night action helpers
    # ------------------------------------------------------------------
    def pick_wolf_target(
        self,
        *,
        wolves: list[int],
        alive_players: list[int],
        assignments: Dict[str, Dict],
    ) -> int:
        """Select a non-wolf target, prefer loud villagers if available."""

        candidates = [pid for pid in alive_players if pid not in wolves]
        if not candidates:
            candidates = alive_players[:]
        return random.choice(candidates) if candidates else random.choice(alive_players)

    def pick_seer_target(
        self,
        *,
        seer_id: int,
        alive_players: list[int],
        known_results: list[Dict[str, int]],
    ) -> int:
        """Avoid checking self or repeated players."""

        checked = {result["player_id"] for result in known_results}
        candidates = [pid for pid in alive_players if pid not in checked and pid != seer_id]
        if not candidates:
            candidates = [pid for pid in alive_players if pid != seer_id]
        return random.choice(candidates) if candidates else seer_id

    def pick_witch_action(
        self,
        *,
        pending_kill: int | None,
        potions: Dict[str, bool],
        alive_players: list[int],
        wolves: list[int],
    ) -> Dict[str, object]:
        """Return witch reaction: optionally save target or poison suspect."""

        action = {"use_antidote": False, "use_poison": False, "poison_target": None}
        if pending_kill and potions.get("antidote"):
            if pending_kill not in wolves:
                action["use_antidote"] = True
                return action
        if potions.get("poison"):
            suspects = [pid for pid in alive_players if pid not in wolves and pid != pending_kill]
            if suspects and random.random() < 0.3:
                action["use_poison"] = True
                action["poison_target"] = random.choice(suspects)
        return action

    # ------------------------------------------------------------------
    # Day time helpers
    # ------------------------------------------------------------------
    def _style_snippet(self) -> tuple[str, str]:
        hints = STYLE_HINTS[self.style]
        return random.choice(hints["prefix"]), random.choice(hints["suffix"])

    def generate_day_speech(
        self,
        *,
        role: str,
        round_number: int,
        last_result: Dict[str, list[int]],
        history: List[Dict],
    ) -> str:
        prefix, suffix = self._style_snippet()
        night_losses = last_result.get("nightKilled", [])
        lynched = last_result.get("lynched", [])
        clues = []
        if night_losses:
            clues.append(f"夜里失去了{len(night_losses)}人")
        if lynched:
            clues.append(f"昨天投票离场的是 {lynched}")
        base = "，".join(clues) if clues else "我们需要更多线索"
        if role == "werewolf":
            body = f"先观察下再决定，{base}"
        elif role == "seer":
            body = f"我有一些推测，{base}，大家注意言行"
        elif role == "witch":
            body = f"女巫会谨慎行事，{base}"
        else:
            body = f"我感觉要团结，{base}"
        if round_number > 1 and history:
            body += "，上一轮的讨论值得复盘"
        return f"{prefix}{body}{suffix}"

    def pick_vote(
        self,
        *,
        voter_id: int,
        alive_players: list[int],
        wolves: list[int],
        assignments: Dict[str, Dict],
    ) -> int:
        """Vote towards enemy faction with slight randomness."""

        candidates = [pid for pid in alive_players if pid != voter_id]
        if not candidates:
            return voter_id
        if voter_id in wolves:
            civilians = [pid for pid in candidates if pid not in wolves]
            return random.choice(civilians) if civilians else random.choice(candidates)
        else:
            werewolves = [pid for pid in candidates if pid in wolves]
            return random.choice(werewolves) if werewolves else random.choice(candidates)

