"""Prometheus metrics helpers used across backend services."""

from __future__ import annotations

import time
from contextlib import contextmanager

from prometheus_client import Counter, Gauge, Histogram

HTTP_REQUEST_LATENCY = Histogram(
    "ai_social_game_http_request_seconds",
    "Duration of HTTP requests",
    labelnames=("method", "endpoint"),
)
HTTP_REQUEST_COUNTER = Counter(
    "ai_social_game_http_requests_total",
    "Total number of HTTP requests",
    labelnames=("method", "endpoint", "status"),
)
WS_CONNECTION_GAUGE = Gauge(
    "ai_social_game_ws_connections",
    "Number of active websocket connections per room",
    labelnames=("room_id",),
)
AI_RESPONSE_LATENCY = Histogram(
    "ai_social_game_ai_latency_seconds",
    "Latency of AI response generation",
)


@contextmanager
def track_request(method: str, endpoint: str):
    """Context manager used by middleware to time requests."""

    start = time.perf_counter()
    try:
        yield
    finally:
        duration = time.perf_counter() - start
        HTTP_REQUEST_LATENCY.labels(method, endpoint).observe(duration)


def record_http_request(method: str, endpoint: str, status: int) -> None:
    HTTP_REQUEST_COUNTER.labels(method, endpoint, str(status)).inc()


def record_ai_latency(duration: float) -> None:
    AI_RESPONSE_LATENCY.observe(duration)
