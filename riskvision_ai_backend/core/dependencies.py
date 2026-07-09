"""
FastAPI authentication dependencies and route guards.
"""

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


async def get_current_user_optional(
    credentials: Optional[HTTPAuthorizationCredentials] = Depends(security_scheme),
    db: Session = Depends(get_db),
) -> Optional[User]:
    """Return current user if authenticated, else None."""
    if not credentials:
        return None
    payload = decode_token(credentials.credentials)
    if not payload or payload.get("type") != "access":
        return None
    jti = payload.get("jti")
    if jti and db.query(RevokedToken).filter(RevokedToken.jti == jti).first():
        return None
    user = db.query(User).filter(User.id == payload.get("sub"), User.is_active == True).first()
    return user


async def get_current_user(
    credentials: Optional[HTTPAuthorizationCredentials] = Depends(security_scheme),
    db: Session = Depends(get_db),
) -> User:
    """Require authenticated user."""
    if not credentials:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Not authenticated")
    payload = decode_token(credentials.credentials)
    if not payload or payload.get("type") != "access":
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid or expired token")
    jti = payload.get("jti")
    if jti and db.query(RevokedToken).filter(RevokedToken.jti == jti).first():
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Token has been revoked")
    user = db.query(User).filter(User.id == payload.get("sub"), User.is_active == True).first()
    if not user:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="User not found or inactive")
    return user


def require_role(*roles: UserRole):
    """Dependency factory requiring one of the specified roles."""

    async def role_checker(current_user: User = Depends(get_current_user)) -> User:
        if UserRole(current_user.role) not in roles:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail=f"Role '{current_user.role}' not authorized for this action.",
            )
        return current_user

    return role_checker


def require_permission(permission: Permission):
    """Dependency factory requiring a specific permission."""

    async def permission_checker(current_user: User = Depends(get_current_user)) -> User:
        if not role_has_permission(UserRole(current_user.role), permission):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail=f"Missing permission: {permission.value}",
            )
        return current_user

    return permission_checker


def get_client_ip(request: Request) -> str:
    """Extract client IP, respecting X-Forwarded-For header."""
    forwarded = request.headers.get("X-Forwarded-For")
    if forwarded:
        return forwarded.split(",")[0].strip()
    return request.client.host if request.client else "unknown"
