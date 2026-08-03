"""Authentication API router."""

from typing import List

from fastapi import APIRouter, Depends, Request
from sqlalchemy.orm import Session

from core.database import get_db
from core.dependencies import get_client_ip, get_current_user, require_permission, require_role
from core.permissions import Permission, UserRole
from models.user import User
from schemas.auth import (
    ChangePasswordRequest,
    MessageResponse,
    PasswordResetConfirm,
    PasswordResetRequest,
    RefreshTokenRequest,
    TokenResponse,
    UserLoginRequest,
    UserRegisterRequest,
    UserResponse,
    UserUpdateRequest,
)
from services.auth_service import AuthService

router = APIRouter(prefix="/auth", tags=["Authentication"])


@router.post("/register", response_model=UserResponse, status_code=201,
             summary="Register a new user account")
def register(payload: UserRegisterRequest, request: Request, db: Session = Depends(get_db)):
    """Create a new user account with default Viewer role."""
    user = AuthService.register(
        db, payload.email, payload.username, payload.password,
        payload.full_name, get_client_ip(request),
    )
    return user


@router.post("/login", response_model=TokenResponse, summary="Authenticate and receive JWT tokens")
def login(payload: UserLoginRequest, request: Request, db: Session = Depends(get_db)):
    """Login with email and password. Returns access and refresh tokens."""
    tokens = AuthService.login(
        db, payload.email, payload.password,
        ip_address=get_client_ip(request),
        user_agent=request.headers.get("User-Agent"),
    )
    return tokens


@router.post("/refresh", response_model=TokenResponse, summary="Refresh access token")
def refresh_token(payload: RefreshTokenRequest, db: Session = Depends(get_db)):
    """Exchange a valid refresh token for a new access token."""
    return AuthService.refresh_access_token(db, payload.refresh_token)


@router.post("/logout", response_model=MessageResponse, summary="Secure logout")
def logout(payload: RefreshTokenRequest, request: Request,
           current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    """Revoke refresh token and log out."""
    AuthService.logout(db, payload.refresh_token, current_user.id, get_client_ip(request))
    return MessageResponse(message="Logged out successfully")


@router.post("/change-password", response_model=MessageResponse, summary="Change password")
def change_password(payload: ChangePasswordRequest, current_user: User = Depends(get_current_user),
                    db: Session = Depends(get_db)):
    """Change password for the authenticated user."""
    AuthService.change_password(db, current_user, payload.current_password, payload.new_password)
    return MessageResponse(message="Password changed successfully")


@router.post("/password-reset", response_model=MessageResponse, summary="Request password reset")
def request_password_reset(payload: PasswordResetRequest, db: Session = Depends(get_db)):
    """Request a password reset email (placeholder — token returned in dev mode)."""
    msg = AuthService.request_password_reset(db, payload.email)
    return MessageResponse(message=msg)


@router.post("/password-reset/confirm", response_model=MessageResponse, summary="Confirm password reset")
def confirm_password_reset(payload: PasswordResetConfirm, db: Session = Depends(get_db)):
    """Reset password using a valid reset token."""
    AuthService.confirm_password_reset(db, payload.token, payload.new_password)
    return MessageResponse(message="Password reset successfully")


@router.get("/verify/{token}", response_model=MessageResponse, summary="Verify email address")
def verify_email(token: str, db: Session = Depends(get_db)):
    """Verify user email using verification token (optional placeholder)."""
    AuthService.verify_email(db, token)
    return MessageResponse(message="Email verified successfully")


@router.get("/me", response_model=UserResponse, summary="Get current user profile")
def get_me(current_user: User = Depends(get_current_user)):
    """Return the authenticated user's profile."""
    return current_user


@router.get("/users", response_model=List[UserResponse], summary="List all users (admin)")
def list_users(current_user: User = Depends(require_permission(Permission.USER_READ)),
               db: Session = Depends(get_db)):
    """List all registered users. Requires user:read permission."""
    return db.query(User).order_by(User.created_at.desc()).all()


@router.patch("/users/{user_id}", response_model=UserResponse, summary="Update user (admin)")
def update_user(user_id: str, payload: UserUpdateRequest,
                current_user: User = Depends(require_permission(Permission.USER_UPDATE)),
                db: Session = Depends(get_db)):
    """Update user profile or role. Requires user:update permission."""
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        from fastapi import HTTPException, status
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found.")
    if payload.role and UserRole(current_user.role) != UserRole.SUPER_ADMIN:
        from fastapi import HTTPException, status
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Only super admin can change roles.")
    for field, value in payload.model_dump(exclude_unset=True).items():
        setattr(user, field, value)
    db.commit()
    db.refresh(user)
    return user
