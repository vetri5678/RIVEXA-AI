"""
Centralized structured logging with daily rotation and request ID support.
"""

import logging
import sys
import uuid
from contextvars import ContextVar
from logging.handlers import TimedRotatingFileHandler
from pathlib import Path

from core.config import get_settings

settings = get_settings()

request_id_ctx: ContextVar[str] = ContextVar("request_id", default="-")


class RequestIdFilter(logging.Filter):
    """Inject request_id from context into log records."""

    def filter(self, record: logging.LogRecord) -> bool:
        record.request_id = request_id_ctx.get("-")
        return True


def setup_app_logging() -> None:
    """Configure application-wide structured logging."""
    settings.log_dir.mkdir(parents=True, exist_ok=True)

    log_format = (
        "%(asctime)s [%(levelname)s] [%(name)s] [req:%(request_id)s] %(message)s"
    )

    root_logger = logging.getLogger()
    root_logger.setLevel(getattr(logging, settings.log_level.upper(), logging.INFO))

    # Clear existing handlers to avoid duplicates on reload
    root_logger.handlers.clear()

    request_filter = RequestIdFilter()

    # Console handler
    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setFormatter(logging.Formatter(log_format))
    console_handler.addFilter(request_filter)
    root_logger.addHandler(console_handler)

    # Rotating file handler — daily rotation
    file_handler = TimedRotatingFileHandler(
        filename=settings.log_dir / "riskvision.log",
        when="midnight",
        interval=1,
        backupCount=settings.log_retention_days,
        encoding="utf-8",
    )
    file_handler.setFormatter(logging.Formatter(log_format))
    file_handler.addFilter(request_filter)
    root_logger.addHandler(file_handler)

    # Error-only file
    error_handler = TimedRotatingFileHandler(
        filename=settings.log_dir / "errors.log",
        when="midnight",
        interval=1,
        backupCount=settings.log_retention_days,
        encoding="utf-8",
    )
    error_handler.setLevel(logging.ERROR)
    error_handler.setFormatter(logging.Formatter(log_format))
    error_handler.addFilter(request_filter)
    root_logger.addHandler(error_handler)


def get_logger(name: str) -> logging.Logger:
    """Return a named logger."""
    return logging.getLogger(name)


def set_request_id(request_id: str | None = None) -> str:
    """Set request ID in context. Generates one if not provided."""
    rid = request_id or str(uuid.uuid4())[:8]
    request_id_ctx.set(rid)
    return rid
