"""Lightweight AI helpers used by the game engines."""

from __future__ import annotations

import json
import logging
import random
import re
from dataclasses import dataclass
from time import perf_counter
from typing import Dict, Iterable, List, Optional

from apps.common import record_ai_latency

from .llm import LLMClient, LLMError, get_llm_client


LOGGER = logging.getLogger(__name__)


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
        "label": "均衡思考者",
        "description": "兼顾理性与情绪的中庸型发言。",
    },
    "rational": {
        "prefix": ["从线索看", "根据词义", "综合考虑"],
        "suffix": ["逻辑上比较合理", "欢迎补充", "这是我的判断"],
        "label": "逻辑分析师",
        "description": "偏向推理解读，强调事实与细节。",
    },
    "humor": {
        "prefix": ["哈哈", "讲个笑话", "别紧张"],
        "suffix": ["别投我啊", "我先说到这", "别打我"],
        "label": "幽默调节者",
        "description": "轻松诙谐，负责调动房间气氛。",
    },
    "aggressive": {
        "prefix": ["直说了", "听我一句", "别藏着"],
        "suffix": ["快表态", "别犹豫", "行动起来"],
        "label": "强势指挥官",
        "description": "语气直接，推动大家快速决策。",
    },
}


def available_style_keys() -> list[str]:
    return list(STYLE_HINTS.keys())


def list_ai_styles() -> list[dict[str, str]]:
    """Return style metadata for API exposure."""

    styles = []
    for key, meta in STYLE_HINTS.items():
        styles.append(
            {
                "key": key,
                "label": meta.get("label", key.title()),
                "description": meta.get("description", ""),
            }
        )
    return styles


def resolve_ai_style(style: str | None) -> str:
    if style and style in STYLE_HINTS:
        return style
    return "balanced"


def ai_style_label(style: str) -> str:
    return STYLE_HINTS.get(style, {}).get("label", style)


def random_ai_style() -> str:
    return random.choice(available_style_keys())


def generate_ai_display_name(existing: Iterable[str]) -> str:
    """Pick a unique-ish display name for auto-filled AI members."""

    existing = set(existing)
    candidates = AI_NAME_POOL[:]
    random.shuffle(candidates)
    for name in candidates:
        if name not in existing:
            return name
    return f"AI玩家{random.randint(100, 999)}"


@dataclass
class VoteDecision:
    target: int
    reason: Optional[str] = None


