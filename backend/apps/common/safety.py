"""Utilities for content safety and sensitive word filtering."""

from __future__ import annotations

import re
from functools import lru_cache
from typing import Iterable, Sequence

from django.conf import settings


class ContentPolicyViolation(Exception):
    """Raised when text violates content safety policy."""

    def __init__(self, banned_words: Sequence[str]):
        self.banned_words = tuple(banned_words)
        message = "内容包含敏感词: " + ", ".join(self.banned_words)
        super().__init__(message)


@lru_cache(maxsize=1)
def _banned_words() -> tuple[str, ...]:
    config = getattr(settings, "CONTENT_SAFETY", {})
    words: Iterable[str] = config.get("banned_words", [])
    normalized = tuple(sorted({word.strip() for word in words if word.strip()}))
    return normalized


@lru_cache(maxsize=1)
def _patterns() -> tuple[re.Pattern[str], ...]:
    patterns = []
    for word in _banned_words():
        escaped = re.escape(word)
        patterns.append(re.compile(escaped, re.IGNORECASE))
    return tuple(patterns)


def scan_prohibited(text: str) -> list[str]:
    """Return a list of banned words found in the text."""

    found: list[str] = []
    for word, pattern in zip(_banned_words(), _patterns()):
        if pattern.search(text):
            found.append(word)
    return found


def mask_prohibited(text: str, *, mask_char: str = "*") -> tuple[str, list[str]]:
    """Mask all banned words in the given text and return sanitized string + hits."""

    hits: list[str] = []
    sanitized = text
    for word, pattern in zip(_banned_words(), _patterns()):
        if pattern.search(sanitized):
            hits.append(word)
            replacement = mask_char * max(len(word), 2)
            sanitized = pattern.sub(replacement, sanitized)
    return sanitized, hits


def enforce_content_policy(text: str, *, mode: str = "mask", mask_char: str = "*") -> str:
    """Validate text against the banned word list.

    Parameters
    ----------
    text: str
        The input message.
    mode: str
        ``"mask"`` (default) replaces sensitive words, ``"reject"`` raises an
        exception when hits are detected.
    mask_char: str
        Character used to mask words when ``mode="mask"``.
    """

    if not text:
        return text
    sanitized, hits = mask_prohibited(text, mask_char=mask_char)
    if hits and mode == "reject":
        raise ContentPolicyViolation(tuple(hits))
    return sanitized
