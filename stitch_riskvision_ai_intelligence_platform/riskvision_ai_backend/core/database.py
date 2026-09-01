"""
SQLAlchemy database engine, session factory, and dependency injection.
"""

from contextlib import contextmanager
from typing import Any, Generator

from sqlalchemy import create_engine, event
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from core.config import get_settings

settings = get_settings()


class Base(DeclarativeBase):
    """Declarative base for all ORM models."""


import logging

logger = logging.getLogger("riskvision.database")


def _create_engine():
    if settings.is_production and settings.is_sqlite:
        logger.error("CRITICAL: Production environment requires a PostgreSQL DATABASE_URL. SQLite is disallowed in production.")
        raise RuntimeError(
            "Production environment (ENVIRONMENT=production) requires a PostgreSQL DATABASE_URL. "
            "SQLite fallback is strictly prohibited in production."
        )

    connect_args: dict[str, Any] = {}
    if settings.is_sqlite:
        connect_args = {"check_same_thread": False}
    else:
        connect_args = {"connect_timeout": 15}

    engine_kwargs: dict[str, Any] = {
        "echo": settings.db_echo,
        "connect_args": connect_args,
        "pool_pre_ping": True,
    }

    if not settings.is_sqlite:
        engine_kwargs["pool_size"] = settings.db_pool_size
        engine_kwargs["max_overflow"] = settings.db_max_overflow

    db_type = "SQLite" if settings.is_sqlite else "PostgreSQL"
    logger.info("Initializing SQLAlchemy database engine (dialect=%s, pool_pre_ping=True)", db_type)
    return create_engine(settings.database_url, **engine_kwargs)


engine = _create_engine()

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


if settings.is_sqlite:

    @event.listens_for(engine, "connect")
    def set_sqlite_pragma(dbapi_connection, _connection_record):
        cursor = dbapi_connection.cursor()
        cursor.execute("PRAGMA foreign_keys=ON")
        cursor.close()


def get_db() -> Generator[Session, None, None]:
    """FastAPI dependency that yields a database session."""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


@contextmanager
def get_db_context() -> Generator[Session, None, None]:
    """Context manager for database sessions outside request scope."""
    db = SessionLocal()
    try:
        yield db
        db.commit()
    except Exception:
        db.rollback()
        raise
    finally:
        db.close()


def init_db() -> None:
    """Create all database tables."""
    # Import models so they register with Base.metadata
    from models import (  # noqa: F401
        audit_log,
        model_version,
        notification,
        prediction,
        project,
        token,
        user,
    )

    if settings.is_sqlite:
        db_path = settings.database_url.replace("sqlite:///", "")
        from pathlib import Path
        Path(db_path).parent.mkdir(parents=True, exist_ok=True)

    Base.metadata.create_all(bind=engine)
