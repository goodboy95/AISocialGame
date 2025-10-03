"""Reusable middleware components for observability and tracing."""

from __future__ import annotations

import time
import uuid
from typing import Callable

from django.utils.deprecation import MiddlewareMixin

from .metrics import record_http_request, track_request


class CorrelationIdMiddleware(MiddlewareMixin):
    """Attach an X-Request-ID header for downstream logging and tracing."""

    header = "HTTP_X_REQUEST_ID"
    response_header = "X-Request-ID"

    def process_request(self, request):  # type: ignore[override]
        correlation_id = request.META.get(self.header)
        if not correlation_id:
            correlation_id = uuid.uuid4().hex
            request.META[self.header] = correlation_id
        request.correlation_id = correlation_id  # type: ignore[attr-defined]

    def process_response(self, request, response):  # type: ignore[override]
        correlation_id = getattr(request, "correlation_id", None)
        if correlation_id:
            response[self.response_header] = correlation_id
        return response


class MetricsMiddleware:
    """Record request metrics for Prometheus."""

    def __init__(self, get_response: Callable):
        self.get_response = get_response

    def __call__(self, request):
        method = request.method
        endpoint = request.resolver_match.view_name if request.resolver_match else request.path
        with track_request(method, endpoint):
            response = self.get_response(request)
        record_http_request(method, endpoint, getattr(response, "status_code", 500))
        return response
