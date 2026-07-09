"""
FastAPI middleware: request ID, security headers, rate limiting, audit hooks.
"""

import time
from collections import defaultdict
from typing import Callable

from fastapi import Request, Response
from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware

from core.config import get_settings
from core.logging_config import get_logger, set_request_id

settings = get_settings()
logger = get_logger("riskvision.middleware")

# In-memory rate limit store (use Redis in multi-instance production)
_rate_limit_store: dict[str, list[float]] = defaultdict(list)


class RequestIdMiddleware(BaseHTTPMiddleware):
    """Assign a unique request ID to every incoming request."""

    async def dispatch(self, request: Request, call_next: Callable) -> Response:
        request_id = request.headers.get("X-Request-ID") or set_request_id()
        request.state.request_id = request_id
        response = await call_next(request)
        response.headers["X-Request-ID"] = request_id
        return response


class SecurityHeadersMiddleware(BaseHTTPMiddleware):
    """Add security headers to all responses."""

    async def dispatch(self, request: Request, call_next: Callable) -> Response:
        response = await call_next(request)
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["X-Frame-Options"] = "DENY"
        response.headers["X-XSS-Protection"] = "1; mode=block"
        response.headers["Referrer-Policy"] = "strict-origin-when-cross-origin"
        response.headers["Permissions-Policy"] = "geolocation=(), microphone=(), camera=()"
        if settings.is_production:
            response.headers["Strict-Transport-Security"] = "max-age=31536000; includeSubDomains"
        return response


class RateLimitMiddleware(BaseHTTPMiddleware):
    """Simple sliding-window rate limiter per client IP."""

    async def dispatch(self, request: Request, call_next: Callable) -> Response:
        if request.url.path in ("/health", "/ready", "/"):
            return await call_next(request)

        client_ip = request.client.host if request.client else "unknown"
        now = time.time()
        window = settings.rate_limit_window_seconds
        max_requests = settings.rate_limit_requests

        timestamps = _rate_limit_store[client_ip]
        _rate_limit_store[client_ip] = [t for t in timestamps if now - t < window]

        if len(_rate_limit_store[client_ip]) >= max_requests:
            logger.warning("Rate limit exceeded for IP: %s", client_ip)
            return JSONResponse(
                status_code=429,
                content={"detail": "Rate limit exceeded. Please try again later."},
            )

        _rate_limit_store[client_ip].append(now)
        return await call_next(request)


class RequestSizeLimitMiddleware(BaseHTTPMiddleware):
    """Reject requests exceeding configured body size limit."""

    async def dispatch(self, request: Request, call_next: Callable) -> Response:
        content_length = request.headers.get("content-length")
        if content_length and int(content_length) > settings.max_upload_bytes:
            return JSONResponse(
                status_code=413,
                content={"detail": f"Request body too large. Max {settings.max_upload_size_mb}MB."},
            )
        return await call_next(request)


class PerformanceLoggingMiddleware(BaseHTTPMiddleware):
    """Log request duration for performance monitoring."""

    async def dispatch(self, request: Request, call_next: Callable) -> Response:
        start = time.perf_counter()
        response = await call_next(request)
        duration_ms = (time.perf_counter() - start) * 1000
        response.headers["X-Response-Time-Ms"] = f"{duration_ms:.2f}"
        if duration_ms > 1000:
            logger.warning(
                "Slow request: %s %s took %.2fms",
                request.method,
                request.url.path,
                duration_ms,
            )
        return response
