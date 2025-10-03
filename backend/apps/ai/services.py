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

