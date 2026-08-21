"""
FastAPI authentication dependencies and route guards.
"""

import logging
from datetime import datetime, timezone
from typing import List, Optional

from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session

from core.database import get_db
from core.permissions import Permission, UserRole, role_has_permission
from core.security import decode_token
from models.token import RefreshToken, RevokedToken
from models.user import User

security_scheme = HTTPBearer(auto_error=False)
logger = logging.getLogger("riskvision.auth.deps")


def _resolve_user_from_payload(payload: dict, db: Session) -> Optional[User]:
    """Attempt to find an existing user from JWT payload claims.
    Searches by: sub (as id), sub (as email), email claim, username claim."""
    sub = payload.get("sub")
    user = None

    import uuid

    if sub:
        # Try sub as user ID if it is a valid UUID
        try:
            uuid.UUID(str(sub))
            user = db.query(User).filter(User.id == sub).first()
        except (ValueError, TypeError):
            user = None

        if not user:
            # Try sub as email or username
            user = db.query(User).filter((User.email == sub) | (User.username == sub)).first()

    if not user:
        email_claim = payload.get("email")
        if email_claim:
            user = db.query(User).filter(User.email == email_claim).first()

    return user


def _auto_provision_user(payload: dict, db: Session) -> Optional[User]:
    """Auto-provision a user from JWT claims when the user exists in the
    Spring Boot database but not in the Python database. This bridges the
    gap between the two separate backends sharing authentication."""
    email = payload.get("email") or payload.get("sub")
    if not email or "@" not in str(email):
        return None

    role = payload.get("role", "viewer")
    # Derive username from email prefix
    username = email.split("@")[0]

    # Ensure uniqueness of username
    existing = db.query(User).filter(User.username == username).first()
    if existing:
        username = f"{username}_{email.split('@')[0][:4]}"

    try:
        from core.security import hash_password
        user = User(
            email=email,
            username=username,
            hashed_password=hash_password("auto_provisioned_placeholder_not_for_login"),
            full_name=username.replace("_", " ").title(),
            role=role if role in [r.value for r in UserRole] else UserRole.VIEWER.value,
            is_active=True,
            is_verified=True,
        )
        db.add(user)
        db.commit()
        db.refresh(user)
        logger.info(f"[Auth] Auto-provisioned user from JWT: {email} (role={role})")
        return user
    except Exception as e:
        db.rollback()
        logger.error(f"[Auth] Failed to auto-provision user {email}: {e}")
        return None


async def get_current_user_optional(
    credentials: Optional[HTTPAuthorizationCredentials] = Depends(security_scheme),
    db: Session = Depends(get_db),
) -> Optional[User]:
    """Return authenticated user if valid token exists, else return None (no anonymous admin fallback)."""
    if credentials and credentials.credentials:
        payload = decode_token(credentials.credentials)
        if payload:
            user = _resolve_user_from_payload(payload, db)
            if user:
                return user
            # Auto-provision from JWT claims
            user = _auto_provision_user(payload, db)
            if user:
                return user

    # No valid token — return None. Do NOT fall back to a mock admin.
    return None


async def get_current_user(
    credentials: Optional[HTTPAuthorizationCredentials] = Depends(security_scheme),
    db: Session = Depends(get_db),
) -> User:
    """Require authenticated user (returns 401 if missing/invalid)."""
    if not credentials or not credentials.credentials:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Not authenticated",
            headers={"WWW-Authenticate": "Bearer"},
        )
    
    payload = decode_token(credentials.credentials)
    if not payload:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired token",
            headers={"WWW-Authenticate": "Bearer"},
        )
    
    sub = payload.get("sub")
    if not sub:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid token payload",
            headers={"WWW-Authenticate": "Bearer"},
        )
    
    # Try to find the user by various claims
    user = _resolve_user_from_payload(payload, db)
    
    # Auto-provision if not found (user exists in Spring Boot DB but not in Python DB)
    if not user:
        user = _auto_provision_user(payload, db)

    if not user:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="User not found and auto-provisioning failed. Please re-register.",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return user


# ── Role alias mapping ─────────────────────────────────────────────────────────
# Spring Boot stores roles as: admin, manager, analyst, viewer, user
# FastAPI UserRole enum uses:  administrator, project_manager, risk_analyst, viewer
_SPRING_ROLE_ALIAS: dict[str, UserRole] = {
    "admin": UserRole.ADMINISTRATOR,
    "super_admin": UserRole.SUPER_ADMIN,
    "administrator": UserRole.ADMINISTRATOR,
    "manager": UserRole.PROJECT_MANAGER,
    "project_manager": UserRole.PROJECT_MANAGER,
    "analyst": UserRole.RISK_ANALYST,
    "risk_analyst": UserRole.RISK_ANALYST,
    "data_scientist": UserRole.DATA_SCIENTIST,
    "viewer": UserRole.VIEWER,
    "user": UserRole.VIEWER,
}


def _resolve_user_role(raw_role: str) -> UserRole:
    """Convert Spring Boot role strings to FastAPI UserRole enum values."""
    normalized = (raw_role or "viewer").lower().strip()
    return _SPRING_ROLE_ALIAS.get(normalized, UserRole.VIEWER)


def require_role(*roles: UserRole):
    """Dependency factory requiring one of the specified UserRole enum values."""

    async def role_checker(current_user: User = Depends(get_current_user)) -> User:
        user_role = _resolve_user_role(current_user.role)
        if user_role not in roles:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail=f"Role '{current_user.role}' does not have access. Required one of: {[r.value for r in roles]}",
            )
        return current_user

    return role_checker


def require_permission(permission: Permission):
    """Dependency factory requiring a specific permission based on the user's role."""

    async def permission_checker(current_user: User = Depends(get_current_user)) -> User:
        user_role = _resolve_user_role(current_user.role)
        if not role_has_permission(user_role, permission):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail=f"Permission '{permission.value}' is required. Your role '{current_user.role}' does not have this permission.",
            )
        return current_user

    return permission_checker


def get_client_ip(request: Request) -> str:
    """Extract client IP, respecting X-Forwarded-For header."""
    forwarded = request.headers.get("X-Forwarded-For")
    if forwarded:
        return forwarded.split(",")[0].strip()
    return request.client.host if request.client else "unknown"
