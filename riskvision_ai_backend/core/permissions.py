"""
Role-Based Access Control (RBAC) permission definitions and validation.
"""

from enum import Enum
from functools import wraps
from typing import Callable, Set

from fastapi import HTTPException, status


class UserRole(str, Enum):
    SUPER_ADMIN = "super_admin"
    ADMINISTRATOR = "administrator"
    PROJECT_MANAGER = "project_manager"
    RISK_ANALYST = "risk_analyst"
    DATA_SCIENTIST = "data_scientist"
    VIEWER = "viewer"


class Permission(str, Enum):
    # User management
    USER_CREATE = "user:create"
    USER_READ = "user:read"
    USER_UPDATE = "user:update"
    USER_DELETE = "user:delete"
    USER_MANAGE_ROLES = "user:manage_roles"

    # Project management
    PROJECT_CREATE = "project:create"
    PROJECT_READ = "project:read"
    PROJECT_UPDATE = "project:update"
    PROJECT_DELETE = "project:delete"
    PROJECT_ARCHIVE = "project:archive"

    # Predictions
    PREDICTION_CREATE = "prediction:create"
    PREDICTION_READ = "prediction:read"
    PREDICTION_DELETE = "prediction:delete"

    # Analytics
    ANALYTICS_READ = "analytics:read"

    # Reports
    REPORT_GENERATE = "report:generate"
    REPORT_DOWNLOAD = "report:download"

    # Model / retraining
    MODEL_TRAIN = "model:train"
    MODEL_READ = "model:read"
    MODEL_ROLLBACK = "model:rollback"

    # Audit
    AUDIT_READ = "audit:read"

    # System
    SYSTEM_ADMIN = "system:admin"


ROLE_PERMISSIONS: dict[UserRole, Set[Permission]] = {
    UserRole.SUPER_ADMIN: set(Permission),
    UserRole.ADMINISTRATOR: {
        Permission.USER_CREATE,
        Permission.USER_READ,
        Permission.USER_UPDATE,
        Permission.USER_DELETE,
        Permission.PROJECT_CREATE,
        Permission.PROJECT_READ,
        Permission.PROJECT_UPDATE,
        Permission.PROJECT_DELETE,
        Permission.PROJECT_ARCHIVE,
        Permission.PREDICTION_CREATE,
        Permission.PREDICTION_READ,
        Permission.PREDICTION_DELETE,
        Permission.ANALYTICS_READ,
        Permission.REPORT_GENERATE,
        Permission.REPORT_DOWNLOAD,
        Permission.MODEL_TRAIN,
        Permission.MODEL_READ,
        Permission.MODEL_ROLLBACK,
        Permission.AUDIT_READ,
    },
    UserRole.PROJECT_MANAGER: {
        Permission.PROJECT_CREATE,
        Permission.PROJECT_READ,
        Permission.PROJECT_UPDATE,
        Permission.PROJECT_ARCHIVE,
        Permission.PREDICTION_CREATE,
        Permission.PREDICTION_READ,
        Permission.ANALYTICS_READ,
        Permission.REPORT_GENERATE,
        Permission.REPORT_DOWNLOAD,
        Permission.MODEL_READ,
    },
    UserRole.RISK_ANALYST: {
        Permission.PROJECT_READ,
        Permission.PREDICTION_CREATE,
        Permission.PREDICTION_READ,
        Permission.ANALYTICS_READ,
        Permission.REPORT_GENERATE,
        Permission.REPORT_DOWNLOAD,
        Permission.MODEL_READ,
    },
    UserRole.DATA_SCIENTIST: {
        Permission.PROJECT_READ,
        Permission.PREDICTION_CREATE,
        Permission.PREDICTION_READ,
        Permission.ANALYTICS_READ,
        Permission.REPORT_GENERATE,
        Permission.REPORT_DOWNLOAD,
        Permission.MODEL_TRAIN,
        Permission.MODEL_READ,
        Permission.MODEL_ROLLBACK,
    },
    UserRole.VIEWER: {
        Permission.PROJECT_READ,
        Permission.PREDICTION_READ,
        Permission.ANALYTICS_READ,
        Permission.MODEL_READ,
    },
}


def role_has_permission(role: UserRole, permission: Permission) -> bool:
    """Check whether a role grants a specific permission."""
    return permission in ROLE_PERMISSIONS.get(role, set())


def require_permissions(*permissions: Permission) -> Callable:
    """
    Decorator for service methods requiring specific permissions.
    Expects `current_user` as first argument with a `.role` attribute.
    """

    def decorator(func: Callable) -> Callable:
        @wraps(func)
        def wrapper(current_user, *args, **kwargs):
            user_role = UserRole(current_user.role)
            for perm in permissions:
                if not role_has_permission(user_role, perm):
                    raise HTTPException(
                        status_code=status.HTTP_403_FORBIDDEN,
                        detail=f"Insufficient permissions. Required: {perm.value}",
                    )
            return func(current_user, *args, **kwargs)

        return wrapper

    return decorator
