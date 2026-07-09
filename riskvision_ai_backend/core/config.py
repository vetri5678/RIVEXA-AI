"""
Application configuration with environment-based settings.

Supports development (SQLite) and production (PostgreSQL) deployments.
"""

import os
from functools import lru_cache
from pathlib import Path
from typing import List, Optional

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


BASE_DIR = Path(__file__).resolve().parent.parent


class Settings(BaseSettings):
    """Centralized application settings loaded from environment variables."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    # Application
    app_name: str = "RiskVision AI — Software Project Graveyard Analyzer"
    app_version: str = "2.0.0"
    api_v1_prefix: str = "/api/v1"
    environment: str = Field(default="development", alias="ENVIRONMENT")
    debug: bool = Field(default=False, alias="DEBUG")

    # Database
    database_url: str = Field(
        default=f"sqlite:///{BASE_DIR / 'data' / 'riskvision.db'}",
        alias="DATABASE_URL",
    )
    db_pool_size: int = Field(default=5, alias="DB_POOL_SIZE")
    db_max_overflow: int = Field(default=10, alias="DB_MAX_OVERFLOW")
    db_echo: bool = Field(default=False, alias="DB_ECHO")

    # Security
    secret_key: str = Field(
        default="change-me-in-production-use-openssl-rand-hex-32",
        alias="SECRET_KEY",
    )
    jwt_algorithm: str = "HS256"
    access_token_expire_minutes: int = Field(default=30, alias="ACCESS_TOKEN_EXPIRE_MINUTES")
    refresh_token_expire_days: int = Field(default=7, alias="REFRESH_TOKEN_EXPIRE_DAYS")
    password_min_length: int = 8
    bcrypt_rounds: int = 12

    # CORS
    cors_origins: List[str] = Field(
        default=["http://localhost:5173", "http://127.0.0.1:5173", "http://localhost:3000"],
        alias="CORS_ORIGINS",
    )
    cors_allow_credentials: bool = True

    # Rate limiting
    rate_limit_requests: int = Field(default=100, alias="RATE_LIMIT_REQUESTS")
    rate_limit_window_seconds: int = Field(default=60, alias="RATE_LIMIT_WINDOW_SECONDS")

    # Request limits
    max_upload_size_mb: int = Field(default=50, alias="MAX_UPLOAD_SIZE_MB")

    # Email (placeholder for verification / password reset)
    smtp_host: Optional[str] = Field(default=None, alias="SMTP_HOST")
    smtp_port: int = Field(default=587, alias="SMTP_PORT")
    smtp_user: Optional[str] = Field(default=None, alias="SMTP_USER")
    smtp_password: Optional[str] = Field(default=None, alias="SMTP_PASSWORD")
    email_from: str = Field(default="noreply@riskvision.ai", alias="EMAIL_FROM")
    frontend_url: str = Field(default="http://localhost:5173", alias="FRONTEND_URL")

    # Logging
    log_level: str = Field(default="INFO", alias="LOG_LEVEL")
    log_dir: Path = Field(default=BASE_DIR / "logs", alias="LOG_DIR")
    log_rotation_days: int = Field(default=1, alias="LOG_ROTATION_DAYS")
    log_retention_days: int = Field(default=30, alias="LOG_RETENTION_DAYS")

    # Model retraining
    retraining_schedule_cron: str = Field(default="0 2 * * 0", alias="RETRAINING_SCHEDULE_CRON")
    auto_retraining_enabled: bool = Field(default=False, alias="AUTO_RETRAINING_ENABLED")

    # Default admin bootstrap
    bootstrap_admin_email: str = Field(default="admin@riskvision.ai", alias="BOOTSTRAP_ADMIN_EMAIL")
    bootstrap_admin_password: str = Field(default="Admin@123456", alias="BOOTSTRAP_ADMIN_PASSWORD")

    @field_validator("cors_origins", mode="before")
    @classmethod
    def parse_cors_origins(cls, v):
        if isinstance(v, str):
            return [origin.strip() for origin in v.split(",") if origin.strip()]
        return v

    @property
    def is_production(self) -> bool:
        return self.environment.lower() == "production"

    @property
    def is_sqlite(self) -> bool:
        return self.database_url.startswith("sqlite")

    @property
    def max_upload_bytes(self) -> int:
        return self.max_upload_size_mb * 1024 * 1024


@lru_cache
def get_settings() -> Settings:
    """Return cached settings singleton."""
    return Settings()
