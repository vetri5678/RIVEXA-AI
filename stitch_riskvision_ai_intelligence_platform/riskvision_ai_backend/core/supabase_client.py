"""
Supabase Python client — singleton for Storage, Auth, and Realtime access.

SQLAlchemy (core/database.py) handles all direct SQL operations.
This client is used for Supabase Storage uploads, Auth admin actions,
and any Realtime subscriptions that bypass the REST/SQL layer.
"""

import logging
from functools import lru_cache
from typing import Optional

logger = logging.getLogger("riskvision.supabase")


@lru_cache(maxsize=1)
def get_supabase_client():
    """
    Return a cached Supabase client instance.

    Uses SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY from the environment
    (loaded via core/config.py → .env).

    Returns None gracefully if the supabase package is not installed or
    the credentials are missing, so the rest of the app keeps running.
    """
    try:
        from supabase import create_client, Client
        from core.config import get_settings

        settings = get_settings()

        url: str = settings.supabase_url
        key: str = settings.supabase_service_role_key

        if not url or not key or "YOUR_PROJECT" in url:
            logger.warning(
                "[Supabase] SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY not configured — "
                "direct Supabase SDK calls will be unavailable."
            )
            return None

        client: Client = create_client(url, key)
        logger.info("[Supabase] Client initialised -> %s", url)
        return client

    except ImportError:
        logger.warning(
            "[Supabase] 'supabase' package not installed. "
            "Run: pip install supabase>=2.0.0"
        )
        return None
    except OSError as exc:
        # WinError 10054 — transient socket reset on Windows; client is still usable
        logger.warning("[Supabase] Transient socket error during init (harmless): %s", exc)
        try:
            from supabase import create_client
            from core.config import get_settings
            settings = get_settings()
            return create_client(settings.supabase_url, settings.supabase_service_role_key)
        except Exception:
            return None
    except Exception as exc:
        logger.error("[Supabase] Failed to create client: %s", exc)
        return None


def get_supabase_anon_client():
    """
    Return a Supabase client using the anon key (for client-facing operations).
    Anon key has RLS-restricted access — use service role key for admin ops.
    """
    try:
        from supabase import create_client, Client
        from core.config import get_settings

        settings = get_settings()

        url: str = settings.supabase_url
        key: str = settings.supabase_anon_key

        if not url or not key:
            return None

        client: Client = create_client(url, key)
        return client

    except Exception as exc:
        logger.error("[Supabase] Failed to create anon client: %s", exc)
        return None
