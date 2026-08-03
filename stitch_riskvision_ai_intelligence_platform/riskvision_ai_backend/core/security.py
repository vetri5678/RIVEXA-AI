"""
Security utilities: password hashing, JWT creation/validation, token generation.
"""

import secrets
from datetime import datetime, timedelta, timezone
from typing import Any, Dict, Optional

from jose import JWTError, jwt
from passlib.context import CryptContext

from core.config import get_settings

settings = get_settings()

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto", bcrypt__rounds=settings.bcrypt_rounds)


def verify_password(plain_password: str, hashed_password: str) -> bool:
    """Verify a plain-text password against its bcrypt hash."""
    plain_truncated = plain_password.encode("utf-8")[:72].decode("utf-8", errors="ignore")
    return pwd_context.verify(plain_truncated, hashed_password)


def hash_password(password: str) -> str:
    """Hash a password using bcrypt. Truncates to 72 bytes (bcrypt hard limit)."""
    # bcrypt silently truncates or raises ValueError for passwords > 72 bytes
    password_bytes = password.encode("utf-8")[:72].decode("utf-8", errors="ignore")
    return pwd_context.hash(password_bytes)


def validate_password_strength(password: str) -> tuple[bool, str]:
    """
    Enforce password policy: min length, upper, lower, digit, special char.
    Returns (is_valid, error_message).
    """
    if len(password) < settings.password_min_length:
        return False, f"Password must be at least {settings.password_min_length} characters."

    checks = [
        (any(c.isupper() for c in password), "Password must contain at least one uppercase letter."),
        (any(c.islower() for c in password), "Password must contain at least one lowercase letter."),
        (any(c.isdigit() for c in password), "Password must contain at least one digit."),
        (any(c in "!@#$%^&*()_+-=[]{}|;:,.<>?" for c in password), "Password must contain at least one special character."),
    ]
    for passed, message in checks:
        if not passed:
            return False, message
    return True, ""


def create_access_token(subject: str, extra_claims: Optional[Dict[str, Any]] = None) -> str:
    """Create a signed JWT access token."""
    expire = datetime.now(timezone.utc) + timedelta(minutes=settings.access_token_expire_minutes)
    payload = {"sub": subject, "exp": expire, "type": "access"}
    if extra_claims:
        payload.update(extra_claims)
    return jwt.encode(payload, settings.secret_key, algorithm=settings.jwt_algorithm)


def create_refresh_token(subject: str) -> tuple[str, datetime]:
    """Create a signed JWT refresh token. Returns (token, expiry_datetime)."""
    expire = datetime.now(timezone.utc) + timedelta(days=settings.refresh_token_expire_days)
    payload = {"sub": subject, "exp": expire, "type": "refresh", "jti": secrets.token_urlsafe(16)}
    token = jwt.encode(payload, settings.secret_key, algorithm=settings.jwt_algorithm)
    return token, expire


def decode_token(token: str) -> Optional[Dict[str, Any]]:
    """Decode and validate a JWT token. Supports tokens from both the native Python
    backend and the Spring Boot backend (dual-decode strategy)."""
    # 1. Try native Python secret first
    try:
        return jwt.decode(token, settings.secret_key, algorithms=[settings.jwt_algorithm])
    except JWTError:
        pass

    # 2. Try Spring Boot secret (raw bytes — matches Java's Keys.hmacShaKeyFor(string.getBytes()))
    try:
        sb_key = settings.springboot_jwt_secret
        # Java's String.getBytes() returns UTF-8 bytes; python-jose can accept bytes as key
        return jwt.decode(token, sb_key.encode("utf-8"), algorithms=[settings.jwt_algorithm])
    except JWTError:
        pass

    # 3. Try Spring Boot secret as plain string (in case secrets happen to match format)
    try:
        sb_key = settings.springboot_jwt_secret
        return jwt.decode(token, sb_key, algorithms=[settings.jwt_algorithm])
    except JWTError:
        return None


def generate_verification_token() -> str:
    """Generate a secure random token for email verification or password reset."""
    return secrets.token_urlsafe(32)


def mask_sensitive(value: str, visible_chars: int = 4) -> str:
    """Mask sensitive strings for logging (e.g., email addresses)."""
    if not value or len(value) <= visible_chars:
        return "****"
    return value[:visible_chars] + "*" * (len(value) - visible_chars)