class UndercoverAIStrategy:
    """Heuristic based promptless AI fallback."""

    def __init__(self, style: str = "balanced", llm_client: Optional[LLMClient] = None) -> None:
        self.style = resolve_ai_style(style)
        self.llm_client = llm_client or get_llm_client()

    def _style_snippet(self) -> str:
        hints = STYLE_HINTS[self.style]
        return random.choice(hints["prefix"]), random.choice(hints["suffix"])

    def _llm_prompt(
        self,
        *,
        role: str,
        word: str,
        round_number: int,
        history: List[Dict],
    ) -> str:
        history_lines = []
        for entry in history[-6:]:
            history_lines.append(
                f"玩家{entry.get('player_id')}：{entry.get('content', '')}"
            )
        history_block = "\n".join(history_lines) or "暂无历史发言"
        role_hint = "卧底" if role == "undercover" else ("白板" if role == "blank" else "平民")
        word_hint = word or "(空白)"
        style_meta = STYLE_HINTS.get(self.style, {})
        tone = style_meta.get("description", "保持自然")
        return (
            "你正在参与中文派对游戏《谁是卧底》。"\
            "请以第一人称、简洁的中文发言，语气符合{tone}。"\
            "你的身份：{role_hint}，拿到的词汇：{word_hint}。"\
            "当前是第 {round_number} 轮发言。"\
            "最近发言记录：\n{history_block}\n"\
            "请给出一句不超过60字的发言，既能融入局势又不要暴露真实身份。"
        ).format(
            tone=tone,
            role_hint=role_hint,
            word_hint=word_hint,
            round_number=round_number,
            history_block=history_block,
        )

    def generate_speech(
        self,
        *,
        role: str,
        word: str,
        round_number: int,
        history: List[Dict],
    ) -> str:
        start = perf_counter()
        if self.llm_client:
            try:
                prompt = self._llm_prompt(
                    role=role,
                    word=word,
                    round_number=round_number,
                    history=history,
                )
                result = self.llm_client.generate_text(
                    prompt=prompt,
                    system_prompt="你是一名桌游玩家，需用自然中文发言。",
                    temperature=0.7,
                    metadata={"game": "undercover", "style": self.style},
                )
                if result:
                    record_ai_latency(perf_counter() - start)
                    return result
            except LLMError:
                pass
        prefix, suffix = self._style_snippet()
        if not word:
            body = "我这边空白，只能听听大家的方向"
        elif role == "undercover":
            body = f"这个词让我想到『{word}』，感觉可以往形象一点的方向聊"
        else:
            body = f"我的词比较倾向『{word}』，先抛个共识看看"
        if round_number > 1 and history:
            body += "，上一轮的讨论我还在回味"
        result = f"{prefix}{body}{suffix}"
        record_ai_latency(perf_counter() - start)
        return result

    def _llm_vote_prompt(
        self,
        *,
        voter_id: int,
        word: str,
        alive_players: List[int],
        assignments: Dict[str, Dict],
        history: List[Dict],
    ) -> str:
        history_lines = []
        for entry in history[-6:]:
            history_lines.append(
                f"玩家{entry.get('player_id')}：{entry.get('content', '')}"
            )
        history_block = "\n".join(history_lines) or "暂无历史发言"
        alive_lines = []
        for pid in alive_players:
            meta = assignments.get(str(pid), {})
            name = meta.get("display_name") or f"玩家{pid}"
            alive_lines.append(f"{pid}号 {name}")
        alive_block = "，".join(alive_lines) or "暂无玩家"
        voter_meta = assignments.get(str(voter_id), {})
        role_hint = voter_meta.get("role") or "civilian"
        role_label = "卧底" if role_hint == "undercover" else ("白板" if role_hint == "blank" else "平民")
        word_hint = word or voter_meta.get("word") or "(空白)"
        return (
            "你正在参与中文派对游戏《谁是卧底》。"\
            "请根据历史发言与仍在场的玩家，决定本轮要投票淘汰的对象。"\
            "你的当前视角：可能是{role_label}，你拿到的词语是：{word_hint}。"\
            "仍存活的玩家包括：{alive_block}。"\
            "最近的发言记录：\n{history_block}\n"\
            "请输出一个 JSON 对象，形如 {{\"decision\": 玩家编号, \"reason\": \"简短理由\"}}，"\
            "务必只输出 JSON 内容。"
        ).format(
            role_label=role_label,
            word_hint=word_hint,
            alive_block=alive_block,
            history_block=history_block,
        )

    def _parse_vote_response(self, response: str, alive_players: List[int]) -> Optional[VoteDecision]:
        text = (response or "").strip()
        if not text:
            return None
        if text.startswith("```"):
            lines = [line for line in text.splitlines() if not line.startswith("```")]
            text = "\n".join(lines).strip()
        try:
            data = json.loads(text)
        except json.JSONDecodeError:
            match = re.search(r"(\d+)", text)
            if not match:
                return None
            candidate = int(match.group(1))
            if candidate not in alive_players:
                return None
            reason_text = text.replace(match.group(1), "").strip()
            return VoteDecision(target=candidate, reason=reason_text or None)
        if isinstance(data, list) and data:
            data = data[0]
        if not isinstance(data, dict):
            return None
        target = data.get("decision") or data.get("target") or data.get("vote") or data.get("player")
        if isinstance(target, str):
            digits = re.findall(r"\d+", target)
            target = int(digits[0]) if digits else None
        if not isinstance(target, int):
            return None
        if target not in alive_players:
            return None
        reason = data.get("reason") or data.get("analysis") or data.get("thought") or ""
        if isinstance(reason, dict):
            reason = reason.get("text") or reason.get("value") or ""
        reason_text = str(reason).strip()
        return VoteDecision(target=target, reason=reason_text or None)

    def pick_vote(
        self,
        *,
        voter_id: int,
        alive_players: List[int],
        assignments: Dict[str, Dict],
        history: List[Dict],
    ) -> VoteDecision:
        """Return a target seat id for the given voter."""

        suspects = [pid for pid in alive_players if pid != voter_id]
        if not suspects:
            return VoteDecision(target=voter_id)
        start = perf_counter()
        voter_meta = assignments.get(str(voter_id), {})
        voter_word = voter_meta.get("word") or ""
        if self.llm_client:
            try:
                prompt = self._llm_vote_prompt(
                    voter_id=voter_id,
                    word=voter_word,
                    alive_players=alive_players,
                    assignments=assignments,
                    history=history,
                )
                response = self.llm_client.generate_text(
                    prompt=prompt,
                    system_prompt="你是一名桌游玩家，正在决定投票对象。",
                    temperature=0.4,
                    metadata={"game": "undercover", "style": self.style, "action": "vote"},
                )
                if response:
                    decision = self._parse_vote_response(response, alive_players)
                    if decision:
                        record_ai_latency(perf_counter() - start)
                        return decision
            except LLMError as exc:
                LOGGER.debug("LLM vote generation failed: %s", exc)
        if history:
            last = history[-1].get("player_id")
            if last and last in suspects:
                record_ai_latency(perf_counter() - start)
                return VoteDecision(target=last)
        voter_role = assignments.get(str(voter_id), {}).get("role")
        civilians = [pid for pid in suspects if assignments.get(str(pid), {}).get("role") != "undercover"]
        undercovers = [pid for pid in suspects if assignments.get(str(pid), {}).get("role") == "undercover"]
        if voter_role == "undercover" and civilians:
            record_ai_latency(perf_counter() - start)
            return VoteDecision(target=random.choice(civilians))
        if voter_role != "undercover" and undercovers:
            record_ai_latency(perf_counter() - start)
            return VoteDecision(target=random.choice(undercovers))
        record_ai_latency(perf_counter() - start)
        return VoteDecision(target=random.choice(suspects))


