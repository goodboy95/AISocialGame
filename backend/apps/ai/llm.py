"""LLM client abstraction for integrating real model outputs."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Dict, List, Optional

import httpx
from django.conf import settings


class LLMError(RuntimeError):
    """Raised when LLM requests fail or configuration is missing."""


@dataclass
class LLMConfig:
    api_key: str
    api_base: str
    model: str
    timeout: float = 30.0
    provider: str = "openai"


class LLMClient:
    """Lightweight wrapper around OpenAI-compatible chat completion endpoints."""

    def __init__(self, config: LLMConfig):
        self.config = config
        if not self.config.api_base:
            if self.config.provider == "openai":
                self.config.api_base = "https://api.openai.com/v1"
            else:
                raise LLMError("未配置 LLM API 基础地址")

    @property
    def _chat_url(self) -> str:
        if self.config.api_base.endswith("/v1"):
            return f"{self.config.api_base}/chat/completions"
        return f"{self.config.api_base.rstrip('/')}/chat/completions"

    def generate_text(
        self,
        *,
        prompt: str,
        system_prompt: Optional[str] = None,
        temperature: float = 0.7,
        max_tokens: int = 256,
        metadata: Optional[Dict[str, Any]] = None,
    ) -> str:
        if not self.config.api_key:
            raise LLMError("缺少 LLM API Key 配置")
        messages: List[Dict[str, str]] = []
        if system_prompt:
            messages.append({"role": "system", "content": system_prompt})
        messages.append({"role": "user", "content": prompt})
        payload: Dict[str, Any] = {
            "model": self.config.model,
            "messages": messages,
            "temperature": temperature,
            "max_tokens": max_tokens,
        }
        if metadata:
            payload["metadata"] = metadata
        headers = {
            "Authorization": f"Bearer {self.config.api_key}",
            "Content-Type": "application/json",
        }
        try:
            with httpx.Client(timeout=self.config.timeout) as client:
                response = client.post(self._chat_url, json=payload, headers=headers)
        except httpx.TimeoutException as exc:  # pragma: no cover - network boundary
            raise LLMError("LLM 请求超时") from exc
        except httpx.HTTPError as exc:  # pragma: no cover - network boundary
            raise LLMError("LLM 请求失败") from exc
        if response.status_code >= 400:
            raise LLMError(f"LLM 返回错误 {response.status_code}: {response.text}")
        data = response.json()
        try:
            return (data["choices"][0]["message"]["content"] or "").strip()
        except (KeyError, IndexError, TypeError) as exc:  # pragma: no cover - defensive
            raise LLMError("LLM 响应格式异常") from exc


def get_llm_client() -> Optional[LLMClient]:
    """Instantiate an LLM client from Django settings if configured."""

    conf = getattr(settings, "LLM_CONFIG", {})
    api_key = conf.get("api_key")
    if not api_key:
        return None
    config = LLMConfig(
        api_key=api_key,
        api_base=conf.get("api_base", ""),
        model=conf.get("model", "gpt-4o-mini"),
        timeout=float(conf.get("timeout", 30)),
        provider=conf.get("provider", "openai"),
    )
    return LLMClient(config)
