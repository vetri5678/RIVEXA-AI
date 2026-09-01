"""Authentication service: registration, login, tokens, password management."""

import logging
from datetime import datetime, timezone
from typing import Optional

from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from core.config import get_settings
from core.permissions import UserRole
from core.security import (
    create_access_token,
    create_refresh_token,
    decode_token,
    generate_verification_token,
    hash_password,
    validate_password_strength,
    verify_password,
)
from models.token import RefreshToken, RevokedToken
from models.user import User
from services.audit_service import AuditService

settings = get_settings()


class AuthService:
    """Handles user authentication lifecycle."""

    @staticmethod
    def register(
        db: Session,
        email: str,
        username: str,
        password: str,
        full_name: Optional[str] = None,
        ip_address: Optional[str] = None,
    ) -> User:
        is_valid, msg = validate_password_strength(password)
        if not is_valid:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=msg)

        email_normalized = email.strip().lower()
        if db.query(User).filter(User.email == email_normalized).first():
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Email already registered.")
        if db.query(User).filter(User.username == username).first():
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Username already taken.")

        user = User(
            email=email_normalized,
            username=username,
            hashed_password=hash_password(password),
            full_name=full_name,
            role=UserRole.VIEWER.value,
            verification_token=generate_verification_token(),
        )
        db.add(user)
        db.commit()
        db.refresh(user)

        AuditService.log(
            db, action="user.register", user_id=user.id, ip_address=ip_address,
            resource_type="user", resource_id=user.id, description=f"User registered: {email}",
        )
        return user

    @staticmethod
    def login(
        db: Session,
        email: str,
        password: str,
        ip_address: Optional[str] = None,
        user_agent: Optional[str] = None,
    ) -> dict:
        logger = logging.getLogger("riskvision.auth")

        # 1. Check for missing email or password
        if not email:
            logger.warning("[Auth] Login failure: Missing email address.")
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid email or password.")
        if not password:
            logger.warning(f"[Auth] Login failure: Missing password for email: {email}")
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid email or password.")

        email_normalized = email.strip().lower()

        # 2. Database query with try-except to detect DB connection issues
        try:
            logger.info(f"[Auth] Querying user record for email: {email_normalized}")
            user = db.query(User).filter(User.email == email_normalized).first()
        except Exception as db_err:
            logger.error(f"[Auth] Login failure: Database connection or query failed: {str(db_err)}")
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="Invalid email or password."
            )

        # 3. Check if user exists
        if not user:
            logger.warning(f"[Auth] Login failure: User not found in database for email: {email_normalized}")
            AuditService.log(
                db, action="user.login", status="failure", ip_address=ip_address,
                description=f"Failed login: user not found ({email_normalized})",
            )
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid email or password.")

        # 4. Verify password hash
        logger.info(f"[Auth] Verifying password hash for user ID: {user.id}")
        if not user.hashed_password:
            logger.error(f"[Auth] Login failure: Password hash is null/empty for user ID: {user.id}")
            AuditService.log(
                db, action="user.login", status="failure", ip_address=ip_address,
                description=f"Failed login: null password hash for {email_normalized}",
            )
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid email or password.")

        # Verify using proper verify_password context method
        is_pwd_correct = False
        try:
            is_pwd_correct = verify_password(password, user.hashed_password)
        except Exception as pwd_err:
            logger.error(f"[Auth] Login failure: Exception during password hash verification for user {user.id}: {str(pwd_err)}")
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid email or password.")

        if not is_pwd_correct:
            logger.warning(f"[Auth] Login failure: Incorrect password for user ID: {user.id}")
            AuditService.log(
                db, action="user.login", status="failure", ip_address=ip_address,
                description=f"Failed login: incorrect password ({email_normalized})",
            )
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid email or password.")

        # 5. Check if user is active
        if not user.is_active:
            logger.warning(f"[Auth] Login failure: Account is deactivated for user ID: {user.id}")
            AuditService.log(
                db, action="user.login", status="failure", ip_address=ip_address,
                description=f"Failed login: account deactivated ({email_normalized})",
            )
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Account is deactivated.")

        logger.info(f"[Auth] Password verification successful. Generating session/JWT for user ID: {user.id}")
        user.last_login = datetime.now(timezone.utc)
        db.commit()

        access_token = create_access_token(user.id, {"role": user.role, "email": user.email})
        refresh_token_str, expires_at = create_refresh_token(user.id)

        try:
            refresh_record = RefreshToken(
                user_id=user.id,
                token=refresh_token_str,
                expires_at=expires_at,
                ip_address=ip_address,
                user_agent=user_agent,
            )
            db.add(refresh_record)
            db.commit()
        except Exception as token_db_err:
            logger.error(f"[Auth] Login failure: Failed to persist refresh token: {str(token_db_err)}")

        AuditService.log(
            db, action="user.login", user_id=user.id, ip_address=ip_address,
            resource_type="user", resource_id=user.id, description="User logged in successfully",
        )

        logger.info(f"[Auth] Login complete. Tokens generated for user ID: {user.id}")
        return {
            "access_token": access_token,
            "refresh_token": refresh_token_str,
            "token_type": "bearer",
            "expires_in": settings.access_token_expire_minutes * 60,
        }

    @staticmethod
    def refresh_access_token(db: Session, refresh_token: str) -> dict:
        payload = decode_token(refresh_token)
        if not payload or payload.get("type") != "refresh":
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid refresh token.")

        stored = db.query(RefreshToken).filter(
            RefreshToken.token == refresh_token,
            RefreshToken.is_revoked == False,
        ).first()
        if not stored or stored.expires_at < datetime.now(timezone.utc):
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Refresh token expired or revoked.")

        user = db.query(User).filter(User.id == payload["sub"], User.is_active == True).first()
        if not user:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="User not found.")

        access_token = create_access_token(user.id, {"role": user.role, "email": user.email})
        return {
            "access_token": access_token,
            "refresh_token": refresh_token,
            "token_type": "bearer",
            "expires_in": settings.access_token_expire_minutes * 60,
        }

    @staticmethod
    def logout(db: Session, refresh_token: str, user_id: str, ip_address: Optional[str] = None) -> None:
        stored = db.query(RefreshToken).filter(
            RefreshToken.token == refresh_token,
            RefreshToken.user_id == user_id,
        ).first()
        if stored:
            stored.is_revoked = True
            db.commit()

        AuditService.log(
            db, action="user.logout", user_id=user_id, ip_address=ip_address,
            description="User logged out",
        )

    @staticmethod
    def change_password(db: Session, user: User, current_password: str, new_password: str) -> None:
        if not verify_password(current_password, user.hashed_password):
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Current password is incorrect.")
        is_valid, msg = validate_password_strength(new_password)
        if not is_valid:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=msg)
        user.hashed_password = hash_password(new_password)
        db.commit()
        AuditService.log(
            db, action="user.password_change", user_id=user.id,
            resource_type="user", resource_id=user.id, description="Password changed",
        )

    @staticmethod
    def request_password_reset(db: Session, email: str) -> str:
        email_normalized = email.strip().lower()
        user = db.query(User).filter(User.email == email_normalized).first()
        if not user:
            return "If the email exists, a reset link has been sent."
        token = generate_verification_token()
        user.reset_token = token
        user.reset_token_expires = datetime.now(timezone.utc) + __import__("datetime").timedelta(hours=1)
        db.commit()
        # Placeholder: in production, send email via SMTP
        AuditService.log(
            db, action="user.password_reset_request", user_id=user.id,
            description=f"Password reset requested for {email}",
        )
        return "If the email exists, a reset link has been sent."

    @staticmethod
    def confirm_password_reset(db: Session, token: str, new_password: str) -> None:
        user = db.query(User).filter(User.reset_token == token).first()
        if not user or (user.reset_token_expires and user.reset_token_expires < datetime.now(timezone.utc)):
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid or expired reset token.")
        is_valid, msg = validate_password_strength(new_password)
        if not is_valid:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=msg)
        user.hashed_password = hash_password(new_password)
        user.reset_token = None
        user.reset_token_expires = None
        db.commit()
        AuditService.log(
            db, action="user.password_reset", user_id=user.id,
            description="Password reset completed",
        )

    @staticmethod
    def verify_email(db: Session, token: str) -> None:
        user = db.query(User).filter(User.verification_token == token).first()
        if not user:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid verification token.")
        user.is_verified = True
        user.verification_token = None
        db.commit()

    @staticmethod
    def bootstrap_admin(db: Session) -> None:
        """Create default super admin if no admin user exists (idempotent & thread-safe)."""
        logger = logging.getLogger("riskvision.auth")
        email_normalized = settings.bootstrap_admin_email.strip().lower()

        # Check if admin with email or username already exists
        existing_admin = db.query(User).filter(
            (User.email == email_normalized) | (User.username == "admin")
        ).first()

        if existing_admin:
            logger.info("[Bootstrap] Admin user already exists (id=%s, email=%s). Skipping bootstrap.", existing_admin.id, existing_admin.email)
            return

        try:
            admin = User(
                email=email_normalized,
                username="admin",
                hashed_password=hash_password(settings.bootstrap_admin_password),
                full_name="System Administrator",
                role=UserRole.SUPER_ADMIN.value,
                is_active=True,
                is_verified=True,
            )
            db.add(admin)
            db.commit()
            logger.info("[Bootstrap] Default super admin created successfully (email=%s).", email_normalized)
        except Exception as exc:
            db.rollback()
            logger.warning("[Bootstrap] Bypassed admin bootstrap (already created or constraint collision): %s", exc)