class WerewolfAIStrategy:
    """Heuristics driving simplified Werewolf night/day behaviors."""

    def __init__(self, style: str = "balanced", llm_client: Optional[LLMClient] = None) -> None:
        self.style = resolve_ai_style(style)
        self.llm_client = llm_client or get_llm_client()

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

    def _day_llm_prompt(
        self,
        *,
        role: str,
        round_number: int,
        last_result: Dict[str, list[int]],
        history: List[Dict],
    ) -> str:
        summary_parts = []
        night_losses = last_result.get("nightKilled", [])
        lynched = last_result.get("lynched", [])
        if night_losses:
            summary_parts.append(f"夜晚死亡玩家: {night_losses}")
        if lynched:
            summary_parts.append(f"昨日投票淘汰: {lynched}")
        summary = "\n".join(summary_parts) or "昨夜局势平稳"
        history_lines = []
        for entry in history[-5:]:
            history_lines.append(f"{entry.get('player_id')}：{entry.get('content', '')}")
        history_block = "\n".join(history_lines) or "暂无讨论记录"
        style_meta = STYLE_HINTS.get(self.style, {})
        tone = style_meta.get("description", "保持冷静")
        role_label = {
            "werewolf": "狼人",
            "seer": "预言家",
            "witch": "女巫",
        }.get(role, "村民")
        return (
            "你正在进行中文狼人杀游戏的白天讨论。"\
            "请用简洁自然的中文，总结局势并给出态度，语气需符合{tone}。"\
            "你的角色：{role_label}（请注意隐藏真实身份）。"\
            "当前轮次：第 {round_number} 天。"\
            "昨夜情报：\n{summary}\n"\
            "最近发言：\n{history_block}\n"\
            "生成一句不超过80字的发言。"
        ).format(
            tone=tone,
            role_label=role_label,
            round_number=round_number,
            summary=summary,
            history_block=history_block,
        )

    def generate_day_speech(
        self,
        *,
        role: str,
        round_number: int,
        last_result: Dict[str, list[int]],
        history: List[Dict],
    ) -> str:
        start = perf_counter()
        if self.llm_client:
            try:
                prompt = self._day_llm_prompt(
                    role=role,
                    round_number=round_number,
                    last_result=last_result,
                    history=history,
                )
                result = self.llm_client.generate_text(
                    prompt=prompt,
                    system_prompt="你是一名狼人杀玩家，请在讨论中自然发言。",
                    temperature=0.8,
                    metadata={"game": "werewolf", "style": self.style},
                )
                if result:
                    record_ai_latency(perf_counter() - start)
                    return result
            except LLMError:
                pass
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
        result = f"{prefix}{body}{suffix}"
        record_ai_latency(perf_counter() - start)
        return result

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

