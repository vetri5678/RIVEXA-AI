"""Authentication request/response schemas."""

from datetime import datetime
from typing import Optional

from pydantic import BaseModel, EmailStr, Field


class UserRegisterRequest(BaseModel):
    email: EmailStr
    username: str = Field(..., min_length=3, max_length=100)
    password: str = Field(..., min_length=8)
    full_name: Optional[str] = None

    model_config = {"json_schema_extra": {"example": {
        "email": "analyst@company.com",
        "username": "janedoe",
        "password": "SecurePass1!",
        "full_name": "Jane Doe",
    }}}


class UserLoginRequest(BaseModel):
    email: EmailStr
    password: str

    model_config = {"json_schema_extra": {"example": {
        "email": "admin@riskvision.ai",
        "password": "Admin@123456",
    }}}


class TokenResponse(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    expires_in: int


class RefreshTokenRequest(BaseModel):
    refresh_token: str


class PasswordResetRequest(BaseModel):
    email: EmailStr


class PasswordResetConfirm(BaseModel):
    token: str
    new_password: str = Field(..., min_length=8)


class ChangePasswordRequest(BaseModel):
    current_password: str
    new_password: str = Field(..., min_length=8)


class UserResponse(BaseModel):
    id: str
    email: str
    username: str
    full_name: Optional[str]
    role: str
    is_active: bool
    is_verified: bool
    last_login: Optional[datetime]
    created_at: datetime

    model_config = {"from_attributes": True}


class UserUpdateRequest(BaseModel):
    full_name: Optional[str] = None
    role: Optional[str] = None
    is_active: Optional[bool] = None


class MessageResponse(BaseModel):
    message: str
    detail: Optional[str] = None
